package pe.lee.core.domain.library

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryUseCasesTest {
    @Test
    fun searchIgnoresAccentCaseWhitespaceAndWordOrder() {
        val search = SearchBooksUseCase(listOf("García Márquez EPUB", "Ñandú PDF")) { it }
        assertEquals(listOf("García Márquez EPUB"), search("  EPUB   garcia "))
        assertEquals(listOf("Ñandú PDF"), search("nandu"))
        assertEquals(emptyList<String>(), search("garcia pdf"))
    }

    @Test
    fun searchBuildsIndexOnceAndKeepsInputOrder() {
        var indexed = 0
        val search = SearchBooksUseCase(listOf("Zeta", "Alfa")) { indexed++; it }
        assertEquals(listOf("Zeta", "Alfa"), search(""))
        search("alfa")
        search("zeta")
        assertEquals(2, indexed)
    }

    @Test
    fun importDeduplicatesIdentityAndPreservesFirstEntry() = runBlocking {
        var saved = emptyList<Pair<String, String>>()
        val useCase = ImportBookUseCase<Pair<String, String>>({ saved = it }, { it.first })
        useCase(listOf("a" to "First", "a" to "Duplicate", "b" to "Second"))
        assertEquals(listOf("a" to "First", "b" to "Second"), saved)
    }

    @Test
    fun emptyImportDoesNotWrite() = runBlocking {
        var wrote = false
        ImportBookUseCase<String>({ wrote = true }, { it })(emptyList())
        assertFalse(wrote)
    }

    @Test
    fun invalidBatchDoesNotPartiallyWrite() {
        var wrote = false
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ImportBookUseCase<String>({ wrote = true }, { it })(listOf("valid", " ")) }
        }
        assertFalse(wrote)
    }

    @Test
    fun deletePreservesIdentityAndPropagatesStorageFailures() {
        var deleted = ""
        val useCase = DeleteBookUseCase { deleted = it; throw IOException("locked") }
        assertThrows(IOException::class.java) { runBlocking { useCase("content://book/1") } }
        assertEquals("content://book/1", deleted)
    }

    @Test
    fun importDoesNotConvertCancellationIntoSuccess() {
        assertThrows(CancellationException::class.java) {
            runBlocking { ImportBookUseCase<String>({ throw CancellationException("cancel") }, { it })(listOf("a")) }
        }
    }

    @Test
    fun progressSanitizesNonFiniteAndOutOfRangeValues() = runBlocking {
        val saved = mutableListOf<Float>()
        val update = UpdateBookProgressUseCase()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.5f, 2f)) {
            update(value) { saved.add(it) }
        }
        assertEquals(listOf(0f, 0f, 0f, 0.5f, 1f), saved)
    }

    @Test
    fun backupAcceptsLegacyAndEncryptedPayloads() {
        val original = "{\"version\":2}".toByteArray()
        val restore = ImportBackupUseCase()
        assertArrayEquals(original, restore(original, null))
        val encrypted = ExportBackupUseCase()(original, "password-test")
        assertArrayEquals(original, restore(encrypted, "password-test"))
        assertThrows(IOException::class.java) { restore(encrypted, null) }
        assertThrows(IOException::class.java) { restore(encrypted, "wrong-password") }
        assertTrue(original.isNotEmpty())
    }
}
