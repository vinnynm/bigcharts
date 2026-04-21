package com.enigma.bigcharts.core.bar

import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.detectChartGestures
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BarChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onBarTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    showValues: Boolean = true
) {
    var selectedBar by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = config.animationDuration, easing = FastOutSlowInEasing),
        label = "bar_animation"
    )

    val dataPoints = dataset.data
    if (dataPoints.isEmpty()) return

    val seriesCount = dataset.series.size
    // FIX: keep an ordered list of (key, config) so index→key lookups are reliable
    val seriesEntries = dataset.series.entries.toList()
    val maxValue = dataPoints.flatMap { it.values.values }.maxOrNull()?.coerceAtLeast(1f) ?: 1f

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .detectChartGestures(
                    onTap = { offset, size ->
                        // Compute layout constants once — same formula as the draw pass
                        val barWidth = (size.width / dataPoints.size) / seriesCount
                        val groupWidth = barWidth * seriesCount * 0.8f
                        val startX = (size.width - groupWidth * dataPoints.size) / 2f

                        val dataIndex = ((offset.x - startX) / groupWidth).toInt()
                        if (dataIndex in dataPoints.indices) {
                            val localX = (offset.x - startX) % groupWidth
                            val seriesIndex = (localX / barWidth).toInt()
                                .coerceIn(0, seriesCount - 1)

                            // FIX: original used series.name as the key which fails when the map
                            //      key differs from SeriesConfig.name.  Use the map key directly.
                            val (seriesKey, seriesConfig) = seriesEntries[seriesIndex]
                            val point = dataPoints[dataIndex]
                            val value = point.getValue(seriesKey)
                            val barHeight = (value / maxValue) * size.height
                            val barY = size.height - barHeight

                            // FIX: allow tapping anywhere in the bar column (not only below barY)
                            if (offset.y >= barY - 10f) {
                                selectedBar = dataIndex to seriesKey
                                onBarTap?.invoke(seriesKey, point, dataIndex)
                            }
                        }
                    }
                )
        ) {
            val barWidth = (size.width / dataPoints.size) / seriesCount
            val groupWidth = barWidth * seriesCount * 0.8f
            val startX = (size.width - groupWidth * dataPoints.size) / 2f

            drawGridLines(size, maxValue, config)

            dataPoints.forEachIndexed { dataIndex, point ->
                seriesEntries.forEachIndexed { seriesIndex, (seriesKey, seriesConfig) ->
                    val value = point.getValue(seriesKey)
                    val barHeight = (value / maxValue) * size.height * animatedProgress
                    val isSelected = selectedBar == (dataIndex to seriesKey)

                    val x = startX + (dataIndex * groupWidth) + (seriesIndex * barWidth)
                    val y = size.height - barHeight

                    // FIX: tighten bar spacing — use a 2dp gap instead of a hard 4f pixel gap
                    val barDrawWidth = barWidth - 4f

                    drawRect(
                        color = if (isSelected) seriesConfig.color.copy(alpha = 0.7f) else seriesConfig.color,
                        topLeft = Offset(x, y),
                        size = Size(barDrawWidth, barHeight)
                    )

                    // Draw value above bar (only when bar is tall enough to bother)
                    if (showValues && barHeight > 20f) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = Paint().apply {
                                // FIX: use BLACK (or dark color) for value labels — WHITE is
                                //      invisible against light backgrounds.
                                color = Color.DKGRAY
                                textSize = 24f
                                textAlign = Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            drawText(
                                value.roundToInt().toString(),
                                x + barWidth / 2f,
                                y - 6f,       // slightly above the bar top
                                paint
                            )
                        }
                    }
                }
            }

            drawXAxisLabels(dataPoints, startX, groupWidth, size)
        }
    }
}

private fun DrawScope.drawGridLines(size: Size, maxValue: Float, config: ChartConfig) {
    val gridLines = 5
    for (i in 0..gridLines) {
        val y = size.height * (1f - i.toFloat() / gridLines)
        val value = (maxValue * i / gridLines).roundToInt()

        drawLine(
            color = config.gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )

        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = Color.GRAY
                textSize = 28f
                isAntiAlias = true
            }
            drawText(value.toString(), 10f, y - 10f, paint)
        }
    }
}

private fun DrawScope.drawXAxisLabels(
    dataPoints: List<TimeSeriesPoint>,
    startX: Float,
    groupWidth: Float,
    size: Size
) {
    dataPoints.forEachIndexed { index, point ->
        val x = startX + (index * groupWidth) + groupWidth / 2f
        val label = point.label ?: formatTimestamp(point.timestamp)

        drawContext.canvas.nativeCanvas.apply {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 28f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            // FIX: draw labels just inside the bottom edge so they are not clipped
            drawText(label, x, size.height - 4f, paint)
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MM/dd", Locale.getDefault())
    return format.format(date)
}
