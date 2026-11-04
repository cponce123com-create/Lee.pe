package pe.lee.app.ui.library

import android.content.Context
import android.net.Uri
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.AudioProgressStore
import pe.lee.app.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException

/** Completes app-private cleanup recorded by the Room transaction that removed a book. */
internal object DeletedBookCleanup {
    suspend fun complete(
        context: Context,
        uri: String,
        settings: SettingsRepository,
        onComplete: suspend (String) -> Unit
    ): Boolean {
        return try {
            check(OfflineBookStore.deleteCopy(context, Uri.parse(uri)))
            check(AudioProgressStore.remove(context, uri))
            check(settings.removeBookScopedPreferences(uri))
            onComplete(uri)
            PersistableUriGrants.release(context, Uri.parse(uri))
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

}
