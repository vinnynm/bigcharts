package com.enigma.charts.core.utils

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect

// ── Annotation model ──────────────────────────────────────────────────────────

/**
 * Declarative annotations drawn as overlays on any line chart.
 *
 * Usage example:
 *   annotations = listOf(
 *       LineChartAnnotation.VerticalLine(index = 6, label = "Launch"),
 *       LineChartAnnotation.HorizontalLine(value = 80f, label = "Target"),
 *       LineChartAnnotation.Region(startIndex = 3, endIndex = 5, label = "Sale period")
 *   )
 */
sealed class LineChartAnnotation {

    /**
     * A vertical dashed line at a specific data-point index.
     * @param index    Index into the data list (0-based).
     * @param label    Short label drawn at the top of the line.
     * @param color    Line + label color. Defaults to a muted orange.
     */
    data class VerticalLine(
        val index: Int,
        val label: String = "",
        val color: Color = Color(0xFFEF9F27),
        val strokeWidth: Float = 2f
    ) : LineChartAnnotation()

    /**
     * A horizontal dashed line at a specific Y-value.
     * @param value    The data value at which to draw the line.
     * @param label    Short label drawn at the right edge.
     * @param color    Line + label color. Defaults to a muted red.
     */
    data class HorizontalLine(
        val value: Float,
        val label: String = "",
        val color: Color = Color(0xFFD85A30),
        val strokeWidth: Float = 2f
    ) : LineChartAnnotation()

    /**
     * A shaded vertical band between two data-point indices.
     * @param startIndex  First index of the region (inclusive).
     * @param endIndex    Last index of the region (inclusive).
     * @param label       Optional label drawn at the top-centre of the band.
     * @param color       Fill color. Use a low-alpha value (e.g. 0.12f).
     */
    data class Region(
        val startIndex: Int,
        val endIndex: Int,
        val label: String = "",
        val color: Color = Color(0xFF378ADD).copy(alpha = 0.12f)
    ) : LineChartAnnotation()
}

// ── Crosshair / scrub state ───────────────────────────────────────────────────

/**
 * Holds the mutable state for a crosshair that can be driven by either
 * tap or drag gestures.  Pass a single instance into the chart composable
 * and optionally observe [activeIndex] / [activePoint] from outside to
 * build a custom tooltip or info panel.
 *
 * @param snapToPoints  If true the crosshair snaps to the nearest data-point
 *                      X-position.  If false it tracks the finger continuously.
 */
@Stable
class CrosshairState(val snapToPoints: Boolean = true) {

    /** Screen-space position of the crosshair, or null when hidden. */
    var position: Offset? by mutableStateOf(null)
        internal set

    /** Index of the nearest data point, or null when hidden. */
    var activeIndex: Int? by mutableStateOf(null)
        internal set

    /** The actual data-point nearest to the crosshair, or null when hidden. */
    var activePoint: TimeSeriesPoint? by mutableStateOf(null)
        internal set

    /** Series key of the nearest data value on the Y axis. */
    var activeSeriesKey: String? by mutableStateOf(null)
        internal set

    /** Clear the crosshair (e.g. on pointer-up if using tap mode). */
    fun clear() {
        position = null
        activeIndex = null
        activePoint = null
        activeSeriesKey = null
    }

    /**
     * Update state from a raw touch [touchOffset] given the canvas [canvasSize],
     * the full [dataPoints] list, and the current [xPositions] for each point.
     */
    fun update(
        touchOffset: Offset,
        canvasSize: androidx.compose.ui.geometry.Size,
        dataPoints: List<TimeSeriesPoint>,
        xPositions: List<Float>,
        maxValue: Float,
        minValue: Float,
        seriesKeys: List<String>
    ) {
        if (dataPoints.isEmpty() || xPositions.isEmpty()) return

        val nearestIndex = xPositions.indices
            .minByOrNull { kotlin.math.abs(xPositions[it] - touchOffset.x) }
            ?: return

        val snappedX = if (snapToPoints) xPositions[nearestIndex] else touchOffset.x

        val valueRange = (maxValue - minValue).coerceAtLeast(1f)
        val point = dataPoints[nearestIndex]

        // Find the series whose Y is closest to the touch Y
        var minDist = Float.MAX_VALUE
        var bestSeries = seriesKeys.firstOrNull() ?: ""
        seriesKeys.forEach { key ->
            val v = point.getValue(key)
            val expectedY = canvasSize.height * (1f - (v - minValue) / valueRange)
            val dist = kotlin.math.abs(expectedY - touchOffset.y)
            if (dist < minDist) {
                minDist = dist
                bestSeries = key
            }
        }

        position = Offset(snappedX, touchOffset.y)
        activeIndex = nearestIndex
        activePoint = point
        activeSeriesKey = bestSeries
    }
}

