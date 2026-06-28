package com.ella.music.data

import android.content.Context
import android.util.Log
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SongPlaybackStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

data class PlaybackHistoryEntry(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long
)

class PlaybackStatsStore private constructor(context: Context) {
    private val statsFile = File(context.applicationContext.filesDir, "playback_stats.json")
    private val historyFile = File(context.applicationContext.filesDir, "playback_history.json")
    private val dailyStatsFile = File(context.applicationContext.filesDir, "playback_daily_stats.json")
    private val _stats = MutableStateFlow<List<SongPlaybackStats>>(emptyList())
    val stats: StateFlow<List<SongPlaybackStats>> = _stats.asStateFlow()
    private val _history = MutableStateFlow<List<PlaybackHistoryEntry>>(emptyList())
    val history: StateFlow<List<PlaybackHistoryEntry>> = _history.asStateFlow()
    private val _dailyListenMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dailyListenMs: StateFlow<Map<String, Long>> = _dailyListenMs.asStateFlow()

    init {
        loadStats()
        loadHistory()
        loadDailyStats()
    }

    suspend fun recordPlay(song: Song) {
        val now = System.currentTimeMillis()
        update(song) { current ->
            current.copy(
                playCount = current.playCount + 1,
                lastPlayedAt = now
            )
        }
        appendHistory(song, now)
    }

    suspend fun addListenTime(song: Song, listenedMs: Long) {
        if (listenedMs <= 0) return
        val now = System.currentTimeMillis()
        update(song) { current ->
            current.copy(
                listenedMs = current.listenedMs + listenedMs,
                lastPlayedAt = now
            )
        }
        addDailyListenTime(now, listenedMs)
    }

    suspend fun exportJson(librarySongs: List<Song> = emptyList()): JSONObject = withContext(Dispatchers.IO) {
        JSONObject()
            .put("stats", statsToJson(_stats.value))
            .put("history", historyToJson(_history.value))
            .put("dailyListenMs", dailyStatsToJson(_dailyListenMs.value))
            .put("sessions", historyToSollinSessions(_history.value, _stats.value, librarySongs))
    }

    suspend fun restoreJson(payload: JSONObject) = withContext(Dispatchers.IO) {
        if (payload.has("sessions")) {
            restoreSollinSessions(payload.optJSONArray("sessions") ?: JSONArray())
            return@withContext
        }
        val stats = payload.optJSONArray("stats")?.toStatsList().orEmpty()
        val history = payload.optJSONArray("history")?.toHistoryList().orEmpty()
        val daily = payload.optJSONObject("dailyListenMs")?.toDailyStatsMap().orEmpty()

        _stats.value = stats
        _history.value = history
        _dailyListenMs.value = daily.toSortedMap()

        save(stats)
        saveHistory(history)
        saveDailyStats(daily)
    }

    private suspend fun update(
        song: Song,
        transform: (SongPlaybackStats) -> SongPlaybackStats
    ) = withContext(Dispatchers.IO) {
        val current = _stats.value.associateBy { it.songId }.toMutableMap()
        val existing = current[song.id] ?: SongPlaybackStats(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playCount = 0,
            listenedMs = 0L,
            lastPlayedAt = 0L
        )
        current[song.id] = transform(
            existing.copy(
                title = song.title,
                artist = song.artist,
                album = song.album
            )
        )
        val sorted = current.values.sortedByDescending { it.lastPlayedAt }
        _stats.value = sorted
        save(sorted)
    }

    private suspend fun appendHistory(song: Song, playedAt: Long) = withContext(Dispatchers.IO) {
        val updated = (listOf(
            PlaybackHistoryEntry(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                playedAt = playedAt
            )
        ) + _history.value)
            .distinctBy { "${it.songId}:${it.playedAt}" }
        _history.value = updated
        saveHistory(updated)
    }

    /**
     * Removes a single playback history entry identified by (songId, playedAt). Lets the user
     * delete a problematic history record (e.g. a song that no longer exists in the library or
     * one whose cover art pollutes the artwork cache) without clearing the whole history.
     */
    suspend fun removeHistoryEntry(entry: PlaybackHistoryEntry) = withContext(Dispatchers.IO) {
        val updated = _history.value.filterNot { it.songId == entry.songId && it.playedAt == entry.playedAt }
        _history.value = updated
        saveHistory(updated)
    }

