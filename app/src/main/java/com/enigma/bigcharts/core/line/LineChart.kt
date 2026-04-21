package com.enigma.bigcharts.core.line

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.LineStyle
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.PointStyle
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.detectChartGestures
import kotlin.math.*

@Composable
fun LineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    showPoints: Boolean = true,
    fillArea: Boolean = false,
    curveSmoothing: Float = 0.5f // 0 = linear, 1 = smooth
) {
    var selectedPoint by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = config.animationDuration, easing = FastOutSlowInEasing),
        label = "line_animation"
    )

    val dataPoints = dataset.data

    // FIX: guard against empty dataset so max/min don't crash
    if (dataPoints.isEmpty()) return

    val allValues = dataPoints.flatMap { it.values.values }
    val maxValue = allValues.maxOrNull() ?: 1f
    // FIX: ensure minValue never equals maxValue to avoid divide-by-zero in Y mapping
    val rawMin = allValues.minOrNull() ?: 0f
    val minValue = if (rawMin == maxValue) maxValue - 1f else rawMin
    val valueRange = maxValue - minValue

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val height = 400.dp

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .detectChartGestures(
                    onTap = { offset, size ->
                        val nearest = findNearestDataPointFromCanvas(
                            offset, size, dataPoints, maxValue, minValue
                        )
                        nearest?.let { (index, point, seriesKey) ->
                            selectedPoint = index to seriesKey
                            onPointTap?.invoke(seriesKey, point, index)
                        } ?: run { selectedPoint = null }
                    }
                )
        ) {
            drawGridLines(size, maxValue, minValue, config)

            dataset.series.forEach { (seriesKey, seriesConfig) ->
                val path = Path()
                val points = mutableListOf<Offset>()

                // FIX: handle single-point datasets (size - 1 would be 0 → division by zero)
                val xDivisor = (dataPoints.size - 1).coerceAtLeast(1).toFloat()

                dataPoints.forEachIndexed { index, point ->
                    val x = (index.toFloat() / xDivisor) * size.width
                    val value = point.getValue(seriesKey)
                    // FIX: apply animatedProgress to value, not to y-position directly
                    val animatedValue = minValue + (value - minValue) * animatedProgress
                    val y = size.height * (1f - ((animatedValue - minValue) / valueRange))
                    val offset = Offset(x, y.coerceIn(0f, size.height))
                    points.add(offset)

                    if (index == 0) {
                        path.moveTo(offset.x, offset.y)
                    } else {
                        if (curveSmoothing > 0f) {
                            val prev = points[index - 1]
                            val controlX1 = prev.x + (offset.x - prev.x) * curveSmoothing
                            val controlX2 = offset.x - (offset.x - prev.x) * curveSmoothing
                            path.cubicTo(controlX1, prev.y, controlX2, offset.y, offset.x, offset.y)
                        } else {
                            path.lineTo(offset.x, offset.y)
                        }
                    }
                }

                // FIX: fillArea was drawn with Stroke(width=0) which renders nothing;
                //      use Fill style instead.
                if (fillArea) {
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, size.height)
                        lineTo(points.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        color = seriesConfig.color.copy(alpha = 0.2f),
                        style = Fill  // was: Stroke(width = 0f) — that draws nothing
                    )
                }

                // Draw line
                drawPath(
                    path = path,
                    color = seriesConfig.color,
                    style = Stroke(
                        width = 4f,
                        pathEffect = when (val ls = seriesConfig.lineStyle) {
                            is LineStyle.Dashed -> PathEffect.dashPathEffect(
                                floatArrayOf(ls.dashLength, ls.gapLength)
                            )
                            else -> null
                        }
                    )
                )

                // Draw points
                if (showPoints) {
                    points.forEachIndexed { index, point ->
                        val isSelected = selectedPoint == (index to seriesKey)
                        val radius = when {
                            isSelected -> 10f
                            seriesConfig.pointStyle is PointStyle.Circle -> seriesConfig.pointStyle.radius
                            else -> 4f
                        }
                        drawCircle(
                            color = if (isSelected) Color.Red else seriesConfig.color,
                            radius = radius,
                            center = point
                        )
                        // FIX: draw a white border on selected point for contrast
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = radius - 3f,
                                center = point,
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// FIX: accept minValue so grid labels reflect the actual data range
private fun DrawScope.drawGridLines(size: Size, maxValue: Float, minValue: Float, config: ChartConfig) {
    val gridLines = 5
    for (i in 0..gridLines) {
        val y = size.height * (1f - i.toFloat() / gridLines)
        val value = minValue + (maxValue - minValue) * (i.toFloat() / gridLines)

        drawLine(
            color = config.gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )

        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 28f
            }
            drawText(value.toInt().toString(), 10f, y - 10f, paint)
        }
    }
}

fun findNearestDataPointFromCanvas(
    touchOffset: Offset,
    size: Size,
    dataPoints: List<TimeSeriesPoint>,
    maxValue: Float,
    minValue: Float
): Triple<Int, TimeSeriesPoint, String>? {
    if (dataPoints.isEmpty()) return null
    // FIX: single-point case
    val xDivisor = (dataPoints.size - 1).coerceAtLeast(1).toFloat()

    val xPositions = dataPoints.indices.map { (it.toFloat() / xDivisor) * size.width }

    val nearestXIndex = xPositions.indices
        .minByOrNull { abs(xPositions[it] - touchOffset.x) }
        ?: return null

    if (abs(xPositions[nearestXIndex] - touchOffset.x) > 50f) return null

    val dataPoint = dataPoints[nearestXIndex]
    val valueRange = (maxValue - minValue).coerceAtLeast(1f)
    var minDistance = Float.MAX_VALUE
    var closestSeries = ""

    dataPoint.values.forEach { (series, value) ->
        val expectedY = size.height * (1f - (value - minValue) / valueRange)
        val distance = abs(expectedY - touchOffset.y)
        if (distance < minDistance && distance < 100f) {
            minDistance = distance
            closestSeries = series
        }
    }

    return if (closestSeries.isNotEmpty()) Triple(nearestXIndex, dataPoint, closestSeries) else null
}
