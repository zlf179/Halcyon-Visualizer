package com.ella.music.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNameUtilsTest {
    @After
    fun tearDown() {
        NameSplitConfigStore.artistCustomSeparators = emptyList()
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.genreCustomSeparators = emptyList()
        NameSplitConfigStore.genreProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
    }

    @Test
    fun splitArtistNames_doesNotUseImplicitSeparators() {
        assertEquals(listOf("R!N/Gemie/澤野弘之"), splitArtistNames("R!N/Gemie/澤野弘之"))
    }

    @Test
    fun splitArtistNames_appliesProtectedNamesBeforeCustomSeparator() {
        NameSplitConfigStore.artistCustomSeparators = listOf("/")
        NameSplitConfigStore.artistProtectedNames = listOf("R!N/Gemie")

        assertEquals(listOf("R!N/Gemie", "澤野弘之"), splitArtistNames("R!N/Gemie/澤野弘之"))
    }

    @Test
    fun splitGenreNames_doesNotUseImplicitSeparators() {
        assertEquals(listOf("Rock/Pop"), splitGenreNames("Rock/Pop"))
    }
}
