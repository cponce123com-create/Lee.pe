package pe.lee.app.ui.library

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val CabinetColors = darkColorScheme(
    primary = Color(0xFFFFDEA0),
    onPrimary = Color(0xFF322113),
    primaryContainer = Color(0xFF007C69),
    onPrimaryContainer = Color(0xFFFFF1D3),
    secondary = Color(0xFFEFB958),
    secondaryContainer = Color(0xFF8B5D1D),
    onSecondaryContainer = Color(0xFFFFE7B3),
    background = Color(0xFF2C1E18),
    onBackground = Color(0xFFFFF0D6),
    surface = Color(0xFF3C2A20),
    onSurface = Color(0xFFFFF0D6),
    surfaceVariant = Color(0xFF4D3626),
    onSurfaceVariant = Color(0xFFDFCBAD),
    outline = Color(0xFF967452),
)

/** Library-only cabinet palette; reader appearance stays independently configurable. */
@Composable
internal fun LibraryShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CabinetColors, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides CabinetColors.onSurface, content = content)
    }
}
