package pe.lee.feature.sharecard.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

internal enum class QuoteCardPattern {
    FRAME,
    DOUBLE_FRAME,
    FIBER,
    HIGHLIGHT,
    LANDSCAPE,
    WAVES,
    SUNSET,
    RAIN,
    WATERCOLOR,
    BOTANICAL,
    FLOWERS,
    STARS,
    BOLD,
    MAGAZINE,
    GEOMETRY,
    POLAROID,
    LETTER,
    BOOK_PAGE,
    SHELVES,
    ANTIQUE,
    PAPYRUS,
    STONE,
    MARBLE,
    CLAY,
    INK,
    CYANOTYPE,
    CRYSTAL,
    CHROME,
    AURA,
    TORN,
    WOOD,
    STITCHES,
    AZULEJOS,
    RETRO,
    MUSEUM,
    MOSS,
    SAND,
    RELIEF,
}

internal data class QuoteCardPalette(
    val background: Brush,
    val text: Color,
    val metadata: Color,
    val accent: Color,
    val fontFamily: FontFamily,
    val fontStyle: FontStyle = FontStyle.Normal,
    val pattern: QuoteCardPattern,
)

internal fun quoteCardPalette(theme: QuoteCardThemeId): QuoteCardPalette = when (theme) {
    QuoteCardThemeId.GOLDEN_NIGHT,
    QuoteCardThemeId.BOTANICAL_IVORY,
    QuoteCardThemeId.ELEGANT_FOREST,
    QuoteCardThemeId.PAPER_ROSE,
    QuoteCardThemeId.STONE_GOLD,
    QuoteCardThemeId.LIVING_PARCHMENT,
    QuoteCardThemeId.INK_MOON,
    QuoteCardThemeId.LINEN_FEATHER,
    QuoteCardThemeId.LITERARY_LEATHER,
    QuoteCardThemeId.LIGHT_SPROUT,
    -> quoteCardPalette(QuoteCardThemeId.EDITORIAL)
    QuoteCardThemeId.EDITORIAL -> p(0xFFFFF9EF, 0xFFEADBC4, 0xFF342319, 0xFF5C3B24, 0xFF7A4B22, FontFamily.Serif, QuoteCardPattern.FRAME)
    QuoteCardThemeId.DARK_OLED -> p(
        0xFF0B111A,
        0xFF17202A,
        0xFFFFF4D6,
        0xFFE5D6AF,
        0xFFE3B95C,
        FontFamily.Serif,
        QuoteCardPattern.DOUBLE_FRAME,
    )
    QuoteCardThemeId.PAPER -> p(0xFFFFF8E9, 0xFFEAD8B6, 0xFF2D251B, 0xFF574633, 0xFF93612D, FontFamily.Monospace, QuoteCardPattern.FIBER)
    QuoteCardThemeId.MINIMAL -> p(
        0xFFFFFDF6,
        0xFFF7F0DD,
        0xFF211F1B,
        0xFF211F1B,
        0xFFE4B934,
        FontFamily.SansSerif,
        QuoteCardPattern.HIGHLIGHT,
    )
    QuoteCardThemeId.PREMIUM -> p(0xFF321017, 0xFF16090D, 0xFFFFF4DE, 0xFFE5C58D, 0xFFD7A943, FontFamily.Serif, QuoteCardPattern.MUSEUM)
    QuoteCardThemeId.ACCENT -> p(0xFF9D31D3, 0xFFFF6C77, 0xFF27112D, 0xFF38213B, 0xFFFFE073, FontFamily.SansSerif, QuoteCardPattern.AURA)
    QuoteCardThemeId.FOREST -> p(0xFF183A2D, 0xFF071D19, 0xFFFFF7E9, 0xFFD9E0C4, 0xFFC9AF68, FontFamily.Serif, QuoteCardPattern.LANDSCAPE)
    QuoteCardThemeId.CALM_SEA -> p(0xFFA9D4E5, 0xFF397A9A, 0xFF102E4A, 0xFF173E60, 0xFF376989, FontFamily.Serif, QuoteCardPattern.WAVES)
    QuoteCardThemeId.SUNSET -> p(0xFFF7C1A5, 0xFFE78078, 0xFF3C2228, 0xFF542B2C, 0xFF9E5142, FontFamily.Serif, QuoteCardPattern.SUNSET)
    QuoteCardThemeId.RAIN -> p(0xFF152C41, 0xFF071520, 0xFFF3F5F2, 0xFFBCCBD2, 0xFFFFC365, FontFamily.Serif, QuoteCardPattern.RAIN)
    QuoteCardThemeId.WATERCOLOR -> p(
        0xFFE8F1DE,
        0xFFC8DCC7,
        0xFF1B2822,
        0xFF536C54,
        0xFF7D9A69,
        FontFamily.Serif,
        QuoteCardPattern.WATERCOLOR,
    )
    QuoteCardThemeId.BOTANICAL -> p(
        0xFFFFF8E8,
        0xFFE8E0C7,
        0xFF20291E,
        0xFF3F5836,
        0xFF5F7D45,
        FontFamily.Serif,
        QuoteCardPattern.BOTANICAL,
    )
    QuoteCardThemeId.DRIED_FLOWERS -> p(
        0xFFF1E5D7,
        0xFFE6C8BB,
        0xFF422A38,
        0xFF72445D,
        0xFFA46D55,
        FontFamily.Serif,
        QuoteCardPattern.FLOWERS,
    )
    QuoteCardThemeId.NIGHT_SKY -> p(0xFF0E183D, 0xFF172454, 0xFFFFF5D8, 0xFFD9D9E6, 0xFFE6C56D, FontFamily.Serif, QuoteCardPattern.STARS)
    QuoteCardThemeId.PROTAGONIST -> p(
        0xFFFFF8EB,
        0xFFF2E9D8,
        0xFF111111,
        0xFF222222,
        0xFFED5A23,
        FontFamily.SansSerif,
        QuoteCardPattern.BOLD,
    )
    QuoteCardThemeId.MAGAZINE -> p(0xFFFFF9F0, 0xFFF1E0D3, 0xFF211A1A, 0xFF50413D, 0xFFC37A55, FontFamily.Serif, QuoteCardPattern.MAGAZINE)
    QuoteCardThemeId.GEOMETRY -> p(
        0xFFFFF8E9,
        0xFFEFE5CF,
        0xFF222A35,
        0xFF243D58,
        0xFFF2A82B,
        FontFamily.SansSerif,
        QuoteCardPattern.GEOMETRY,
    )
    QuoteCardThemeId.POLAROID -> p(0xFF8F6544, 0xFF4E3323, 0xFF261C18, 0xFF514035, 0xFFE6D5B8, FontFamily.Serif, QuoteCardPattern.POLAROID)
    QuoteCardThemeId.LETTER -> p(0xFFFFF5E7, 0xFFE8D9C2, 0xFF35281F, 0xFF655142, 0xFF9B7554, FontFamily.Cursive, QuoteCardPattern.LETTER)
    QuoteCardThemeId.BOOK_PAGE -> p(
        0xFFF7EEDB,
        0xFFE7D8B9,
        0xFF30291F,
        0xFF51463A,
        0xFFD3B545,
        FontFamily.Serif,
        QuoteCardPattern.BOOK_PAGE,
    )
    QuoteCardThemeId.LIBRARY -> p(0xFF5C3825, 0xFF29180F, 0xFFFFEBC5, 0xFFD7B76B, 0xFFD5A64A, FontFamily.Serif, QuoteCardPattern.SHELVES)
    QuoteCardThemeId.OLD_BOOK -> p(0xFF5F361C, 0xFF2E190E, 0xFFFFE6B5, 0xFFD0AF76, 0xFFC18A42, FontFamily.Serif, QuoteCardPattern.ANTIQUE)
    QuoteCardThemeId.PAPYRUS -> p(0xFFF0D29B, 0xFFD6B26F, 0xFF32251A, 0xFF67472A, 0xFF9A622D, FontFamily.Serif, QuoteCardPattern.PAPYRUS)
    QuoteCardThemeId.CARVED_STONE -> p(0xFF9B9A91, 0xFF565851, 0xFF1F211E, 0xFF41443D, 0xFF64704B, FontFamily.Serif, QuoteCardPattern.STONE)
    QuoteCardThemeId.MARBLE_GOLD -> p(0xFF151719, 0xFF08090B, 0xFFFFF3D3, 0xFFDDC987, 0xFFD3AD54, FontFamily.Serif, QuoteCardPattern.MARBLE)
    QuoteCardThemeId.CLAY -> p(0xFFD89369, 0xFFA85D42, 0xFF2A1812, 0xFF4E2C20, 0xFFE9B176, FontFamily.Serif, QuoteCardPattern.CLAY)
    QuoteCardThemeId.REAL_PARCHMENT -> p(
        0xFFF6E3B9,
        0xFFDFC18B,
        0xFF2F2417,
        0xFF604526,
        0xFFB17B37,
        FontFamily.Serif,
        QuoteCardPattern.PAPYRUS,
    )
    QuoteCardThemeId.JAPANESE_INK -> p(0xFFF7F2E8, 0xFFE9DFCE, 0xFF161616, 0xFF26231E, 0xFFB72A22, FontFamily.Serif, QuoteCardPattern.INK)
    QuoteCardThemeId.CYANOTYPE -> p(
        0xFF145188,
        0xFF082D5B,
        0xFFF4F4E8,
        0xFFD7E8F6,
        0xFF9FC8E4,
        FontFamily.Serif,
        QuoteCardPattern.CYANOTYPE,
    )
    QuoteCardThemeId.CRYSTAL -> p(0xFFF4E9FA, 0xFFC0E3EE, 0xFF172530, 0xFF47555E, 0xFF8E79B9, FontFamily.Serif, QuoteCardPattern.CRYSTAL)
    QuoteCardThemeId.CHROME -> p(0xFF373B42, 0xFF101216, 0xFFFFFFFF, 0xFFE8E8E8, 0xFFB6C0C7, FontFamily.SansSerif, QuoteCardPattern.CHROME)
    QuoteCardThemeId.AURA -> p(0xFF4E137B, 0xFFEA3B9D, 0xFFFFF5E9, 0xFFFFD4F1, 0xFFFFC85E, FontFamily.Serif, QuoteCardPattern.AURA)
    QuoteCardThemeId.TORN_PAPER -> p(0xFFA3382B, 0xFF5D201C, 0xFF261914, 0xFF4B3227, 0xFFE9CE9E, FontFamily.Serif, QuoteCardPattern.TORN)
    QuoteCardThemeId.WOOD_ENGRAVING -> p(
        0xFF60391F,
        0xFF21130C,
        0xFFFFE2A1,
        0xFFD7B779,
        0xFFD1A248,
        FontFamily.Serif,
        QuoteCardPattern.WOOD,
    )
    QuoteCardThemeId.EMBROIDERY -> p(
        0xFFEDE1C7,
        0xFFD9C7A4,
        0xFF172D4A,
        0xFF31537A,
        0xFFB54535,
        FontFamily.Serif,
        QuoteCardPattern.STITCHES,
    )
    QuoteCardThemeId.AZULEJOS -> p(0xFFF9F8EF, 0xFFE6E8DE, 0xFF123D76, 0xFF194B8C, 0xFF2D62A0, FontFamily.Serif, QuoteCardPattern.AZULEJOS)
    QuoteCardThemeId.RETRO_POSTER -> p(
        0xFFED632A,
        0xFFB83D24,
        0xFF173858,
        0xFF12304C,
        0xFFFFD16B,
        FontFamily.SansSerif,
        QuoteCardPattern.RETRO,
    )
    QuoteCardThemeId.MOSS_GARDEN -> p(0xFF314B2B, 0xFF172D20, 0xFFFFF5DF, 0xFFE4D8B6, 0xFFB8C87C, FontFamily.Serif, QuoteCardPattern.MOSS)
    QuoteCardThemeId.SAND -> p(0xFFE6B86F, 0xFFC58D48, 0xFF3A2919, 0xFF624725, 0xFF9B6833, FontFamily.Serif, QuoteCardPattern.SAND)
    QuoteCardThemeId.WHITE_RELIEF -> p(
        0xFFF4F0E9,
        0xFFE4DED4,
        0xFF28231F,
        0xFF665E54,
        0xFFA99B87,
        FontFamily.Serif,
        QuoteCardPattern.RELIEF,
    )
}

private fun p(top: Long, bottom: Long, text: Long, metadata: Long, accent: Long, font: FontFamily, pattern: QuoteCardPattern) =
    QuoteCardPalette(
        background = Brush.verticalGradient(listOf(Color(top), Color(bottom))),
        text = Color(text),
        metadata = Color(metadata),
        accent = Color(accent),
        fontFamily = font,
        fontStyle = if (pattern == QuoteCardPattern.LETTER) FontStyle.Italic else FontStyle.Normal,
        pattern = pattern,
    )
