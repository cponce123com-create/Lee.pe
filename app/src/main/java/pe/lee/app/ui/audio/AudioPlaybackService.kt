package pe.lee.app.ui.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pe.lee.app.MainActivity
import pe.lee.app.data.repository.AudioProgressStore

class AudioPlaybackService : MediaSessionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var lastSavedMediaId: String? = null
    private var lastSavedPositionMs: Long = -1L

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).setWakeMode(C.WAKE_MODE_LOCAL).build()
        exoPlayer.setAudioAttributes(AudioAttributes.DEFAULT, true)
        player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition(exoPlayer, force = true)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    savePosition(exoPlayer, force = true)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                savePosition(exoPlayer, force = true)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                lastSavedMediaId = null
                lastSavedPositionMs = -1L
                savePosition(exoPlayer, force = true)
            }
        })

        val activityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(activityIntent)
            .build()

        serviceScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) savePosition(exoPlayer, force = false)
                delay(PROGRESS_SAVE_INTERVAL_MS)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        MediaSessionAccess.sessionFor(this, controllerInfo, mediaSession)

    override fun onDestroy() {
        player?.let { savePosition(it, force = true) }
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun savePosition(player: Player, force: Boolean) {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition
        val sameItem = lastSavedMediaId == mediaId
        if (!force && sameItem && position - lastSavedPositionMs < PROGRESS_SAVE_INTERVAL_MS) return
        AudioProgressStore.put(this, mediaId, position)
        lastSavedMediaId = mediaId
        lastSavedPositionMs = position
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
