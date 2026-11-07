package pe.lee.feature.sharecard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

class QuoteCardThemeTest {
    @Test
    fun everyThemeResolvesToACompletePaletteAndEveryPatternIsUsed() {
        val palettes = QuoteCardThemeId.entries.map(::quoteCardPalette)
        val patterns = palettes.map { it.pattern }.toSet()

        assertEquals(QuoteCardThemeId.entries.size, palettes.size)
        assertTrue(QuoteCardPattern.entries.all { it in patterns })
        assertTrue(palettes.all { it.text != Color.Unspecified })
        assertTrue(palettes.all { it.metadata != Color.Unspecified })
        assertTrue(palettes.all { it.accent != Color.Unspecified })
    }

    @Test
    fun namedPalettesMapToTheirExpectedColorsPatternsAndFonts() {
        val rain = quoteCardPalette(QuoteCardThemeId.RAIN)
        assertEquals(Color(0xFFF3F5F2), rain.text)
        assertEquals(Color(0xFFFFC365), rain.accent)
        assertEquals(QuoteCardPattern.RAIN, rain.pattern)
        assertSame(FontFamily.Serif, rain.fontFamily)

        val chrome = quoteCardPalette(QuoteCardThemeId.CHROME)
        assertEquals(Color.White, chrome.text)
        assertEquals(Color(0xFFB6C0C7), chrome.accent)
        assertEquals(QuoteCardPattern.CHROME, chrome.pattern)
        assertSame(FontFamily.SansSerif, chrome.fontFamily)

        val papyrus = quoteCardPalette(QuoteCardThemeId.PAPYRUS)
        assertEquals(Color(0xFF32251A), papyrus.text)
        assertEquals(QuoteCardPattern.PAPYRUS, papyrus.pattern)
    }

    @Test
    fun letterThemeIsTheOnlyRepresentativeItalicPaletteAndEditorialAliasesSharePalette() {
        val letter = quoteCardPalette(QuoteCardThemeId.LETTER)
        assertSame(FontFamily.Cursive, letter.fontFamily)
        assertEquals(FontStyle.Italic, letter.fontStyle)
        assertEquals(QuoteCardPattern.LETTER, letter.pattern)

        val papyrus = quoteCardPalette(QuoteCardThemeId.PAPYRUS)
        assertEquals(FontStyle.Normal, papyrus.fontStyle)
        assertNotEquals(letter.pattern, papyrus.pattern)

        val editorial = quoteCardPalette(QuoteCardThemeId.EDITORIAL)
        val alias = quoteCardPalette(QuoteCardThemeId.GOLDEN_NIGHT)
        assertEquals(editorial.text, alias.text)
        assertEquals(editorial.metadata, alias.metadata)
        assertEquals(editorial.accent, alias.accent)
        assertEquals(editorial.fontFamily, alias.fontFamily)
        assertEquals(editorial.fontStyle, alias.fontStyle)
        assertEquals(editorial.pattern, alias.pattern)
    }
}
