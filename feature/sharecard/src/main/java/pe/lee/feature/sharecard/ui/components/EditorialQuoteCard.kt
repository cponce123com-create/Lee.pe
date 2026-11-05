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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.ui.theme.PremiumArtwork
import pe.lee.feature.sharecard.ui.theme.editorialLightPaper

/** Text is drawn directly on the approved artwork, without a panel or logo badge. */
@Composable
internal fun EditorialQuoteCard(card: QuoteCard, modifier: Modifier, onFitsChanged: (Boolean) -> Unit, onAuthorFitsChanged: (Boolean) -> Unit = {}) {
    val light = editorialLightPaper(card.themeId)
    val ink = if (light) Color(0xFF30251B) else Color(0xFFFFF2D6)
    val accent = if (light) Color(0xFF755238) else Color(0xFFE7C787)
    val family = if (card.font == QuoteCardFont.SANS_SERIF) FontFamily.SansSerif else FontFamily.Serif
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val author = card.author.ifBlank { stringResource(R.string.share_card_unknown_author) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Give longer selections more room while keeping the artwork's corner details clear.
        val longQuote = card.text.length > 280
        val horizontalMargin = maxWidth * if (longQuote) .09f else .13f
        val brandMarkSize = (minOf(maxWidth, maxHeight) * .22f).coerceIn(60.dp, 88.dp)
        val topMargin = maxHeight * if (longQuote) .10f else .18f
        val bottomMargin = maxOf(
            maxHeight * if (longQuote) .18f else .23f,
            brandMarkSize + 32.dp,
        )
        val authorHeight = if (longQuote) 32.dp else 36.dp
        val quoteGap = if (longQuote) 8.dp else 12.dp
        val quoteHeight = (maxHeight - topMargin - bottomMargin - authorHeight - quoteGap - 9.dp).coerceAtLeast(0.dp)
        val textWidth = (maxWidth - horizontalMargin * 2).coerceAtLeast(0.dp)
        fun fit(text: String, color: Color, height: androidx.compose.ui.unit.Dp, minimum: Int, maximum: Int): Pair<TextStyle, Boolean> {
            val widthPx = with(density) { textWidth.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { height.roundToPx() }
            for (size in maximum downTo minimum) {
                val style = TextStyle(color = color, fontFamily = family, textAlign = TextAlign.Start, fontSize = size.sp, lineHeight = (size * 1.35f).sp)
                val layout = measurer.measure(text, style, constraints = Constraints(maxWidth = widthPx))
                if (!layout.didOverflowWidth && layout.size.height <= heightPx) return style to true
            }
            return TextStyle(color = color, fontFamily = family, textAlign = TextAlign.Start, fontSize = minimum.sp, lineHeight = (minimum * 1.35f).sp) to false
        }
        val quoteLayout = fit(card.text.trim(), ink, quoteHeight, 8, 32)
        val authorLayout = fit(author, ink, authorHeight, 9, 14)
        SideEffect {
            onFitsChanged(quoteLayout.second && authorLayout.second)
            onAuthorFitsChanged(authorLayout.second)
        }
        PremiumArtwork(card.themeId, Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().padding(
                start = horizontalMargin,
                end = horizontalMargin,
                top = topMargin,
                bottom = bottomMargin,
            ),
        ) {
            Text(
                text = card.text.trim(),
                style = quoteLayout.first,
                modifier = Modifier.fillMaxWidth().height(quoteHeight),
            )
            Spacer(Modifier.height(if (longQuote) 8.dp else 12.dp))
            Box(Modifier.width(28.dp).height(1.dp).background(accent))
            Spacer(Modifier.height(8.dp))
            Text(
                text = author,
                style = authorLayout.first,
                modifier = Modifier.fillMaxWidth().height(authorHeight),
            )
        }
        QuoteCardBrandWatermark(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = horizontalMargin, bottom = 16.dp)
                .size(brandMarkSize),
        )
    }
}
