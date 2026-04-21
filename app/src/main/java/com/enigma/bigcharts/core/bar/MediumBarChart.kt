package com.enigma.bigcharts.core.bar
// core/medium/MediumBarChart.kt
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.ChartLegend


@Composable
fun MediumBarChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showLegend: Boolean = true,
    showValues: Boolean = true,
    onBarTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    var enabledSeries by remember { mutableStateOf(dataset.series.keys) }
    val filteredDataset = dataset.copy(
        series = dataset.series.filterKeys { it in enabledSeries },
        data = dataset.data.map { point ->
            point.copy(values = point.values.filterKeys { it in enabledSeries })
        }
    )

    Column(modifier = modifier.fillMaxWidth()) {
        if (showLegend) {
            ChartLegend(
                series = dataset.series,
                enabledSeries = enabledSeries,
                onSeriesToggle = { key ->
                    enabledSeries = if (key in enabledSeries) enabledSeries - key else enabledSeries + key
                },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        BarChart(
            dataset = filteredDataset,
            config = config,
            onBarTap = onBarTap,
            showValues = showValues
        )
    }
}
