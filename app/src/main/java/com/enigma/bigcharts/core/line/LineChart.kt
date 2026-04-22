package com.enigma.bigcharts.core.line

import android.annotation.SuppressLint
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ── Padding constants shared between draw and gesture code ───────────────────
private const val PAD_L = 52f
private const val PAD_T = 16f
private const val PAD_R = 16f
private const val PAD_B = 36f

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    showPoints: Boolean = true,
    fillArea: Boolean = false,
    curveSmoothing: Float = 0.4f,       // 0 = linear, 1 = very smooth
    // NEW — crosshair
    crosshairState: CrosshairState = remember { CrosshairState() },
    scrubMode: Boolean = false,          // true = drag to scrub, false = tap only
    // NEW — annotations
    annotations: List<LineChartAnnotation> = emptyList()
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = config.animationDuration, easing = FastOutSlowInEasing),
        label = "line_animation"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val seriesKeys = dataset.series.keys.toList()
    val allValues = dataPoints.flatMap { it.values.values }
    val maxValue = allValues.maxOrNull() ?: 1f
    val rawMin = allValues.minOrNull() ?: 0f
    // BUG FIX: ensure minValue != maxValue to avoid divide-by-zero
    val minValue = if (rawMin == maxValue) maxValue - 1f else rawMin

    // Pre-compute X positions once so gesture handler and draw pass agree perfectly
    val xDivisor = (dataPoints.size - 1).coerceAtLeast(1).toFloat()
    var canvasSizeForGestures by remember { mutableStateOf(Size.Zero) }
    val xPositions by remember(canvasSizeForGestures, dataPoints.size) {
        derivedStateOf {
            val plotW = canvasSizeForGestures.width - PAD_L - PAD_R
            if (plotW <= 0) emptyList<Float>()
            else dataPoints.indices.map { i -> PAD_L + (i.toFloat() / xDivisor) * plotW }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .detectChartGestures(
                    onTap = { offset, canvasSize ->
                        canvasSizeForGestures = canvasSize
                        if (xPositions.isNotEmpty()) {
                            crosshairState.update(
                                touchOffset = offset,
                                canvasSize = canvasSize,
                                dataPoints = dataPoints,
                                xPositions = xPositions,
                                maxValue = maxValue,
                                minValue = minValue,
                                seriesKeys = seriesKeys
                            )
                            val idx = crosshairState.activeIndex
                            val key = crosshairState.activeSeriesKey
                            if (idx != null && key != null) {
                                onPointTap?.invoke(key, dataPoints[idx], idx)
                            }
                        }
                    },
                    onDrag = if (scrubMode) { _, current, canvasSize ->
                        canvasSizeForGestures = canvasSize
                        if (xPositions.isNotEmpty()) {
                            crosshairState.update(
                                touchOffset = current,
                                canvasSize = canvasSize,
                                dataPoints = dataPoints,
                                xPositions = xPositions,
                                maxValue = maxValue,
                                minValue = minValue,
                                seriesKeys = seriesKeys
                            )
                        }
                    } else null
                )
        ) {
            val plotW = size.width - PAD_L - PAD_R
            val plotH = size.height - PAD_T - PAD_B

            // 1. Annotations (behind everything)
            drawAnnotations(annotations, PAD_L, PAD_T, plotW, plotH, dataPoints.size, maxValue, minValue)

            // 2. Grid
            drawLineChartGrid(size, maxValue, minValue, config, PAD_L, PAD_T, plotW, plotH)

            // 3. X-axis labels
            drawLineChartXLabels(dataPoints, PAD_L, PAD_T, plotW, plotH, xDivisor)

            // 4. Series paths + points
            dataset.series.forEach { (seriesKey, seriesConfig) ->
                val points = mutableListOf<Offset>()
                val path = Path()

                dataPoints.forEachIndexed { index, point ->
                    val x = PAD_L + (index.toFloat() / xDivisor) * plotW
                    val value = point.getValue(seriesKey)
                    // BUG FIX: animate value, not raw Y position
                    val animatedValue = minValue + (value - minValue) * animatedProgress
                    val y = PAD_T + plotH * (1f - ((animatedValue - minValue) / (maxValue - minValue).coerceAtLeast(1f)))
                    val offset = Offset(x, y.coerceIn(PAD_T, PAD_T + plotH))
                    points.add(offset)

                    if (index == 0) {
                        path.moveTo(offset.x, offset.y)
                    } else {
                        if (curveSmoothing > 0f) {
                            val prev = points[index - 1]
                            val cpX1 = prev.x + (offset.x - prev.x) * curveSmoothing
                            val cpX2 = offset.x - (offset.x - prev.x) * curveSmoothing
                            path.cubicTo(cpX1, prev.y, cpX2, offset.y, offset.x, offset.y)
                        } else {
                            path.lineTo(offset.x, offset.y)
                        }
                    }
                }

                // Fill area — gradient instead of flat alpha
                if (fillArea && points.isNotEmpty()) {
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, PAD_T + plotH)
                        lineTo(points.first().x, PAD_T + plotH)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                seriesConfig.color.copy(alpha = 0.28f),
                                seriesConfig.color.copy(alpha = 0.0f)
                            ),
                            startY = points.minOfOrNull { it.y } ?: PAD_T,
                            endY = PAD_T + plotH
                        ),
                        style = Fill
                    )
                }

                // Line
                drawPath(
                    path = path,
                    color = seriesConfig.color,
                    style = Stroke(
                        width = 3.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = when (val ls = seriesConfig.lineStyle) {
                            is LineStyle.Dashed -> PathEffect.dashPathEffect(
                                floatArrayOf(ls.dashLength, ls.gapLength)
                            )
                            else -> null
                        }
                    )
                )

                // Points
                if (showPoints) {
                    val isActiveSeries = crosshairState.activeSeriesKey == seriesKey
                    points.forEachIndexed { index, pt ->
                        val isSelected = isActiveSeries && crosshairState.activeIndex == index
                        // BUG FIX: radius based on fraction, not hardcoded subtraction
                        val baseRadius = when (val ps = seriesConfig.pointStyle) {
                            is PointStyle.Circle -> ps.radius
                            else -> 4f
                        }
                        val radius = if (isSelected) baseRadius * 2.2f else baseRadius

                        // Outer glow ring for selected
                        if (isSelected) {
                            drawCircle(
                                color = seriesConfig.color.copy(alpha = 0.22f),
                                radius = radius * 1.9f,
                                center = pt
                            )
                        }
                        // Filled dot
                        drawCircle(color = seriesConfig.color, radius = radius, center = pt)
                        // White inner ring — size is a fraction, not a constant
                        drawCircle(
                            color = Color.White,
                            radius = radius * 0.52f,
                            center = pt,
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // 5. Crosshair (on top of everything)
            val activeIdx = crosshairState.activeIndex
            if (activeIdx != null && activeIdx in dataPoints.indices) {
                val activeSeriesKey = crosshairState.activeSeriesKey ?: seriesKeys.firstOrNull()
                val accentColor = if (activeSeriesKey != null)
                    dataset.series[activeSeriesKey]?.color ?: Color(0xFF378ADD)
                else Color(0xFF378ADD)

                // Snap crosshair Y to the actual data point on the nearest series
                if (activeSeriesKey != null) {
                    val value = dataPoints[activeIdx].getValue(activeSeriesKey)
                    val animatedValue = minValue + (value - minValue) * animatedProgress
                    val snappedY = PAD_T + plotH * (1f - ((animatedValue - minValue) / (maxValue - minValue).coerceAtLeast(1f)))
                    crosshairState.position = Offset(
                        PAD_L + (activeIdx.toFloat() / xDivisor) * plotW,
                        snappedY.coerceIn(PAD_T, PAD_T + plotH)
                    )
                }

                drawCrosshair(crosshairState, PAD_L, PAD_T, plotW, plotH, accentColor)

                // Tooltip label — drawn as a pill above the crosshair point
                val pos = crosshairState.position
                if (pos != null && activeSeriesKey != null) {
                    val value = dataPoints[activeIdx].getValue(activeSeriesKey)
                    val label = "${dataset.series[activeSeriesKey]?.name ?: activeSeriesKey}: ${value.toInt()}"
                    drawTooltipPill(label, pos, PAD_L, PAD_T, plotW, dataset.series[activeSeriesKey]?.color ?: Color(0xFF378ADD))
                }
            }
        }
    }
}

