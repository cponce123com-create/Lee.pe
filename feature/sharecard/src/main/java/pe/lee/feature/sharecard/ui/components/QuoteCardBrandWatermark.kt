package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pe.lee.feature.sharecard.R

/** Full Lee.pe artwork, kept legible against both light and dark quote backgrounds. */
@Composable
internal fun QuoteCardBrandWatermark(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Image(
        painter = painterResource(R.drawable.leepe_brand_logo),
        contentDescription = stringResource(R.string.share_card_brand),
        modifier = modifier.shadow(5.dp, shape).clip(shape),
        contentScale = ContentScale.Fit,
    )
}
