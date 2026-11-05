package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import pe.lee.core.domain.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.repository.SettingsRepository

/**
 * Copies the bundled Salgari EPUB into app-private storage once
 * and adds it to Room. The persisted flag prevents it from reappearing if deleted.
 *
 * The book is exposed through a [FileProvider] (`content://`), not `file://`, so a
 * future share/open action cannot throw `FileUriExposedException` and the backup
 * never exports a private path (A-17).
 */
internal object BundledWelcomeBook {
    private const val ASSET_NAME = "el-tesoro-de-los-incas.epub"
    private const val SEEDED_KEY = "treasure_book_added"

    suspend fun installIfNeeded(
        context: Context,
        settings: SettingsRepository,
        findBook: suspend (String) -> BookEntity?,
        insertBook: suspend (BookEntity) -> Unit,
        rebindBook: suspend (String, String) -> Boolean,
    ): Boolean {
        val appContext = context.applicationContext
        val alreadySeeded = settings.getBoolean(SettingsRepository.Store.BUNDLED_BOOK, SEEDED_KEY, false)

        return try {
            withContext(Dispatchers.IO) {
                val directory = File(appContext.filesDir, "bundled_books")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw StorageException(StorageException.Reason.DIRECTORY_UNAVAILABLE)
                }
                val bookFile = File(directory, ASSET_NAME)
                if (!bookFile.exists() || bookFile.length() == 0L) {
                    val temporary = File(directory, "$ASSET_NAME.part")
                    appContext.assets.open(ASSET_NAME).use { input ->
                        temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                    }
                    if (bookFile.exists()) bookFile.delete()
                    if (!temporary.renameTo(bookFile)) {
                        temporary.delete()
                        throw StorageException(StorageException.Reason.FINALIZATION_FAILED)
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.bundled",
                    bookFile,
                ).toString()
                val legacyFileUri = Uri.fromFile(bookFile).toString()

                if (alreadySeeded) {
                    // Installations seeded before this change still point at the
                    // private file path; rebind them to the content URI.
                    val legacy = findBook(legacyFileUri)
                    if (legacy != null) {
                        runCatching { rebindBook(legacy.id, contentUri) }
                    }
                    return@withContext
                }

                if (findBook(contentUri) == null && findBook(legacyFileUri) == null) {
                    insertBook(
                        BookEntity(
                            uri = contentUri,
                            title = "El tesoro de los Incas — Emilio Salgari",
                            format = "EPUB",
                            importedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            settings.apply(SettingsRepository.Store.BUNDLED_BOOK) { putBoolean(SEEDED_KEY, true) }
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}
