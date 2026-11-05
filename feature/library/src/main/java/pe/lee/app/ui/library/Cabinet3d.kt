package pe.lee.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import pe.lee.feature.library.R

/** Coordinates measured against the user's original 1024 × 1536 cabinet artwork. */
internal val cabinetRowTops = listOf(.175f, .407f, .623f)
internal val cabinetRowBottoms = listOf(.361f, .575f, .780f)

/** Fit, never crop or stretch: content and furniture share the same coordinate space. */
@Composable
internal fun Cabinet3d(height: Dp, footer: @Composable () -> Unit, content: @Composable BoxWithConstraintsScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        val cabinetWidth = minOf(maxWidth, maxHeight / 1.5f)
        BoxWithConstraints(Modifier.width(cabinetWidth).aspectRatio(2f / 3f)) {
            Image(
                painterResource(R.drawable.library_cabinet_3d),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            content()
            Box(
                Modifier.offset(maxWidth * .355f, maxHeight * .846f).size(maxWidth * .29f, maxHeight * .063f),
                contentAlignment = Alignment.Center,
            ) { footer() }
        }
    }
}
