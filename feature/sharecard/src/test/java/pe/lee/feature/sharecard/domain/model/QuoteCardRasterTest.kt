package pe.lee.feature.sharecard.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class QuoteCardRasterTest {
    @Test
    fun acceptsSinglePixelAndKeepsRowMajorArgbData() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val raster = QuoteCardRaster(width = 2, height = 2, argbPixels = pixels)

        assertEquals(2, raster.width)
        assertEquals(2, raster.height)
        assertArrayEquals(pixels, raster.argbPixels)
    }

    @Test
    fun rejectsNonPositiveDimensions() {
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(0, 1, intArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(1, 0, intArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(-1, 1, intArrayOf()) }
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(1, -1, intArrayOf()) }
    }

    @Test
    fun rejectsPixelBuffersThatDoNotMatchDimensions() {
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(2, 2, intArrayOf(1, 2, 3)) }
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(1, 1, intArrayOf(1, 2)) }
        assertThrows(IllegalArgumentException::class.java) { QuoteCardRaster(1, 1, intArrayOf()) }
    }
}
