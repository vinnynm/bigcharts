package com.enigma.bigcharts.core.scatter

// core/scatter/SimpleScatterChart.kt
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.ScatterDataset


@Composable
fun SimpleScatterChart(
    dataset: ScatterDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig()
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "scatter_simple"
    )

    if (dataset.points.isEmpty()) return

    val allX = dataset.points.map { it.x }
    val allY = dataset.points.map { it.y }
    val minX = allX.minOrNull() ?: 0f
    val maxX = allX.maxOrNull() ?: 1f
    val minY = allY.minOrNull() ?: 0f
    val maxY = allY.maxOrNull() ?: 1f
    val xRange = (maxX - minX).coerceAtLeast(1f)
    val yRange = (maxY - minY).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(300.dp)) {
        val padL = 50f; val padR = 20f; val padT = 20f; val padB = 40f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        // Simple grid (4 lines)
        for (i in 0..4) {
            val x = padL + (i / 4f) * w
            val y = padT + (1f - i / 4f) * h
            drawLine(config.gridColor, Offset(x, padT), Offset(x, padT + h), 1f)
            drawLine(config.gridColor, Offset(padL, y), Offset(padL + w, y), 1f)
        }

        // Draw points (single series)
        val seriesEntry = dataset.series.entries.firstOrNull()
        var color = seriesEntry?.value?.color ?: Color.Blue
        dataset.points.forEach { point ->
            val animatedProgress = progress // all points appear together
            val x = padL + ((point.x - minX) / xRange) * w
            val y = padT + h * (1f - (point.y - minY) / yRange)
            drawCircle(color, 8f * animatedProgress, Offset(x, y))
        }

        // Axis labels
        drawContext.canvas.nativeCanvas.drawText(
            "${minX.toInt()}", padL, size.height - 5f,
            android.graphics.Paint().apply { color = Color.Gray; textSize = 22f }
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${maxX.toInt()}", padL + w - 20f, size.height - 5f,
            android.graphics.Paint().apply { color =Color.Gray; textSize = 22f }
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${minY.toInt()}", 10f, padT + h,
            android.graphics.Paint().apply { color = Color.Gray; textSize = 22f }
        )
        drawContext.canvas.nativeCanvas.drawText(
            "${maxY.toInt()}", 10f, padT + 15f,
            android.graphics.Paint().apply { color = Color.Gray; textSize = 22f }
        )
    }
}