// ── Canvas drawing helpers ────────────────────────────────────────────────────

import android.graphics.Paint as AndroidPaint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Draw all [annotations] onto a chart canvas.
 *
 * @param padL      Left padding of the plot area.
 * @param padT      Top padding of the plot area.
 * @param plotW     Width of the plot area (canvas.width - padL - padR).
 * @param plotH     Height of the plot area (canvas.height - padT - padB).
 * @param totalPts  Total number of data points (used to compute X from index).
 * @param maxValue  Maximum data value (used to compute Y from value).
 * @param minValue  Minimum data value.
 */
fun DrawScope.drawAnnotations(
    annotations: List<LineChartAnnotation>,
    padL: Float,
    padT: Float,
    plotW: Float,
    plotH: Float,
    totalPts: Int,
    maxValue: Float,
    minValue: Float
) {
    if (annotations.isEmpty() || totalPts == 0) return

    val xDivisor = (totalPts - 1).coerceAtLeast(1).toFloat()
    val valueRange = (maxValue - minValue).coerceAtLeast(1f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))

    val labelPaint = AndroidPaint().apply {
        isAntiAlias = true
        textSize = 22f
    }

    annotations.forEach { annotation ->
        when (annotation) {
            is LineChartAnnotation.VerticalLine -> {
                val idx = annotation.index.coerceIn(0, totalPts - 1)
                val x = padL + (idx.toFloat() / xDivisor) * plotW
                drawLine(
                    color = annotation.color,
                    start = Offset(x, padT),
                    end = Offset(x, padT + plotH),
                    strokeWidth = annotation.strokeWidth,
                    pathEffect = dash
                )
                if (annotation.label.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        annotation.label,
                        x + 6f,
                        padT + 18f,
                        labelPaint.apply { color = annotation.color.toArgb() }
                    )
                }
            }

            is LineChartAnnotation.HorizontalLine -> {
                val y = padT + plotH * (1f - (annotation.value - minValue) / valueRange)
                drawLine(
                    color = annotation.color,
                    start = Offset(padL, y),
                    end = Offset(padL + plotW, y),
                    strokeWidth = annotation.strokeWidth,
                    pathEffect = dash
                )
                if (annotation.label.isNotEmpty()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        annotation.label,
                        padL + plotW - labelPaint.measureText(annotation.label) - 6f,
                        y - 6f,
                        labelPaint.apply { color = annotation.color.toArgb() }
                    )
                }
            }

            is LineChartAnnotation.Region -> {
                val startIdx = annotation.startIndex.coerceIn(0, totalPts - 1)
                val endIdx = annotation.endIndex.coerceIn(startIdx, totalPts - 1)
                val x1 = padL + (startIdx.toFloat() / xDivisor) * plotW
                val x2 = padL + (endIdx.toFloat() / xDivisor) * plotW
                drawRect(
                    color = annotation.color,
                    topLeft = Offset(x1, padT),
                    size = Size(x2 - x1, plotH)
                )
                if (annotation.label.isNotEmpty()) {
                    val midX = (x1 + x2) / 2f
                    drawContext.canvas.nativeCanvas.drawText(
                        annotation.label,
                        midX,
                        padT + 18f,
                        labelPaint.apply {
                            color = annotation.color.copy(alpha = 1f).toArgb()
                            textAlign = AndroidPaint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}

/**
 * Draw the crosshair overlay (vertical + horizontal dashed lines, intersection dot).
 */
fun DrawScope.drawCrosshair(
    crosshairState: CrosshairState,
    padL: Float,
    padT: Float,
    plotW: Float,
    plotH: Float,
    accentColor: Color = Color(0xFF378ADD)
) {
    val pos = crosshairState.position ?: return
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))

    // Vertical line — full plot height
    drawLine(
        color = accentColor.copy(alpha = 0.55f),
        start = Offset(pos.x, padT),
        end = Offset(pos.x, padT + plotH),
        strokeWidth = 1.5f,
        pathEffect = dash
    )

    // Horizontal line — full plot width
    drawLine(
        color = accentColor.copy(alpha = 0.35f),
        start = Offset(padL, pos.y.coerceIn(padT, padT + plotH)),
        end = Offset(padL + plotW, pos.y.coerceIn(padT, padT + plotH)),
        strokeWidth = 1f,
        pathEffect = dash
    )

    // Intersection dot
    drawCircle(
        color = accentColor,
        radius = 4f,
        center = Offset(pos.x, pos.y.coerceIn(padT, padT + plotH))
    )
    drawCircle(
        color = Color.White,
        radius = 2.5f,
        center = Offset(pos.x, pos.y.coerceIn(padT, padT + plotH))
    )
}

// Helper to convert Compose Color → Android ARGB int
private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)
