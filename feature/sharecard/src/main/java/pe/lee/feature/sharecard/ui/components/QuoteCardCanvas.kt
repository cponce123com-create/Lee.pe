package pe.lee.feature.sharecard.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import pe.lee.feature.sharecard.domain.model.QuoteCard

/** Renders at export resolution; only its presentation is scaled to fit the screen. */
@Composable
internal fun QuoteCardCanvas(
    card: QuoteCard,
    captureModifier: Modifier,
    onFitsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            // Artwork has a stable physical pixel size, independent of device density/font scale.
            // The surrounding editor continues to respect the user's accessibility settings.
            CompositionLocalProvider(LocalDensity provides Density(3f, 1f)) {
                QuoteCardPreview(card, captureModifier, onFitsChanged)
            }
        }
    ) { measurables, constraints ->
        val width = card.format.widthPx
        val height = card.format.heightPx
        val placeable = measurables.single().measure(Constraints.fixed(width, height))
        val previewWidth = constraints.maxWidth
        val previewHeight = (previewWidth.toLong() * height / width).toInt()
        layout(previewWidth, previewHeight) {
            placeable.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = previewWidth.toFloat() / width
                scaleY = scaleX
            }
        }
    }
}
