package pe.lee.feature.sharecard.data

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster

@RunWith(AndroidJUnit4::class)
class ComposeShareCardGeneratorAndroidTest {
    @Test
    fun generatesPngWithRequestedSocialDimensions() = runBlocking {
        val bytes = ComposeShareCardGenerator().generate(
            card = QuoteCard("Frase", "Libro", "Autora"),
            raster = QuoteCardRaster(2, 2, intArrayOf(-1, -1, -1, -1)),
            format = QuoteCardFormat.SQUARE,
        )

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1080, bitmap.width)
        assertEquals(1080, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun generatesStoryPngWithNineBySixteenDimensions() = runBlocking {
        val bytes = ComposeShareCardGenerator().generate(
            card = QuoteCard("Frase", "Libro", "Autora"),
            raster = QuoteCardRaster(2, 2, intArrayOf(-1, -1, -1, -1)),
            format = QuoteCardFormat.STORY,
        )
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun generatesPortraitFeedPngWithFourByFiveDimensions() = runBlocking {
        val bytes = ComposeShareCardGenerator().generate(
            card = QuoteCard("Frase", "Libro", "Autora"),
            raster = QuoteCardRaster(2, 2, intArrayOf(-1, -1, -1, -1)),
            format = QuoteCardFormat.PORTRAIT,
        )
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        bitmap.recycle()
    }
}
