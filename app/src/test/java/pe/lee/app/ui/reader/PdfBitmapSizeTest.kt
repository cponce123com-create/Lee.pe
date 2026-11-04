package pe.lee.app.ui.reader

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page budget is what keeps a big PDF from taking the whole heap on a
 * low-RAM phone. These tests pin the invariant: never render more pixels than
 * the budget allows, whatever the page size.
 */
class PdfBitmapSizeTest {
    private fun assertWithinBudget(pageWidth: Int, pageHeight: Int, budget: Long) {
        val size = pdfBitmapSize(
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            maxWidthPx = 4000,
            maxPixels = budget,
        )
        val pixels = size.width.toLong() * size.height.toLong()
        assertTrue("presupuesto excedido: $pixels > $budget", pixels <= budget)
        assertTrue("tamaño vacío", size.width > 0 && size.height > 0)
    }

    @Test
    fun respectsTheLowRamBudget() {
        assertWithinBudget(2480, 3508, LOW_RAM_PDF_BITMAP_PIXELS) // A4 a 300 dpi
        assertWithinBudget(4000, 6000, LOW_RAM_PDF_BITMAP_PIXELS)
        assertWithinBudget(600, 800, LOW_RAM_PDF_BITMAP_PIXELS)
    }

    @Test
    fun neverExceedsTheDefaultBudget() {
        assertWithinBudget(6000, 9000, MAX_PDF_BITMAP_PIXELS)
        assertWithinBudget(2480, 3508, MAX_PDF_BITMAP_PIXELS)
    }

    @Test
    fun keepsThePageAspectRatio() {
        val size = pdfBitmapSize(
            pageWidth = 1000,
            pageHeight = 2000,
            maxWidthPx = 500,
            maxPixels = MAX_PDF_BITMAP_PIXELS,
        )
        assertTrue(size.width <= 500)
        val ratio = size.height.toDouble() / size.width
        assertTrue("relación inesperada: $ratio", ratio in 1.9..2.1)
    }

    @Test
    fun hugePageNeverProducesAnEmptyBitmap() {
        val size = pdfBitmapSize(
            pageWidth = 20_000,
            pageHeight = 30_000,
            maxWidthPx = 40,
            maxPixels = LOW_RAM_PDF_BITMAP_PIXELS,
        )
        assertTrue(size.width >= 1 && size.height >= 1)
    }
}
