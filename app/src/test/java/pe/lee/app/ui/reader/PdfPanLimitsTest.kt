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
}
