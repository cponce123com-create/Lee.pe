package pe.lee.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBackupMatchingTest {
    private fun book(uri: String, title: String, format: String = "EPUB") = BookEntity(
        uri = uri,
        title = title,
        format = format,
        importedAt = 1234L,
    )

    @Test
    fun exactUriWinsEvenWhenNameWasEdited() {
        val saved = book("content://book/1", "Un título antiguo")
        val current = listOf(book("content://book/1", "Nombre corregido"))
        assertEquals(current.single(), findBackupMatch(saved, current))
    }

    @Test
    fun titleAndFormatAllowsRelinkingAfterManualImport() {
        val saved = book("content://old/1", "Donde la vida vuelve a empezar")
        val current = listOf(book("content://new/2", "  donde LA VIDA vuelve a empezar "))
        assertEquals(current.single(), findBackupMatch(saved, current))
    }

    @Test
    fun duplicateTitleNeedsManualReviewRatherThanGuessing() {
        val saved = book("content://old/1", "Mi libro")
        val current = listOf(book("content://new/2", "Mi libro"), book("content://new/3", "Mi libro"))
        assertNull(findBackupMatch(saved, current))
    }

    @Test
    fun mismatchedFormatsAreNeverMapped() {
        assertNull(
            findBackupMatch(
                book("content://old/1", "Manual", "PDF"),
                listOf(book("content://new/1", "Manual", "EPUB")),
            ),
        )
    }

    @Test
    fun noLocalFileMeansBackupCannotCreateBrokenBook() {
        assertNull(findBackupMatch(book("content://missing/1", "Manual"), emptyList()))
    }
}
