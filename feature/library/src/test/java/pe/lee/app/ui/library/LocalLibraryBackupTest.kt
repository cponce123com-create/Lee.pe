package pe.lee.app.ui.library

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LocalBackupSnapshot
import pe.lee.app.data.local.ReadingAnnotationEntity

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
        assertEquals(0.5f, archive.snapshot.books.single().readProgress, 0.00001f)
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

    @Test
    fun versionOneStillReadsWithoutCollections() {
        val archive = LocalLibraryBackup.parse(root(books = "[$book]"))
        assertEquals(1, archive.snapshot.books.size)
        assertTrue(archive.snapshot.collections.isEmpty())
        assertTrue(archive.snapshot.bookCollectionNames.isEmpty())
    }

    @Test
    fun versionTwoRestoresEveryCollectionOfABook() {
        val v2 = """
            {"schema":"lee.pe.local-library","version":2,"books":[{
              "uri":"content://example/test","title":"Mi libro","format":"EPUB",
              "importedAt":1,"lastOpenedAt":2,"epubLocator":null,
              "readProgress":0.5,"pdfPageIndex":0,"isFavorite":true,
              "collectionId":"Novela","collections":["Novela","Perú"]
            }],"annotations":[],"sessions":[],"settings":{},
            "collections":[{"name":"Novela","sortOrder":0},{"name":"Perú","sortOrder":1},{"name":"Vacía","sortOrder":2}]}
        """.trimIndent()
        val archive = LocalLibraryBackup.parse(v2)
        assertEquals(
            listOf("Novela", "Perú"),
            archive.snapshot.bookCollectionNames["content://example/test"],
        )
        assertEquals(
            listOf("Novela", "Perú", "Vacía"),
            archive.snapshot.collections.map { it.name },
        )
    }

    @Test
    fun versionTwoRejectsInvalidCollectionNames() {
        val v2 = """
            {"schema":"lee.pe.local-library","version":2,"books":[{
              "uri":"content://example/test","title":"Mi libro","format":"EPUB",
              "importedAt":1,"lastOpenedAt":2,"epubLocator":null,
              "readProgress":0.5,"pdfPageIndex":0,"isFavorite":true,
              "collectionId":null,"collections":["   "]
            }],"annotations":[],"sessions":[],"settings":{},"collections":[]}
        """.trimIndent()
        assertThrows(IOException::class.java) { LocalLibraryBackup.parse(v2) }
    }

    @Test
    fun futureVersionIsStillRejected() {
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root().replace("\"version\":1", "\"version\":3"))
        }
    }

    @Test
    fun oversizedAnnotationTextIsShortenedInsteadOfBlockingExport() {
        val longText = "x".repeat(12_001)
        val sanitized = LocalLibraryBackup.sanitizeSnapshot(
            LocalBackupSnapshot(
                books = listOf(BookEntity(uri = "content://example/test", title = "Mi libro", format = "EPUB", importedAt = 1)),
                annotations = listOf(
                    ReadingAnnotationEntity(
                        id = "n1",
                        bookUri = "content://example/test",
                        kind = "NOTE",
                        locatorJson = "{}",
                        noteText = longText,
                        createdAt = 1,
                    ),
                ),
                sessions = emptyList(),
            ),
        )

        assertEquals(1, sanitized.adjustedRecords)
        assertEquals(12_000, sanitized.snapshot.annotations.single().noteText?.length)
        val exportedNote = sanitized.snapshot.annotations.single().noteText
        val json = root(
            books = "[$book]",
            notes = """[{
                "id":"n1","bookUri":"content://example/test","kind":"NOTE","locatorJson":"{}",
                "selectedText":null,"noteText":"$exportedNote","colorArgb":null,"createdAt":1,"updatedAt":1
            }]""",
        )
        assertEquals(12_000, LocalLibraryBackup.parse(json).snapshot.annotations.single().noteText?.length)
    }
    @Test
    fun rejectsImpossibleSessionDay() {
        val session = """{"dayKey":"2026-13-45","bookUri":"content://example/test","durationSeconds":60}"""
        assertThrows(IOException::class.java) {
            LocalLibraryBackup.parse(root(books = "[$book]").replace("\"sessions\":[]", "\"sessions\":[$session]"))
        }
    }

    @Test
    fun rejectsPdfPageOutsideSupportedRange() {
        val invalid = book.replace("\"pdfPageIndex\":0", "\"pdfPageIndex\":1000001")
        assertThrows(IOException::class.java) { LocalLibraryBackup.parse(root(books = "[$invalid]")) }
    }

    @Test
    fun archiveReaderStopsAtSixteenMegabytes() {
        val source = object : java.io.InputStream() {
            private var remaining = MAX_ARCHIVE_BYTES + 1
            override fun read(): Int = if (remaining-- > 0) 0 else -1
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(length, remaining)
                bytes.fill(0, offset, offset + count)
                remaining -= count
                return count
            }
        }
        assertThrows(IOException::class.java) { LocalLibraryBackup.readArchivePayload(source) }
    }

}
