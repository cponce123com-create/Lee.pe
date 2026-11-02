package pe.lee.app.ui.audio

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.readium.navigator.media.common.Media3Adapter
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.ui.reader.ReaderActivity

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TtsPlaybackService : MediaSessionService() {
    class Session(
        val navigator: AndroidTtsNavigator,
        val mediaSession: MediaSession,
        val bookUri: String,
        val title: String
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    inner class ReaderBinder : Binder() {
        private val sessionState = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = sessionState.asStateFlow()
        val currentSession: Session? get() = sessionState.value

        fun openSession(navigator: AndroidTtsNavigator, bookUri: String, title: String) {
            closeSession()
            val launchIntent = Intent(this@TtsPlaybackService, ReaderActivity::class.java)
                .setData(android.net.Uri.parse(bookUri))
                .putExtra(ReaderActivity.EXTRA_TITLE, title)
            val pendingIntent = PendingIntent.getActivity(
                this@TtsPlaybackService,
                bookUri.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val session = MediaSession.Builder(
                this@TtsPlaybackService,
                (navigator as Media3Adapter).asMedia3Player()
            )
                .setSessionActivity(pendingIntent)
                .setId(bookUri.hashCode().toString())
                .build()
            addSession(session)
            val activeSession = Session(navigator, session, bookUri, title)
            sessionState.value = activeSession

            activeSession.scope.launch {
                navigator.currentLocator.collect { locator ->
                    val progression = locator.locations.totalProgression?.toFloat() ?: 0f
                    LeeDatabase.get(this@TtsPlaybackService).bookDao().saveEpubProgress(
                        uri = bookUri,
                        locatorJson = locator.toJSON().toString(),
                        progress = progression,
                        openedAt = System.currentTimeMillis()
                    )
                }
            }
            activeSession.scope.launch {
                navigator.playback.collect { playback ->
                    updateWakeLock(playback.playWhenReady)
                }
            }
        }

        fun closeSession() {
            val activeSession = sessionState.value ?: return
            sessionState.value = null
            activeSession.scope.cancel()
            activeSession.mediaSession.release()
            activeSession.navigator.close()
            updateWakeLock(false)
        }

        fun stop() {
            closeSession()
            ServiceCompat.stopForeground(this@TtsPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            this@TtsPlaybackService.stopSelf()
        }
    }

    private val binder by lazy { ReaderBinder() }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:epub-tts")
            .apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
            binder
        } else {
            super.onBind(intent)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        binder.currentSession?.mediaSession

    override fun onDestroy() {
        binder.closeSession()
        updateWakeLock(false)
        wakeLock = null
        super.onDestroy()
    }

    private fun updateWakeLock(shouldHold: Boolean) {
        val lock = wakeLock ?: return
        if (shouldHold && !lock.isHeld) lock.acquire()
        else if (!shouldHold && lock.isHeld) lock.release()
    }

    companion object {
        const val SERVICE_INTERFACE = "pe.lee.app.ui.audio.TtsPlaybackService"

        private fun serviceIntent(application: Application) =
            Intent(SERVICE_INTERFACE).setClass(application, TtsPlaybackService::class.java)

        fun start(application: Application) {
            application.startService(serviceIntent(application))
        }

        suspend fun bind(application: Application): ReaderBinder {
            start(application)
            val result = CompletableDeferred<ReaderBinder>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    result.complete(service as ReaderBinder)
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit

                override fun onNullBinding(name: ComponentName?) {
                    result.completeExceptionally(IllegalStateException("No se pudo conectar el servicio de voz."))
                }
            }
            val intent = serviceIntent(application)
            if (!application.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                throw IllegalStateException("No se pudo iniciar el servicio de voz.")
            }
            return result.await()
        }
    }
}
