package pe.lee.app.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lee.core.domain.ReaderAnnotation

class AnnotationMarkdownExporterTest {
    @Test
    fun exportsAnnotationContentWithoutPrivateBookUri() {
        val privateUri = "content://private/provider/secret-book.epub"
        val markdown = AnnotationMarkdownExporter.toMarkdown(
            bookTitle = "Mi historia",
            format = "EPUB",
            exportedAt = 0L,
            annotations = listOf(
                ReaderAnnotation(
                    id = "note-1",
                    bookUri = privateUri,
                    kind = "NOTE",
                    locatorJson = "{\"locations\":{\"totalProgression\":0.5}}",
                    selectedText = "Una frase importante",
                    noteText = "Recordar esta idea",
                    createdAt = 0L
                )
            )
        )

        assertTrue(markdown.contains("# Anotaciones: Mi historia"))
        assertTrue(markdown.contains("**Texto marcado:**"))
        assertTrue(markdown.contains("Una frase importante"))
        assertTrue(markdown.contains("Recordar esta idea"))
        assertTrue(markdown.contains("50%"))
        assertFalse(markdown.contains(privateUri))
    }

    @Test
    fun createsSafeSpanishExportFileName() {
        assertTrue(annotationExportFileName("Árbol y río").endsWith("_anotaciones.md"))
        assertFalse(annotationExportFileName("../../").contains("/"))
    }

    @Test
    fun acceptsLocalizedPageAndFallbackBookLabels() {
        val markdown = AnnotationMarkdownExporter.toMarkdown(
            bookTitle = "Libro",
            format = "PDF",
            exportedAt = 0L,
            annotations = listOf(
                ReaderAnnotation(
                    id = "note-page",
                    bookUri = "content://private/book.pdf",
                    kind = "NOTE",
                    locatorJson = "{\"pageIndex\":4}",
                    noteText = "Nota",
                    createdAt = 0L
                )
            ),
            pageLabel = { "Page $it" }
        )

        assertTrue(markdown.contains("Page 5"))
        assertTrue(annotationExportFileName("../../", "book").startsWith("book_"))
    }
}
