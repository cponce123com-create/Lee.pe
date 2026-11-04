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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.readium.navigator.media.common.Media3Adapter
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import pe.lee.app.LeePeApplication
import pe.lee.app.R
import pe.lee.app.ui.reader.ReaderActivity
import pe.lee.core.domain.readingProgressOr

@OptIn(ExperimentalReadiumApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TtsPlaybackService : MediaSessionService() {
    class Session(
        val navigator: AndroidTtsNavigator,
        val publication: Publication,
        val mediaSession: MediaSession,
        val bookUri: String,
        val title: String,
        val onPublicationClosed: () -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var sleepTimerJob: Job? = null
        val sleepTimerMinutes = MutableStateFlow(0)
    }

    inner class ReaderBinder : Binder() {
        private val sessionState = MutableStateFlow<Session?>(null)
        val session: StateFlow<Session?> = sessionState.asStateFlow()
        val currentSession: Session? get() = sessionState.value

        fun openSession(
            navigator: AndroidTtsNavigator,
            publication: Publication,
            bookUri: String,
            title: String,
            onPublicationClosed: () -> Unit,
        ) {
            closeSession()
            val launchIntent = Intent(this@TtsPlaybackService, ReaderActivity::class.java)
                .setData(android.net.Uri.parse(bookUri))
                .putExtra(ReaderActivity.EXTRA_TITLE, title)
            val pendingIntent = PendingIntent.getActivity(
                this@TtsPlaybackService,
                bookUri.hashCode(),
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val session = MediaSession.Builder(
                this@TtsPlaybackService,
                (navigator as Media3Adapter).asMedia3Player(),
            )
                .setSessionActivity(pendingIntent)
                .setId(bookUri.hashCode().toString())
                .build()
            addSession(session)
            val activeSession = Session(navigator, publication, session, bookUri, title, onPublicationClosed)
            sessionState.value = activeSession
            activeBookUri = bookUri

            activeSession.scope.launch {
                var lastSavedAt = 0L
                val container = (application as LeePeApplication).container
                // Books without a page list report a null progression; keep the last
                // known value instead of resetting the user's progress to 0 %.
                var knownProgress = runCatching { container.books.findByUri(bookUri)?.readProgress }
                    .getOrNull() ?: 0f
                navigator.currentLocator.collect { locator ->
                    val now = System.currentTimeMillis()
                    if (now - lastSavedAt >= 3_000L) {
                        val progression = readingProgressOr(
                            locator.locations.totalProgression,
                            knownProgress,
                        )
                        knownProgress = progression
                        container.books.saveEpubProgress(
                            uri = bookUri,
                            locator = locator.toJSON().toString(),
                            progress = progression,
                            at = now,
                        )
                        lastSavedAt = now
                    }
                }
            }
            activeSession.scope.launch {
                navigator.playback.collect { playback ->
                    val shouldHoldWakeLock = playback.playWhenReady &&
                        playback.state !is TtsNavigator.State.Ended &&
                        playback.state !is TtsNavigator.State.Failure
                    updateWakeLock(shouldHoldWakeLock)
                    if (playback.state is TtsNavigator.State.Ended || playback.state is TtsNavigator.State.Failure) {
                        if (sessionState.value === activeSession) closeSession()
                    }
                }
            }
        }

        fun setSleepTimer(minutes: Int) {
            val activeSession = sessionState.value ?: return
            val safeMinutes = minutes.coerceIn(0, 60)
            activeSession.sleepTimerJob?.cancel()
            activeSession.sleepTimerMinutes.value = safeMinutes
            activeSession.sleepTimerJob = if (safeMinutes > 0) {
                activeSession.scope.launch {
                    delay(safeMinutes * 60_000L)
                    if (sessionState.value === activeSession) {
                        activeSession.navigator.pause()
                        activeSession.sleepTimerMinutes.value = 0
                    }
                }
            } else {
                null
            }
        }

        fun closeSession() {
            val activeSession = sessionState.value ?: return
            sessionState.value = null
            activeBookUri = null
            activeSession.sleepTimerJob?.cancel()
            activeSession.sleepTimerMinutes.value = 0
            activeSession.scope.cancel()
            activeSession.mediaSession.release()
            try {
                activeSession.navigator.close()
            } finally {
                try {
                    activeSession.publication.close()
                } finally {
                    activeSession.onPublicationClosed()
                }
            }
            updateWakeLock(false)
        }

        fun stop() {
            closeSession()
            ServiceCompat.stopForeground(this@TtsPlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            this@TtsPlaybackService.stopSelf()
        }
    }

    class ServiceBinding internal constructor(
        val binder: TtsPlaybackService.ReaderBinder,
        private val application: Application,
        private val connection: ServiceConnection,
    ) {
        private var isBound = true

        fun unbind() {
            if (!isBound) return
            isBound = false
            runCatching { application.unbindService(connection) }
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

    override fun onBind(intent: Intent?): IBinder? = if (intent?.action == SERVICE_INTERFACE) {
        // This action returns a plain local Binder used by Lee.pe's reader.
        // It does not expose an AIDL or Messenger interface to remote callers.
        super.onBind(intent)
        binder
    } else {
        super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        MediaSessionAccess.sessionFor(this, controllerInfo, binder.currentSession?.mediaSession)

    override fun onDestroy() {
        binder.closeSession()
        updateWakeLock(false)
        wakeLock = null
        super.onDestroy()
    }

    private fun updateWakeLock(shouldHold: Boolean) {
        val lock = wakeLock ?: return
        if (shouldHold && !lock.isHeld) {
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else if (!shouldHold && lock.isHeld) {
            lock.release()
        }
    }

    companion object {
        const val SERVICE_INTERFACE = "pe.lee.app.ui.audio.TtsPlaybackService"

        /**
         * Book currently being narrated, if any. The reader reads it after a
         * rotation to re-attach its TTS panel to a session that kept playing.
         */
        @Volatile
        var activeBookUri: String? = null
            private set

        /** Safety net: a stuck session must never keep the CPU awake forever. */
        private const val WAKE_LOCK_TIMEOUT_MS = 30L * 60L * 1000L

        private fun serviceIntent(application: Application) =
            Intent(SERVICE_INTERFACE).setClass(application, TtsPlaybackService::class.java)

        /**
         * Binds only. Media3 promotes the service to foreground as soon as the
         * narration session becomes active, which also keeps it alive after the
         * reader unbinds. Calling `startService` from a background context would
         * throw `IllegalStateException` on Android 8+ (A-14), and a plain
         * `startForegroundService` without a ready notification risks the 5 s ANR.
         */
        suspend fun bind(application: Application): ServiceBinding {
            val result = CompletableDeferred<ReaderBinder>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                    result.complete(service as ReaderBinder)
                }

                override fun onServiceDisconnected(name: ComponentName?) = Unit

                override fun onNullBinding(name: ComponentName?) {
                    result.completeExceptionally(IllegalStateException(application.getString(R.string.tts_service_connection_failed)))
                }
            }
            val intent = serviceIntent(application)
            if (!application.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                throw IllegalStateException(application.getString(R.string.tts_service_start_failed))
            }
            return try {
                ServiceBinding(result.await(), application, connection)
            } catch (cancelled: CancellationException) {
                runCatching { application.unbindService(connection) }
                throw cancelled
            } catch (error: Exception) {
                runCatching { application.unbindService(connection) }
                throw error
            }
        }
    }
}
