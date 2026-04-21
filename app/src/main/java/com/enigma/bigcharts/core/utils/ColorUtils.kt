package com.enigma.bigcharts.core.utils

// core/utils/ColorUtils.kt
import androidx.compose.ui.graphics.Color

fun generateDistinctColors(count: Int, saturation: Float = 0.7f, lightness: Float = 0.6f): List<Color> {
    if (count <= 0) return emptyList()
    return List(count) { index ->
        val hue = (index * 360f / count).coerceIn(0f, 360f)
        Color.hsv(hue, saturation, lightness)
    }
}