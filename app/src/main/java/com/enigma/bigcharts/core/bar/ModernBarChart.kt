package com.enigma.bigcharts.core.bar

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import kotlin.math.roundToInt

@Composable
fun ModernBarChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showValues: Boolean = true,
    barCornerRadius: Float = 8f
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration, easing = FastOutSlowInEasing),
        label = "bar_progress"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val seriesEntries = dataset.series.entries.toList()
    val seriesCount = seriesEntries.size
    val allValues = dataPoints.flatMap { it.values.values }
    val maxV = allValues.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Canvas(modifier = modifier.fillMaxWidth().height(300.dp)) {
        val padL = 60f
        val padB = 40f
        val padR = 20f
        val padT = 30f
        val w = size.width - padL - padR
        val h = size.height - padT - padB

        // Grid Lines (Dashboard Style)
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = padT + h * (1f - i / gridLines.toFloat())
            drawLine(
                color = config.gridColor.copy(alpha = 0.15f),
                start = Offset(padL, y),
                end = Offset(padL + w, y),
                strokeWidth = 1.dp.toPx()
            )
            
            // Y-Axis Labels
            val labelValue = (maxV * i / gridLines).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                labelValue.toString(),
                10f,
                y + 10f,
                Paint().apply {
                    color = Color.GRAY
                    textSize = 24f
                    isAntiAlias = true
                }
            )
        }

        val groupW = w / dataPoints.size
        val barW = (groupW * 0.7f) / seriesCount
        val groupGap = groupW * 0.15f

        dataPoints.forEachIndexed { gi, point ->
            val groupStartX = padL + gi * groupW + groupGap

            seriesEntries.forEachIndexed { si, (seriesKey, seriesConfig) ->
                val v = point.getValue(seriesKey)
                val bh = (v / maxV) * h * progress
                val x = groupStartX + si * barW
                val y = padT + h - bh

                val path = Path().apply {
                    val r = barCornerRadius
                    moveTo(x, padT + h)
                    lineTo(x, y + r)
                    quadraticTo(x, y, x + r, y)
                    lineTo(x + barW - r, y)
                    quadraticTo(x + barW, y, x + barW, y + r)
                    lineTo(x + barW, padT + h)
                    close()
                }
                
                // Shadow/Glow effect like dashboard
                drawPath(
                    path = path,
                    color = seriesConfig.color.copy(alpha = 0.1f),
                    // Slightly offset shadow could go here but keep it clean
                )

                drawPath(path, seriesConfig.color)

                if (showValues && bh > 20f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        v.roundToInt().toString(),
                        x + barW / 2f,
                        y - 8f,
                        Paint().apply {
                            color = Color.GRAY
                            textSize = 20f
                            textAlign = Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }

            // X-Axis Labels
            val label = point.label ?: ""
            drawContext.canvas.nativeCanvas.drawText(
                label,
                groupStartX + (seriesCount * barW) / 2f,
                size.height - 5f,
                Paint().apply {
                    color = Color.GRAY
                    textSize = 22f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }
    }
}
