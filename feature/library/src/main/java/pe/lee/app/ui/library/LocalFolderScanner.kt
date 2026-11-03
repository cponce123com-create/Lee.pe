package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.ArrayDeque
import kotlin.coroutines.coroutineContext

/**
 * Finds books in ONE folder explicitly authorized by the user using Android SAF.
 * Does not require MANAGE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE or INTERNET.
 */
internal object LocalFolderScanner {
    const val MAX_FILES = 20_000
    const val MAX_DIRECTORIES = 2_000
    private const val MAX_DEPTH = 16

    data class Book(
        val uri: Uri,
        val title: String,
        val format: String,
        val size: Long
    )
    data class Result(
        val books: List<Book>,
        val visitedFiles: Int,
        val visitedFolders: Int,
        val duplicates: Int,
        val skippedFolders: Int,
        val limitReached: Boolean
    )
    data class Progress(val folders: Int, val files: Int, val books: Int)

    internal fun formatOf(fileName: String): String? =
        when (fileName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)) {
            "epub" -> "EPUB"
            "pdf" -> "PDF"
            "mp3", "m4a", "m4b", "aac", "ogg", "wav", "flac" -> "Audio"
            else -> null
        }

    internal fun catalogKey(uri: Uri): String {
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        return if (id != null && uri.authority != null) "${uri.authority}:$id" else uri.toString()
    }

    suspend fun scan(
        context: Context,
        treeUri: Uri,
        alreadyImported: Collection<String>,
        onProgress: suspend (Progress) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        require(treeUri.scheme == "content" && DocumentsContract.isTreeUri(treeUri)) {
            "Selecciona una carpeta con el explorador de Android."
        }
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(rootId to 0)
        val seenFolders = HashSet<String>()
        val seenFiles = HashSet<String>()
        val existingKeys = alreadyImported.asSequence().map { catalogKey(Uri.parse(it)) }.toHashSet()
        val found = ArrayList<Book>()
        var files = 0
        var duplicates = 0
        var skippedFolders = 0
        var reachedLimit = false
        var lastProgressFiles = 0

        while (queue.isNotEmpty()) {
            coroutineContext.ensureActive()
            if (seenFolders.size >= MAX_DIRECTORIES || files >= MAX_FILES) {
                reachedLimit = true
                break
            }
            val (parentId, depth) = queue.removeFirst()
            if (!seenFolders.add(parentId)) continue
            if (depth > MAX_DEPTH) {
                skippedFolders++
                reachedLimit = true
                continue
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            // Providers may refuse some subfolders. Continue with accessible ones.
            val cursor = try {
                resolver.query(childrenUri, projection, null, null, null)
            } catch (error: SecurityException) {
                throw IOException("Permiso de carpeta revocado. Vuelve a seleccionarla.", error)
            } catch (_: Exception) {
                null
            }
            if (cursor == null) {
                skippedFolders++
                continue
            }
            cursor.use { rows ->
                val idIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (rows.moveToNext()) {
                    coroutineContext.ensureActive()
                    val childId = if (idIndex >= 0) rows.getString(idIndex) else null
                    if (childId.isNullOrBlank()) continue
                    val mime = if (mimeIndex >= 0) rows.getString(mimeIndex) else null
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) queue.add(childId to depth + 1)
                        else { skippedFolders++; reachedLimit = true }
                        continue
                    }
                    files++
                    if (files > MAX_FILES) {
                        reachedLimit = true
                        break
                    }
                    val name = if (nameIndex >= 0) rows.getString(nameIndex) else null
                    if (name.isNullOrBlank()) continue
                    val format = formatOf(name) ?: continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val key = catalogKey(childUri)
                    if (key in existingKeys || !seenFiles.add(key)) {
                        duplicates++
                        continue
                    }
                    val size = if (sizeIndex >= 0 && !rows.isNull(sizeIndex)) rows.getLong(sizeIndex) else -1L
                    if (size == 0L) continue
                    found.add(
                        Book(
                            uri = childUri,
                            title = name.substringBeforeLast('.', name).ifBlank { name },
                            format = format,
                            size = size
                        )
                    )
                    if (files - lastProgressFiles >= 100) {
                        lastProgressFiles = files
                        onProgress(Progress(seenFolders.size, files, found.size))
                    }
                }
            }
            onProgress(Progress(seenFolders.size, files, found.size))
        }
        Result(
            books = found,
            visitedFiles = files.coerceAtMost(MAX_FILES),
            visitedFolders = seenFolders.size,
            duplicates = duplicates,
            skippedFolders = skippedFolders,
            limitReached = reachedLimit
        )
    }
}
