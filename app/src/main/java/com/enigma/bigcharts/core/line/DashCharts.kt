package com.enigma.charts.core

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import com.enigma.charts.core.utils.BarSeries
import com.enigma.bigcharts.core.line.CrosshairState
import com.enigma.charts.core.utils.HBarEntry
import com.enigma.bigcharts.core.line.LineChartAnnotation
import com.enigma.charts.core.utils.LineSeries
import com.enigma.charts.core.utils.PieSegment
import com.enigma.charts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.line.drawAnnotations
import com.enigma.bigcharts.core.line.drawCrosshair
import kotlin.collections.flatMap
import kotlin.math.*

// ── Layout constants ──────────────────────────────────────────────────────────

private const val PAD_L = 44f
private const val PAD_R = 8f
private const val PAD_T = 10f
private const val PAD_B = 30f
private const val GRID_LINES = 5   // BUG FIX: was series.size — constant is correct

// ── DashLineChart ─────────────────────────────────────────────────────────────

@Composable
fun DashLineChart(
    series: List<LineSeries>,
    labels: List<String>,
    fillArea: Boolean = false,
    modifier: Modifier = Modifier,
    // NEW — crosshair / scrub
    crosshairState: CrosshairState = remember { CrosshairState() },
    scrubMode: Boolean = true,
    onPointScrub: ((Int, String, Float) -> Unit)? = null,   // index, seriesLabel, value
    // NEW — annotations
    annotations: List<LineChartAnnotation> = emptyList()
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "line"
    )

    // BUG FIX: guard empty labels
    if (labels.isEmpty() || series.isEmpty()) return

    val allValues = series.flatMap { it.points }
    val maxV = allValues.maxOrNull() ?: 1f
    val minV = allValues.minOrNull() ?: 0f
    val range = (maxV - minV).coerceAtLeast(1f)

    // BUG FIX: guard single-point series — (size - 1) would be 0
    val pointCount = series.maxOf { it.points.size }
    val xDivisor = (pointCount - 1).coerceAtLeast(1).toFloat()

    Canvas(
        modifier = modifier
            .pointerInput(series, labels) {
                detectTapGestures(
                    onTap = { offset ->
                        val cw = size.width.toFloat()
                        val ch = size.height.toFloat()
                        val plotW = cw - PAD_L - PAD_R
                        val plotH = ch - PAD_T - PAD_B
                        val xPositions = (0 until pointCount).map { i ->
                            PAD_L + (i.toFloat() / xDivisor) * plotW
                        }
                        // Find nearest X index
                        val nearestIdx = xPositions.indices
                            .minByOrNull { abs(xPositions[it] - offset.x) } ?: return@detectTapGestures

                        val snappedX = xPositions[nearestIdx]

                        // Find nearest series Y
                        var bestSeries = series.firstOrNull()?.label ?: ""
                        var minDist = Float.MAX_VALUE
                        series.forEach { s ->
                            val v = s.points.getOrNull(nearestIdx) ?: return@forEach
                            val animV = minV + (v - minV) * progress
                            val expectedY = PAD_T + plotH * (1f - (animV - minV) / range)
                            val dist = abs(expectedY - offset.y)
                            if (dist < minDist) {
                                minDist = dist
                                bestSeries = s.label
                            }
                        }

                        val snappedY = run {
                            val s = series.firstOrNull { it.label == bestSeries }
                            val v = s?.points?.getOrNull(nearestIdx) ?: 0f
                            val animV = minV + (v - minV) * progress
                            PAD_T + (ch - PAD_T - PAD_B) * (1f - (animV - minV) / range)
                        }

                        crosshairState.position = Offset(snappedX, snappedY)
                        crosshairState.activeIndex = nearestIdx
                        crosshairState.activeSeriesKey = bestSeries

                        val v = series.firstOrNull { it.label == bestSeries }?.points?.getOrNull(nearestIdx) ?: 0f
                        onPointScrub?.invoke(nearestIdx, bestSeries, v)
                    }
                )
            }
    ) {
        val plotW = size.width - PAD_L - PAD_R
        val plotH = size.height - PAD_T - PAD_B

        // Convert labels list into fake TimeSeriesPoints for the shared helper
        val fakePoints: List<TimeSeriesPoint> = labels.mapIndexed { i, lbl ->
            TimeSeriesPoint(timestamp = i.toLong(), values = emptyMap(), label = lbl)
        }

        // ── 1. Annotations ────────────────────────────────────────────────────
        drawAnnotations(annotations, PAD_L, PAD_T, plotW, plotH, pointCount, maxV, minV)

        // ── 2. Grid ───────────────────────────────────────────────────────────
        // BUG FIX: use GRID_LINES constant, not series.size
        val labelPaint = Paint().apply {
            color = 0xFF888888.toInt()
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        for (i in 0..GRID_LINES) {
            val y = PAD_T + plotH * (1f - i / GRID_LINES.toFloat())
            drawLine(Color.Gray.copy(alpha = 0.18f), Offset(PAD_L, y), Offset(PAD_L + plotW, y), 1f)
            val v = (minV + range * i / GRID_LINES).toInt()
            // BUG FIX: right-align so labels never bleed into the plot area
            drawContext.canvas.nativeCanvas.drawText("$v", PAD_L - 6f, y + 8f, labelPaint)
        }

        // ── 3. X labels ───────────────────────────────────────────────────────
        val xLabelPaint = Paint().apply {
            color = 0xFF888888.toInt()
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val skipStep = (labels.size / 6).coerceAtLeast(1)
        labels.forEachIndexed { i, lbl ->
            if (i % skipStep != 0 && i != labels.size - 1) return@forEachIndexed
            val x = PAD_L + (i.toFloat() / xDivisor) * plotW
            drawContext.canvas.nativeCanvas.drawText(lbl, x, size.height - 4f, xLabelPaint)
        }

        // ── 4. Series ─────────────────────────────────────────────────────────
        series.forEach { s ->
            // BUG FIX: guard single-point series
            if (s.points.isEmpty()) return@forEach

            val pts = s.points.mapIndexed { i, v ->
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
                            prev.x + (p.x - prev.x) * 0.4f, prev.y,
                            p.x - (p.x - prev.x) * 0.4f, p.y,
                            p.x, p.y
                        )
                    }
                }
            }

            // Gradient fill (was flat 0.13f alpha — now proper vertical gradient)
            if (fillArea) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, PAD_T + plotH)
                    lineTo(pts.first().x, PAD_T + plotH)
                    close()
                }
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(s.color.copy(alpha = 0.26f), s.color.copy(alpha = 0f)),
                        startY = pts.minOfOrNull { it.y } ?: PAD_T,
                        endY = PAD_T + plotH
                    )
                )
            }

            drawPath(
                path, s.color,
                style = Stroke(
                    width = 3.5f,
                    cap = StrokeCap.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 6f)) else null
                )
            )

            // Points — with selection state
            val isActiveSeries = crosshairState.activeSeriesKey == s.label
            pts.forEachIndexed { i, p ->
                val isSelected = isActiveSeries && crosshairState.activeIndex == i
                val r = if (isSelected) 9f else 4f
                if (isSelected) drawCircle(s.color.copy(alpha = 0.20f), r * 2.2f, p)
                drawCircle(Color.White, r, p)
                drawCircle(s.color, r * 0.65f, p)
            }
        }

        // ── 5. Crosshair ──────────────────────────────────────────────────────
        if (crosshairState.position != null) {
            val accentColor = series.firstOrNull { it.label == crosshairState.activeSeriesKey }?.color
                ?: Color(0xFF378ADD)
            drawCrosshair(crosshairState, PAD_L, PAD_T, plotW, plotH, accentColor)

            // Tooltip
            val idx = crosshairState.activeIndex
            val key = crosshairState.activeSeriesKey
            val pos = crosshairState.position
            if (idx != null && key != null && pos != null) {
                val v = series.firstOrNull { it.label == key }?.points?.getOrNull(idx) ?: 0f
                val label = "$key: ${v.toInt()}"
                drawDashTooltip(label, pos, PAD_L, PAD_T, plotW, plotH, accentColor)
            }
        }
    }
}

