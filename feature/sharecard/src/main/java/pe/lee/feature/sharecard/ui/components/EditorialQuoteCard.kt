package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.ui.theme.PremiumArtwork
import pe.lee.feature.sharecard.ui.theme.editorialLightPaper

/** Text is drawn directly on the approved artwork, without a panel or logo badge. */
@Composable
internal fun EditorialQuoteCard(card: QuoteCard, modifier: Modifier, onFitsChanged: (Boolean) -> Unit) {
    var quoteFits by remember(card) { mutableStateOf(false) }
    var authorFits by remember(card) { mutableStateOf(false) }
    SideEffect { onFitsChanged(quoteFits && authorFits) }
    val light = editorialLightPaper(card.themeId)
    val ink = if (light) Color(0xFF30251B) else Color(0xFFFFF2D6)
    val accent = if (light) Color(0xFF755238) else Color(0xFFE7C787)
    val family = if (card.font == QuoteCardFont.SANS_SERIF) FontFamily.SansSerif else FontFamily.Serif
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontalMargin = maxWidth * .13f
        // Relative reservations also protect corner decorations in square exports.
        val topMargin = maxHeight * .18f
        val bottomMargin = maxHeight * .23f
        PremiumArtwork(card.themeId, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(
                start = horizontalMargin,
                end = horizontalMargin,
                top = topMargin,
                bottom = bottomMargin,
            ),
        ) {
            FittedCardText(
                text = card.text.trim(),
                style = TextStyle(color = ink, fontFamily = family, textAlign = TextAlign.Start),
                modifier = Modifier.fillMaxWidth().weight(1f),
                minimum = 8,
                maximum = 32,
                onFitsChanged = { quoteFits = it },
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(28.dp).height(1.dp).background(accent))
            Spacer(Modifier.height(8.dp))
            FittedCardText(
                text = card.author.ifBlank { stringResource(R.string.share_card_unknown_author) },
                style = TextStyle(color = ink, fontFamily = family, textAlign = TextAlign.Start),
                modifier = Modifier.fillMaxWidth().height(36.dp),
                minimum = 9,
                maximum = 14,
                onFitsChanged = { authorFits = it },
            )
        }
        val brandMarkSize = (minOf(maxWidth, maxHeight) * .22f).coerceIn(60.dp, 88.dp)
        QuoteCardBrandWatermark(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = horizontalMargin, bottom = 16.dp)
                .size(brandMarkSize),
        )
    }
}
