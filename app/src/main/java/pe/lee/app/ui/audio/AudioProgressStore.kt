package pe.lee.app.ui.audio

import android.content.Context
import pe.lee.app.data.repository.SettingsRepository
import java.security.MessageDigest

internal object AudioProgressStore {
    private val store = SettingsRepository.Store.AUDIO_PROGRESS

    fun get(context: Context, mediaId: String): Long =
        SettingsRepository(context).getLong(store, key(mediaId), 0L)
            .coerceAtLeast(0L)

    fun put(context: Context, mediaId: String, positionMs: Long) {
        SettingsRepository(context).apply(store) {
            putLong(key(mediaId), positionMs.coerceAtLeast(0L))
        }
    }

    fun remove(context: Context, mediaId: String): Boolean =
        SettingsRepository(context).update(store) { remove(key(mediaId)) }

    private fun key(mediaId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(mediaId.toByteArray(Charsets.UTF_8))
        return "position_" + digest.joinToString("") { "%02x".format(it) }
    }
}