// ── Grid ──────────────────────────────────────────────────────────────────────

private fun DrawScope.drawLineChartGrid(
    size: Size,
    maxValue: Float,
    minValue: Float,
    config: ChartConfig,
    padL: Float, padT: Float, plotW: Float, plotH: Float
) {
    val gridLines = 5
    for (i in 0..gridLines) {
        val y = padT + plotH * (1f - i.toFloat() / gridLines)
        val value = (minValue + (maxValue - minValue) * (i.toFloat() / gridLines)).toInt()

        drawLine(
            color = config.gridColor,
            start = Offset(padL, y),
            end = Offset(padL + plotW, y),
            strokeWidth = 1f
        )

        // BUG FIX: right-align labels into the left-pad zone so they don't overlap the plot
        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = android.graphics.Color.GRAY
                textSize = 24f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
            }
            drawText(value.toString(), padL - 6f, y + 8f, paint)
        }
    }
}

// ── X-axis labels ─────────────────────────────────────────────────────────────

private fun DrawScope.drawLineChartXLabels(
    dataPoints: List<TimeSeriesPoint>,
    padL: Float, padT: Float, plotW: Float, plotH: Float,
    xDivisor: Float
) {
    val labelPaint = Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 22f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    val skipStep = (dataPoints.size / 8).coerceAtLeast(1)
    val bottomY = padT + plotH + 26f

    dataPoints.forEachIndexed { i, point ->
        if (i % skipStep != 0 && i != dataPoints.size - 1) return@forEachIndexed
        val x = padL + (i.toFloat() / xDivisor) * plotW
        val label = point.label ?: formatTimestamp(point.timestamp)
        drawContext.canvas.nativeCanvas.drawText(label, x, bottomY, labelPaint)
    }
}

// ── Tooltip pill ──────────────────────────────────────────────────────────────

private fun DrawScope.drawTooltipPill(
    text: String,
    anchor: Offset,
    padL: Float, padT: Float, plotW: Float,
    color: Color
) {
    var textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 26f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textW = textPaint.measureText(text)
    val pillW = textW + 24f
    val pillH = 36f
    val pillR = 10f

    // Keep pill inside plot area horizontally
    val pillX = (anchor.x - pillW / 2f).coerceIn(padL, padL + plotW - pillW)
    val pillY = (anchor.y - pillH - 10f).coerceAtLeast(padT)

    // Background pill
    drawRoundRect(
        color = color.copy(alpha = 0.92f),
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillR)
    )

    // Text centred in pill
    drawContext.canvas.nativeCanvas.drawText(
        text,
        pillX + pillW / 2f,
        pillY + pillH / 2f + 9f,
        textPaint.apply { textAlign = Paint.Align.CENTER }
    )
}

// ── Timestamp formatter ───────────────────────────────────────────────────────

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestamp))
