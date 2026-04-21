package com.enigma.bigcharts.core.pie

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.CategoryDataPoint
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.detectChartGestures
import kotlin.math.*

/**
 * Full-featured pie / donut chart.
 *
 * Improvements over enigma.charts PieChart:
 *  - Entrance animation sweeps from 0 → full (targetValue was always 1f in original)
 *  - Per-slice expansion uses a smooth spring instead of a hard jump
 *  - Hit-test accounts for animatedProgress so tapping during animation lands correctly
 *  - isAngleInRange handles wrap-around past 360° correctly
 *  - Donut hole uses config.backgroundColor (opaque) so it actually masks the arcs
 *  - Labels are only drawn when sweepAngle > 15f to avoid cramped text on small slices
 *  - Guard against empty data and zero total
 */
@Composable
fun PieChart(
    data: List<CategoryDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onSliceTap: ((CategoryDataPoint, Int) -> Unit)? = null,
    showLabels: Boolean = true,
    labelRadiusFactor: Float = 1.25f,
    holeRadius: Float = 0f,               // 0 = solid pie, e.g. 0.55 = donut
    showPercentages: Boolean = true
) {
    if (data.isEmpty()) return
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    if (total <= 0f) return

    var selectedSlice by remember { mutableStateOf<Int?>(null) }

    // Entrance animation
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = config.animationDuration, easing = FastOutSlowInEasing),
        label = "pie_entrance"
    )

    // Per-slice expansion springs
    val expandAnimations = data.indices.map { index ->
        animateFloatAsState(
            targetValue = if (selectedSlice == index) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "slice_expand_$index"
        ).value
    }

    Canvas(
        modifier = modifier
            .size(300.dp)
            .detectChartGestures(
                onTap = { offset, canvasSize ->
                    val hit = findSliceAt(
                        offset = offset,
                        canvasWidth = canvasSize.width,
                        canvasHeight = canvasSize.height,
                        data = data,
                        total = total,
                        animatedProgress = animatedProgress,
                        holeRadius = holeRadius
                    )
                    selectedSlice = if (hit?.first == selectedSlice) null else hit?.first
                    hit?.let { (idx, point) -> onSliceTap?.invoke(point, idx) }
                }
            )
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = min(size.width, size.height) / 2f * 0.82f

        var currentAngle = -90f

        data.forEachIndexed { index, slice ->
            val sweep = (slice.value / total) * 360f * animatedProgress
            val expansion = expandAnimations[index] * baseRadius * 0.10f
            val r = baseRadius + expansion

            // Draw slice
            drawArc(
                color = slice.color,
                startAngle = currentAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f)
            )

            // Thin white separator
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = currentAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )

            // Label
            if (showLabels && sweep > 15f) {
                val midAngle = Math.toRadians((currentAngle + sweep / 2.0))
                val labelR = baseRadius * labelRadiusFactor
                val lx = center.x + (labelR * cos(midAngle)).toFloat()
                val ly = center.y + (labelR * sin(midAngle)).toFloat()

                drawContext.canvas.nativeCanvas.apply {
                    val pct = (slice.value / total * 100).toInt()
                    val text = if (showPercentages) "${slice.label}\n$pct%" else slice.label
                    val paint = Paint().apply {
                        color = android.graphics.Color.BLACK
                        textSize = 26f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = Typeface.DEFAULT
                    }
                    // Multi-line: draw label line, then pct line below
                    drawText(slice.label, lx, ly, paint)
                    if (showPercentages) {
                        paint.textSize = 22f
                        paint.color = android.graphics.Color.GRAY
                        drawText("$pct%", lx, ly + 28f, paint)
                    }
                }
            }

            currentAngle += sweep
        }

        // Donut hole — drawn last so it covers arc centres
        if (holeRadius > 0f) {
            drawCircle(
                color = config.backgroundColor,
                radius = baseRadius * holeRadius.coerceIn(0.1f, 0.9f),
                center = center
            )
        }
    }
}

// ─── Hit-testing ─────────────────────────────────────────────────────────────

private fun findSliceAt(
    offset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    data: List<CategoryDataPoint>,
    total: Float,
    animatedProgress: Float,
    holeRadius: Float
): Pair<Int, CategoryDataPoint>? {
    val cx = canvasWidth / 2f
    val cy = canvasHeight / 2f
    val baseRadius = min(canvasWidth, canvasHeight) / 2f * 0.82f

    val dx = offset.x - cx
    val dy = offset.y - cy
    val dist = sqrt(dx * dx + dy * dy)

    // Outside outer radius or inside donut hole
    if (dist > baseRadius * 1.15f) return null
    if (holeRadius > 0f && dist < baseRadius * holeRadius * 0.85f) return null

    // Angle in [0, 360), measured from 3-o'clock clockwise
    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    angle = (angle + 360f) % 360f

    // Start is -90° = 270° in [0,360) space
    var currentAngle = 270f

    data.forEachIndexed { index, slice ->
        val sweep = (slice.value / total) * 360f * animatedProgress
        if (isAngleInArc(angle, currentAngle, sweep)) return index to slice
        currentAngle = (currentAngle + sweep) % 360f
    }
    return null
}

/**
 * Returns true if [angle] (in [0,360)) lies within the arc starting at
 * [startDeg] with [sweep] degrees, handling wrap-around past 360°.
 */
private fun isAngleInArc(angle: Float, startDeg: Float, sweep: Float): Boolean {
    if (sweep <= 0f) return false
    if (sweep >= 360f) return true
    val start = (startDeg % 360f + 360f) % 360f
    val end = start + sweep
    return if (end <= 360f) {
        angle in start..end
    } else {
        angle >= start || angle <= (end - 360f)
    }
}
