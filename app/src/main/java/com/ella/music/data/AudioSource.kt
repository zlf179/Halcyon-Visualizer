package com.ella.music.data

fun String.isHttpAudioSource(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

fun String.isContentAudioSource(): Boolean =
    startsWith("content://", ignoreCase = true)

fun String.isMediaStoreContentAudioSource(): Boolean =
    startsWith("content://media/", ignoreCase = true)

fun String.isFileUriAudioSource(): Boolean =
    startsWith("file://", ignoreCase = true)

fun String.isUriAudioSource(): Boolean =
    isContentAudioSource() || isHttpAudioSource() || isFileUriAudioSource()
