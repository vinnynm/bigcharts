package com.enigma.bigcharts.core.line

// core/simple/SimpleLineChart.kt
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset

@Composable
fun SimpleLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig()
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "simple_line"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    // Single series – use the first one
    val seriesEntry = dataset.series.entries.firstOrNull() ?: return
    val (seriesKey, seriesConfig) = seriesEntry
    val allValues = dataPoints.map { it.getValue(seriesKey) }
    val maxValue = allValues.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val minValue = allValues.minOrNull() ?: 0f
    val range = (maxValue - minValue).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(300.dp)) {
        val padL = 40f; val padR = 10f; val padT = 20f; val padB = 30f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        // Simple grid (3 lines)
        for (i in 0..3) {
            val y = padT + h * (1f - i / 3f)
            drawLine(config.gridColor, Offset(padL, y), Offset(padL + w, y), 1f)
            val value = minValue + (maxValue - minValue) * (i / 3f)
            drawContext.canvas.nativeCanvas.drawText(
                value.toInt().toString(), 5f, y + 5f,
                android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 24f }
            )
        }

        // Draw line
        val xStep = w / (dataPoints.size - 1).coerceAtLeast(1)
        val path = Path()
        dataPoints.forEachIndexed { i, point ->
            val v = point.getValue(seriesKey)
            val animatedV = minValue + (v - minValue) * progress
            val x = padL + i * xStep
            val y = padT + h * (1f - (animatedV - minValue) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, seriesConfig.color, style = Stroke(3f))
    }
}