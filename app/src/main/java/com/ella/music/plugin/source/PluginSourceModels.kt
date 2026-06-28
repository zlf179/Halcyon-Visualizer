package com.ella.music.plugin.source

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PluginSearchSongsRequest(
    val keyword: String,
    val page: Int = 1,
    val pageSize: Int = 20,
    val separator: String = "/",
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class PluginGetLyricsRequest(
    val song: PluginSongRequest,
    val config: Map<String, String> = emptyMap()
)

@Serializable
data class PluginSongRequest(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val sourceId: String,
    val pluginId: String,
    val fields: Map<String, String> = emptyMap(),
    val internal: Map<String, String> = emptyMap()
)

data class PluginSongSearchResult(
    val id: String,
    val pluginId: String,
    val pluginName: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L,
    val date: String = "",
    val trackNumber: String = "",
    val picUrl: String = "",
    val fields: Map<String, String> = emptyMap(),
    val internal: Map<String, String> = emptyMap()
)

data class PluginLyricsWord(
    val start: Long,
    val end: Long,
    val text: String
)

data class PluginLyricsLine(
    val start: Long,
    val end: Long,
    val words: List<PluginLyricsWord>
) {
    val text: String get() = words.joinToString("") { it.text }
}

data class PluginLyricsResult(
    val tags: Map<String, String>,
    val original: List<PluginLyricsLine>,
    val translated: List<PluginLyricsLine>?,
    val romanization: List<PluginLyricsLine>?,
    val payloadType: PluginLyricsPayloadType = PluginLyricsPayloadType.STRUCTURED,
    val rawPlainLrc: String = "",
    val rawVerbatimLrc: String = "",
    val rawEnhancedLrc: String = "",
    val rawTtml: String = "",
    val rawMultiPersonEnhancedLrc: String = ""
)

enum class PluginLyricsPayloadType {
    STRUCTURED,
    RAW_PLAIN_LRC,
    RAW_VERBATIM_LRC,
    RAW_ENHANCED_LRC,
    RAW_TTML,
    RAW_MULTI_PERSON_ENHANCED_LRC
}

data class LyricoPluginSource(
    val manifest: com.ella.music.plugin.model.PluginManifest,
    val assetDir: String,
    val script: String
)

val pluginJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
