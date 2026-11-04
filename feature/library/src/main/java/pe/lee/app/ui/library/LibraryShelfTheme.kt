package pe.lee.app.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ShelfPaperColors = lightColorScheme(
    primary = Color(0xFF075B50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7ECE3),
    onPrimaryContainer = Color(0xFF063F38),
    secondary = Color(0xFF9D6B18),
    secondaryContainer = Color(0xFFFFE8AC),
    onSecondaryContainer = Color(0xFF503608),
    background = Color(0xFFFFF8EF),
    onBackground = Color(0xFF263D36),
    surface = Color(0xFFFFFBF5),
    onSurface = Color(0xFF263D36),
    surfaceVariant = Color(0xFFF0E5D7),
    onSurfaceVariant = Color(0xFF655E54),
    outline = Color(0xFFB5A897),
)

/** The library uses the approved paper/teal identity; reader appearance remains independent. */
@Composable
internal fun LibraryShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ShelfPaperColors, typography = MaterialTheme.typography, content = content)
}