    private suspend fun addDailyListenTime(timestampMs: Long, listenedMs: Long) = withContext(Dispatchers.IO) {
        val key = timestampMs.toDateKey()
        val updated = _dailyListenMs.value.toMutableMap()
        updated[key] = (updated[key] ?: 0L) + listenedMs
        _dailyListenMs.value = updated.toSortedMap()
        saveDailyStats(_dailyListenMs.value)
    }

    private fun loadStats() {
        if (!statsFile.exists()) return
        runCatching {
            val array = JSONArray(statsFile.readText())
            _stats.value = array.toStatsList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback stats", it)
        }
    }

    private fun loadHistory() {
        if (!historyFile.exists()) return
        runCatching {
            val array = JSONArray(historyFile.readText())
            _history.value = array.toHistoryList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback history", it)
        }
    }

    private fun loadDailyStats() {
        if (!dailyStatsFile.exists()) return
        runCatching {
            val payload = JSONObject(dailyStatsFile.readText())
            _dailyListenMs.value = payload.toDailyStatsMap().toSortedMap()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load daily playback stats", it)
        }
    }

    private fun save(stats: List<SongPlaybackStats>) {
        runCatching {
            statsFile.writeText(statsToJson(stats).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save playback stats", it)
        }
    }

    private fun saveHistory(history: List<PlaybackHistoryEntry>) {
        runCatching {
            historyFile.writeText(historyToJson(history).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save playback history", it)
        }
    }

    private fun saveDailyStats(dailyStats: Map<String, Long>) {
        runCatching {
            dailyStatsFile.writeText(dailyStatsToJson(dailyStats).toString())
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to save daily playback stats", it)
        }
    }

    private fun statsToJson(stats: List<SongPlaybackStats>): JSONArray {
        val array = JSONArray()
        stats.forEach { stat ->
            array.put(
                JSONObject()
                    .put("songId", stat.songId)
                    .put("title", stat.title)
                    .put("artist", stat.artist)
                    .put("album", stat.album)
                    .put("playCount", stat.playCount)
                    .put("listenedMs", stat.listenedMs)
                    .put("lastPlayedAt", stat.lastPlayedAt)
            )
        }
        return array
    }

    private fun historyToJson(history: List<PlaybackHistoryEntry>): JSONArray {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
                JSONObject()
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("playedAt", entry.playedAt)
            )
        }
        return array
    }

    private fun dailyStatsToJson(dailyStats: Map<String, Long>): JSONObject {
        val payload = JSONObject()
        dailyStats.forEach { (date, listenedMs) ->
            payload.put(date, listenedMs)
        }
        return payload
    }

    private fun historyToSollinSessions(
        history: List<PlaybackHistoryEntry>,
        stats: List<SongPlaybackStats>,
        librarySongs: List<Song>
    ): JSONArray {
        val statBySong = stats.associateBy { it.songId }
        val libraryById = librarySongs.associateBy { it.id }
        val libraryByFingerprint = librarySongs.associateBy { it.statsFingerprint() }
        val array = JSONArray()
        history.forEach { entry ->
            val stat = statBySong[entry.songId]
            val song = libraryById[entry.songId] ?: libraryByFingerprint[entry.statsFingerprint()]
            val averagePlayedMs = stat?.let {
                if (it.playCount > 0) it.listenedMs / it.playCount else it.listenedMs
            } ?: 0L
            val durationMs = song?.duration ?: 0L
            val playedMs = averagePlayedMs
                .takeIf { it > 0L }
                ?: durationMs.takeIf { it > 0L }
                ?: DEFAULT_SOLIN_SESSION_PLAYED_MS
            val endedAtMs = entry.playedAt
            val startedAtMs = (endedAtMs - playedMs).coerceAtLeast(1L)
            array.put(
                JSONObject()
                    .put("uid", "${entry.songId}|${entry.playedAt}")
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("albumKey", song?.let { "id:${it.albumId.takeIf { id -> id > 0L } ?: it.albumIdentityId()}" }.orEmpty())
                    .put("durationMs", durationMs)
                    .put("playedMs", playedMs)
                    .put("startedAtMs", startedAtMs)
                    .put("endedAtMs", endedAtMs)
                    .put("dayBucket", endedAtMs.toDayBucket())
                    .put("cover", song?.statsCoverUri().orEmpty())
            )
        }
        return array
    }

