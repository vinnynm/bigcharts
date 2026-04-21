package com.enigma.bigcharts.core.line

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.LineStyle
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import kotlin.text.get

@Composable
fun ModernLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    fillArea: Boolean = true,
    showPoints: Boolean = true,
    curveSmoothing: Float = 0.4f
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "line_progress"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val allValues = dataPoints.flatMap { it.values.values }
    val maxV = allValues.maxOrNull() ?: 1f
    val minV = allValues.minOrNull() ?: 0f
    val range = (maxV - minV).coerceAtLeast(1f)

    Canvas(modifier = modifier.fillMaxWidth().height(300.dp)) {
        val padL = 50f
        val padB = 40f
        val padR = 20f
        val padT = 20f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        // Grid Lines (Dashboard Style)
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = padT + h * (1f - i / gridLines.toFloat())
            drawLine(
                color = config.gridColor.copy(alpha = 0.15f),
                start = Offset(padL, y),
                end = Offset(padL + w, y),
                strokeWidth = 1.dp.toPx()
            )
            
            // Y-Axis Labels
            val labelValue = (minV + (range * i / gridLines)).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                labelValue.toString(),
                10f,
                y + 10f,
                Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    isAntiAlias = true
                }
            )
        }

        dataset.series.forEach { (seriesKey, seriesConfig) ->
            val pts = dataPoints.mapIndexed { i, point ->
                val v = point.getValue(seriesKey)
                val animV = minV + (v - minV) * progress
                Offset(
                    padL + (i.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * w,
                    padT + h * (1f - (animV - minV) / range)
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

            if (fillArea) {
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, padT + h)
                    lineTo(pts.first().x, padT + h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            seriesConfig.color.copy(alpha = 0.3f),
                            seriesConfig.color.copy(alpha = 0.0f)
                        ),
                        startY = pts.minOf { it.y },
                        endY = padT + h
                    )
                )
            }

            drawPath(
                path = path,
                color = seriesConfig.color,
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = when (val style = seriesConfig.lineStyle) {
                        is LineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(style.dashLength, style.gapLength))
                        else -> null
                    },
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            if (showPoints) {
                pts.forEach { p ->
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = p
                    )
                    drawCircle(
                        color = seriesConfig.color,
                        radius = 2.dp.toPx(),
                        center = p
                    )
                }
            }
        }

        // X-Axis Labels (Every other or based on space)
        dataPoints.forEachIndexed { i, point ->
            if (dataPoints.size < 10 || i % (dataPoints.size / 5).coerceAtLeast(1) == 0) {
                val label = point.label ?: ""
                val x = padL + (i.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * w
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x - 20f,
                    size.height - 5f,
                    Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 22f
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}
