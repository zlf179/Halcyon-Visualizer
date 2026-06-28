package com.ella.music.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceTest {
    @Test
    fun detectsUriBackedAudioSources() {
        assertTrue("content://media/external/audio/media/1".isUriAudioSource())
        assertTrue("content://media/external/audio/media/1".isMediaStoreContentAudioSource())
        assertTrue("HTTPS://example.com/a.flac".isUriAudioSource())
        assertTrue("file:///storage/emulated/0/Music/a.flac".isUriAudioSource())
        assertFalse("/storage/emulated/0/Music/a.flac".isUriAudioSource())
        assertFalse("content://com.android.providers.media.documents/document/audio%3A1".isMediaStoreContentAudioSource())
    }
}
