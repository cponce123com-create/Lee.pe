package pe.lee.feature.sharecard.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

internal data class QuoteCardPalette(
    val background: Brush,
    val text: Color,
    val metadata: Color,
    val accent: Color,
    val fontFamily: FontFamily,
    val fontStyle: FontStyle = FontStyle.Normal
)

internal fun quoteCardPalette(theme: QuoteCardThemeId): QuoteCardPalette = when (theme) {
    QuoteCardThemeId.MINIMAL -> QuoteCardPalette(
        Brush.linearGradient(listOf(Color.White, Color.White)), Color(0xFF17202A),
        Color(0xFF465362), Color(0xFF146C67), FontFamily.SansSerif
    )
    QuoteCardThemeId.EDITORIAL -> QuoteCardPalette(
        Brush.linearGradient(listOf(Color(0xFFFAF7F2), Color(0xFFFAF7F2))), Color(0xFF1A1A1A),
        Color(0xFF54483D), Color(0xFF943F35), FontFamily.Serif
    )
    QuoteCardThemeId.PREMIUM -> QuoteCardPalette(
        Brush.verticalGradient(listOf(Color(0xFF102F32), Color(0xFF071B20))), Color(0xFFFFF4DD),
        Color(0xFFDDD1B8), Color(0xFFE1BD78), FontFamily.Serif, FontStyle.Italic
    )
    QuoteCardThemeId.PAPER -> QuoteCardPalette(
        background = Brush.verticalGradient(listOf(Color(0xFFFAF7F2), Color(0xFFF1E9DC))),
        text = Color(0xFF1A1A1A),
        metadata = Color(0xFF5E554A),
        accent = Color(0xFFB8793E),
        fontFamily = FontFamily.Serif
    )
    QuoteCardThemeId.DARK_OLED -> QuoteCardPalette(
        background = Brush.verticalGradient(listOf(Color.Black, Color(0xFF111318))),
        text = Color(0xFFF5F5F5),
        metadata = Color(0xFFB8BBC5),
        accent = Color(0xFFD6B46A),
        fontFamily = FontFamily.Serif
    )
    QuoteCardThemeId.ACCENT -> QuoteCardPalette(
        background = Brush.linearGradient(listOf(Color(0xFFFDE68A), Color(0xFFFCA5A5), Color(0xFFC4B5FD))),
        text = Color(0xFF241A25),
        metadata = Color(0xFF493749),
        accent = Color(0xFF5B3C7A),
        fontFamily = FontFamily.SansSerif,
        fontStyle = FontStyle.Italic
    )
}
