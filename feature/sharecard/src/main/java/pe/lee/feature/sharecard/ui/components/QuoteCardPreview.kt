package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.random.Random
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId
import pe.lee.feature.sharecard.ui.theme.QuoteCardPalette
import pe.lee.feature.sharecard.ui.theme.QuoteCardPattern
import pe.lee.feature.sharecard.ui.theme.quoteCardPalette

/** Card-only artwork. Quote and author are measured before they are rendered or exported. */
@Composable
fun QuoteCardPreview(
    card: QuoteCard,
    modifier: Modifier = Modifier,
    onFitsChanged: (Boolean) -> Unit = {}
) {
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
    Box(modifier.fillMaxSize().background(palette.background)) {
        QuoteCardBackdrop(theme = card.themeId, palette = palette, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Text("“", color = palette.accent, fontSize = 46.sp, lineHeight = 48.sp)
            FittedCardText(
                text = card.text.trim(),
                style = TextStyle(color = palette.text, fontFamily = family, fontStyle = palette.fontStyle),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                minimum = 8,
                maximum = 32,
                onFitsChanged = { quoteFits = it }
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(42.dp).height(2.dp).background(palette.accent))
            Spacer(Modifier.height(10.dp))
            // The exported footer intentionally contains the author only, without the book title.
            FittedCardText(
                text = card.author.ifBlank { stringResource(R.string.share_card_unknown_author) },
                style = TextStyle(color = palette.metadata, fontFamily = family, fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth().height(42.dp),
                minimum = 9,
                maximum = 16,
                onFitsChanged = { authorFits = it }
            )
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.leepe_logo_mark), null, Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.share_card_brand), color = palette.metadata, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun QuoteCardBackdrop(
    theme: QuoteCardThemeId,
    palette: QuoteCardPalette,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val tint = palette.accent.copy(alpha = 0.38f)
        val soft = palette.accent.copy(alpha = 0.16f)
        val inset = size.minDimension * 0.055f
        val stroke = (size.minDimension * 0.003f).coerceAtLeast(1f)
        val random = Random(theme.ordinal + 71)
        when (palette.pattern) {
            QuoteCardPattern.FRAME, QuoteCardPattern.DOUBLE_FRAME, QuoteCardPattern.ANTIQUE,
            QuoteCardPattern.MUSEUM, QuoteCardPattern.RELIEF -> {
                drawRect(tint, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke))
                if (palette.pattern != QuoteCardPattern.FRAME) {
                    drawRect(soft, Offset(inset * 1.65f, inset * 1.65f), Size(size.width - inset * 3.3f, size.height - inset * 3.3f), style = Stroke(stroke * 1.4f))
                }
                repeat(4) { index ->
                    val x = if (index % 2 == 0) inset else size.width - inset
                    val y = if (index < 2) inset else size.height - inset
                    drawCircle(tint, stroke * 3f, Offset(x, y))
                }
            }
            QuoteCardPattern.FIBER, QuoteCardPattern.PAPYRUS, QuoteCardPattern.SAND,
            QuoteCardPattern.WOOD -> repeat(34) {
                val y = random.nextFloat() * size.height
                val x = random.nextFloat() * size.width
                val length = size.width * (0.04f + random.nextFloat() * 0.13f)
                drawLine(soft, Offset(x, y), Offset((x + length).coerceAtMost(size.width), y), stroke * 0.7f)
            }
            QuoteCardPattern.HIGHLIGHT -> {
                drawLine(tint, Offset(inset, size.height - inset * 2), Offset(size.width - inset, size.height - inset * 2), stroke * 2)
                drawRect(soft, Offset(inset, inset), Size(size.width - inset * 2, size.height * 0.025f))
            }
            QuoteCardPattern.LANDSCAPE, QuoteCardPattern.SUNSET, QuoteCardPattern.MOSS -> {
                val hill = Path().apply {
                    moveTo(0f, size.height * .78f)
                    lineTo(size.width * .22f, size.height * .62f)
                    lineTo(size.width * .43f, size.height * .78f)
                    lineTo(size.width * .69f, size.height * .56f)
                    lineTo(size.width, size.height * .73f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(hill, soft)
                if (palette.pattern == QuoteCardPattern.SUNSET) drawCircle(tint, size.minDimension * .09f, Offset(size.width * .78f, size.height * .22f))
            }
            QuoteCardPattern.WAVES, QuoteCardPattern.RAIN -> repeat(18) { index ->
                val y = size.height * (0.52f + index * 0.025f)
                if (palette.pattern == QuoteCardPattern.RAIN) {
                    val x = size.width * (index / 18f)
                    drawLine(soft, Offset(x, y), Offset(x - size.width * .04f, y + size.height * .12f), stroke)
                } else {
                    drawLine(soft, Offset(0f, y), Offset(size.width, y + size.height * .012f), stroke)
                }
            }
            QuoteCardPattern.WATERCOLOR, QuoteCardPattern.CRYSTAL, QuoteCardPattern.CHROME,
            QuoteCardPattern.AURA -> {
                repeat(5) { index ->
                    val radius = size.minDimension * (.1f + index * .065f)
                    drawCircle(if (index % 2 == 0) soft else tint.copy(alpha = .12f), radius,
                        Offset(size.width * (if (index % 2 == 0) .12f else .88f), size.height * (if (index % 2 == 0) .16f else .84f)), style = Stroke(stroke * 2))
                }
            }
            QuoteCardPattern.BOTANICAL, QuoteCardPattern.FLOWERS, QuoteCardPattern.STITCHES,
            QuoteCardPattern.AZULEJOS -> {
                repeat(8) { index ->
                    val x = size.width * (0.08f + index * .12f)
                    val y = if (index % 2 == 0) inset * 1.5f else size.height - inset * 1.5f
                    drawLine(tint, Offset(x, y), Offset(x + size.width * .035f, y + if (index % 2 == 0) inset else -inset), stroke * 1.5f)
                    drawCircle(soft, inset * .35f, Offset(x + size.width * .035f, y + if (index % 2 == 0) inset else -inset))
                }
                if (palette.pattern == QuoteCardPattern.AZULEJOS) {
                    drawRect(tint, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke * 2))
                }
            }
            QuoteCardPattern.STARS -> repeat(90) {
                val radius = size.minDimension * (.0015f + random.nextFloat() * .003f)
                drawCircle(tint, radius, Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
            }
            QuoteCardPattern.BOLD, QuoteCardPattern.RETRO -> {
                val band = Path().apply {
                    moveTo(0f, size.height * .12f); lineTo(size.width, size.height * .03f)
                    lineTo(size.width, size.height * .09f); lineTo(0f, size.height * .19f); close()
                }
                drawPath(band, tint)
                repeat(5) { index ->
                    val x = size.width * index / 5f
                    drawLine(soft, Offset(x, size.height * .88f), Offset(x + size.width * .12f, size.height), stroke * 2)
                }
            }
            QuoteCardPattern.MAGAZINE, QuoteCardPattern.GEOMETRY -> {
                drawRect(tint, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(stroke))
                drawCircle(soft, size.minDimension * .11f, Offset(size.width * .88f, size.height * .15f))
                drawLine(tint, Offset(inset, size.height * .18f), Offset(size.width * .32f, size.height * .18f), stroke * 2)
            }
            QuoteCardPattern.POLAROID -> drawRect(tint, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(inset * .55f))
            QuoteCardPattern.LETTER, QuoteCardPattern.TORN -> {
                val tear = Path().apply {
                    moveTo(0f, size.height * .12f)
                    for (index in 0..20) lineTo(size.width * index / 20f, size.height * (.12f + if (index % 2 == 0) .012f else 0f))
                }
                drawPath(tear, tint, style = Stroke(stroke * 2))
                drawLine(soft, Offset(size.width * .72f, 0f), Offset(size.width, size.height * .16f), stroke * 2)
            }
            QuoteCardPattern.BOOK_PAGE, QuoteCardPattern.SHELVES -> repeat(12) { index ->
                val y = size.height * (.12f + index * .055f)
                val end = if (index % 3 == 0) size.width * .78f else size.width * .93f
                drawLine(soft, Offset(inset, y), Offset(end, y), stroke)
            }
            QuoteCardPattern.STONE, QuoteCardPattern.CLAY -> repeat(70) {
                drawCircle(soft, size.minDimension * (.001f + random.nextFloat() * .002f), Offset(random.nextFloat() * size.width, random.nextFloat() * size.height))
            }
            QuoteCardPattern.MARBLE -> repeat(8) { index ->
                val x = size.width * index / 7f
                drawLine(tint, Offset(x, 0f), Offset((x + size.width * .18f).coerceAtMost(size.width), size.height), stroke)
            }
            QuoteCardPattern.INK, QuoteCardPattern.CYANOTYPE -> {
                drawCircle(tint, size.minDimension * .28f, Offset(size.width * .84f, size.height * .16f), style = Stroke(stroke * 4))
                drawLine(soft, Offset(size.width * .08f, size.height * .86f), Offset(size.width * .9f, size.height * .82f), stroke * 2)
            }
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
        Text(text, style = style.copy(fontSize = result.first.sp, lineHeight = (result.first * 1.35f).sp, textAlign = TextAlign.Center))
    }
}
