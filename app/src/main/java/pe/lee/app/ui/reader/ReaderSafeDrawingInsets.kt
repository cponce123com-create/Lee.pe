package pe.lee.app.ui.reader

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Applies status, navigation, and display-cutout safe areas to reader content. */
@Composable
internal fun Modifier.readerSafeDrawingInsets(): Modifier = windowInsetsPadding(WindowInsets.safeDrawing)
