package com.enigma.bigcharts.core.bar
// core/simple/SimpleBarChart.kt
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset

@Composable
fun SimpleBarChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig()
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "simple_bar"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val seriesEntry = dataset.series.entries.firstOrNull() ?: return
    val (_, seriesConfig) = seriesEntry
    val values = dataPoints.map { it.values.values.first() }
    val maxValue = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(300.dp)) {
        val padL = 40f; val padR = 10f; val padT = 20f; val padB = 40f
        val w = size.width - padL - padR
        val h = size.height - padT - padB
        val barWidth = (w / dataPoints.size) * 0.6f

        // Grid
        for (i in 0..4) {
            val y = padT + h * (1f - i / 4f)
            drawLine(config.gridColor, Offset(padL, y), Offset(padL + w, y), 1f)
            val value = (maxValue * i / 4).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                value.toString(), 5f, y + 5f,
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f }
            )
        }

        // Draw bars
        val xStep = w / dataPoints.size
        dataPoints.forEachIndexed { i, point ->
            val value = point.values.values.first()
            val barHeight = (value / maxValue) * h * progress
            val x = padL + i * xStep + (xStep - barWidth) / 2f
            val y = padT + h - barHeight
            drawRect(seriesConfig.color, Offset(x, y), Size(barWidth, barHeight))

            // Label
            val label = point.label ?: ""
            drawContext.canvas.nativeCanvas.drawText(
                label, x + barWidth / 2f, size.height - 5f,
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER }
            )
        }
    }
}
