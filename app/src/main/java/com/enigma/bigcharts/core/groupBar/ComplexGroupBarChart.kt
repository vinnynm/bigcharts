package com.enigma.bigcharts.core.groupBar

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.enigma.bigcharts.core.bar.BarChart
import com.enigma.bigcharts.core.utils.*


/**
 * Complex group bar chart where data is a map of label to inner map of series->value.
 * Missing series in any entry are treated as 0.
 *
 * @param data Map of label -> Map<seriesName, value> (e.g., "Jan" to mapOf("profit" to 10f, "sales" to 20f))
 * @param modifier Modifier for the chart
 * @param config Chart configuration
 * @param onBarTap Callback when a bar is tapped (seriesKey, point, index)
 * @param showValues Whether to show numeric values above bars
 * @param customColors Optional map of seriesName -> Color to override auto-assigned colors
 */
@Composable
fun ComplexGroupBarChart(
    data: Map<String, Map<String, Float>>,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    onBarTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null,
    showValues: Boolean = true,
    customColors: Map<String, Color> = emptyMap()
) {
    if (data.isEmpty()) return

    // Collect all unique series names across all entries
    val allSeries = data.values.flatMap { it.keys }.toSet()
    val seriesList = allSeries.toList()

    // Auto-generate colors for any series without custom color
    val autoColors = generateDistinctColors(seriesList.size)
    val seriesMap = seriesList.mapIndexed { index, seriesName ->
        val color = customColors[seriesName] ?: autoColors[index]
        seriesName to SeriesConfig(
            name = seriesName,
            color = color,
            lineStyle = LineStyle.Solid,
            pointStyle = PointStyle.Circle(4f)
        )
    }.toMap()

    // Convert to TimeSeriesPoint list, filling missing values with 0f
    val timePoints = data.map { (label, innerMap) ->
        val valueMap = seriesList.associateWith { seriesName ->
            innerMap[seriesName] ?: 0f
        }
        TimeSeriesPoint(
            timestamp = label.hashCode().toLong(),
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