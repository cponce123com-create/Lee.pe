package pe.lee.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class BookSpineTitleTest {
    @Test
    fun removesSupportedExtensionAndNormalizesSeparatorsAndWhitespace() {
        assertEquals("El árbol azul", bookSpineTitle("  El__árbol   azul.EPUB"))
        assertEquals("Notas de viaje", bookSpineTitle("Notas_de_viaje.PdF"))
    }

    @Test
    fun removesOnlyRecognizedFinalExtensionAndLeavesOtherPunctuationAlone() {
        assertEquals("Libro.final", bookSpineTitle("Libro.final.mobi"))
        assertEquals("Libro.final.docx", bookSpineTitle("Libro.final.docx"))
        assertEquals("Libro v2", bookSpineTitle("Libro v2.epub"))
        assertEquals("", bookSpineTitle("   "))
    }
}
