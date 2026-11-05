package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.ui.theme.PremiumArtwork
import pe.lee.feature.sharecard.ui.theme.premiumLightPaper
import pe.lee.feature.sharecard.ui.theme.quoteCardPalette

/** Card-only artwork. Quote and author are measured before they are rendered or exported. */
@Composable
fun QuoteCardPreview(card: QuoteCard, modifier: Modifier = Modifier, onFitsChanged: (Boolean) -> Unit = {}) {
    var quoteFits by remember(card) { mutableStateOf(false) }
    var authorFits by remember(card) { mutableStateOf(false) }
    val cardFits = quoteFits && authorFits
    SideEffect { onFitsChanged(cardFits) }

    val palette = quoteCardPalette(card.themeId)
    val family = when (card.font) {
        QuoteCardFont.TEMPLATE -> palette.fontFamily
        QuoteCardFont.SERIF -> FontFamily.Serif
        QuoteCardFont.SANS_SERIF -> FontFamily.SansSerif
    }
    val lightPaper = premiumLightPaper(card.themeId)
    val ink = if (lightPaper) Color(0xFF2D2018) else Color(0xFFFFF2D6)
    val metadataInk = if (lightPaper) Color(0xFF604C32) else Color(0xFFE5CCA3)
    val accent = if (lightPaper) Color(0xFF8B5A27) else Color(0xFFE7C787)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val cardHeight = maxHeight
        PremiumArtwork(card.themeId, Modifier.fillMaxSize())
        val fraction = when {
            card.text.length < 140 -> .46f
            card.text.length < 400 -> .65f
            else -> .82f
        }
        val panelHeight = maxOf(cardHeight * fraction, minOf(220.dp, cardHeight - 60.dp))
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 24.dp)
                .height(panelHeight).shadow(8.dp, RoundedCornerShape(14.dp))
                .background(if (lightPaper) Color(0xE6FFF3DD) else Color(0xCC080B10), RoundedCornerShape(14.dp))
                .padding(20.dp),
        ) {
            Text("“", color = accent, fontSize = 30.sp, lineHeight = 32.sp)
            FittedCardText(
                text = card.text.trim(),
                style = TextStyle(color = ink, fontFamily = family, fontStyle = palette.fontStyle),
                modifier = Modifier.weight(1f).fillMaxWidth(), minimum = 8, maximum = 32,
                onFitsChanged = { quoteFits = it },
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(1.dp).background(accent))
            Spacer(Modifier.height(8.dp))
            FittedCardText(
                text = card.author.ifBlank { stringResource(R.string.share_card_unknown_author) },
                style = TextStyle(color = metadataInk, fontFamily = family, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().height(42.dp), minimum = 9, maximum = 16,
                onFitsChanged = { authorFits = it },
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .background(Color(0xEDFFF8EB), RoundedCornerShape(7.dp)).padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.leepe_logo_mark), null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.share_card_brand), color = Color(0xFF133F3A), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
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
    onFitsChanged: (Boolean) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
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
                    style = style.copy(fontSize = size.sp, lineHeight = (size * 1.35f).sp, textAlign = TextAlign.Center),
                    constraints = Constraints(maxWidth = width, maxHeight = height),
                )
                if (!measured.hasVisualOverflow) {
                    best = size
                    fits = true
                    low = size + 1
                } else {
                    high = size - 1
                }
            }
            best to fits
        }
        SideEffect { onFitsChanged(result.second) }
        Text(text, style = style.copy(fontSize = result.first.sp, lineHeight = (result.first * 1.35f).sp, textAlign = TextAlign.Center))
    }
}
