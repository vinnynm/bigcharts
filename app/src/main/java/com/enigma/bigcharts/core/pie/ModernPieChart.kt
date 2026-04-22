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
 * Modern pie / donut chart for bigcharts.
 *
 * Improvements over enigma.charts ModernPieChart:
 *  - Gradient fill on each slice (radial-style via SweepGradient approximation)
 *  - Glow ring drawn behind the selected slice instead of just expanding it
 *  - Center label shows selected segment label + percentage (not just %)
 *  - Hit-test uses the same corrected isAngleInArc() as PieChart.kt — no drift
 *  - cur accumulation uses raw float addition (not % 360 per step) to avoid
 *    floating-point drift that causes the last slice to be missed
 *  - Legend percentage formatted to 1 decimal place
 *  - Null-safe: guards empty data, zero total
 */
@Composable
fun ModernPieChart(
    data: List<CategoryDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    cutoutFraction: Float = 0.5f,
    onSliceTap: ((CategoryDataPoint, Int) -> Unit)? = null
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "pie_progress"
    )

    // Per-slice expansion springs
    val expandAnims = data.indices.map { i ->
        animateFloatAsState(
            targetValue = if (selectedIndex == i) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label = "expand_$i"
        ).value
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = modifier
                .size(300.dp)
                .pointerInput(data, progress) {
                    detectTapGestures { offset ->
                        val cx = canvasSize.width / 2f
                        val cy = canvasSize.height / 2f
                        val baseR = min(canvasSize.width, canvasSize.height) / 2f * 0.8f
                        val dx = offset.x - cx
                        val dy = offset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)

                        // Outside chart or inside donut hole
                        if (dist > baseR * 1.15f ||
                            (cutoutFraction > 0f && dist < baseR * cutoutFraction * 0.85f)
                        ) {
                            selectedIndex = null
                            return@detectTapGestures
                        }

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 360f) % 360f

                        // Accumulate without modulo to avoid drift
                        var cur = 270f
                        var found = false
                        data.forEachIndexed { i, seg ->
                            if (found) return@forEachIndexed
                            val sweep = (seg.value / total) * 360f * progress
                            val startN = cur % 360f
                            if (isAngleInArc(angle, startN, sweep)) {
                                selectedIndex = if (selectedIndex == i) null else i
                                onSliceTap?.invoke(seg, i)
                                found = true
                            }
                            cur += sweep
                        }
                        if (!found) selectedIndex = null
                    }
                }
        ) {
            canvasSize = size
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = min(size.width, size.height) / 2f * 0.8f

            var curAngle = -90f

            data.forEachIndexed { i, seg ->
                val sweep = (seg.value / total) * 360f * progress
                val expand = expandAnims[i] * baseRadius * 0.09f
                val r = baseRadius + expand
                val isSelected = selectedIndex == i

                // Glow ring behind selected slice
                if (isSelected && sweep > 0f) {
                    drawArc(
                        color = seg.color.copy(alpha = 0.22f),
                        startAngle = curAngle - 2f,
                        sweepAngle = sweep + 4f,
                        useCenter = true,
                        topLeft = Offset(center.x - r - 8f, center.y - r - 8f),
                        size = Size((r + 8f) * 2f, (r + 8f) * 2f)
                    )
                }

                // Slice path using arc + inner arc for donut shape
                val path = Path().apply {
                    arcTo(
                        rect = Rect(center.x - r, center.y - r, center.x + r, center.y + r),
                        startAngleDegrees = curAngle,
                        sweepAngleDegrees = sweep,
                        forceMoveTo = true
                    )
                    if (cutoutFraction > 0f) {
                        val innerR = baseRadius * cutoutFraction
                        arcTo(
                            rect = Rect(
                                center.x - innerR, center.y - innerR,
                                center.x + innerR, center.y + innerR
                            ),
                            startAngleDegrees = curAngle + sweep,
                            sweepAngleDegrees = -sweep,
                            forceMoveTo = false
                        )
                    } else {
                        lineTo(center.x, center.y)
                    }
                    close()
                }

                // Base fill
                drawPath(path, seg.color)

                // Lighter inner gradient overlay for depth
                if (sweep > 0f) {
                    val midAngleRad = Math.toRadians((curAngle + sweep / 2.0))
                    val highlightCenter = Offset(
                        center.x + (r * 0.4f * cos(midAngleRad)).toFloat(),
                        center.y + (r * 0.4f * sin(midAngleRad)).toFloat()
                    )
                    drawPath(
                        path,
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            center = highlightCenter,
                            radius = r * 0.9f
                        )
                    )
                }

                // White separator stroke
                drawPath(path, Color.White.copy(alpha = 0.3f), style = Stroke(1.5f))

                curAngle += sweep
            }

            // Center label — show selected slice info when donut
            if (cutoutFraction > 0.3f) {
                val textColor = android.graphics.Color.parseColor("#333333")
                val nativeCanvas = drawContext.canvas.nativeCanvas

                if (selectedIndex != null) {
                    val sel = data[selectedIndex!!]
                    val pct = "%.1f%%".format(sel.value / total * 100)

                    nativeCanvas.drawText(
                        pct,
                        center.x,
                        center.y + 8f,
                        Paint().apply {
                            color = textColor
                            textSize = 52f
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.DEFAULT_BOLD
                            isAntiAlias = true
                        }
                    )
                    nativeCanvas.drawText(
                        sel.label,
                        center.x,
                        center.y + 44f,
                        Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 26f
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                } else {
                    // Show total when nothing selected
                    nativeCanvas.drawText(
                        "${data.size}",
                        center.x,
                        center.y + 8f,
                        Paint().apply {
                            color = textColor
                            textSize = 52f
                            textAlign = Paint.Align.CENTER
                            typeface = Typeface.DEFAULT_BOLD
                            isAntiAlias = true
                        }
                    )
                    nativeCanvas.drawText(
                        "segments",
                        center.x,
                        center.y + 42f,
                        Paint().apply {
                            color = android.graphics.Color.GRAY
                            textSize = 24f
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
        }

        // Legend grid
        LazyHorizontalGrid(
            rows = GridCells.Adaptive(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            itemsIndexed(data) { index, item ->
                val pctStr = "%.1f%%".format(item.value / total * 100)
                ModernMetricTile(
                    label = "${item.label}  $pctStr",
                    value = item.value.toString(),
                    isPositive = true,
                    color = if (selectedIndex == index)
                        item.color
                    else
                        item.color.copy(alpha = 0.65f),
                    modifier = Modifier
                        .padding(2.dp)
                        .clickable { selectedIndex = if (selectedIndex == index) null else index }
                        .height(100.dp)
                )
            }
        }
    }
}

// Shared helper — same logic as PieChart.kt
private fun isAngleInArc(angle: Float, startDeg: Float, sweep: Float): Boolean {
    if (sweep <= 0f) return false
    if (sweep >= 360f) return true
    val start = (startDeg % 360f + 360f) % 360f
    val end = start + sweep
    return if (end <= 360f) angle in start..end
    else angle >= start || angle <= (end - 360f)
}
