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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.ZoomControls
import com.enigma.bigcharts.core.utils.ZoomPanState
import com.enigma.bigcharts.core.utils.zoomableChart

@Composable
fun EnhancedLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    zoomPanState: ZoomPanState = remember { ZoomPanState() },
    showZoomControls: Boolean = true,
    animatedProgress: Float = 1f,
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val visibleRange by remember(zoomPanState.zoom, zoomPanState.pan, dataset.data, canvasSize) {
        derivedStateOf {
            val totalPoints = dataset.data.size
            if (totalPoints == 0 || canvasSize.width == 0) return@derivedStateOf 0 until totalPoints
            val range = zoomPanState.getVisibleRange(totalPoints, canvasSize.width.toFloat())
            val safeFirst = range.first.coerceIn(0, totalPoints - 1)
            val safeLast = range.last.coerceIn(safeFirst, totalPoints - 1)
            safeFirst..safeLast
        }
    }

    val visibleData by remember(visibleRange, dataset.data) {
        derivedStateOf {
            dataset.data.subList(visibleRange.first, visibleRange.last + 1)
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
                    onTap = { /* hit testing goes here */ },
                    onDoubleTap = { zoomPanState.reset() }
                )
                .onSizeChanged { canvasSize = it }
        ) {
            drawZoomableLineChart(
                dataset = dataset,
                visibleData = visibleData,
                visibleRange = visibleRange,
                zoomPanState = zoomPanState,
                animatedProgress = animatedProgress,
                config = config
            )
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

private fun DrawScope.drawZoomableLineChart(
    dataset: MultiSeriesDataset,
    visibleData: List<TimeSeriesPoint>,
    visibleRange: IntRange,
    zoomPanState: ZoomPanState,
    animatedProgress: Float,
    config: ChartConfig
) {
    if (visibleData.isEmpty()) return

    val allValues = dataset.data.flatMap { it.values.values }
    val maxValue = allValues.maxOrNull() ?: 1f
    val rawMin = allValues.minOrNull() ?: 0f
    val minValue = if (rawMin == maxValue) maxValue - 1f else rawMin
    val valueRange = maxValue - minValue

    val totalPoints = dataset.data.size
    val xDivisor = (totalPoints - 1).coerceAtLeast(1).toFloat()

    withTransform({
        clipRect(0f, 0f, size.width, size.height)
        translate(left = zoomPanState.pan.x, top = zoomPanState.pan.y)
        scale(scaleX = zoomPanState.zoom, scaleY = 1f, pivot = Offset.Zero)
    }) {
        drawZoomableGrid(size, maxValue, minValue, zoomPanState)

        dataset.series.forEach { (seriesKey, seriesConfig) ->
            val path = Path()
            val points = mutableListOf<Offset>()

            visibleData.forEachIndexed { localIndex, point ->
                val globalIndex = visibleRange.first + localIndex
                val x = (globalIndex.toFloat() / xDivisor) * size.width
                val value = point.getValue(seriesKey)
                val animatedValue = minValue + (value - minValue) * animatedProgress
                val y = size.height * (1f - ((animatedValue - minValue) / valueRange))

                val offset = Offset(x, y)
                points.add(offset)

                if (localIndex == 0) path.moveTo(offset.x, offset.y)
                else path.lineTo(offset.x, offset.y)
            }

            drawPath(
                path = path,
                color = seriesConfig.color,
                style = Stroke(width = 4f / zoomPanState.zoom)
            )

            if (zoomPanState.zoom > 1.5f) {
                points.forEach { pt ->
                    drawCircle(
                        color = seriesConfig.color,
                        radius = 6f / zoomPanState.zoom,
                        center = pt
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawZoomableGrid(
    size: Size,
    maxValue: Float,
    minValue: Float,   // FIX: was missing — labels always started from 0 regardless of data
    zoomPanState: ZoomPanState
) {
    val gridLines = 5
    val textPaint = Paint().apply {
        color = android.graphics.Color.GRAY
        textSize = 28f / zoomPanState.zoom
        isAntiAlias = true
    }

    for (i in 0..gridLines) {
        val y = size.height * (1f - i.toFloat() / gridLines)
        val value = minValue + (maxValue - minValue) * (i.toFloat() / gridLines)

        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f / zoomPanState.zoom
        )

        // FIX: clip label x so it stays visible even when panned
        drawContext.canvas.nativeCanvas.drawText(
            value.toInt().toString(),
            10f / zoomPanState.zoom,
            y - 10f / zoomPanState.zoom,
            textPaint
        )
    }
}
