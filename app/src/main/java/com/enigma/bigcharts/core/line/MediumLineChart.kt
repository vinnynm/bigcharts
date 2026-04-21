package com.enigma.bigcharts.core.line
// core/medium/MediumLineChart.kt
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.ChartLegend
import com.enigma.bigcharts.core.utils.chartEntranceTween

@Composable
fun MediumLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showLegend: Boolean = true,
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = chartEntranceTween(config.animationDuration),
        label = "medium_line"
    )

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

        EnhancedLineChart(
            dataset = filteredDataset,
            config = config,
            showZoomControls = false,
            animatedProgress = progress,
            onPointTap = onPointTap
        )
    }
}
