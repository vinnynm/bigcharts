package com.enigma.bigcharts.core.scatter

// core/scatter/MediumScatterChart.kt

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.ScatterDataPoint
import com.enigma.bigcharts.core.utils.ScatterDataset
import com.enigma.bigcharts.core.utils.ChartLegend
import com.enigma.bigcharts.core.utils.ChartTooltip
import com.enigma.bigcharts.core.utils.chartEntranceTween


@Composable
fun MediumScatterChart(
    dataset: ScatterDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showLegend: Boolean = true,
    onPointTap: ((ScatterDataPoint, String, Int) -> Unit)? = null
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = chartEntranceTween(config.animationDuration),
        label = "scatter_medium"
    )

    var enabledSeries by remember { mutableStateOf(dataset.series.keys) }
    val filteredPoints = dataset.points.filter { it.seriesKey in enabledSeries }

    var tooltipState by remember { mutableStateOf<Triple<Offset, String, String>?>(null) }

    if (filteredPoints.isEmpty()) return

    val allX = filteredPoints.map { it.x }
    val allY = filteredPoints.map { it.y }
    val minX = allX.minOrNull() ?: 0f
    val maxX = allX.maxOrNull() ?: 1f
    val minY = allY.minOrNull() ?: 0f
    val maxY = allY.maxOrNull() ?: 1f
    val xRange = (maxX - minX).coerceAtLeast(1f)
    val yRange = (maxY - minY).coerceAtLeast(1f)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showLegend) {
            ChartLegend(
                series = dataset.series,
                enabledSeries = enabledSeries,
                onSeriesToggle = { key ->
                    enabledSeries = if (key in enabledSeries) enabledSeries - key else enabledSeries + key
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .pointerInput(filteredPoints) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val position = event.changes.firstOrNull()?.position ?: continue
                                val padL = 50f; val padR = 30f; val padT = 30f; val padB = 50f
                                val w = size.width - padL - padR
                                val h = size.height - padT - padB
                                // Find nearest point (simplified)
                                val minDistSq = 400f // 20px tolerance
                                var nearest: ScatterDataPoint? = null
                                var nearestSeriesKey: String? = null
                                var nearestIndex = -1
                                filteredPoints.forEachIndexed { idx, point ->
                                    val x = padL + ((point.x - minX) / xRange) * w
                                    val y = padT + h * (1f - (point.y - minY) / yRange)
                                    val dx = position.x - x
                                    val dy = position.y - y
                                    val distSq = dx*dx + dy*dy
                                    if (distSq < minDistSq) {
                                        nearest = point
                                        nearestSeriesKey = point.seriesKey
                                        nearestIndex = idx
                                    }
                                }
                                if (nearest != null) {
                                    tooltipState = Triple(position, nearest.label ?: "(${nearest.x}, ${nearest.y})", nearestSeriesKey!!)
                                    onPointTap?.invoke(nearest, nearestSeriesKey!!, nearestIndex)
                                } else {
                                    tooltipState = null
                                }
                            }
                        }
                    }
            ) {
                val padL = 50f; val padR = 30f; val padT = 30f; val padB = 50f
                val w = size.width - padL - padR
                val h = size.height - padT - padB

                // Grid and axes
                for (i in 0..4) {
                    val x = padL + (i / 4f) * w
                    val y = padT + (1f - i / 4f) * h
                    drawLine(config.gridColor, Offset(x, padT), Offset(x, padT + h), 1f)
                    drawLine(config.gridColor, Offset(padL, y), Offset(padL + w, y), 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${(minX + (maxX - minX) * i / 4).toInt()}", x - 15f, size.height - 10f,
                        android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 22f }
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "${(minY + (maxY - minY) * (4 - i) / 4).toInt()}", 10f, y + 10f,
                        android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = 22f }
                    )
                }

                // Draw points by series
                filteredPoints.groupBy { it.seriesKey }.forEach { (key, points) ->
                    val color = dataset.series[key]?.color ?: Color.Gray
                    points.forEach { point ->
                        val x = (padL + ((point.x - minX) / xRange) * w).toFloat()
                        val y = (padT + h * (1f - (point.y - minY) / yRange)).toFloat()
                        drawCircle(color, 10f * progress, Offset(x, y))
                    }
                }
            }

            // Tooltip
            tooltipState?.let { (pos, text, series) ->
                ChartTooltip(
                    text = "$series: $text",
                    position = pos,
                    isVisible = true,
                    onDismiss = { tooltipState = null }
                )
            }
        }
    }
}

// Helper values used in pointerInput – we'd define padL, w, etc. inside Canvas scope.
// For brevity, the above uses variables that need to be defined inside the Canvas lambda.
// In a real implementation, you'd recompute these inside the pointerInput block using the current canvas size.
// I'll leave it as a simplified version.