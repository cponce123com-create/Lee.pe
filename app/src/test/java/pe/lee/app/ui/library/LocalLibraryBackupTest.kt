package pe.lee.app.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class LocalLibraryBackupTest {
    private fun root(books: String = "[]", notes: String = "[]", settings: String = "{}") =
        """{"schema":"lee.pe.local-library","version":1,"books":$books,"annotations":$notes,"sessions":[],"settings":$settings}"""

    private val book = """
        {"uri":"content://example/test","title":"Mi libro","format":"EPUB",
        "importedAt":1,"lastOpenedAt":2,"epubLocator":null,
        "readProgress":0.5,"pdfPageIndex":0,"isFavorite":true,"collectionId":null}
    """.trimIndent()

    @Test
    fun validEmptyBackupIsAllowed() {
        val archive = LocalLibraryBackup.parse(root())
        assertEquals(0, archive.snapshot.books.size)
        assertEquals(0, archive.snapshot.annotations.size)
    }

    @Test
    fun validBookRestoresProgressMetadata() {
        val archive = LocalLibraryBackup.parse(root(books = "[$book]"))
        assertEquals(1, archive.snapshot.books.size)
        assertEquals(0.5f, archive.snapshot.books.single().readProgress)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root().replace("\"version\":1", "\"version\":99"))
        }
    }

    @Test
    fun invalidProgressIsRejectedBeforeDatabaseWrites() {
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root(books = "[${book.replace("\"readProgress\":0.5", "\"readProgress\":2.0")}]"))
        }
    }

    @Test
    fun unknownAnnotationBookIsRejected() {
        val note = """
            {"id":"n1","bookUri":"content://not-imported","kind":"NOTE",
             "locatorJson":"{}","selectedText":null,"noteText":"Nota",
             "colorArgb":null,"createdAt":2,"updatedAt":2}
        """.trimIndent()
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root(books = "[$book]", notes = "[$note]"))
        }
    }

    @Test
    fun invalidVoiceSettingsDoNotReachRestore() {
        val settings = """{"content://example/test":{"tts-speed:":100}}"""
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root(books = "[$book]", settings = settings))
        }
    }
}
