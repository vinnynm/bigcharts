package com.enigma.bigcharts.core.scatter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.ScatterDataPoint
import com.enigma.bigcharts.core.utils.ScatterDataset
import com.enigma.bigcharts.core.utils.ZoomControls
import com.enigma.bigcharts.core.utils.ZoomPanState
import com.enigma.bigcharts.core.utils.zoomableChart
import kotlin.math.*

@Composable
fun ComplexScatterChart(
    dataset: ScatterDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showRegression: Boolean = false,
    bubbleSizeField: ((ScatterDataPoint) -> Float)? = null, // maps point to radius (dp)
    onPointTap: ((ScatterDataPoint, String, Int) -> Unit)? = null
) {
    val zoomPanState = remember { ZoomPanState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Compute min/max from all points (respecting zoom/pan? Actually fixed for regression)
    val allPoints = dataset.points
    if (allPoints.isEmpty()) return
    val minX = allPoints.minOf { it.x }
    val maxX = allPoints.maxOf { it.x }
    val minY = allPoints.minOf { it.y }
    val maxY = allPoints.maxOf { it.y }
    val xRange = (maxX - minX).coerceAtLeast(1f)
    val yRange = (maxY - minY).coerceAtLeast(1f)

    // Regression line (least squares) for each series if requested
    val regressionLines = if (showRegression) {
        dataset.points.groupBy { it.seriesKey }.mapNotNull { (key, pts) ->
            if (pts.size < 2) return@mapNotNull null
            val n = pts.size
            val sumX = pts.sumOf { it.x.toDouble() }
            val sumY = pts.sumOf { it.y.toDouble() }
            val sumXY = pts.sumOf { it.x.toInt() * it.y.toInt() }
            val sumX2 = pts.sumOf { it.x.toInt() * it.x.toInt() }
            val denominator = n * sumX2 - sumX * sumX
            if (denominator == 0.0) return@mapNotNull null
            val slope = (n * sumXY - sumX * sumY) / denominator
            val intercept = (sumY - slope * sumX) / n
            Triple(key, slope.toFloat(), intercept.toFloat())
        }
    } else emptyList()

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas (
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .zoomableChart(zoomPanState, canvasSize, onDoubleTap = { zoomPanState.reset() })
                .onSizeChanged { canvasSize = it }
        ) {
            val padL = 60f; val padR = 40f; val padT = 40f; val padB = 60f
            val w = size.width - padL - padR
            val h = size.height - padT - padB

            // Grid with zoom adaptation (simplified – same as EnhancedLineChart)
            for (i in 0..4) {
                val x = padL + (i / 4f) * w
                val y = padT + (1f - i / 4f) * h
                drawLine(config.gridColor, Offset(x, padT), Offset(x, padT + h), 1f)
                drawLine(config.gridColor, Offset(padL, y), Offset(padL + w, y), 1f)
            }

            // Draw regression lines first (so they appear behind points)
            regressionLines.forEach { (seriesKey, slope, intercept) ->
                val color = dataset.series[seriesKey]?.color ?: Color.Gray
                val x1 = minX
                val y1 = slope * x1 + intercept
                val x2 = maxX
                val y2 = slope * x2 + intercept
                val startX = padL + ((x1 - minX) / xRange) * w
                val startY = padT + h * (1f - (y1 - minY) / yRange)
                val endX = padL + ((x2 - minX) / xRange) * w
                val endY = padT + h * (1f - (y2 - minY) / yRange)
                drawLine(color.copy(alpha = 0.6f), Offset(startX, startY), Offset(endX, endY), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }

            // Draw scatter points
            dataset.points.groupBy { it.seriesKey }.forEach { (key, points) ->
                val color = dataset.series[key]?.color ?: Color.Gray
                points.forEach { point ->
                    val x = padL + ((point.x - minX) / xRange) * w
                    val y = padT + h * (1f - (point.y - minY) / yRange)
                    val radius = bubbleSizeField?.invoke(point)?.coerceIn(4f, 30f) ?: 10f
                    drawCircle(color, radius, Offset(x, y))
                }
            }
        }

        ZoomControls(
            zoomPanState = zoomPanState,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            contentSize = canvasSize
        )
    }
}