// ── Tooltip helper ─────────────────────────────────────────────────────────────

private fun DrawScope.drawDashTooltip(
    text: String,
    anchor: Offset,
    padL: Float, padT: Float,
    plotW: Float, plotH: Float,
    color: Color
) {
    val tp = Paint().apply {
        isAntiAlias = true
        textSize = 25f
        this.color = android.graphics.Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }
    val tw = tp.measureText(text)
    val pw = tw + 26f; val ph = 36f
    val px = (anchor.x - pw / 2f).coerceIn(padL, padL + plotW - pw)
    val py = if (anchor.y - ph - 12f >= padT) anchor.y - ph - 12f else anchor.y + 12f
    drawRoundRect(color.copy(alpha = 0.9f), Offset(px, py), Size(pw, ph), CornerRadius(10f))
    drawContext.canvas.nativeCanvas.drawText(text, px + pw / 2f, py + ph / 2f + 9f,
        tp.apply { textAlign = Paint.Align.CENTER })
}

// ── DashBarChart, DashPieChart, DashHBarChart, MiniTrendBar ───────────────────
// (unchanged below — only DashLineChart was refactored)

@Composable
fun DashBarChart(
    series: List<BarSeries>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "bar"
    )

    Canvas(modifier = modifier) {
        val maxV = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val padL = 40f; val padB = 28f; val padR = 8f; val padT = 8f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        for (i in 0..4) {
            val y = padT + h * (1f - i / 4f)
            drawLine(Color.Gray.copy(alpha = 0.18f), Offset(padL, y), Offset(padL + w, y), 1f)
            val v = (maxV * i / 4).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "$v", 0f, y + 5f,
                Paint().apply { color = 0xFF888888.toInt(); textSize = 22f }
            )
        }

        val n = labels.size
        val groupW = w / n
        val barW = groupW * 0.35f
        val seriesCount = series.size

        labels.forEachIndexed { gi, lbl ->
            val groupX = padL + gi * groupW + groupW / 2f
            drawContext.canvas.nativeCanvas.drawText(
                lbl, groupX - 15f, size.height,
                Paint().apply { color = 0xFF888888.toInt(); textSize = 22f }
            )

            series.forEachIndexed { si, s ->
                val v = s.values.getOrNull(gi) ?: return@forEachIndexed
                val bh = (v / maxV) * h * progress
                val x = groupX - (seriesCount * barW / 2f) + si * barW

                val path = Path().apply {
                    val r = 6f
                    val top = padT + h - bh; val bot = padT + h
                    moveTo(x, bot)
                    lineTo(x, top + r)
                    quadraticTo(x, top, x + r, top)
                    lineTo(x + barW - r - 4f, top)
                    quadraticTo(x + barW - 4f, top, x + barW - 4f, top + r)
                    lineTo(x + barW - 4f, bot)
                    close()
                }
                drawPath(path, s.color)
            }
        }
    }
}

