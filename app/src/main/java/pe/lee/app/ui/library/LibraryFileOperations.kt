package pe.lee.app.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Column
import pe.lee.app.ui.reader.PdfReaderActivity
import pe.lee.app.ui.reader.ReaderActivity
import pe.lee.app.ui.audio.AudioPlayerActivity
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.SettingsRepository
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



private val SUPPORTED_AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "wav", "flac")
private const val KEY_DOCUMENT_URIS = "document_uris"
private const val KEY_LEGACY_MIGRATED = "room_library_migrated"

internal fun rebindBookScopedPreferences(context: Context, oldUri: String, newUri: String) {
    val settings = SettingsRepository(context)
    val entries = settings.all(SettingsRepository.Store.LIBRARY).filterKeys { it.endsWith(":$oldUri") }
    if (entries.isEmpty()) return
    settings.apply(SettingsRepository.Store.LIBRARY) {
        entries.forEach { (oldKey, value) ->
            val newKey = oldKey.removeSuffix(oldUri) + newUri
            when (value) {
                is String -> putString(newKey, value)
                is Boolean -> putBoolean(newKey, value)
                is Int -> putInt(newKey, value)
                is Long -> putLong(newKey, value)
                is Float -> putFloat(newKey, value)
                is Set<*> -> putStringSet(newKey, value.filterIsInstance<String>().toSet())
            }
            remove(oldKey)
        }
    }
}


internal fun queryDisplayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameColumn >= 0) cursor.getString(nameColumn) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Libro importado"

internal fun formatFromFileName(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension == "epub" -> "EPUB"
        extension == "pdf" -> "PDF"
        extension in SUPPORTED_AUDIO_EXTENSIONS -> "Audio"
        else -> null
    }
}

internal fun openBook(context: Context, book: LocalBook) {
    if (book.format == "Audio") {
        context.startActivity(
            Intent(context, AudioPlayerActivity::class.java)
                .setData(book.uri)
                .putExtra(AudioPlayerActivity.EXTRA_TITLE, book.title)
                .putExtra(OfflineBookStore.EXTRA_COPY_URI, book.offlineCopyUri?.toString())
        )
        return
    }
    val activity = if (book.format == "PDF") PdfReaderActivity::class.java else ReaderActivity::class.java
    val titleExtra = if (book.format == "PDF") PdfReaderActivity.EXTRA_TITLE else ReaderActivity.EXTRA_TITLE
    context.startActivity(
        Intent(context, activity)
            .setData(book.uri)
            .putExtra(titleExtra, book.title)
            .putExtra(OfflineBookStore.EXTRA_COPY_URI, book.offlineCopyUri?.toString())
    )
}

internal suspend fun migrateLegacyLibrary(context: Context, viewModel: LibraryViewModel) {
    val settings = SettingsRepository(context)
    if (settings.getBoolean(SettingsRepository.Store.LIBRARY, KEY_LEGACY_MIGRATED, false)) return

    val legacyUris = settings.getStringSet(SettingsRepository.Store.LIBRARY, KEY_DOCUMENT_URIS)
    if (legacyUris.isEmpty()) {
        settings.apply(SettingsRepository.Store.LIBRARY) {
            putBoolean(KEY_LEGACY_MIGRATED, true)
            remove(KEY_DOCUMENT_URIS)
        }
        return
    }

    val (books, pendingUris) = withContext(Dispatchers.IO) {
        val books = mutableListOf<BookEntity>()
        val pendingUris = mutableSetOf<String>()
        val persistedReadUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        legacyUris.forEach { rawUri ->
            val uri = runCatching { Uri.parse(rawUri) }.getOrNull()
            if (uri == null || rawUri !in persistedReadUris) {
                pendingUris += rawUri
                return@forEach
            }

            val fileName = runCatching { queryDisplayName(context, uri) }.getOrNull()
            val format = fileName?.let(::formatFromFileName)
            if (fileName == null || format == null) {
                pendingUris += rawUri
                return@forEach
            }

            books += BookEntity(
                uri = uri.toString(),
                title = fileName.substringBeforeLast('.', fileName),
                format = format,
                importedAt = System.currentTimeMillis()
            )
        }
        books to pendingUris
    }

    viewModel.insertBooks(books)

    settings.apply(SettingsRepository.Store.LIBRARY) {
        if (pendingUris.isEmpty()) {
            putBoolean(KEY_LEGACY_MIGRATED, true)
            remove(KEY_DOCUMENT_URIS)
        } else {
            putStringSet(KEY_DOCUMENT_URIS, pendingUris)
        }
    }
}
