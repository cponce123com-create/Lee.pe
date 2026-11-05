package pe.lee.feature.sharecard.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

class PremiumArtworkTest {
    @Test
    fun everyStableThemeHasOneDistinctArtwork() {
        assertEquals(40, premiumThemes.size)
        assertEquals(QuoteCardThemeId.entries.toSet(), premiumThemes.toSet())
        assertEquals(40, premiumThemes.map(::premiumCrop).toSet().size)
    }

    @Test
    fun cropsStayInsideTheOriginalImageWithoutNeighbouringCards() {
        val crops = premiumThemes.map(::premiumCrop)
        crops.forEach { crop ->
            assertTrue(crop.width > 0 && crop.height > 0)
            assertTrue(crop.left >= 0 && crop.top >= 0)
            assertTrue(crop.left + crop.width <= 1536 && crop.top + crop.height <= 1519)
        }
        crops.forEachIndexed { index, a ->
            crops.drop(index + 1).forEach { b ->
                val overlaps = a.left < b.left + b.width && a.left + a.width > b.left &&
                    a.top < b.top + b.height && a.top + a.height > b.top
                assertFalse(overlaps)
            }
        }
    }

    @Test
    fun moodsAreUsableAndKeepAllFortyAccessible() {
        assertEquals(40, premiumThemes.count { premiumMatchesMood(it, QuoteMood.ALL) })
        QuoteMood.entries.forEach { mood -> assertTrue(premiumThemes.any { premiumMatchesMood(it, mood) }) }
        assertTrue(premiumMatchesMood(QuoteCardThemeId.RAIN, QuoteMood.MELANCHOLY))
        assertTrue(premiumMatchesMood(QuoteCardThemeId.WATERCOLOR, QuoteMood.JOY))
        assertTrue(premiumMatchesMood(QuoteCardThemeId.PAPYRUS, QuoteMood.LITERARY))
    }
}
