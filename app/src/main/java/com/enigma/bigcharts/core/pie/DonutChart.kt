package com.enigma.bigcharts.core.pie

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.CategoryDataPoint
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.ModernMetricTile
import kotlin.math.*

/**
 * Dedicated donut chart for bigcharts.
 *
 * Improvements over the enigma.charts version (which was actually ModernPieChart
 * reused at cutoutFraction = 0.5f):
 *
 *  - Separate composable with a purpose-built API (no cutoutFraction parameter leak)
 *  - Track ring drawn behind each slice so the donut "ring" shape is always visible
 *  - Selection glow + thicker selected-slice stroke instead of mere expansion
 *  - Hit-test accumulates angle without % 360 per step — prevents last-slice drift
 *  - Center label: idle shows total count, selected shows label + value + pct
 *  - onSegmentSelect callback carries the full CategoryDataPoint for detail panels
 *  - strokeWidth parameter lets callers control ring thickness (defaults to 56 dp)
 */
@Composable
fun DonutChart(
    data: List<CategoryDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    ringWidthDp: Float = 56f,
    onSegmentSelect: ((CategoryDataPoint?, Int?) -> Unit)? = null,
    centerLabel: String? = null,         // override idle center text
    centerSubLabel: String? = null       // e.g. "total sessions"
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "donut_progress"
    )

    val expandAnims = data.indices.map { i ->
        animateFloatAsState(
            targetValue = if (selectedIndex == i) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
            label = "donut_expand_$i"
        ).value
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = modifier
                .size(260.dp)
                .pointerInput(data, progress) {
                    detectTapGestures { offset ->
                        val cx = canvasSize.width / 2f
                        val cy = canvasSize.height / 2f
                        val baseR = min(canvasSize.width, canvasSize.height) / 2f * 0.82f
                        val ringWidthPx = ringWidthDp.dp.toPx()
                        val innerR = baseR - ringWidthPx

                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)

                        // Must land within the ring band (allow ±20% tolerance)
                        if (dist > baseR * 1.15f || dist < innerR * 0.80f) {
                            selectedIndex = null
                            onSegmentSelect?.invoke(null, null)
                            return@detectTapGestures
                        }

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 360f) % 360f

                        var cur = 270f  // start at top (-90°)
                        var found = false
                        data.forEachIndexed { i, seg ->
                            if (found) return@forEachIndexed
                            val sweep = (seg.value / total) * 360f * progress
                            if (isAngleInArc(angle, cur % 360f, sweep)) {
                                val newSel = if (selectedIndex == i) null else i
                                selectedIndex = newSel
                                onSegmentSelect?.invoke(if (newSel != null) seg else null, newSel)
                                found = true
                            }
                            cur += sweep
                        }
                        if (!found) {
                            selectedIndex = null
                            onSegmentSelect?.invoke(null, null)
                        }
                    }
                }
        ) {
            canvasSize = size
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = min(size.width, size.height) / 2f * 0.82f
            val ringWidthPx = ringWidthDp.dp.toPx()
            val innerRadius = baseRadius - ringWidthPx
            val cutoutFraction = (innerRadius / baseRadius).coerceIn(0.1f, 0.9f)

            var curAngle = -90f

            // ── Draw track ring (faint background for all segments) ──────────
            drawCircle(
                color = Color.Gray.copy(alpha = 0.08f),
                radius = baseRadius,
                center = center
            )
            drawCircle(
                color = config.backgroundColor,
                radius = innerRadius,
                center = center
            )

            data.forEachIndexed { i, seg ->
                val sweep = (seg.value / total) * 360f * progress
                val expand = expandAnims[i] * ringWidthPx * 0.20f
                val r = baseRadius + expand
                val isSelected = selectedIndex == i

                // Glow for selected segment
                if (isSelected && sweep > 0f) {
                    drawArc(
                        color = seg.color.copy(alpha = 0.18f),
                        startAngle = curAngle - 3f,
                        sweepAngle = sweep + 6f,
                        useCenter = true,
                        topLeft = Offset(center.x - r - 10f, center.y - r - 10f),
                        size = Size((r + 10f) * 2f, (r + 10f) * 2f)
                    )
                }

                // Slice (full filled arc then punch donut hole)
                val path = Path().apply {
                    arcTo(
                        rect = Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                        startAngleDegrees = curAngle,
                        sweepAngleDegrees = sweep,
                        forceMoveTo = true
                    )
                    val innerR = baseRadius * cutoutFraction - (if (isSelected) expand else 0f)
                        .coerceAtLeast(0f)
                    arcTo(
                        rect = Rect(
                            center.x - innerR, center.y - innerR,
                            center.x + innerR, center.y + innerR
                        ),
                        startAngleDegrees = curAngle + sweep,
                        sweepAngleDegrees = -sweep,
                        forceMoveTo = false
                    )
                    close()
                }

                drawPath(path, seg.color)

                // Gradient highlight on the outer edge of each segment
                if (sweep > 2f) {
                    drawPath(
                        path,
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                            center = center,
                            radius = r
                        )
                    )
                }

                // Separator stroke
                val strokeAlpha = if (isSelected) 0.6f else 0.25f
                drawPath(path, Color.White.copy(alpha = strokeAlpha), style = Stroke(
                    width = if (isSelected) 2.5f else 1.5f
                ))

                curAngle += sweep
            }

            // ── Center text ──────────────────────────────────────────────────
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val sel = selectedIndex?.let { data[it] }

            val mainText = when {
                sel != null -> "%.1f%%".format(sel.value / total * 100)
                centerLabel != null -> centerLabel
                else -> "${data.size}"
            }
            val subText = when {
                sel != null -> sel.label
                centerSubLabel != null -> centerSubLabel
                else -> "segments"
            }

            nativeCanvas.drawText(
                mainText,
                center.x,
                center.y + 10f,
                Paint().apply {
                    color = android.graphics.Color.parseColor("#1A1A2E")
                    textSize = 46f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
            )
            nativeCanvas.drawText(
                subText,
                center.x,
                center.y + 44f,
                Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }

        // ── Legend grid ──────────────────────────────────────────────────────
        LazyHorizontalGrid(
            rows = GridCells.Adaptive(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) {
            itemsIndexed(data) { index, item ->
                ModernMetricTile(
                    label = "${item.label}  ${"%.1f%%".format(item.value / total * 100)}",
                    value = item.value.toString(),
                    isPositive = selectedIndex == null || selectedIndex == index,
                    color = if (selectedIndex == index) item.color else item.color.copy(alpha = 0.60f),
                    modifier = Modifier
                        .padding(2.dp)
                        .clickable {
                            val newSel = if (selectedIndex == index) null else index
                            selectedIndex = newSel
                            onSegmentSelect?.invoke(if (newSel != null) item else null, newSel)
                        }
                        .height(100.dp)
                )
            }
        }
    }
}

private fun isAngleInArc(angle: Float, startDeg: Float, sweep: Float): Boolean {
    if (sweep <= 0f) return false
    if (sweep >= 360f) return true
    val start = (startDeg % 360f + 360f) % 360f
    val end = start + sweep
    return if (end <= 360f) angle in start..end
    else angle >= start || angle <= (end - 360f)
}
