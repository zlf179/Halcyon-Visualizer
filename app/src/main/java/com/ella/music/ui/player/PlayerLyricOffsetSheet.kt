package com.ella.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import kotlin.math.round
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricOffsetSheetContent(
    offsetMs: Long,
    onBack: () -> Unit,
    onOffsetChange: (Long) -> Unit
) {
    HalfSheetTitle(title = stringResource(R.string.player_lyric_offset), onBack = onBack)
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.player_lyric_offset_summary),
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    )
    Spacer(modifier = Modifier.height(14.dp))
    DottedValueSlider(
        value = offsetMs.toFloat(),
        valueRange = -5000f..5000f,
        steps = 100,
        label = offsetMs.formatLyricOffset(),
        onValueChange = { onOffsetChange(it.toLong().roundToStep(100L)) },
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        HalfSheetPill(
            text = "-500ms",
            onClick = { onOffsetChange((offsetMs - 500L).coerceIn(-5000L, 5000L)) },
            modifier = Modifier.weight(1f)
        )
        HalfSheetPill(
            text = stringResource(R.string.common_reset),
            selected = offsetMs == 0L,
            onClick = { onOffsetChange(0L) },
            modifier = Modifier.weight(1f)
        )
        HalfSheetPill(
            text = "+500ms",
            onClick = { onOffsetChange((offsetMs + 500L).coerceIn(-5000L, 5000L)) },
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Long.formatLyricOffset(): String =
    if (this > 0) "+${this}ms" else "${this}ms"

private fun Long.roundToStep(step: Long): Long =
    (round(this.toFloat() / step).toLong() * step).coerceIn(-5000L, 5000L)
