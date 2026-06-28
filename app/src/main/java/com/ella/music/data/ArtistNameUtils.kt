package com.ella.music.data

object NameSplitConfigStore {
    @Volatile
    var artistCustomSeparators: List<String> = emptyList()

    @Volatile
    var artistProtectedNames: List<String> = emptyList()

    @Volatile
    var genreCustomSeparators: List<String> = emptyList()

    @Volatile
    var genreProtectedNames: List<String> = emptyList()

    @Volatile
    var tagIgnoreCase: Boolean = false
}

// Compiled separator regexes are reused across calls; building them per call was a
// hot spot when grouping the whole library (artist/genre/composer/lyricist screens).
private val separatorRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
private val protectedNameRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

fun splitArtistNames(value: String): List<String> {
    return splitNames(
        value = value,
        symbolSeparatorPatterns = emptyList(),
        wordSeparatorPatterns = emptyList(),
        customSeparators = NameSplitConfigStore.artistCustomSeparators,
        protectedNames = NameSplitConfigStore.artistProtectedNames,
        unknownValues = setOf("<unknown>")
    )
}

fun splitGenreNames(value: String): List<String> {
    return splitNames(
        value = value,
        symbolSeparatorPatterns = emptyList(),
        wordSeparatorPatterns = emptyList(),
        customSeparators = NameSplitConfigStore.genreCustomSeparators,
        protectedNames = NameSplitConfigStore.genreProtectedNames,
        unknownValues = setOf("<unknown>")
    )
}

fun String.matchesArtistName(artistName: String): Boolean {
    val target = artistName.trim()
    if (target.isBlank()) return false
    return splitArtistNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.matchesGenreName(genreName: String): Boolean {
    val target = genreName.trim()
    if (target.isBlank()) return false
    return splitGenreNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.tagIdentityKey(): String =
    if (NameSplitConfigStore.tagIgnoreCase) trim().lowercase() else trim()

fun parseNameSplitSetting(value: String): List<String> {
    return value
        .lines()
        .flatMap { line -> line.split('\t') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

private fun splitNames(
    value: String,
    symbolSeparatorPatterns: List<String>,
    wordSeparatorPatterns: List<String>,
    customSeparators: List<String>,
    protectedNames: List<String>,
    unknownValues: Set<String>
): List<String> {
    val normalized = value
        .replace("（", "(")
        .replace("）", ")")
        .trim()
    if (normalized.isBlank()) return emptyList()

    val protectedMap = linkedMapOf<String, String>()
    var protectedText = normalized
    protectedNames
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }
        .forEachIndexed { index, name ->
            val token = "\uE000${index}\uE000"
            val regex = protectedNameRegexCache.getOrPut(name) {
                Regex(Regex.escape(name), RegexOption.IGNORE_CASE)
            }
            if (regex.containsMatchIn(protectedText)) {
                protectedMap[token] = name
                protectedText = regex.replace(protectedText, token)
            }
        }

    val separatorRegex = separatorRegexFor(symbolSeparatorPatterns, wordSeparatorPatterns, customSeparators)
        ?: return listOf(normalized)

    return protectedText
        .split(separatorRegex)
        .map { raw ->
            protectedMap.entries.fold(raw.trim()) { current, (token, name) ->
                current.replace(token, name)
            }.trim()
        }
        .filter { item ->
            item.isNotBlank() && item.lowercase() !in unknownValues
        }
        .distinctBy { it.tagIdentityKey() }
}

private fun separatorRegexFor(
    symbolSeparatorPatterns: List<String>,
    wordSeparatorPatterns: List<String>,
    customSeparators: List<String>
): Regex? {
    val symbolParts = symbolSeparatorPatterns + customSeparators
        .filter { it.isNotBlank() }
        .map { Regex.escape(it) }
    val alternatives = buildList {
        if (symbolParts.isNotEmpty()) {
            add("""\s*(?:${symbolParts.joinToString("|")})\s*""")
        }
        if (wordSeparatorPatterns.isNotEmpty()) {
            add("""\s+(?:${wordSeparatorPatterns.joinToString("|")})\s+""")
        }
    }
    if (alternatives.isEmpty()) return null
    val pattern = alternatives.joinToString("|")
    return separatorRegexCache.getOrPut(pattern) {
        Regex(pattern, RegexOption.IGNORE_CASE)
    }
}
