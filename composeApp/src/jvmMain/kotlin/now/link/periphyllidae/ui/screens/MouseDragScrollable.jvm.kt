package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

internal actual fun Modifier.mouseDragScrollable(
    listState: LazyListState,
    orientation: Orientation
): Modifier =
    pointerInput(listState, orientation) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type != PointerType.Mouse) return@awaitEachGesture

            val pointerId = down.id
            var totalX = 0f
            var totalY = 0f
            var drag = down

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                if (change.changedToUp() || change.type != PointerType.Mouse || change.isConsumed) {
                    return@awaitEachGesture
                }

                if (change.positionChanged()) {
                    val positionChange = change.positionChange()
                    totalX += positionChange.x
                    totalY += positionChange.y

                    val primaryDelta = if (orientation == Orientation.Horizontal) totalX else totalY
                    val secondaryDelta = if (orientation == Orientation.Horizontal) totalY else totalX
                    if (abs(primaryDelta) > viewConfiguration.touchSlop) {
                        if (abs(primaryDelta) <= abs(secondaryDelta)) return@awaitEachGesture

                        val overSlop = primaryDelta - viewConfiguration.touchSlop * if (primaryDelta > 0) 1 else -1
                        change.consume()
                        drag = change
                        listState.dispatchRawDelta(-overSlop)
                        break
                    }
                }
            }

            if (drag.type == PointerType.Mouse) {
                dragMouseAxis(drag.id, orientation, listState)
            }
        }
    }

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragMouseAxis(
    pointerId: androidx.compose.ui.input.pointer.PointerId,
    orientation: Orientation,
    listState: LazyListState
) {
    val onDrag: (androidx.compose.ui.input.pointer.PointerInputChange) -> Unit = { change ->
        if (change.type == PointerType.Mouse) {
            val positionChange = change.positionChange()
            val delta = if (orientation == Orientation.Horizontal) positionChange.x else positionChange.y
            listState.dispatchRawDelta(-delta)
            change.consume()
        }
    }

    if (orientation == Orientation.Horizontal) {
        horizontalDrag(pointerId, onDrag)
    } else {
        verticalDrag(pointerId, onDrag)
    }
}
