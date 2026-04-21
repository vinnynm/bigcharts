package com.enigma.bigcharts.core.line

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.enigma.charts.core.utils.CrosshairState
import com.enigma.charts.core.utils.LineChartAnnotation
import com.enigma.charts.core.utils.ZoomControls
import com.enigma.charts.core.utils.ZoomPanState
import com.enigma.charts.core.utils.zoomableChart
import com.enigma.charts.core.utils.ChartConfig
import com.enigma.charts.core.utils.MultiSeriesDataset
import com.enigma.charts.core.utils.TimeSeriesPoint
import com.enigma.charts.core.utils.drawAnnotations
import com.enigma.charts.core.utils.drawCrosshair

private const val PAD_L = 56f
private const val PAD_T = 20f
private const val PAD_R = 16f
private const val PAD_B = 40f

@Composable
fun EnhancedLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    zoomPanState: ZoomPanState = remember { ZoomPanState() },
    showZoomControls: Boolean = true,
    crosshairState: CrosshairState = remember { CrosshairState() },
    annotations: List<LineChartAnnotation> = emptyList(),
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val seriesKeys = dataset.series.keys.toList()

    val allValues = dataset.data.flatMap { it.values.values }
    val maxValue = (allValues.maxOrNull() ?: 1f)
    val rawMin = allValues.minOrNull() ?: 0f
    // BUG FIX: guard divide-by-zero when all values equal
    val minValue = if (rawMin == maxValue) maxValue - 1f else rawMin

    // BUG FIX: O(1) visible range using offset tracked here, not indexOf in draw
    val visibleData by remember(zoomPanState.zoom, zoomPanState.pan, dataset.data, canvasSize) {
        derivedStateOf {
            val totalPoints = dataset.data.size
            if (totalPoints == 0 || canvasSize.width == 0) return@derivedStateOf dataset.data
            val range = zoomPanState.getVisibleRange(totalPoints, canvasSize.width.toFloat())
            val safeFirst = range.first.coerceIn(0, totalPoints - 1)
            val safeLast = range.last.coerceIn(safeFirst, totalPoints - 1)
            dataset.data.subList(safeFirst, safeLast + 1)
        }
    }

    // Resolve the first index of visibleData in the full dataset — computed once per recompose
    val visibleStartIndex by remember(zoomPanState.zoom, zoomPanState.pan, dataset.data, canvasSize) {
        derivedStateOf {
            val totalPoints = dataset.data.size
            if (totalPoints == 0 || canvasSize.width == 0) return@derivedStateOf 0
            val range = zoomPanState.getVisibleRange(totalPoints, canvasSize.width.toFloat())
            range.first.coerceIn(0, totalPoints - 1)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .zoomableChart(
                    zoomPanState = zoomPanState,
                    contentSize = canvasSize,
                    onTap = { offset ->
                        // BUG FIX: implement the TODO hit-test
                        val cw = canvasSize.width.toFloat()
                        val ch = canvasSize.height.toFloat()
                        val plotW = cw - PAD_L - PAD_R
                        val plotH = ch - PAD_T - PAD_B
                        val totalPoints = dataset.data.size
                        val xDivisor = (totalPoints - 1).coerceAtLeast(1).toFloat()

                        // Transform offset from screen space → content space
                        val contentOffset = zoomPanState.transformPoint(offset)

                        val xPositions = dataset.data.indices.map { i ->
                            PAD_L + (i.toFloat() / xDivisor) * plotW
                        }
                        crosshairState.update(
                            touchOffset = contentOffset,
                            canvasSize = Size(cw, ch),
                            dataPoints = dataset.data,
                            xPositions = xPositions,
                            maxValue = maxValue,
                            minValue = minValue,
                            seriesKeys = seriesKeys
                        )
                        val idx = crosshairState.activeIndex
                        val key = crosshairState.activeSeriesKey
                        if (idx != null && key != null) {
                            onPointTap?.invoke(key, dataset.data[idx], idx)
                        }
                    },
                    onDoubleTap = { zoomPanState.reset() }
                )
                .onSizeChanged { canvasSize = it }
        ) {
            val plotW = size.width - PAD_L - PAD_R
            val plotH = size.height - PAD_T - PAD_B
            val totalPoints = dataset.data.size
            val xDivisor = (totalPoints - 1).coerceAtLeast(1).toFloat()

            // ── Grid — drawn OUTSIDE the zoom transform so it stays fixed ────
            // BUG FIX: original drawZoomableGrid was inside withTransform — grid
            //          would pan/zoom with data, making labels fly off screen.
            drawEnhancedGrid(size, maxValue, minValue, zoomPanState, config, plotW, plotH)

            // ── Annotations (inside transform so they scale with data) ────────
            withTransform({
                clipRect(PAD_L, PAD_T, PAD_L + plotW, PAD_T + plotH)
                translate(left = zoomPanState.pan.x, top = 0f)
                scale(scaleX = zoomPanState.zoom, scaleY = 1f, pivot = Offset(PAD_L, 0f))
            }) {
                drawAnnotations(annotations, PAD_L, PAD_T, plotW, plotH, totalPoints, maxValue, minValue)
            }

            // ── Series paths ──────────────────────────────────────────────────
            withTransform({
                clipRect(PAD_L, PAD_T, PAD_L + plotW, PAD_T + plotH)
                translate(left = zoomPanState.pan.x, top = 0f)
                scale(scaleX = zoomPanState.zoom, scaleY = 1f, pivot = Offset(PAD_L, 0f))
            }) {
                dataset.series.forEach { (seriesKey, seriesConfig) ->
                    val path = Path()
                    val points = mutableListOf<Offset>()

                    // BUG FIX: use visibleStartIndex + localIndex instead of indexOf (was O(n²))
                    visibleData.forEachIndexed { localIndex, point ->
                        val globalIndex = visibleStartIndex + localIndex
                        val x = PAD_L + (globalIndex.toFloat() / xDivisor) * plotW
                        val value = point.getValue(seriesKey)
                        val y = PAD_T + plotH * (1f - ((value - minValue) / (maxValue - minValue)))
                        val offset = Offset(x, y.coerceIn(PAD_T, PAD_T + plotH))
                        points.add(offset)

                        if (localIndex == 0) path.moveTo(offset.x, offset.y)
                        else path.lineTo(offset.x, offset.y)
                    }

                    drawPath(
                        path = path,
                        color = seriesConfig.color,
                        // BUG FIX: scale stroke width inversely so it looks consistent at any zoom
                        style = Stroke(width = 4f / zoomPanState.zoom, cap = StrokeCap.Round)
                    )

                    // Show dots when zoomed in enough to appreciate them
                    if (zoomPanState.zoom > 1.5f) {
                        val isActiveSeries = crosshairState.activeSeriesKey == seriesKey
                        points.forEachIndexed { localIndex, pt ->
                            val globalIndex = visibleStartIndex + localIndex
                            val isSelected = isActiveSeries && crosshairState.activeIndex == globalIndex
                            val r = (if (isSelected) 9f else 5f) / zoomPanState.zoom
                            if (isSelected) {
                                drawCircle(seriesConfig.color.copy(alpha = 0.22f), r * 2.2f, pt)
                            }
                            drawCircle(Color.White, r, pt)
                            drawCircle(seriesConfig.color, r * 0.65f, pt)
                        }
                    }
                }
            }

            // ── Crosshair + tooltip (screen-space, no transform) ──────────────
            val activeIdx = crosshairState.activeIndex
            if (activeIdx != null && activeIdx in dataset.data.indices) {
                val key = crosshairState.activeSeriesKey ?: seriesKeys.firstOrNull()
                val accent = dataset.series[key]?.color ?: Color(0xFF378ADD)

                // Map data-space X back to screen space (accounting for zoom/pan)
                val dataX = PAD_L + (activeIdx.toFloat() / xDivisor) * plotW
                val screenX = dataX * zoomPanState.zoom + zoomPanState.pan.x

                if (key != null) {
                    val v = dataset.data[activeIdx].getValue(key)
                    val screenY = (PAD_T + plotH * (1f - (v - minValue) / (maxValue - minValue)))
                        .coerceIn(PAD_T, PAD_T + plotH)

                    // Update position to screen space
                    crosshairState.position = Offset(screenX.coerceIn(PAD_L, PAD_L + plotW), screenY)
                }

                drawCrosshair(crosshairState, PAD_L, PAD_T, plotW, plotH, accent)

                // Tooltip
                val pos = crosshairState.position
                if (pos != null && key != null) {
                    val value = dataset.data[activeIdx].getValue(key)
                    val label = "${dataset.series[key]?.name ?: key}: ${value.toInt()}"
                    drawEnhancedTooltip(label, pos, PAD_L, PAD_T, plotW, plotH, accent)
                }
            }
        }

        if (showZoomControls) {
            ZoomControls(
                zoomPanState = zoomPanState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                contentSize = canvasSize
            )
        }
    }
}

