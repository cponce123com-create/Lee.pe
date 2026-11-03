package pe.lee.app.ui.audio

import android.content.Context
import java.security.MessageDigest

internal object AudioProgressStore {
    private const val PREFS = "lee_pe_audio_progress"

    fun get(context: Context, mediaId: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key(mediaId), 0L)
            .coerceAtLeast(0L)

    fun put(context: Context, mediaId: String, positionMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(key(mediaId), positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun remove(context: Context, mediaId: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(mediaId))
            .commit()

    private fun key(mediaId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(mediaId.toByteArray(Charsets.UTF_8))
        return "position_" + digest.joinToString("") { "%02x".format(it) }
    }
}
