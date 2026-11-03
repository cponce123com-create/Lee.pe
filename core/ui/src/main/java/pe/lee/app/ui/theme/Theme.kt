package pe.lee.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LeePeDarkColors = darkColorScheme(
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

/**
 * Light palette with the same identity: deep teal text on paper surfaces and the
 * brand purple/cyan as accents. The app used to force the dark scheme even when
 * the system was in light mode.
 */
private val LeePeLightColors = lightColorScheme(
    primary = Color(0xFF5B3FBF),
    onPrimary = Color.White,
    secondary = Color(0xFF00708F),
    background = Color(0xFFF7F5FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8E4F2),
    onBackground = Color(0xFF1B1428),
    onSurface = Color(0xFF1B1428),
    onSurfaceVariant = Color(0xFF4E4860),
    outline = Color(0xFF9A93AD)
)

@Composable
fun LeePeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LeePeDarkColors else LeePeLightColors,
        content = content
    )
}
