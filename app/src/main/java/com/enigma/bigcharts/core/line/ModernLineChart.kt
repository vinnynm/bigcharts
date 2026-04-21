package com.enigma.bigcharts.core.line

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enigma.charts.core.utils.ChartConfig
import com.enigma.charts.core.utils.CrosshairState
import com.enigma.charts.core.utils.LineChartAnnotation
import com.enigma.charts.core.utils.LineStyle
import com.enigma.charts.core.utils.MultiSeriesDataset
import com.enigma.charts.core.utils.TimeSeriesPoint
import com.enigma.charts.core.utils.drawAnnotations
import com.enigma.charts.core.utils.drawCrosshair
import com.enigma.charts.core.utils.detectChartGestures

// ── Layout constants ──────────────────────────────────────────────────────────
private const val PAD_L = 56f
private const val PAD_T = 24f   // extra top breathing room so max value never clips
private const val PAD_R = 20f
private const val PAD_B = 44f

@Composable
fun ModernLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    fillArea: Boolean = true,
    showPoints: Boolean = true,
    curveSmoothing: Float = 0.4f,
    // NEW — crosshair / scrub
    crosshairState: CrosshairState = remember { CrosshairState() },
    scrubMode: Boolean = true,         // default ON for ModernLineChart — drag to scrub feels natural
    onPointScrub: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    // NEW — annotations
    annotations: List<LineChartAnnotation> = emptyList()
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "line_progress"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val seriesKeys = dataset.series.keys.toList()
    val allValues = dataPoints.flatMap { it.values.values }
    val maxV = allValues.maxOrNull() ?: 1f
    val minV = allValues.minOrNull() ?: 0f
    // BUG FIX: coerce range so it's never zero
    val range = (maxV - minV).coerceAtLeast(1f)

    val xDivisor = (dataPoints.size - 1).coerceAtLeast(1).toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .detectChartGestures(
                onTap = { offset, canvasSize ->
                    val plotW = canvasSize.width - PAD_L - PAD_R
                    val xPositions = dataPoints.indices.map { i ->
                        PAD_L + (i.toFloat() / xDivisor) * plotW
                    }
                    crosshairState.update(
                        touchOffset = offset,
                        canvasSize = canvasSize,
                        dataPoints = dataPoints,
                        xPositions = xPositions,
                        maxValue = maxV,
                        minValue = minV,
                        seriesKeys = seriesKeys
                    )
                    val idx = crosshairState.activeIndex
                    val key = crosshairState.activeSeriesKey
                    if (idx != null && key != null) {
                        onPointScrub?.invoke(key, dataPoints[idx], idx)
                    }
                },
                onDrag = if (scrubMode) { _, current, canvasSize ->
                    val plotW = canvasSize.width - PAD_L - PAD_R
                    val xPositions = dataPoints.indices.map { i ->
                        PAD_L + (i.toFloat() / xDivisor) * plotW
                    }
                    crosshairState.update(
                        touchOffset = current,
                        canvasSize = canvasSize,
                        dataPoints = dataPoints,
                        xPositions = xPositions,
                        maxValue = maxV,
                        minValue = minV,
                        seriesKeys = seriesKeys
                    )
                    val idx = crosshairState.activeIndex
                    val key = crosshairState.activeSeriesKey
                    if (idx != null && key != null) {
                        onPointScrub?.invoke(key, dataPoints[idx], idx)
                    }
                } else null
            )
    ) {
        val plotW = size.width - PAD_L - PAD_R
        val plotH = size.height - PAD_T - PAD_B

        // ── 1. Annotations (behind grid) ──────────────────────────────────────
        drawAnnotations(annotations, PAD_L, PAD_T, plotW, plotH, dataPoints.size, maxV, minV)

        // ── 2. Grid + Y-labels ────────────────────────────────────────────────
        val gridLines = 5
        val yLabelPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        for (i in 0..gridLines) {
            val y = PAD_T + plotH * (1f - i / gridLines.toFloat())
            drawLine(
                color = config.gridColor.copy(alpha = 0.15f),
                start = Offset(PAD_L, y),
                end = Offset(PAD_L + plotW, y),
                strokeWidth = 1.dp.toPx()
            )
            val labelValue = (minV + range * i / gridLines).toInt()
            // BUG FIX: right-align labels so they never overflow into plot area
            drawContext.canvas.nativeCanvas.drawText(
                labelValue.toString(),
                PAD_L - 8f,
                y + 9f,
                yLabelPaint
            )
        }

        // ── 3. Series ─────────────────────────────────────────────────────────
        dataset.series.forEach { (seriesKey, seriesConfig) ->
            val pts = dataPoints.mapIndexed { i, point ->
                val v = point.getValue(seriesKey)
                val animV = minV + (v - minV) * progress
                Offset(
                    PAD_L + (i.toFloat() / xDivisor) * plotW,
                    PAD_T + plotH * (1f - (animV - minV) / range)
                )
            }

            val path = Path().apply {
                pts.forEachIndexed { i, p ->
                    if (i == 0) moveTo(p.x, p.y)
                    else {
                        val prev = pts[i - 1]
                        cubicTo(
                            prev.x + (p.x - prev.x) * curveSmoothing, prev.y,
                            p.x - (p.x - prev.x) * curveSmoothing, p.y,
                            p.x, p.y
                        )
                    }
                }
            }

            // Gradient fill
            if (fillArea) {
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, PAD_T + plotH)
                    lineTo(pts.first().x, PAD_T + plotH)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            seriesConfig.color.copy(alpha = 0.30f),
                            seriesConfig.color.copy(alpha = 0.00f)
                        ),
                        startY = pts.minOfOrNull { it.y } ?: PAD_T,
                        endY = PAD_T + plotH
                    )
                )
            }

            // Line stroke
            drawPath(
                path = path,
                color = seriesConfig.color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = when (val style = seriesConfig.lineStyle) {
                        is LineStyle.Dashed -> PathEffect.dashPathEffect(
                            floatArrayOf(style.dashLength, style.gapLength)
                        )
                        else -> null
                    }
                )
            )

            // Points
            if (showPoints) {
                val isActiveSeries = crosshairState.activeSeriesKey == seriesKey
                pts.forEachIndexed { i, p ->
                    val isSelected = isActiveSeries && crosshairState.activeIndex == i
                    val radius = if (isSelected) 10.dp.toPx() else 4.dp.toPx()

                    if (isSelected) {
                        // Outer pulse ring
                        drawCircle(
                            color = seriesConfig.color.copy(alpha = 0.20f),
                            radius = radius * 2f,
                            center = p
                        )
                    }
                    // White base
                    drawCircle(color = Color.White, radius = radius, center = p)
                    // Colored fill
                    drawCircle(color = seriesConfig.color, radius = radius * 0.7f, center = p)
                }
            }
        }

        // ── 4. X-axis labels ──────────────────────────────────────────────────
        val xLabelPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val skipStep = (dataPoints.size / 7).coerceAtLeast(1)
        val bottomY = size.height - 6f
        dataPoints.forEachIndexed { i, point ->
            if (i % skipStep != 0 && i != dataPoints.size - 1) return@forEachIndexed
            val rawX = PAD_L + (i.toFloat() / xDivisor) * plotW
            // BUG FIX: clamp label so first label never goes left of padL
            val labelW = xLabelPaint.measureText(point.label ?: "")
            val clampedX = rawX.coerceIn(PAD_L + labelW / 2, PAD_L + plotW - labelW / 2)
            drawContext.canvas.nativeCanvas.drawText(
                point.label ?: "",
                clampedX,
                bottomY,
                xLabelPaint
            )
        }

        // ── 5. Crosshair + tooltip ────────────────────────────────────────────
        val activeIdx = crosshairState.activeIndex
        if (activeIdx != null && activeIdx in dataPoints.indices) {
            val activeSeriesKey = crosshairState.activeSeriesKey ?: seriesKeys.firstOrNull()
            val accentColor = dataset.series[activeSeriesKey]?.color ?: Color(0xFF378ADD)

            // Snap crosshair Y to the active data point
            if (activeSeriesKey != null) {
                val v = dataPoints[activeIdx].getValue(activeSeriesKey)
                val animV = minV + (v - minV) * progress
                val snappedY = PAD_T + plotH * (1f - (animV - minV) / range)
                val snappedX = PAD_L + (activeIdx.toFloat() / xDivisor) * plotW
                crosshairState.position = Offset(snappedX, snappedY.coerceIn(PAD_T, PAD_T + plotH))
            }

            drawCrosshair(crosshairState, PAD_L, PAD_T, plotW, plotH, accentColor)

            // Tooltip pill
            val pos = crosshairState.position
            val key = crosshairState.activeSeriesKey
            if (pos != null && key != null) {
                val value = dataPoints[activeIdx].getValue(key)
                val label = "${dataset.series[key]?.name ?: key}: ${value.toInt()}"
                drawModernTooltip(label, pos, PAD_L, PAD_T, plotW, plotH, accentColor)
            }
        }
    }
}

// ── Modern tooltip ────────────────────────────────────────────────────────────

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawModernTooltip(
    text: String,
    anchor: Offset,
    padL: Float, padT: Float,
    plotW: Float, plotH: Float,
    color: Color
) {
    val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 26f
        color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    val textW = textPaint.measureText(text)
    val pillW = textW + 28f
    val pillH = 38f
    val pillR = 12f

    // Keep pill inside plot horizontally
    val pillX = (anchor.x - pillW / 2f).coerceIn(padL, padL + plotW - pillW)
    // Keep pill inside plot vertically — show below if near top
    val pillY = if (anchor.y - pillH - 14f >= padT)
        anchor.y - pillH - 14f
    else
        anchor.y + 14f

    // Shadow-like blurred backing (draw a slightly larger dark rect first)
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.18f),
        topLeft = Offset(pillX + 2f, pillY + 3f),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillR)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillR)
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        pillX + pillW / 2f,
        pillY + pillH / 2f + 9f,
        textPaint.apply { textAlign = Paint.Align.CENTER }
    )
}
