package pe.lee.lumenreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LumenColors = darkColorScheme(
    primary = Color(0xFF9B72FF),
    onPrimary = Color(0xFF170B32),
    secondary = Color(0xFF56D7FF),
    background = Color(0xFF07111F),
    surface = Color(0xFF0D1A2B),
    surfaceVariant = Color(0xFF14243A),
    onBackground = Color(0xFFF5F3FF),
    onSurface = Color(0xFFF5F3FF),
    onSurfaceVariant = Color(0xFFB7C2D8),
    outline = Color(0xFF304666)
)

@Composable
fun LumenReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumenColors,
        content = content
    )
}
