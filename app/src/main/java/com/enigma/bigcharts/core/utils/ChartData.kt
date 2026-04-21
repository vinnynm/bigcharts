package com.enigma.bigcharts.core.utils

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

// Base data point interface
sealed interface DataPoint {
    val value: Float
    val label: String
    val color: Color
}

// For time-series data (line/bar charts)
@Stable
data class TimeSeriesPoint(
    val timestamp: Long, // Unix timestamp in milliseconds
    val values: Map<String, Float>, // Series name -> value
    val label: String? = null
) {
    fun getValue(seriesKey: String): Float = values[seriesKey] ?: 0f
}

// For category-based data (bar/pie charts)
@Stable
data class CategoryDataPoint(
    override val value: Float,
    override val label: String,
    override val color: Color,
    val metadata: Map<String, Any>? = null
) : DataPoint

// Chart configuration
@Stable
data class ChartConfig(
    val animationDuration: Int = 800,
    val isTouchEnabled: Boolean = true,
    val showTooltips: Boolean = true,
    val tooltipBackgroundColor: Color = Color.Black.copy(alpha = 0.8f),
    val tooltipTextColor: Color = Color.White,
    val gridColor: Color = Color.Gray.copy(alpha = 0.3f),
    val backgroundColor: Color = Color.White  // FIX: was Color.Transparent — donut hole needs an opaque background
)

// Multi-series dataset
@Stable
data class MultiSeriesDataset(
    val series: Map<String, SeriesConfig>,
    val data: List<TimeSeriesPoint>
)

@Stable
data class SeriesConfig(
    val name: String,
    val color: Color,
    val lineStyle: LineStyle = LineStyle.Solid,
    val pointStyle: PointStyle = PointStyle.Circle(radius = 4f)
)

sealed class LineStyle {
    object Solid : LineStyle()
    data class Dashed(val dashLength: Float, val gapLength: Float) : LineStyle()
}

sealed class PointStyle {
    object None : PointStyle()
    data class Circle(val radius: Float) : PointStyle()
    data class Square(val size: Float) : PointStyle()
}

@Stable
data class ScatterDataPoint(
    val x: Float,
    val y: Float,
    val label: String? = null,
    val seriesKey: String = "default"
)

// Scatter dataset
@Stable
data class ScatterDataset(
    val series: Map<String, SeriesConfig>,
    val points: List<ScatterDataPoint>
)