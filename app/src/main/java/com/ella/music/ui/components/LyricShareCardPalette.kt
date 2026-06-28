package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class SharePaletteRegion(
    val leftFraction: Float,
    val topFraction: Float,
    val rightFraction: Float,
    val bottomFraction: Float
)

private data class SharePalettePoint(
    val xFraction: Float,
    val yFraction: Float
)

internal fun resolveLyricShareBackgroundColors(
    cover: Bitmap?,
    fallbackColors: List<Int>
): List<Int> = cover.extractSharePalette(fallbackColors)

private fun Bitmap?.extractSharePalette(fallback: List<Int>): List<Int> {
    if (this == null || width <= 0 || height <= 0) return fallback
    val regions = listOf(
        SharePaletteRegion(0f, 0f, 0.58f, 0.54f),
        SharePaletteRegion(0.42f, 0.06f, 1f, 0.64f),
        SharePaletteRegion(0.10f, 0.44f, 0.92f, 1f),
        SharePaletteRegion(0.18f, 0.18f, 0.82f, 0.82f)
    )
    val points = listOf(
        SharePalettePoint(0.16f, 0.18f),
        SharePalettePoint(0.50f, 0.18f),
        SharePalettePoint(0.84f, 0.20f),
        SharePalettePoint(0.20f, 0.52f),
        SharePalettePoint(0.50f, 0.50f),
        SharePalettePoint(0.82f, 0.58f),
        SharePalettePoint(0.50f, 0.84f)
    )
    val sampledColors = regions.mapNotNull { sampleShareRegionColor(it) } +
        points.mapNotNull { sampleSharePointColor(it) }
    val fallbackColors = fallback.filter { Color.alpha(it) > 0 }
    val palette = mutableListOf<Int>()
    sampledColors
        .sortedByDescending { it.shareVibrancyScore() }
        .forEach { color ->
            if (palette.none { it.shareColorDistanceTo(color) < 68f }) {
                palette += color.boostForShare()
            }
        }
    fallbackColors.forEach { color ->
        if (palette.size >= SHARE_BACKGROUND_COLOR_LIMIT) return@forEach
        if (palette.none { it.shareColorDistanceTo(color) < 56f }) {
            palette += color.boostForShare()
        }
    }
    if (palette.isEmpty()) return fallback
    if (palette.size == 1) {
        val base = palette.first()
        palette += base.lightenForShare(1.10f)
        palette += base.darkenForShare(0.78f)
    } else if (palette.size == 2) {
        val bridge = blendShareColors(palette.first(), palette.last(), 0.45f).lightenForShare(1.04f)
        palette.add(if (palette.none { it.shareColorDistanceTo(bridge) < 36f }) bridge else palette.last().darkenForShare(0.78f))
    }
    return palette.take(SHARE_BACKGROUND_COLOR_LIMIT)
}

private fun Bitmap.sampleShareRegionColor(region: SharePaletteRegion): Int? {
    val left = (width * region.leftFraction).roundToInt().coerceIn(0, width - 1)
    val top = (height * region.topFraction).roundToInt().coerceIn(0, height - 1)
    val right = (width * region.rightFraction).roundToInt().coerceIn(left + 1, width)
    val bottom = (height * region.bottomFraction).roundToInt().coerceIn(top + 1, height)
    val step = (minOf(right - left, bottom - top) / 18).coerceAtLeast(1)
    var red = 0.0
    var green = 0.0
    var blue = 0.0
    var weightSum = 0.0
    val hsv = FloatArray(3)
    var y = top
    while (y < bottom) {
        var x = left
        while (x < right) {
            val pixel = getPixel(x, y)
            if (Color.alpha(pixel) > 32) {
                Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1].coerceIn(0f, 1f)
                val value = hsv[2].coerceIn(0f, 1f)
                val chromaWeight = if (saturation > 0.16f) 1.0 else 0.55
                val weight = (0.30 + saturation * 2.35 + value * 0.82).let {
                    if (value < 0.10f) it * 0.20 else it
                } * chromaWeight
                red += Color.red(pixel) * weight
                green += Color.green(pixel) * weight
                blue += Color.blue(pixel) * weight
                weightSum += weight
            }
            x += step
        }
        y += step
    }
    if (weightSum <= 0.0) return null
    return Color.rgb(
        (red / weightSum).toInt().coerceIn(0, 255),
        (green / weightSum).toInt().coerceIn(0, 255),
        (blue / weightSum).toInt().coerceIn(0, 255)
    )
}

private fun Bitmap.sampleSharePointColor(point: SharePalettePoint): Int? {
    val centerX = (width * point.xFraction).roundToInt().coerceIn(0, width - 1)
    val centerY = (height * point.yFraction).roundToInt().coerceIn(0, height - 1)
    val radius = (minOf(width, height) * 0.13f).roundToInt().coerceAtLeast(2)
    val left = (centerX - radius).coerceAtLeast(0)
    val top = (centerY - radius).coerceAtLeast(0)
    val right = (centerX + radius).coerceAtMost(width - 1)
    val bottom = (centerY + radius).coerceAtMost(height - 1)
    return sampleShareRegionColor(
        SharePaletteRegion(
            left.toFloat() / width,
            top.toFloat() / height,
            (right + 1).toFloat() / width,
            (bottom + 1).toFloat() / height
        )
    )
}

internal fun Int.boostForShare(): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv[1] = (hsv[1] * 1.18f + 0.06f).coerceIn(0.20f, 0.98f)
    hsv[2] = (hsv[2] * 1.08f + 0.05f).coerceIn(0.28f, 0.98f)
    return Color.HSVToColor(hsv)
}

internal fun Int.lightenForShare(factor: Float): Int {
    return Color.rgb(
        (Color.red(this) * factor).toInt().coerceIn(0, 255),
        (Color.green(this) * factor).toInt().coerceIn(0, 255),
        (Color.blue(this) * factor).toInt().coerceIn(0, 255)
    )
}

internal fun Int.darkenForShare(factor: Float): Int {
    return Color.rgb(
        (Color.red(this) * factor).toInt().coerceIn(0, 255),
        (Color.green(this) * factor).toInt().coerceIn(0, 255),
        (Color.blue(this) * factor).toInt().coerceIn(0, 255)
    )
}

private fun Int.shareColorDistanceTo(other: Int): Float {
    val dr = (Color.red(this) - Color.red(other)).toFloat()
    val dg = (Color.green(this) - Color.green(other)).toFloat()
    val db = (Color.blue(this) - Color.blue(other)).toFloat()
    return sqrt(dr * dr + dg * dg + db * db)
}

private fun Int.shareVibrancyScore(): Float {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    return hsv[1] * 1.6f + hsv[2]
}

private fun blendShareColors(start: Int, end: Int, fraction: Float): Int {
    val t = fraction.coerceIn(0f, 1f)
    val inverse = 1f - t
    return Color.rgb(
        (Color.red(start) * inverse + Color.red(end) * t).roundToInt().coerceIn(0, 255),
        (Color.green(start) * inverse + Color.green(end) * t).roundToInt().coerceIn(0, 255),
        (Color.blue(start) * inverse + Color.blue(end) * t).roundToInt().coerceIn(0, 255)
    )
}

private const val SHARE_BACKGROUND_COLOR_LIMIT = 7