// ── Grid (drawn in screen space — stays fixed during zoom/pan) ────────────────

private fun DrawScope.drawEnhancedGrid(
    size: Size,
    maxValue: Float,
    minValue: Float,
    zoomPanState: ZoomPanState,
    config: ChartConfig,
    plotW: Float,
    plotH: Float
) {
    val gridLines = 5
    val paint = Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }
    for (i in 0..gridLines) {
        val y = PAD_T + plotH * (1f - i.toFloat() / gridLines)
        val value = (minValue + (maxValue - minValue) * (i.toFloat() / gridLines)).toInt()

        drawLine(
            color = config.gridColor.copy(alpha = 0.18f),
            start = Offset(PAD_L, y),
            end = Offset(PAD_L + plotW, y),
            strokeWidth = 1f
        )
        // BUG FIX: right-align label text so it never spills into the plot area
        drawContext.canvas.nativeCanvas.drawText(
            value.toString(),
            PAD_L - 8f,
            y + 9f,
            paint
        )
    }
}

// ── Tooltip ────────────────────────────────────────────────────────────────────

private fun DrawScope.drawEnhancedTooltip(
    text: String,
    anchor: Offset,
    padL: Float, padT: Float,
    plotW: Float, plotH: Float,
    color: Color
) {
    val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 26f
        this.color = android.graphics.Color.WHITE
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val tw = textPaint.measureText(text)
    val pw = tw + 28f
    val ph = 38f
    val px = (anchor.x - pw / 2f).coerceIn(padL, padL + plotW - pw)
    val py = if (anchor.y - ph - 12f >= padT) anchor.y - ph - 12f else anchor.y + 12f

    drawRoundRect(
        color = Color.Black.copy(alpha = 0.15f),
        topLeft = Offset(px + 2f, py + 3f),
        size = Size(pw, ph),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(11f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(px, py),
        size = Size(pw, ph),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(11f)
    )
    drawContext.canvas.nativeCanvas.drawText(
        text,
        px + pw / 2f,
        py + ph / 2f + 9f,
        textPaint.apply { textAlign = Paint.Align.CENTER }
    )
}
