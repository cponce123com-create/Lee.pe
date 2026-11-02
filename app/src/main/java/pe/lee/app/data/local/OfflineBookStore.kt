package pe.lee.app.data.local

import android.content.Context
import android.net.Uri
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * Stores user-requested offline copies in app-private storage excluded from Android Auto Backup.
 * The original document URI remains the library key; these copies are only a playback source.
 */
object OfflineBookStore {
    const val EXTRA_COPY_URI = "pe.lee.app.library.OFFLINE_COPY_URI"

    private const val DIRECTORY_NAME = "lee_pe_offline_books"

    fun findCopy(context: Context, originalUri: Uri): File? {
        val prefix = "${sha256(originalUri.toString())}."
        return directory(context).listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith(prefix) && it.length() > 0L }
    }

    suspend fun saveCopy(context: Context, originalUri: Uri, displayName: String): File =
        withContext(Dispatchers.IO) {
            findCopy(context, originalUri)?.let { return@withContext it }

            val directory = directory(context)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("No se pudo crear el almacenamiento local.")
            }
            val extension = displayName.substringAfterLast('.', "")
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: "book"
            val target = File(directory, "${sha256(originalUri.toString())}.$extension")
            val temporary = File.createTempFile("lee-pe-", ".part", directory)
            try {
                val availableBytes = StatFs(directory.absolutePath).availableBytes
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
                            if (copiedBytes + count > availableBytes ||
                                StatFs(directory.absolutePath).availableBytes < count.toLong()
                            ) {
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
                if (target.exists() || !temporary.renameTo(target)) {
                    throw IOException("No se pudo finalizar la copia sin conexión.")
                }
                target
            } catch (error: Exception) {
                temporary.delete()
                throw error
            }
        }

    fun deleteCopy(context: Context, originalUri: Uri) {
        val prefix = "${sha256(originalUri.toString())}."
        directory(context).listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.forEach(File::delete)
    }

    private fun directory(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
