package com.enigma.bigcharts.core.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Stable
class ZoomPanState(
    var initialZoom: Float = 1f,
    var initialPan: Offset = Offset.Zero,
    var minZoom: Float = 0.5f,
    var maxZoom: Float = 4f
) {
    var zoom by mutableStateOf(initialZoom)
        private set
    var pan by mutableStateOf(initialPan)
        private set

    fun reset() {
        zoom = initialZoom
        pan = initialPan
    }

    fun setZoom(newZoom: Float, center: Offset, contentSize: IntSize) {
        val clampedZoom = newZoom.coerceIn(minZoom, maxZoom)
        if (clampedZoom != zoom) {
            val scaleFactor = clampedZoom / zoom
            val newPan = Offset(
                x = center.x - (center.x - pan.x) * scaleFactor,
                y = center.y - (center.y - pan.y) * scaleFactor
            )
            zoom = clampedZoom
            pan = newPan
        }
    }

    fun setPan(newPan: Offset, contentSize: IntSize) {
        val maxPanX = (contentSize.width * (zoom - 1f)).coerceAtLeast(0f) / 2f
        val maxPanY = (contentSize.height * (zoom - 1f)).coerceAtLeast(0f) / 2f
        pan = Offset(
            x = newPan.x.coerceIn(-maxPanX, maxPanX),
            y = newPan.y.coerceIn(-maxPanY, maxPanY)
        )
    }

    fun transformPoint(point: Offset): Offset =
        Offset((point.x - pan.x) / zoom, (point.y - pan.y) / zoom)

    fun inverseTransformPoint(point: Offset): Offset =
        Offset(point.x * zoom + pan.x, point.y * zoom + pan.y)

    /**
     * FIX: original formula had a sign error — pan.x is negative when panned left,
     * so dividing by zoom and multiplying back gave a visibleStart that drifted in
     * the wrong direction.  The correct interpretation:
     *   The left edge of the viewport in content-space is  -pan.x / zoom.
     *   The right edge is  (-pan.x + canvasWidth) / zoom.
     */
    fun getVisibleRange(totalPoints: Int, canvasWidth: Float): IntRange {
        if (canvasWidth <= 0f || totalPoints == 0) return 0 until totalPoints
        val leftContent = -pan.x / zoom
        val rightContent = (-pan.x + canvasWidth) / zoom
        val visibleStart = ((leftContent / canvasWidth) * totalPoints).toInt().coerceAtLeast(0)
        val visibleEnd = ((rightContent / canvasWidth) * totalPoints).toInt()
            .coerceAtMost(totalPoints - 1)
        return visibleStart..visibleEnd.coerceAtLeast(visibleStart)
    }
}

/**
 * FIX: [lastTapTime] was a private member of [EnhancedChartGestureDetector] but was
 * read/written inside a [Modifier.pointerInput] lambda that lives in the extension
 * function [detectZoomPanGestures], which is not a member of the class — it won't
 * compile.  Moved lastTapTime to a local `var` inside the coroutine block where it
 * is used.
 *
 * FIX: drag delta was computed as `currentPointer - pointers.values.firstOrNull()`
 * which always subtracted the *same* pointer from itself, giving Offset.Zero.
 * Track the previous single-pointer position explicitly.
 */
@Stable
class EnhancedChartGestureDetector(
    private val zoomPanState: ZoomPanState,
    private val onTap: ((Offset) -> Unit)? = null,
    private val onDoubleTap: ((Offset) -> Unit)? = null,
    private val onLongPress: ((Offset) -> Unit)? = null
) {
    fun Modifier.detectZoomPanGestures(contentSize: IntSize): Modifier =
        this.pointerInput(Unit) {
            var lastTapTime = 0L   // FIX: moved here so it compiles

            awaitEachGesture {
                val down = awaitFirstDown()
                var isDragging = false
                var isPinching = false
                var initialDistance = 0f
                var savedZoom = zoomPanState.zoom
                var savedPan = zoomPanState.pan
                var zoomCentroid = Offset.Zero

                val pointers = mutableMapOf<PointerId, Offset>()
                pointers[down.id] = down.position

                // FIX: remember the previous single-pointer position for drag delta
                var prevSinglePointer = down.position

                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        if (change.pressed) pointers[change.id] = change.position
                        else pointers.remove(change.id)
                        change.consume()
                    }

                    when (pointers.size) {
                        1 -> {
                            if (!isPinching) {
                                val currentPointer = pointers.values.first()
                                // FIX: delta vs previous position, not vs itself
                                val dragDelta = currentPointer - prevSinglePointer

                                if (!isDragging && dragDelta.getDistance() > 10f) {
                                    isDragging = true
                                    savedPan = zoomPanState.pan
                                }

                                if (isDragging) {
                                    zoomPanState.setPan(zoomPanState.pan + dragDelta, contentSize)
                                }
                                prevSinglePointer = currentPointer
                            }
                        }

                        2 -> {
                            val (p1, p2) = pointers.values.toList()
                            val currentDistance = (p1 - p2).getDistance()

                            if (!isPinching) {
                                isPinching = true
                                initialDistance = currentDistance.coerceAtLeast(1f) // avoid /0
                                savedZoom = zoomPanState.zoom
                                zoomCentroid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                            } else {
                                val scale = currentDistance / initialDistance
                                val newZoom = (savedZoom * scale).coerceIn(zoomPanState.minZoom, zoomPanState.maxZoom)
                                if (newZoom != zoomPanState.zoom) {
                                    zoomPanState.setZoom(newZoom, zoomCentroid, contentSize)
                                }
                            }
                            isDragging = false
                        }
                    }

                    // Tap / double-tap detection on pointer-up
                    if (!isDragging && !isPinching) {
                        val up = event.changes.find { !it.pressed }
                        if (up != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 300L) {
                                onDoubleTap?.invoke(up.position)
                                lastTapTime = 0L
                            } else {
                                lastTapTime = now
                                onTap?.invoke(up.position)
                            }
                        }
                    }

                } while (pointers.isNotEmpty())
            }
        }
}

fun Modifier.zoomableChart(
    zoomPanState: ZoomPanState,
    contentSize: IntSize,
    onTap: ((Offset) -> Unit)? = null,
    onDoubleTap: ((Offset) -> Unit)? = null
): Modifier = composed {
    val detector = EnhancedChartGestureDetector(
        zoomPanState = zoomPanState,
        onTap = onTap,
        onDoubleTap = onDoubleTap
    )
    with(detector) {
        Modifier.detectZoomPanGestures(contentSize)
    }
}

@Composable
fun ZoomControls(
    zoomPanState: ZoomPanState,
    modifier: Modifier = Modifier,
    contentSize: IntSize
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val center = Offset(contentSize.width / 2f, contentSize.height / 2f)

        IconButton(
            onClick = { zoomPanState.setZoom(zoomPanState.zoom - 0.25f, center, contentSize) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
        }

        Text(
            text = "${(zoomPanState.zoom * 100).toInt()}%",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterVertically)
        )

        IconButton(
            onClick = { zoomPanState.setZoom(zoomPanState.zoom + 0.25f, center, contentSize) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
        }

        IconButton(
            onClick = { zoomPanState.reset() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
        }
    }
}
