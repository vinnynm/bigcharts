package com.enigma.bigcharts.core.bar

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.CategoryDataPoint
import com.enigma.bigcharts.core.utils.ChartConfig
import kotlin.math.roundToInt

@Composable
fun ModernHBarChart(
    data: List<CategoryDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    barHeightFraction: Float = 0.6f,
    showValues: Boolean = true
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "hbar_progress"
    )

    if (data.isEmpty()) return
    val maxV = data.maxOf { it.value }.coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height((data.size * 45).dp)) {
        val padL = 120f
        val padR = 60f
        val padT = 10f
        val padB = 10f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        
        val rowH = h / data.size
        val barH = rowH * barHeightFraction

        data.forEachIndexed { i, item ->
            val y = padT + i * rowH + rowH / 2f
            val bw = (item.value / maxV) * w * progress
            val top = y - barH / 2f

            // Track (background)
            drawRoundRect(
                color = item.color.copy(alpha = 0.1f),
                topLeft = Offset(padL, top),
                size = Size(w, barH),
                cornerRadius = CornerRadius(barH / 2f)
            )

            // Bar
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(item.color.copy(alpha = 0.8f), item.color)
                ),
                topLeft = Offset(padL, top),
                size = Size(bw, barH),
                cornerRadius = CornerRadius(barH / 2f)
            )

            // Labels
            drawContext.canvas.nativeCanvas.drawText(
                item.label,
                10f,
                y + 8f,
                Paint().apply {
                    color = Color.GRAY
                    textSize = 24f
                    isAntiAlias = true
                }
            )

            if (showValues) {
                drawContext.canvas.nativeCanvas.drawText(
                    item.value.roundToInt().toString(),
                    padL + bw + 15f,
                    y + 8f,
                    Paint().apply {
                        color = Color.DKGRAY
                        textSize = 22f
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}
