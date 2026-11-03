package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import pe.lee.app.data.local.BookDao
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.ui.audio.AudioProgressStore
import kotlinx.coroutines.CancellationException

/** Completes app-private cleanup recorded by the Room transaction that removed a book. */
internal object DeletedBookCleanup {
    private const val LIBRARY_PREFERENCES = "lee_pe_library"

    suspend fun complete(context: Context, dao: BookDao, uri: String): Boolean {
        return try {
            check(OfflineBookStore.deleteCopy(context, Uri.parse(uri)))
            check(AudioProgressStore.remove(context, uri))
            check(clearBookPreferences(context, uri))
            dao.completeBookCleanup(uri)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    private fun clearBookPreferences(context: Context, uri: String): Boolean {
        val preferences = context.getSharedPreferences(LIBRARY_PREFERENCES, Context.MODE_PRIVATE)
        val keys = preferences.all.keys.filter { it.endsWith(":$uri") }
        if (keys.isEmpty()) return true
        return preferences.edit().also { editor -> keys.forEach(editor::remove) }.commit()
    }
}
