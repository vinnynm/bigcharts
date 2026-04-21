package com.enigma.bigcharts.core.line
// core/complex/ComplexLineChart.kt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.enigma.bigcharts.core.utils.ChartConfig
import com.enigma.bigcharts.core.utils.MultiSeriesDataset
import com.enigma.bigcharts.core.utils.TimeSeriesPoint
import com.enigma.bigcharts.core.utils.ZoomControls
import com.enigma.bigcharts.core.utils.ZoomPanState


@Composable
fun ComplexLineChart(
    dataset: MultiSeriesDataset,
    modifier: Modifier = Modifier,
    config: ChartConfig = ChartConfig(),
    fillArea: Boolean = true,
    curveSmoothing: Float = 0.4f,
    showPoints: Boolean = true,
    onPointTap: ((String, TimeSeriesPoint, Int) -> Unit)? = null
) {
    val zoomPanState = remember { ZoomPanState() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(config.animationDuration),
        label = "complex_line"
    )

    Box(modifier = modifier.fillMaxWidth().onSizeChanged { canvasSize = it }) {
        EnhancedLineChart(
            dataset = dataset,
            config = config,
            zoomPanState = zoomPanState,
            showZoomControls = false,
            animatedProgress = progress,
            onPointTap = onPointTap,
        )
        ZoomControls(
            zoomPanState = zoomPanState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            contentSize = canvasSize
        )
    }
}