    private fun JSONArray.toStatsList(): List<SongPlaybackStats> =
        List(length()) { index ->
            val item = getJSONObject(index)
            SongPlaybackStats(
                songId = item.getLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playCount = item.optInt("playCount"),
                listenedMs = item.optLong("listenedMs"),
                lastPlayedAt = item.optLong("lastPlayedAt")
            )
        }.sortedByDescending { it.lastPlayedAt }

    private fun JSONArray.toHistoryList(): List<PlaybackHistoryEntry> =
        List(length()) { index ->
            val item = getJSONObject(index)
            PlaybackHistoryEntry(
                songId = item.optLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playedAt = item.optLong("playedAt")
            )
        }.filter { it.playedAt > 0L }
            .sortedByDescending { it.playedAt }

    private fun JSONObject.toDailyStatsMap(): Map<String, Long> {
        val parsed = mutableMapOf<String, Long>()
        keys().forEach { key ->
            parsed[key] = optLong(key)
        }
        return parsed
    }

    private fun restoreSollinSessions(sessions: JSONArray) {
        val history = mutableListOf<PlaybackHistoryEntry>()
        val aggregates = linkedMapOf<String, SollinAggregate>()
        val daily = mutableMapOf<String, Long>()

        for (index in 0 until sessions.length()) {
            val item = sessions.optJSONObject(index) ?: continue
            val songId = item.optLong("songId")
            val title = item.optString("title")
            val artist = item.optString("artist")
            val album = item.optString("album")
            val startedAt = item.optLong("startedAtMs")
            val endedAt = item.optLong("endedAtMs").takeIf { it > 0L } ?: startedAt
            val playedMs = item.optLong("playedMs").coerceAtLeast(0L)
            val key = if (songId != 0L) "id:$songId" else "$title|$artist|$album"

            if (endedAt > 0L) {
                history += PlaybackHistoryEntry(
                    songId = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    playedAt = endedAt
                )
            }

            val aggregate = aggregates.getOrPut(key) {
                SollinAggregate(songId, title, artist, album)
            }
            aggregate.playCount += 1
            aggregate.listenedMs += playedMs
            aggregate.lastPlayedAt = maxOf(aggregate.lastPlayedAt, endedAt)

            val dayKey = item.optInt("dayBucket")
                .takeIf { it > 0 }
                ?.toDateKeyFromBucket()
                ?: endedAt.toDateKey()
            daily[dayKey] = (daily[dayKey] ?: 0L) + playedMs
        }

        val stats = aggregates.values.map { aggregate ->
            SongPlaybackStats(
                songId = aggregate.songId,
                title = aggregate.title,
                artist = aggregate.artist,
                album = aggregate.album,
                playCount = aggregate.playCount,
                listenedMs = aggregate.listenedMs,
                lastPlayedAt = aggregate.lastPlayedAt
            )
        }.sortedByDescending { it.lastPlayedAt }

        val sortedHistory = history
            .distinctBy { "${it.songId}:${it.playedAt}" }
            .sortedByDescending { it.playedAt }

        _stats.value = stats
        _history.value = sortedHistory
        _dailyListenMs.value = daily.toSortedMap()
        save(stats)
        saveHistory(sortedHistory)
        saveDailyStats(daily)
    }

    private fun Long.toDateKey(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = this
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun Long.toDayBucket(): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = this
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return year * 10000 + month * 100 + day
    }

    private fun Int.toDateKeyFromBucket(): String {
        val year = this / 10000
        val month = (this / 100) % 100
        val day = this % 100
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun Song.statsCoverUri(): String =
        coverUrl.takeIf { it.isNotBlank() }
            ?: albumId.takeIf { it > 0L }?.let { "content://media/external/audio/albumart/$it" }
            ?: ""

    private fun Song.statsFingerprint(): String =
        listOf(title, artist, album).joinToString("|") { it.statsKeyPart() }

    private fun PlaybackHistoryEntry.statsFingerprint(): String =
        listOf(title, artist, album).joinToString("|") { it.statsKeyPart() }

    private fun String.statsKeyPart(): String =
        trim().lowercase().replace(Regex("\\s+"), " ")

    private data class SollinAggregate(
        val songId: Long,
        val title: String,
        val artist: String,
        val album: String,
        var playCount: Int = 0,
        var listenedMs: Long = 0L,
        var lastPlayedAt: Long = 0L
    )

    companion object {
        private const val DEFAULT_SOLIN_SESSION_PLAYED_MS = 60_000L

        @Volatile
        private var instance: PlaybackStatsStore? = null

        fun getInstance(context: Context): PlaybackStatsStore {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
