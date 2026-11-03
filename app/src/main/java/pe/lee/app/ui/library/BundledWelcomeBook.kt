package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.BookEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Copies the bundled Salgari EPUB into app-private storage once
 * and adds it to Room. The persisted flag prevents it from reappearing if deleted.
 */
internal object BundledWelcomeBook {
    private const val ASSET_NAME = "el-tesoro-de-los-incas.epub"
    private const val PREFS_NAME = "lee_pe_bundled_book"
    private const val SEEDED_KEY = "treasure_book_added"

    suspend fun installIfNeeded(context: Context, dao: BookDao): Boolean {
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(SEEDED_KEY, false)) return true

        return try {
            withContext(Dispatchers.IO) {
                val directory = File(appContext.filesDir, "bundled_books")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IOException("No se pudo crear la carpeta del libro incluido.")
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
                        throw IOException("No se pudo guardar El tesoro de los Incas.")
                    }
                }

                val bookUri = Uri.fromFile(bookFile).toString()
                if (dao.findByUri(bookUri) == null) {
                    dao.insert(
                        BookEntity(
                            uri = bookUri,
                            title = "El tesoro de los Incas — Emilio Salgari",
                            format = "EPUB",
                            importedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
            preferences.edit().putBoolean(SEEDED_KEY, true).apply()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }
}
