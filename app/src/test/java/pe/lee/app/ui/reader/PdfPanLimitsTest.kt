package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPanLimitsTest {
    @Test
    fun portraitPageCannotPanAcrossItsLetterbox() {
        assertEquals(0f to 200f, pdfPanLimits(100f, 400f, 300f, 400f, 0, 2f))
    }

    @Test
    fun quarterTurnsSwapTheAvailablePanAxes() {
        for (rotation in listOf(90, 270)) {
            assertEquals(0f to 200f, pdfPanLimits(400f, 100f, 400f, 300f, rotation, 2f))
        }
    }

    @Test
    fun pageAtFitOrBelowFitCannotPan() {
        assertEquals(0f to 0f, pdfPanLimits(400f, 600f, 300f, 500f, 0, 1f))
        assertEquals(0f to 0f, pdfPanLimits(400f, 600f, 300f, 500f, 0, 0.5f))
    }

    @Test
    fun halfTurnsKeepAxesAndNegativeQuarterTurnSwapsThem() {
        val expected = pdfPanLimits(400f, 100f, 400f, 300f, 0, 2f)
        assertEquals(expected, pdfPanLimits(400f, 100f, 400f, 300f, 180, 2f))
        assertEquals(expected.second to expected.first, pdfPanLimits(400f, 100f, 400f, 300f, -90, 2f))
    }
}
