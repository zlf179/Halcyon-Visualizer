package com.ella.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import kotlin.math.abs

@Composable
internal fun LandscapeLyricShowcase(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    fontWeight: FontWeight,
    onLineClick: (LyricLine) -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(
            modifier = modifier.padding(top = 14.dp, bottom = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            LandscapeLyricLine(
                line = null,
                currentPositionMs = currentPositionMs,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                primary = true,
                alpha = 0.9f,
                scale = 1f,
                onLineClick = onLineClick,
                onLineLongClick = onLineLongClick
            )
        }
        return
    }

    val safeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val listState = rememberLazyListState()
    LaunchedEffect(safeIndex, lyrics.size) {
        listState.animateScrollToItem((safeIndex - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = lyrics,
            key = { index, line -> "${line.timeMs}_$index" }
        ) { index, line ->
            val distance = abs(index - safeIndex)
            LandscapeLyricLine(
                line = line,
                currentPositionMs = currentPositionMs,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                primary = index == safeIndex,
                alpha = when (distance) {
                    0 -> 0.98f
                    1 -> 0.42f
                    2 -> 0.24f
                    else -> 0.14f
                },
                scale = when (distance) {
                    0 -> 1f
                    1 -> 0.86f
                    2 -> 0.78f
                    else -> 0.72f
                },
                onLineClick = onLineClick,
                onLineLongClick = onLineLongClick
            )
        }
    }
}
