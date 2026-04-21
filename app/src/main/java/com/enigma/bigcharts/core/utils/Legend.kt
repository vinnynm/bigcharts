package com.enigma.bigcharts.core.utils

// common/Legend.kt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChartLegend(
    series: Map<String, SeriesConfig>,
    modifier: Modifier = Modifier,
    onSeriesToggle: ((String) -> Unit)? = null,
    enabledSeries: Set<String> = series.keys,
    orientation: LegendOrientation = LegendOrientation.Horizontal
) {
    val layoutModifier = if (orientation == LegendOrientation.Horizontal) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(120.dp)
    }

    val items = series.entries.toList()

    if (orientation == LegendOrientation.Horizontal) {
        LazyRow(
            modifier = modifier.then(layoutModifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(items) { (key, config) ->
                LegendItem(
                    name = config.name,
                    color = config.color,
                    isEnabled = key in enabledSeries,
                    onToggle = if (onSeriesToggle != null) {
                        { onSeriesToggle(key) }
                    } else null
                )
            }
        }
    } else {
        Column(
            modifier = modifier.then(layoutModifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { (key, config) ->
                LegendItem(
                    name = config.name,
                    color = config.color,
                    isEnabled = key in enabledSeries,
                    onToggle = if (onSeriesToggle != null) {
                        { onSeriesToggle(key) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    name: String,
    color: Color,
    isEnabled: Boolean,
    onToggle: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .then(if (onToggle != null) Modifier.clickable { onToggle() } else Modifier)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .then(if (!isEnabled) Modifier.graphicsLayer(alpha = 0.3f) else Modifier)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = color)
            }
        }

        Text(
            text = name,
            fontSize = 12.sp,
            color = if (!isEnabled) Color.Gray else Color.Black
        )
    }
}

enum class LegendOrientation {
    Horizontal, Vertical
}