@Composable
fun DashPieChart(
    segments: List<PieSegment>,
    cutoutFraction: Float = 0f,
    selectedIndex: Int? = null,
    onSegmentTap: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "pie"
    )
    val expandAnims = segments.indices.map { i ->
        animateFloatAsState(
            targetValue = if (selectedIndex == i) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy),
            label = "expand$i"
        ).value
    }

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Canvas(
        modifier = modifier
            .pointerInput(segments) {
                detectTapGestures { offset ->
                    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                    val radius = min(canvasSize.width, canvasSize.height) / 2f * 0.82f
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist > radius * 1.15f) return@detectTapGestures

                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    angle = (angle + 360f) % 360f

                    val total = segments.sumOf { it.value.toDouble() }.toFloat()
                    var cur = ((-90f) % 360f + 360f) % 360f
                    segments.forEachIndexed { i, seg ->
                        val sweep = (seg.value / total) * 360f * progress
                        val end = cur + sweep
                        val normalEnd = end % 360f
                        val hit = if (end <= 360f) angle in cur..end
                        else angle >= cur || angle <= normalEnd
                        if (hit) { onSegmentTap(i); return@detectTapGestures }
                        cur = (cur + sweep) % 360f
                    }
                }
            }
    ) {
        canvasSize = size
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = min(size.width, size.height) / 2f * 0.82f
        val total = segments.sumOf { it.value.toDouble() }.toFloat()

        var curAngle = -90f
        segments.forEachIndexed { i, seg ->
            val sweep = (seg.value / total) * 360f * progress
            val expand = expandAnims[i] * baseRadius * 0.1f
            val r = baseRadius + expand

            drawArc(
                color = seg.color,
                startAngle = curAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f)
            )
            curAngle += sweep
        }

        if (cutoutFraction > 0f) {
            drawCircle(Color.White, baseRadius * cutoutFraction, center)
        }
    }
}

@Composable
fun DashHBarChart(
    entries: List<HBarEntry>,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "hbar"
    )

    Canvas(modifier = modifier) {
        val maxV = entries.maxOf { it.value }.coerceAtLeast(1f)
        val padL = 130f; val padR = 50f; val padT = 8f
        val rowH = (size.height - padT) / entries.size
        val barH = rowH * 0.48f
        val w = size.width - padL - padR

        val lblPaint = Paint().apply { color = 0xFF555555.toInt(); textSize = 24f }
        val valPaint = Paint().apply {
            color = 0xFF555555.toInt(); textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }

        entries.forEachIndexed { i, e ->
            val y = padT + i * rowH + rowH / 2f
            val bw = (e.value / maxV) * w * progress
            val top = y - barH / 2f

            val path = Path().apply {
                val r = barH / 2f
                moveTo(padL, top)
                lineTo(padL + bw - r, top)
                quadraticTo(padL + bw, top, padL + bw, top + r)
                quadraticTo(padL + bw, top + barH, padL + bw - r, top + barH)
                lineTo(padL, top + barH)
                close()
            }
            drawPath(path, e.color.copy(alpha = 0.85f))
            drawLine(
                e.color.copy(alpha = 0.12f),
                Offset(padL + bw, y),
                Offset(padL + w, y),
                barH
            )

            drawContext.canvas.nativeCanvas.apply {
                drawText(e.label, 0f, y + 8f, lblPaint)
                drawText("${e.value.toInt()}K", padL + bw + 8f, y + 8f, valPaint)
            }
        }
    }
}

@Composable
fun MiniTrendBar(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val maxV = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val gap = 4f
        val barW = (size.width - gap * (values.size - 1)) / values.size.coerceAtLeast(1)
        values.forEachIndexed { i, v ->
            val bh = (v / maxV) * size.height
            val x = i * (barW + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(x, size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(3f)
            )
        }
    }
}
