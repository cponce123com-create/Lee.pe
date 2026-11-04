package pe.lee.app.ui.audio

import android.content.Context
import androidx.media3.session.MediaSession

/**
 * Gate for external media controllers.
 *
 * Lee.pe exposes media sessions for system playback controls. Media3's trust
 * classification gates external controllers. Android Auto, Wear OS and
 * Assistant compatibility is not verified.
 */
internal object MediaSessionAccess {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun isTrusted(context: Context, controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == context.packageName || controller.isTrusted

    /** Returns the session only for trusted controllers; `null` aborts their connection. */
    fun sessionFor(context: Context, controller: MediaSession.ControllerInfo, session: MediaSession?): MediaSession? =
        session?.takeIf { isTrusted(context, controller) }
}
