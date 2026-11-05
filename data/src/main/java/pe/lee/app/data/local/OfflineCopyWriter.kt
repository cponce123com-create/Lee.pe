package pe.lee.app.data.local

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import pe.lee.core.domain.StorageException

/** Copies to a staging file and publishes only after complete, successful I/O. */
internal object OfflineCopyWriter {
    private const val SPACE_CHECK_INTERVAL_BYTES = 8L * 1024L * 1024L

    fun write(
        target: File,
        sourceSize: Long,
        openSource: () -> InputStream,
        availableBytes: () -> Long,
        checkActive: () -> Unit,
        publish: (File, File) -> Boolean = { temporary, destination -> temporary.renameTo(destination) },
    ): File {
        if (sourceSize >= 0) requireSpace(sourceSize, availableBytes)
        val temporary = File.createTempFile("lee-pe-", ".part", target.parentFile)
        try {
            val copied = openSource().use { source ->
                temporary.outputStream().buffered().use { output ->
                    copy(source, output, sourceSize, availableBytes, checkActive)
                }
            }
            validateSize(copied, sourceSize)
            checkActive()
            if (target.exists() || !publish(temporary, target)) throw StorageException(StorageException.Reason.FINALIZATION_FAILED)
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun copy(
        source: InputStream,
        output: OutputStream,
        sourceSize: Long,
        availableBytes: () -> Long,
        checkActive: () -> Unit,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var sinceSpaceCheck = SPACE_CHECK_INTERVAL_BYTES
        while (true) {
            checkActive()
            val count = readChunk(source, buffer)
            if (count < 0) break
            if (sinceSpaceCheck >= SPACE_CHECK_INTERVAL_BYTES) {
                val remaining = if (sourceSize >= 0) (sourceSize - copied).coerceAtLeast(count.toLong()) else count.toLong()
                requireSpace(remaining, availableBytes)
                sinceSpaceCheck = 0
            }
            output.write(buffer, 0, count)
            copied += count
            sinceSpaceCheck += count
        }
        return copied
    }

    private fun readChunk(source: InputStream, buffer: ByteArray): Int {
        val count = source.read(buffer)
        if (count != 0) return count
        val single = source.read()
        buffer[0] = single.toByte()
        return if (single < 0) -1 else 1
    }

    private fun requireSpace(required: Long, availableBytes: () -> Long) {
        if (availableBytes() < required) throw StorageException(StorageException.Reason.INSUFFICIENT_SPACE)
    }

    private fun validateSize(copied: Long, expected: Long) {
        if (copied == 0L) throw StorageException(StorageException.Reason.EMPTY_SOURCE)
        if (expected >= 0 && copied != expected) throw StorageException(StorageException.Reason.SOURCE_CHANGED)
    }
}
