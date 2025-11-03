package now.link.periphyllidae.ui.screens.find.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A minimal vertical A-Z quick locate sidebar that floats over the content
 * Shows only available characters and scrolls to corresponding items when clicked
 */
@Composable
fun AlphabetSidebar(
    availableChars: List<Char>,
    charIndexMap: Map<Char, Int>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .width(40.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            availableChars.forEach { char ->
                Text(
                    text = char.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            val index = charIndexMap[char]
                            if (index != null) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }
                        .padding(5.dp)
                )
            }
        }
    }
}