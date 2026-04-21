package com.enigma.bigcharts.core.bar

// core/complex/ComplexBarChart.kt

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint


@Composable
fun ComplexBarChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    showValues: Boolean = true,
    onBarTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    // Bar charts typically don't zoom/pan, but we can wrap with a scalable container.
    // For now we simply reuse BarChart and add a reset button or pinch‑to‑zoom container.
    // Alternatively, we can embed BarChart inside a zoomable Canvas – omitted for brevity.

    BarChart(
        dataset = dataset,
        modifier = modifier,
        config = config,
        onBarTap = onBarTap,
        showValues = showValues
    )
    // For true zoom/pan, you'd need a custom implementation similar to EnhancedLineChart.
}