package pe.lee.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val speed: Float = 1f,
    val error: String? = null
)

class AudioPlayerViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = mutableState.asStateFlow()
    private var controller: MediaController? = null
    private var listener: Player.Listener? = null
    private var positionJob: Job? = null

    fun attach(controller: MediaController?) {
        positionJob?.cancel()
        positionJob = null
        listener?.let { this.controller?.removeListener(it) }
        listener = null
        this.controller = controller
        val player = controller ?: run { mutableState.value = AudioPlayerState(); return }
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.value = mutableState.value.copy(playing = isPlaying)
            }
            override fun onPlaybackStateChanged(playbackState: Int) { refreshPosition() }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                mutableState.value = mutableState.value.copy(speed = playbackParameters.speed)
            }
            override fun onPlayerError(playbackException: PlaybackException) {
                mutableState.value = mutableState.value.copy(
                    error = "No se pudo reproducir este archivo. Comprueba que no esté dañado o protegido."
                )
            }
        }
        listener = playerListener
        player.addListener(playerListener)
        mutableState.value = mutableState.value.copy(
            playing = player.isPlaying,
            speed = player.playbackParameters.speed,
            error = null
        )
        positionJob = viewModelScope.launch {
            while (isActive && controller === player) {
                refreshPosition()
                delay(500)
            }
        }
    }

    fun showError(message: String?) {
        mutableState.value = mutableState.value.copy(error = message)
    }

    fun playPause() { if (controller?.isPlaying == true) controller?.pause() else controller?.play() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)); refreshPosition() }
    fun setSpeed(speed: Float) {
        controller?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        mutableState.value = mutableState.value.copy(speed = speed)
    }

    private fun refreshPosition() {
        val player = controller ?: return
        val nextState = mutableState.value.copy(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L
        )
        if (nextState != mutableState.value) mutableState.value = nextState
    }

    override fun onCleared() {
        positionJob?.cancel()
        positionJob = null
        listener?.let { controller?.removeListener(it) }
        listener = null
        controller = null
    }
}
