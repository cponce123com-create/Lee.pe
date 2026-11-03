package pe.lee.app.data.local

import android.content.Context
import android.net.Uri
import android.os.StatFs
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
                            val input = context.contentResolver.openInputStream(originalUri)
                                ?: throw IOException("No se pudo leer el archivo original. Revisa su permiso.")
                            input.use { source ->
                                temporary.outputStream().buffered().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var copiedBytes = 0L
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = source.read(buffer)
                                        if (count < 0) break
                                        // Re-check every chunk because free space can change while copying.
                                        if (StatFs(directory.absolutePath).availableBytes < count.toLong()) {
                                            throw IOException("No hay espacio suficiente para guardar la copia sin conexión.")
                                        }
                                        output.write(buffer, 0, count)
                                        copiedBytes += count
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

    fun deleteCopy(context: Context, originalUri: Uri) {
        val prefix = "${sha256(originalUri.toString())}."
        directory(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
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
}
