package now.link.periphyllidae.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light theme colors
private val LightColors = lightColorScheme(
    primary = Color(0xFF1DB954), // Spotify Green
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4CAF50),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF191414), // Spotify Black
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF5F5F5),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

// Dark theme colors
private val DarkColors = darkColorScheme(
    primary = Color(0xFF1DB954), // Spotify Green
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF4CAF50),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF191414), // Spotify Black
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    background = Color(0xFF121212), // Spotify Dark Background
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB3B3B3),
    error = Color(0xFFE57373),
    onError = Color.Black
)

@Composable
fun PeriphyllidaeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
