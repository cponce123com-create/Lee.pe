package pe.lee.app.data.local

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.coroutineContext

/**
 * Stores user-requested offline copies in app-private storage excluded from Android Auto Backup.
 * The original document URI remains the library key; these copies are only a playback source.
 */
object OfflineBookStore {
    const val EXTRA_COPY_URI = "pe.lee.app.library.OFFLINE_COPY_URI"

    private const val DIRECTORY_NAME = "lee_pe_offline_books"

    /** How much is copied between free-space checks (one statfs each). */
    private const val SPACE_CHECK_INTERVAL_BYTES = 8L * 1024L * 1024L
    private val saveLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun findCopy(context: Context, originalUri: Uri): File? =
        findCopies(context, listOf(originalUri))[originalUri.toString()]

    /**
     * Scans the private directory ONCE for the whole library, instead of
     * listFiles() once for each book (quadratic work with large collections).
     * Call from Dispatchers.IO: File metadata must not be read on the UI thread.
     */
    fun findCopies(context: Context, originalUris: Collection<Uri>): Map<String, File> {
        if (originalUris.isEmpty()) return emptyMap()
        val available = directory(context).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L }
            ?.mapNotNull { file ->
                val prefix = file.name.substringBefore('.', "")
                if (prefix.length == 64 && prefix.all { it in '0'..'9' || it in 'a'..'f' }) {
                    prefix to file
                } else null
            }
            ?.toMap()
            .orEmpty()
        return originalUris.mapNotNull { uri ->
            available[sha256(uri.toString())]?.let { uri.toString() to it }
        }.toMap()
    }

    suspend fun saveCopy(context: Context, originalUri: Uri, displayName: String): File =
        withContext(Dispatchers.IO) {
            val contentHash = sha256(originalUri.toString())
            saveLocks.computeIfAbsent(contentHash) { ReentrantLock() }.withLock {
                findCopy(context, originalUri)?.let { return@withContext it }

                val directory = directory(context)
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("No se pudo crear el almacenamiento local.")
                }
                val extension = displayName.substringAfterLast('.', "")
                    .lowercase()
                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                    ?: "book"
                val target = File(directory, "$contentHash.$extension")
                val lockFile = File(directory, "$contentHash.lock")
                RandomAccessFile(lockFile, "rw").channel.use { channel ->
                    channel.lock().use {
                        findCopy(context, originalUri)?.let { return@withContext it }
                        val temporary = File.createTempFile("lee-pe-", ".part", directory)
                        try {
                            val sourceSize = context.contentResolver.query(
                                originalUri,
                                arrayOf(OpenableColumns.SIZE),
                                null,
                                null,
                                null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else -1L
                                } else -1L
                            } ?: -1L
                            if (sourceSize >= 0L && StatFs(directory.absolutePath).availableBytes < sourceSize) {
                                throw IOException("No hay espacio suficiente para guardar la copia sin conexión.")
                            }
                            val input = context.contentResolver.openInputStream(originalUri)
                                ?: throw IOException("No se pudo leer el archivo original. Revisa su permiso.")
                            input.use { source ->
                                temporary.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var copiedBytes = 0L
                                    var bytesSinceSpaceCheck = 0L
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = source.read(buffer)
                                        if (count < 0) break
                                        // Checking free space costs a syscall; once every
                                        // few megabytes is enough and keeps large audio books
                                        // from crawling (A-09).
                                        if (bytesSinceSpaceCheck >= SPACE_CHECK_INTERVAL_BYTES) {
                                            val remaining = if (sourceSize >= 0L) {
                                                (sourceSize - copiedBytes).coerceAtLeast(count.toLong())
                                            } else count.toLong()
                                            if (StatFs(directory.absolutePath).availableBytes < remaining) {
                                                throw IOException("No hay espacio suficiente para guardar la copia sin conexión.")
                                            }
                                            bytesSinceSpaceCheck = 0L
                                        }
                                        output.write(buffer, 0, count)
                                        copiedBytes += count
                                        bytesSinceSpaceCheck += count
                                    }
                                    if (copiedBytes == 0L) {
                                        throw IOException("El archivo está vacío o no se pudo leer.")
                                    }
                                }
                            }
                            coroutineContext.ensureActive()
                            // The per-URI OS file lock makes this check and rename a single-writer section.
                            if (target.exists()) {
                                temporary.delete()
                                return@withContext target
                            }
                            if (!temporary.renameTo(target)) {
                                throw IOException("No se pudo finalizar la copia sin conexión.")
                            }
                            target
                        } catch (error: Exception) {
                            temporary.delete()
                            throw error
                        }
                    }
                }
            }
        }

    fun deleteCopy(context: Context, originalUri: Uri): Boolean {
        val prefix = "${sha256(originalUri.toString())}."
        val directory = directory(context)
        if (!directory.exists()) return true
        val files = directory.listFiles() ?: return false
        return files.filter { it.isFile && it.name.startsWith(prefix) }
            .all { file -> !file.exists() || file.delete() }
    }

    /** Removes incomplete copies left behind by a force stop or power loss. */
    fun purgeOrphanedParts(context: Context) {
        val cutoff = System.currentTimeMillis() - ORPHAN_PART_MAX_AGE_MS
        directory(context).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".part") && it.lastModified() < cutoff }
            ?.forEach(File::delete)
    }

    @Synchronized
    fun rebindCopy(context: Context, oldUri: Uri, newUri: Uri) {
        if (oldUri == newUri) return
        val source = findCopy(context, oldUri) ?: return
        val directory = directory(context)
        val extension = source.extension.ifBlank { "book" }
        val target = File(directory, "${sha256(newUri.toString())}.$extension")
        if (target.exists()) {
            source.delete()
            return
        }
        try {
            if (source.renameTo(target)) return
            source.copyTo(target, overwrite = false)
            if (!source.delete()) target.delete()
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private const val ORPHAN_PART_MAX_AGE_MS = 24 * 60 * 60 * 1000L
}
