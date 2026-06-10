package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier

internal actual fun Modifier.mouseDragScrollable(
    listState: LazyListState,
    orientation: Orientation
): Modifier = this
