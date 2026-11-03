package pe.lee.app.ui.audio

import android.content.Context
import android.content.pm.PackageManager
import androidx.media3.session.MediaSession

/**
 * Gate for external media controllers.
 *
 * Lee.pe exposes [AudioPlaybackService] and [TtsPlaybackService] with an intent
 * filter so the system can render media controls and Android Auto can browse the
 * session. That also means any installed app can try to bind and become a
 * controller, which would let it pause playback or read the current book
 * metadata. Only our own app and components that hold
 * `android.permission.MEDIA_CONTENT_CONTROL` (SystemUI, Bluetooth, Android Auto
 * and trusted assistants) are allowed to obtain a session.
 */
internal object MediaSessionAccess {
    private const val MEDIA_CONTENT_CONTROL = "android.permission.MEDIA_CONTENT_CONTROL"

    fun isTrusted(context: Context, controller: MediaSession.ControllerInfo): Boolean {
        if (controller.packageName == context.packageName) return true
        return runCatching {
            context.packageManager.checkPermission(
                MEDIA_CONTENT_CONTROL,
                controller.packageName
            ) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /** Returns the session only for trusted controllers; `null` aborts their connection. */
    fun sessionFor(
        context: Context,
        controller: MediaSession.ControllerInfo,
        session: MediaSession?
    ): MediaSession? = session?.takeIf { isTrusted(context, controller) }
}
