package com.enigma.bigcharts.core.pie

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.*

/**
 * Lightweight pie / donut for thumbnails, list rows, and embedded cards.
 *
 * Deliberately minimal:
 *  - No labels (add an external legend if needed)
 *  - No legend grid (call site composes its own)
 *  - Single modifier size parameter — caller controls the size
 *  - No heavy dependencies (no ModernMetricTile, no LazyHorizontalGrid)
 *  - Entrance animation + tap selection still included for interactivity
 *
 * Use this instead of PieChart/DonutChart when:
 *  - You need a small inline chart (e.g. 48 dp in a table row)
 *  - You want a pure Canvas composable with no layout side-effects
 *  - You are rendering many charts in a list (RecyclerView / LazyColumn)
 */
@Composable
fun SimplePieChart(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    holeRadius: Float = 0f,            // 0 = solid, 0.5 = donut
    strokeColor: Color = Color.White,
    strokeWidth: Float = 1.5f,
    animDurationMs: Int = 700,
    onSliceTap: ((Int) -> Unit)? = null
) {
    require(values.size == colors.size) {
        "SimplePieChart: values.size (${values.size}) must equal colors.size (${colors.size})"
    }
    if (values.isEmpty()) return

    val total = values.sum().coerceAtLeast(1f)
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(animDurationMs, easing = FastOutSlowInEasing),
        label = "simple_pie"
    )

    val expandAnims = values.indices.map { i ->
        animateFloatAsState(
            targetValue = if (selectedIndex == i) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy),
            label = "sp_expand_$i"
        ).value
    }

    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(values, progress) {
                if (onSliceTap == null) return@pointerInput
                detectTapGestures { offset ->
                    val cx = w / 2f; val cy = h / 2f
                    val baseR = min(w, h) / 2f * 0.9f
                    val dx = offset.x - cx; val dy = offset.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist > baseR * 1.1f) { selectedIndex = null; return@detectTapGestures }
                    if (holeRadius > 0f && dist < baseR * holeRadius * 0.85f) {
                        selectedIndex = null; return@detectTapGestures
                    }
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    angle = (angle + 360f) % 360f

                    var cur = 270f
                    var found = false
                    values.forEachIndexed { i, v ->
                        if (found) return@forEachIndexed
                        val sweep = (v / total) * 360f * progress
                        if (isAngleInSimpleArc(angle, cur % 360f, sweep)) {
                            selectedIndex = if (selectedIndex == i) null else i
                            onSliceTap(i)
                            found = true
                        }
                        cur += sweep
                    }
                    if (!found) selectedIndex = null
                }
            }
    ) {
        w = size.width; h = size.height
        val center = Offset(w / 2f, h / 2f)
        val baseRadius = min(w, h) / 2f * 0.9f

        var curAngle = -90f

        values.forEachIndexed { i, v ->
            val sweep = (v / total) * 360f * progress
            val expand = expandAnims[i] * baseRadius * 0.08f
            val r = baseRadius + expand

            if (sweep > 0f) {
                drawArc(
                    color = colors[i],
                    startAngle = curAngle,
                    sweepAngle = sweep,
                    useCenter = holeRadius <= 0f,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = if (holeRadius > 0f)
                        Stroke(width = (baseRadius * (1f - holeRadius)).coerceAtLeast(4f))
                    else
                        androidx.compose.ui.graphics.drawscope.Fill
                )

                if (strokeWidth > 0f) {
                    drawArc(
                        color = strokeColor.copy(alpha = 0.4f),
                        startAngle = curAngle,
                        sweepAngle = sweep,
                        useCenter = holeRadius <= 0f,
                        topLeft = Offset(center.x - r, center.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(strokeWidth)
                    )
                }
            }

            curAngle += sweep
        }
    }
}

private fun isAngleInSimpleArc(angle: Float, startDeg: Float, sweep: Float): Boolean {
    if (sweep <= 0f) return false
    if (sweep >= 360f) return true
    val start = (startDeg % 360f + 360f) % 360f
    val end = start + sweep
    return if (end <= 360f) angle in start..end
    else angle >= start || angle <= (end - 360f)
}
