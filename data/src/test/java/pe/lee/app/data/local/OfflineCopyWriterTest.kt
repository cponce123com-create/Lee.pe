package pe.lee.app.data.local

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import pe.lee.core.domain.StorageException

class OfflineCopyWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun target() = File(folder.root, "book.epub")

    private fun write(bytes: ByteArray, size: Long = bytes.size.toLong()): File = OfflineCopyWriter.write(
        target(),
        size,
        { ByteArrayInputStream(bytes) },
        { Long.MAX_VALUE },
        {},
    )

    private fun assertNothingPublished() {
        assertFalse(target().exists())
        assertEquals(emptyList<String>(), folder.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun successfulCopyPublishesExactBytesAndRemovesStagingFile() {
        val bytes = ByteArray(20_000) { it.toByte() }
        assertArrayEquals(bytes, write(bytes).readBytes())
        assertEquals(listOf("book.epub"), folder.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun unknownSizeCanBeCopied() {
        assertArrayEquals(byteArrayOf(1, 2, 3), write(byteArrayOf(1, 2, 3), -1).readBytes())
    }

    @Test
    fun earlyEofAndGrowingSourceAreNotPublished() {
        for (size in listOf(1L, 10L)) {
            val error = assertThrows(StorageException::class.java) { write(byteArrayOf(1, 2), size) }
            assertEquals(StorageException.Reason.SOURCE_CHANGED, error.reason)
            assertNothingPublished()
        }
    }

    @Test
    fun emptySourceIsNotPublished() {
        val error = assertThrows(StorageException::class.java) { write(byteArrayOf()) }
        assertEquals(StorageException.Reason.EMPTY_SOURCE, error.reason)
        assertNothingPublished()
    }

    @Test
    fun insufficientSpaceFailsBeforeOpeningTheSource() {
        var opened = false
        val error = assertThrows(StorageException::class.java) {
            OfflineCopyWriter.write(target(), 100, {
                opened = true
                ByteArrayInputStream(byteArrayOf(1))
            }, { 99 }, {})
        }
        assertEquals(StorageException.Reason.INSUFFICIENT_SPACE, error.reason)
        assertFalse(opened)
        assertNothingPublished()
    }

    @Test
    fun readFailureClosesSourceAndDeletesThePartialFile() {
        var closed = false
        val input = object : InputStream() {
            var calls = 0
            override fun read(): Int = if (calls++ < 10) 1 else throw IOException("disconnected")
            override fun close() {
                closed = true
            }
        }
        assertThrows(IOException::class.java) {
            OfflineCopyWriter.write(target(), -1, { input }, { Long.MAX_VALUE }, {})
        }
        assertEquals(true, closed)
        assertNothingPublished()
    }

    @Test
    fun cancellationAfterWritingRemovesStagingFileAndPropagates() {
        var checks = 0
        assertThrows(CancellationException::class.java) {
            OfflineCopyWriter.write(target(), -1, { ByteArrayInputStream(ByteArray(20_000)) }, { Long.MAX_VALUE }, {
                if (++checks == 2) throw CancellationException("cancelled")
            })
        }
        assertNothingPublished()
    }

    @Test
    fun failedRenameNeverPublishesAnIncompleteTarget() {
        val error = assertThrows(StorageException::class.java) {
            OfflineCopyWriter.write(target(), 1, { ByteArrayInputStream(byteArrayOf(1)) }, { Long.MAX_VALUE }, {}, { _, _ -> false })
        }
        assertEquals(StorageException.Reason.FINALIZATION_FAILED, error.reason)
        assertNothingPublished()
    }

    @Test
    fun existingCopyIsNeverOverwritten() {
        target().writeBytes(byteArrayOf(7))
        assertThrows(StorageException::class.java) { write(byteArrayOf(1)) }
        assertArrayEquals(byteArrayOf(7), target().readBytes())
        assertEquals(listOf("book.epub"), folder.root.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun runningOutOfSpaceMidCopyDoesNotPublish() {
        var queries = 0
        val error = assertThrows(StorageException::class.java) {
            OfflineCopyWriter.write(target(), -1, { ByteArrayInputStream(ByteArray(9 * 1024 * 1024)) }, {
                if (++queries == 1) Long.MAX_VALUE else 0L
            }, {})
        }
        assertEquals(StorageException.Reason.INSUFFICIENT_SPACE, error.reason)
        assertNothingPublished()
    }

    @Test
    fun zeroLengthReadsCannotSpinOrPublishEmptyContent() {
        val input = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        val file = OfflineCopyWriter.write(target(), -1, { input }, { Long.MAX_VALUE }, {})
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }
}
