package com.enigma.bigcharts.core.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * FIX: [pointerInput] keyed on [Unit] means the block is never restarted if the
 * lambda captures changing values (e.g. onTap changes because a parent recomposes).
 * Key on the callbacks themselves so the gesture detector is reset when they change.
 *
 * FIX: [canvasSize] is captured at the moment [pointerInput] first runs; if the
 * composable resizes afterwards the size will be stale.  Read it inside the gesture
 * loop from [size] which reflects the current layout size.
 */
fun Modifier.detectChartGestures(
    onTap: ((Offset, Size) -> Unit)? = null,
    onLongPress: ((Offset, Size) -> Unit)? = null,
    onDrag: ((start: Offset, current: Offset, Size) -> Unit)? = null
): Modifier = this.pointerInput(onTap, onLongPress, onDrag) {  // FIX: key on callbacks
    coroutineScope {
        awaitEachGesture {
            val canvasSize = size.toSize()   // FIX: read inside gesture so it's always current
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val downPosition = down.position

            val longPressJob = launch {
                delay(500)
                onLongPress?.invoke(downPosition, canvasSize)
            }

            val up = waitForUpOrCancellation()
            longPressJob.cancel()

            if (up != null) {
                val distance = (up.position - downPosition).getDistance()
                if (distance < 10f) {
                    onTap?.invoke(downPosition, canvasSize)
                }
            }
        }
    }
}

/**
 * Helper for finding nearest data point by X position.
 * FIX: renamed [tolerance] usage was never applied in original — added it to the guard.
 */
fun findNearestDataPoint(
    touchX: Float,
    xPositions: List<Float>,
    dataPoints: List<TimeSeriesPoint>,
    tolerance: Float = 30f
): Pair<Int, TimeSeriesPoint>? {
    if (xPositions.isEmpty() || dataPoints.isEmpty()) return null

    val nearestIndex = xPositions.indices
        .minByOrNull { abs(xPositions[it] - touchX) }
        ?: return null

    val distance = abs(xPositions[nearestIndex] - touchX)
    return if (distance <= tolerance) nearestIndex to dataPoints[nearestIndex] else null
}
