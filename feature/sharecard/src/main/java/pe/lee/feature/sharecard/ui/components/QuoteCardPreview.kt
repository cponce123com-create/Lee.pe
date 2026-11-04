package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.ui.theme.quoteCardPalette

/** Card-only artwork. Text is measured in its actual available area before rendering. */
@Composable
fun QuoteCardPreview(
    card: QuoteCard,
    modifier: Modifier = Modifier,
    onFitsChanged: (Boolean) -> Unit = {}
) {
    var quoteFits by remember(card) { mutableStateOf(false) }
    var metadataFits by remember(card) { mutableStateOf(false) }
    SideEffect { onFitsChanged(quoteFits && metadataFits) }
    val palette = quoteCardPalette(card.themeId)
    val family = when (card.font) {
        QuoteCardFont.TEMPLATE -> palette.fontFamily
        QuoteCardFont.SERIF -> FontFamily.Serif
        QuoteCardFont.SANS_SERIF -> FontFamily.SansSerif
    }
    Column(modifier.background(palette.background).padding(28.dp)) {
        Text("“", color = palette.accent, fontSize = 46.sp, lineHeight = 48.sp)
        FittedCardText(
            text = card.text.trim(),
            style = TextStyle(color = palette.text, fontFamily = family, fontStyle = palette.fontStyle),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // Story cards have enough vertical space to remain readable at 8sp
            // for long, legitimate quotations. Extremely long text still fails
            // the measured height check and remains non-exportable.
            minimum = 8,
            maximum = 32,
            onFitsChanged = { quoteFits = it }
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(40.dp).height(2.dp).background(palette.accent))
        Spacer(Modifier.height(10.dp))
        // Keep bibliographic metadata visible in an independently measured footer.
        FittedCardText(
            text = card.bookTitle.ifBlank { stringResource(R.string.share_card_unknown_book) } + "\n" +
                card.author.ifBlank { stringResource(R.string.share_card_unknown_author) },
            style = TextStyle(color = palette.metadata, fontFamily = family, fontWeight = FontWeight.Medium),
            modifier = Modifier.fillMaxWidth().height(58.dp),
            minimum = 9,
            maximum = 15,
            onFitsChanged = { metadataFits = it }
        )
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.leepe_logo_mark), null, Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.share_card_brand), color = palette.metadata, fontSize = 11.sp)
        }
    }
}

@Composable
private fun FittedCardText(
    text: String,
    style: TextStyle,
    modifier: Modifier,
    minimum: Int,
    maximum: Int,
    onFitsChanged: (Boolean) -> Unit
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val width = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val height = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val result = remember(text, style, width, height, density, minimum, maximum) {
            var low = minimum
            var high = maximum
            var best = minimum
            var fits = false
            while (low <= high) {
                val size = (low + high) / 2
                val measured = measurer.measure(
                    text = text,
                    style = style.copy(fontSize = size.sp, lineHeight = (size * 1.35f).sp),
                    constraints = Constraints(maxWidth = width, maxHeight = height)
                )
                if (!measured.hasVisualOverflow) {
                    best = size
                    fits = true
                    low = size + 1
                } else high = size - 1
            }
            best to fits
        }
        SideEffect { onFitsChanged(result.second) }
        Text(text, style = style.copy(fontSize = result.first.sp, lineHeight = (result.first * 1.35f).sp))
    }
}
