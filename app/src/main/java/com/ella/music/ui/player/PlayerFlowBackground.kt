package com.ella.music.ui.player

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ella.music.data.model.Song
import kotlin.math.max

@Composable
internal fun PlayerFlowBackground(
    palette: PlayerPalette,
    flowEffectMode: Int,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(animate) {
        Log.d("PlayerScreenPerf", "flow background ${if (animate) "animated" else "static"}")
    }
    val sweepDrift = rememberSharedFlowProgress(
        durationMillis = 92_000,
        animate = animate,
        reverse = true,
        fallback = 0f
    )

    Canvas(modifier = modifier.background(palette.middle)) {
        val w = size.width
        val h = size.height
        val baseTop = palette.top.boosted().lighten(0.18f)
        val baseMid = palette.middle.boosted().lighten(0.12f)
        val baseBottom = palette.bottom.boosted().lighten(0.08f)

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    baseTop.copy(alpha = 0.96f),
                    baseMid.copy(alpha = 0.98f),
                    baseBottom.copy(alpha = 1f)
                )
            )
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    palette.accent.lighten(0.20f).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h * 0.72f)
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.White.copy(alpha = 0.06f),
                    0.48f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.28f)
                )
            )
        )

        val sweepStart = Offset((-0.36f + sweepDrift * 1.72f) * w, -0.08f * h)
        val sweepEnd = Offset((0.12f + sweepDrift * 1.72f) * w, 1.08f * h)
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.42f to Color.Transparent,
                    0.50f to Color.White.copy(alpha = 0.08f),
                    0.58f to Color.Transparent,
                    1.0f to Color.Transparent
                ),
                start = sweepStart,
                end = sweepEnd
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent
                )
            )
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.24f)
                )
            )
        )
    }
}

internal fun playerContentSurfaceBrush(
    palette: PlayerPalette,
    flowEffectMode: Int
): Brush {
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to palette.middle.copy(alpha = 0.70f),
            0.16f to palette.middle.copy(alpha = 0.82f),
            1.0f to palette.middle.copy(alpha = 0.90f)
        )
    )
}

@Composable
internal fun NonImmersiveAlbumFlowBackground(
    song: Song?,
    embeddedCover: Bitmap?,
    palette: PlayerPalette,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val drift = rememberSharedFlowProgress(
        durationMillis = 28_000,
        animate = true,
        reverse = true,
        fallback = 0f
    )
    val pulse = if (isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(drift * kotlin.math.PI.toFloat() * 2f)
    } else {
        0.28f
    }

    Box(modifier = modifier.background(palette.middle)) {
        PlayerBlurBackground(
            song = song,
            embeddedCover = embeddedCover,
            palette = palette,
            motion = drift,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxSize()
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val t = drift * kotlin.math.PI.toFloat() * 2f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.top.copy(alpha = 0.78f),
                        palette.middle.copy(alpha = 0.70f),
                        palette.bottom.copy(alpha = 0.88f)
                    )
                )
            )
            val glows = listOf(
                Triple(
                    Offset((0.12f + 0.10f * kotlin.math.sin(t * 0.72f)) * w, (0.18f + 0.08f * kotlin.math.cos(t)) * h),
                    palette.accent.lighten(0.18f).copy(alpha = 0.24f + pulse * 0.08f),
                    0.56f
                ),
                Triple(
                    Offset((0.88f + 0.08f * kotlin.math.cos(t * 0.62f)) * w, (0.28f + 0.10f * kotlin.math.sin(t * 0.9f)) * h),
                    palette.top.lighten(0.30f).copy(alpha = 0.22f),
                    0.48f
                ),
                Triple(
                    Offset((0.50f + 0.12f * kotlin.math.sin(t * 0.45f)) * w, (0.82f + 0.05f * kotlin.math.cos(t * 0.8f)) * h),
                    Color.White.copy(alpha = 0.08f + pulse * 0.04f),
                    0.42f
                )
            )
            glows.forEach { (center, color, radiusScale) ->
                val radius = max(w, h) * radiusScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
            val sweepStart = Offset((-0.42f + drift * 1.72f) * w, -0.12f * h)
            val sweepEnd = Offset((0.16f + drift * 1.72f) * w, 1.08f * h)
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.50f to Color.White.copy(alpha = 0.11f),
                        0.58f to Color.Transparent,
                        1.0f to Color.Transparent
                    ),
                    start = sweepStart,
                    end = sweepEnd
                )
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black.copy(alpha = 0.06f),
                        0.42f to Color.Black.copy(alpha = 0.10f),
                        0.74f to Color.Black.copy(alpha = 0.22f),
                        1.0f to Color.Black.copy(alpha = 0.48f)
                    )
                )
            )
        }
    }
}

@Composable
internal fun rememberSharedFlowProgress(
    durationMillis: Int,
    animate: Boolean,
    reverse: Boolean = false,
    fallback: Float = 0f
): Float {
    var frameTimeNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(durationMillis, animate) {
        if (!animate) {
            frameTimeNs = 0L
            return@LaunchedEffect
        }
        while (true) {
            frameTimeNs = withFrameNanos { it }
        }
    }
    if (!animate || frameTimeNs == 0L) return fallback
    val safeDuration = durationMillis.coerceAtLeast(1)
    val elapsedMs = frameTimeNs / 1_000_000L
    val cycle = elapsedMs / safeDuration
    val fraction = (elapsedMs % safeDuration).toFloat() / safeDuration.toFloat()
    return if (reverse && cycle % 2L == 1L) 1f - fraction else fraction
}
