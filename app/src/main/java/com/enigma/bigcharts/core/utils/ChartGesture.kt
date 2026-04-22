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

/**
 * Unified gesture detector for chart canvases.
 *
 * Changes vs original:
 * - Key on callbacks so the block is restarted when lambdas change (avoids stale captures)
 * - [canvasSize] read inside the gesture loop from [size] (always current)
 * - NEW: [onDrag] callback for scrub/crosshair interaction — receives (start, current, canvasSize)
 *   It fires on every pointer-move event while the pointer is down, before a tap is confirmed.
 *   A gesture is classified as a drag when the finger travels > [dragThreshold] dp; until then
 *   the tap path is still live.
 */
fun Modifier.detectChartGestures(
    onTap: ((Offset, Size) -> Unit)? = null,
    onLongPress: ((Offset, Size) -> Unit)? = null,
    onDrag: ((start: Offset, current: Offset, Size) -> Unit)? = null,
    dragThreshold: Float = 8f
): Modifier = this.pointerInput(onTap, onLongPress, onDrag) {
    coroutineScope {
        awaitEachGesture {
            val canvasSize = size.toSize()
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val downPosition = down.position
            var isDragging = false

            val longPressJob = launch {
                delay(500)
                onLongPress?.invoke(downPosition, canvasSize)
            }

            // BUG FIX: if onDrag is supplied, poll pointer moves manually so we can
            // stream positions rather than waiting for the pointer to lift.
            if (onDrag != null) {
                // Use manual event loop so we can observe every move
                var currentEvent = currentEvent  // first event (the down)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break

                    if (!change.pressed) {
                        // Pointer lifted
                        longPressJob.cancel()
                        if (!isDragging) {
                            val distance = (change.position - downPosition).getDistance()
                            if (distance < 10f) {
                                onTap?.invoke(change.position, canvasSize)
                            }
                        }
                        break
                    }

                    val distance = (change.position - downPosition).getDistance()
                    if (!isDragging && distance > dragThreshold) {
                        isDragging = true
                        longPressJob.cancel()
                    }

                    if (isDragging || onDrag != null) {
                        // Always stream drag updates when onDrag is wired up
                        onDrag.invoke(downPosition, change.position, canvasSize)
                        if (isDragging) change.consume()
                    }
                }
            } else {
                // No drag handler — simple tap / long-press path (original behaviour)
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
}

/**
 * Helper for finding nearest data point by X position.
 */
fun findNearestDataPoint(
    touchX: Float,
    xPositions: List<Float>,
    dataPoints: List<TimeSeriesPoint>,
    tolerance: Float = 30f
): Pair<Int, TimeSeriesPoint>? {
    if (xPositions.isEmpty() || dataPoints.isEmpty()) return null

    val nearestIndex = xPositions.indices
        .minByOrNull { Math.abs(xPositions[it] - touchX) }
        ?: return null

    val distance = Math.abs(xPositions[nearestIndex] - touchX)
    return if (distance <= tolerance) nearestIndex to dataPoints[nearestIndex] else null
}
