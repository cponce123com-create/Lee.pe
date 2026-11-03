package pe.lee.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing scale for Lee.pe Compose screens. */
object LeePeSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
}

private val LeePeColors = darkColorScheme(
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
fun LeePeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LeePeColors,
        content = content
    )
}
