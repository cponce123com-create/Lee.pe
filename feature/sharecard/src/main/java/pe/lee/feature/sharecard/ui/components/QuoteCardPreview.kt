package pe.lee.feature.sharecard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.ui.theme.quoteCardPalette

/** The card-only composable used both for preview and PNG capture. */
@Composable
fun QuoteCardPreview(
    card: QuoteCard,
    modifier: Modifier = Modifier
) {
    val palette = quoteCardPalette(card.themeId)
    val verticalPadding = if (card.format == QuoteCardFormat.STORY) 34.dp else 24.dp

    Box(
        modifier = modifier
            .background(palette.background)
            .padding(horizontal = verticalPadding, vertical = verticalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "“",
            modifier = Modifier.align(Alignment.TopStart),
            color = palette.accent.copy(alpha = 0.55f),
            fontFamily = palette.fontFamily,
            fontSize = if (card.format == QuoteCardFormat.STORY) 96.sp else 72.sp,
            lineHeight = 84.sp
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = card.text.trim(),
                color = palette.text,
                fontFamily = palette.fontFamily,
                fontStyle = palette.fontStyle,
                fontSize = quoteFontSize(card.text.length, card.format),
                lineHeight = quoteLineHeight(card.text.length, card.format),
                textAlign = TextAlign.Start,
                maxLines = if (card.format == QuoteCardFormat.STORY) 11 else 6,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Box(
                    Modifier
                        .padding(bottom = 10.dp)
                        .width(42.dp)
                        .height(2.dp)
                        .background(palette.accent.copy(alpha = 0.72f))
                )
                Text(
                    text = card.bookTitle.ifBlank { stringResource(R.string.share_card_unknown_book) },
                    color = palette.text,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.author.ifBlank { stringResource(R.string.share_card_unknown_author) },
                    color = palette.metadata,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.leepe_logo_mark),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .background(Color(0xFF0D3450), CircleShape)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.share_card_brand),
                color = palette.metadata,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun quoteFontSize(length: Int, format: QuoteCardFormat) = when {
    length > 420 -> 17.sp
    length > 280 -> 20.sp
    length > 170 -> 24.sp
    length > 95 -> 28.sp
    format == QuoteCardFormat.SQUARE -> 31.sp
    else -> 34.sp
}

private fun quoteLineHeight(length: Int, format: QuoteCardFormat) = when {
    length > 280 -> 25.sp
    format == QuoteCardFormat.SQUARE -> 39.sp
    else -> 43.sp
}
