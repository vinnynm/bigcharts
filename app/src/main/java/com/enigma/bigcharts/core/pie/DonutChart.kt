package com.enigma.bigcharts.core.pie

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.CategoryDataPoint
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.ModernMetricTile
import kotlin.math.*

@Composable
fun ModernPieChart(
    data: List<CategoryDataPoint>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    cutoutFraction: Float = 0.5f, // 0 = pie, 0.5 = donut
    onSliceTap: ((CategoryDataPoint, Int) -> Unit)? = null
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "pie_progress"
    )

    val expandAnims = data.indices.map { i ->
        animateFloatAsState(
            targetValue = if (selectedIndex == i) 1f else 0f,
            animationSpec = spring(Spring.DampingRatioMediumBouncy),
            label = "expand$i"
        ).value
    }

    if (data.isEmpty()) return
    val total = data.sumOf { it.value.toDouble() }.toFloat()

    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Row(modifier= Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            modifier = modifier
                .size(300.dp)
                .pointerInput(data) {
                    detectTapGestures { offset ->
                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                        val radius = min(canvasSize.width, canvasSize.height) / 2f * 0.8f
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)

                        if (dist > radius * 1.2f || dist < radius * cutoutFraction * 0.8f) {
                            selectedIndex = null
                            return@detectTapGestures
                        }

                        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 360f) % 360f

                        var cur = 270f // -90 degrees
                        data.forEachIndexed { i, seg ->
                            val sweep = (seg.value / total) * 360f * progress
                            val end = cur + sweep

                            val normalizedAngle = angle
                            val startNormalized = cur % 360f
                            val endNormalized = end % 360f

                            val hit = if (endNormalized > startNormalized) {
                                normalizedAngle in startNormalized..endNormalized
                            } else {
                                normalizedAngle >= startNormalized || normalizedAngle <= endNormalized
                            }

                            if (hit) {
                                selectedIndex = if (selectedIndex == i) null else i
                                onSliceTap?.invoke(seg, i)
                                return@detectTapGestures
                            }
                            cur = (cur + sweep) % 360f
                        }
                        selectedIndex = null
                    }
                }
        ) {
            canvasSize = size
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = min(size.width, size.height) / 2f * 0.8f

            var curAngle = -90f
            data.forEachIndexed { i, seg ->
                val sweep = (seg.value / total) * 360f * progress
                val expand = expandAnims[i] * baseRadius * 0.08f
                val r = baseRadius + expand

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
                            rect = Rect(center.x - innerR, center.y - innerR, center.x + innerR, center.y + innerR),
                            startAngleDegrees = curAngle + sweep,
                            sweepAngleDegrees = -sweep,
                            forceMoveTo = false
                        )
                    } else {
                        lineTo(center.x, center.y)
                    }
                    close()
                }

                drawPath(path, seg.color)

                // Subtle border between slices
                drawPath(path, Color.White.copy(alpha = 0.2f), style = Stroke(1.dp.toPx()))

                curAngle += sweep
            }

            // Center Text (if donut)
            if (cutoutFraction > 0.4f && selectedIndex != null) {
                val selected = data[selectedIndex!!]
                drawContext.canvas.nativeCanvas.drawText(
                    "${(selected.value / total * 100).toInt()}%",
                    center.x,
                    center.y + 10f,
                    Paint().apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 48f
                        textAlign = Paint.Align.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                )
            }
        }
        val sum = data.sumOf { it.value.toDouble() }

        LazyHorizontalGrid(
            rows = GridCells.Adaptive(100.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            itemsIndexed(data){index, it ->
                ModernMetricTile(
                    label = it.label + " ${it.value/sum * 100} %",
                    value = it.value.toString(),
                    isPositive = true,
                    color =it.color,
                    modifier = Modifier.padding(2.dp).clickable {
                        selectedIndex = index
                    }.height(
                        100.dp
                    )
                )
            }
        }
    }



}

