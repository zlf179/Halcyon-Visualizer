package com.ella.music.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SpeedPitchSheetContent(
    speed: Float,
    pitch: Float,
    onBack: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit
) {
    HalfSheetTitle(title = stringResource(R.string.player_speed_pitch), onBack = onBack)
    Spacer(modifier = Modifier.height(22.dp))
    SpeedPitchHeader(title = stringResource(R.string.player_speed_playback))
    DottedValueSlider(
        value = speed,
        valueRange = 0.5f..2f,
        steps = 30,
        label = speed.formatPlaybackStep(),
        onValueChange = onSpeed,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    )
    SpeedPitchHeader(title = stringResource(R.string.player_pitch_playback))
    DottedValueSlider(
        value = pitch,
        valueRange = 0.5f..2f,
        steps = 30,
        label = pitch.formatPlaybackStep(),
        onValueChange = onPitch,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    )
}

@Composable
private fun SpeedPitchHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun Float.formatPlaybackStep(): String = "%.2f".format(this.coerceIn(0.5f, 2f))
