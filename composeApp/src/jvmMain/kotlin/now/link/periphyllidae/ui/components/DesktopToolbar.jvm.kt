package now.link.periphyllidae.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState

@Composable
fun DesktopToolbarContent(
    windowScope: WindowScope,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    with(windowScope) {
        DesktopToolbarContentImpl(windowState, onCloseRequest)
    }
}

@Composable
private fun WindowScope.DesktopToolbarContentImpl(
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        WindowDraggableArea {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side - Window controls (Mac style)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WindowControlButton(
                            color = Color(0xFFFF5F57),
                            hoverColor = Color(0xFFFF3B30),
                            onClick = onCloseRequest
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                modifier = Modifier.size(10.dp),
                                tint = Color.Black.copy(alpha = 0.6f)
                            )
                        }

                        WindowControlButton(
                            color = Color(0xFFFFBD2E),
                            hoverColor = Color(0xFFFF9500),
                            onClick = {
                                windowState.isMinimized = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Minimize,
                                contentDescription = "Minimize",
                                modifier = Modifier.size(10.dp),
                                tint = Color.Black.copy(alpha = 0.6f)
                            )
                        }

                        WindowControlButton(
                            color = Color(0xFF28CA42),
                            hoverColor = Color(0xFF30D158),
                            onClick = {
                                // Toggle Maximized/floating mode
                                windowState.placement =
                                    if (windowState.placement == WindowPlacement.Maximized) {
                                        WindowPlacement.Floating
                                    } else {
                                        WindowPlacement.Maximized
                                    }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowControlButton(
    color: Color,
    hoverColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = if (isHovered) hoverColor else color,
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .hoverable(interactionSource),
        contentAlignment = Alignment.Center
    ) {
        if (isHovered) {
            content()
        }
    }
}
