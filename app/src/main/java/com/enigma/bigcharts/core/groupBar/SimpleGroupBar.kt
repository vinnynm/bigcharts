package com.enigma.bigcharts.core.groupBar

// core/groupbar/SimpleGroupBarChart.kt


import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.enigma.bigcharts.core.bar.BarChart
import com.enigma.bigcharts.core.utils.*

import kotlin.math.abs

/**
 * Simple group bar chart where data is a map of label to list of values.
 * Each list represents values for different series (same order for all labels).
 * Series are named "Series 1", "Series 2", etc. Colors are auto-generated.
 *
 * @param data Map of label -> list of values (e.g., "Jan" to listOf(10f, 20f))
 * @param modifier Modifier for the chart
 * @param config Chart configuration
 * @param onBarTap Callback when a bar is tapped (seriesKey, point, index)
 * @param showValues Whether to show numeric values above bars
 */
@Composable
fun SimpleGroupBarChart(
    data: Map<String, List<Float>>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onBarTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    showValues: Boolean = true
) {
    if (data.isEmpty()) return

    // Determine number of series from the first entry
    val seriesCount = data.values.firstOrNull()?.size ?: return
    val seriesNames = List(seriesCount) { index -> "Series ${index + 1}" }

    // Auto-generate distinct colors
    val colors = generateDistinctColors(seriesCount)

    val seriesMap = seriesNames.mapIndexed { index, name ->
        name to SeriesConfig(
            name = name,
            color = colors[index],
            lineStyle = LineStyle.Solid,
            pointStyle = PointStyle.Circle(4f)
        )
    }.toMap()

    // Convert to TimeSeriesPoint list
    val timePoints = data.map { (label, values) ->
        val valueMap = seriesNames.mapIndexed { idx, name ->
            name to values.getOrElse(idx) { 0f }
        }.toMap()
        TimeSeriesPoint(
            timestamp = label.hashCode().toLong(), // dummy timestamp
            values = valueMap,
            label = label
        )
    }

    val dataset = MultiSeriesDataset(
        series = seriesMap,
        data = timePoints
    )

    BarChart(
        dataset = dataset,
        modifier = modifier,
        config = config,
        onBarTap = onBarTap,
        showValues = showValues
    )
}

/**
 * Generate a set of visually distinct colors.
 * Uses HSL color space with evenly spaced hues.
 */
private fun generateDistinctColors(count: Int): List<Color> {
    val saturation = 0.7f
    val lightness = 0.6f
    return List(count) { index ->
        val hue = (index * 360f / count).coerceIn(0f, 360f)
        Color.hsv(hue, saturation, lightness)
    }
}