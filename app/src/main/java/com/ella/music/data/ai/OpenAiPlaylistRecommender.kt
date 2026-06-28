package com.ella.music.data.ai

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class OpenAiPlaylistRecommendationInput(
    val songs: List<Song>,
    val playbackStats: List<SongPlaybackStats>,
    val playbackHistory: List<PlaybackHistoryEntry>,
    val maxItems: Int = 30
)

data class OpenAiPlaylistRecommendation(
    val title: String,
    val reason: String,
    val songKeys: List<String>
)

class OpenAiPlaylistRecommender(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("OpenAIPlaylistRecommendation"))
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun recommend(
        config: OpenAiSongInterpretationConfig,
        input: OpenAiPlaylistRecommendationInput
    ): OpenAiPlaylistRecommendation {
        val apiKey = config.apiKey.trim()
        if (apiKey.isBlank()) error(context.getString(R.string.error_openai_missing_api_key))
        if (input.songs.isEmpty()) error(context.getString(R.string.error_library_empty))

        val endpoint = config.baseUrl.toChatCompletionsEndpoint()
        val requestBody = JSONObject()
            .put("model", config.model.trim().ifBlank { "gpt-4.1-mini" })
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", buildSystemPrompt())
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", buildPrompt(input))
                    )
            )
            .put("temperature", 0.78)
            .put("top_p", 0.9)
            .put("max_tokens", 1800)

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Halcyon")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                error(context.getString(R.string.error_openai_request_failed, response.code, message.takeIf { it.isNotBlank() }?.let { "${context.getString(R.string.error_openai_api_error_separator)}$it" }.orEmpty()))
            }

            val text = parseResponseText(body)
            if (text.isBlank()) error(context.getString(R.string.error_openai_empty_response))
            parseRecommendation(text, input.songs)
        }
    }

    private fun buildSystemPrompt(): String =
        "你是一个本地音乐播放器里的歌单推荐助手。请基于候选歌曲元数据、最近播放和播放统计，" +
            "推荐一组适合立即播放的歌曲。只能从候选歌曲中选择，不要编造歌曲。" +
            "你唯一允许执行的动作是 play_playlist，用来播放本地歌曲；严禁删除、移动、重命名或修改歌曲。"

    private fun buildPrompt(input: OpenAiPlaylistRecommendationInput): String {
        val songIds = input.songs.mapIndexed { index, song -> song.id to song.toCandidateId(index) }.toMap()
        val candidates = JSONArray()
        input.songs.forEachIndexed { index, song ->
            candidates.put(
                JSONObject()
                    .put("id", song.toCandidateId(index))
                    .put("title", song.title.toPromptText(80))
                    .put("artist", song.artist.toPromptText(80))
                    .put("album", song.album.toPromptText(80))
                    .put("albumArtist", song.albumArtist.toPromptText(80))
                    .put("genre", song.genre.toPromptText(60))
                    .put("year", song.year.toPromptText(20))
                    .put("duration", song.durationText)
            )
        }

        val recent = JSONArray()
        input.playbackHistory
            .mapNotNull { entry -> songIds[entry.songId] }
            .take(50)
            .forEachIndexed { index, id ->
                recent.put(JSONObject().put("id", id).put("rank", index + 1))
            }

        val stats = JSONArray()
        input.playbackStats
            .mapNotNull { stat -> songIds[stat.songId]?.let { it to stat } }
            .sortedWith(
                compareByDescending<Pair<String, SongPlaybackStats>> { it.second.playCount }
                    .thenByDescending { it.second.listenedMs }
                    .thenByDescending { it.second.lastPlayedAt }
            )
            .take(60)
            .forEach { (id, stat) ->
                stats.put(
                    JSONObject()
                        .put("id", id)
                        .put("playCount", stat.playCount)
                        .put("listenedMinutes", stat.listenedMs / 60_000L)
                )
            }

        return buildString {
            appendLine("请为当前用户生成一个 AI 推荐歌单，并只返回 JSON。")
            appendLine()
            appendLine("要求：")
            appendLine("1. action 必须是 play_playlist，songIds 必须全部来自候选歌曲 id。")
            appendLine("2. 推荐 ${input.maxItems.coerceIn(5, 50)} 首以内，兼顾用户最近偏好和适度新鲜感。")
            appendLine("3. 不允许请求删除、移动、重命名、修改歌曲或访问候选列表之外的文件。")
            appendLine("4. 不要输出 Markdown，不要输出解释文字，只输出一个 JSON 对象。")
            appendLine("5. JSON 格式：{\"action\":\"play_playlist\",\"title\":\"歌单名\",\"reason\":\"一句中文推荐理由\",\"songIds\":[\"song_1\",\"song_2\"]}")
            appendLine()
            appendLine("候选歌曲：")
            appendLine(candidates.toString())
            appendLine()
            appendLine("最近播放：")
            appendLine(recent.toString())
            appendLine()
            appendLine("播放统计：")
            appendLine(stats.toString())
        }
    }

    private fun parseRecommendation(text: String, songs: List<Song>): OpenAiPlaylistRecommendation {
        val root = JSONObject(text.toJsonObjectText())
        val action = root.optString("action", "play_playlist")
        if (action.isNotBlank() && action != "play_playlist") {
            error(context.getString(R.string.error_ai_disallowed_action, action))
        }
        val ids = root.optJSONArray("songIds")
            ?: root.optJSONArray("ids")
            ?: root.optJSONArray("songs")
            ?: JSONArray()
        val songsByCandidateId = songs.mapIndexed { index, song -> song.toCandidateId(index) to song }.toMap()
        val keys = mutableListOf<String>()
        for (i in 0 until ids.length()) {
            val id = when (val item = ids.opt(i)) {
                is JSONObject -> item.optString("id")
                else -> item?.toString().orEmpty()
            }
            val song = songsByCandidateId[id] ?: continue
            keys += song.playlistIdentityKey()
        }

        return OpenAiPlaylistRecommendation(
            title = root.optString("title").ifBlank { context.getString(R.string.ai_default_playlist_title) },
            reason = root.optString("reason"),
            songKeys = keys.distinct()
        )
    }

    private fun parseResponseText(body: String): String {
        val root = JSONObject(body)
        root.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }

        val choices = root.optJSONArray("choices")
        if (choices != null) {
            val parts = mutableListOf<String>()
            for (i in 0 until choices.length()) {
                val text = choices
                    .optJSONObject(i)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (text.isNotBlank()) parts += text
            }
            if (parts.isNotEmpty()) return parts.joinToString("\n").trim()
        }

        val output = root.optJSONArray("output") ?: return ""
        val parts = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val contentItem = content.optJSONObject(j) ?: continue
                val text = contentItem.optString("text")
                    .ifBlank { contentItem.optString("output_text") }
                if (text.isNotBlank()) parts += text
            }
        }
        return parts.joinToString("\n").trim()
    }

    private fun String.toChatCompletionsEndpoint(): String {
        val trimmed = trim().ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/responses") -> trimmed.removeSuffix("/responses") + "/chat/completions"
            else -> "$trimmed/chat/completions"
        }
    }

    private fun String.toJsonObjectText(): String {
        val trimmed = trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
        return trimmed
    }

    private fun Song.toCandidateId(index: Int): String = "song_${index + 1}"

    private fun String.toPromptText(maxLength: Int): String =
        trim().replace(Regex("\\s+"), " ").let { text ->
            if (text.length <= maxLength) text else text.take(maxLength) + "..."
        }
}
