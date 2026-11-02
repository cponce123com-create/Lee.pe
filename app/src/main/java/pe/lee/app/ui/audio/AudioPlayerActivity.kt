package pe.lee.app.ui.audio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pe.lee.app.ui.theme.LeePeTheme

class AudioPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Audiolibro"
        setContent {
            LeePeTheme {
                AudioPlayerScreen(title, exoPlayer) { finish() }
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.audio.TITLE"
    }
}

@Composable
private fun AudioPlayerScreen(title: String, player: ExoPlayer, onClose: () -> Unit) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableStateOf(1f) }
    var error by remember { mutableStateOf<String?>(null) }
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) {
                duration = player.duration.takeIf { it > 0L } ?: 0L
            }
            override fun onPlayerError(playbackException: PlaybackException) {
                error = "No se pudo reproducir este archivo. Comprueba que no esté dañado o protegido."
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0L } ?: 0L
            delay(500)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                }
                Text("Reproductor", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(224.dp).clip(RoundedCornerShape(34.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF702EFF), Color(0xFF087FAF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(100.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("Audiolibro local · disponible sin conexión", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = position.coerceAtMost(duration).toFloat(),
                    onValueChange = { position = it.toLong() },
                    onValueChangeFinished = { player.seekTo(position.coerceIn(0L, duration.coerceAtLeast(0L))) },
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    enabled = duration > 0L
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("-${formatTime((duration - position).coerceAtLeast(0L))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 15_000L).coerceAtLeast(0L)) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Retroceder 15 segundos", modifier = Modifier.size(32.dp))
                    }
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.padding(horizontal = 22.dp).size(76.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = {
                        player.seekTo((player.currentPosition + 15_000L).coerceAtMost(duration.coerceAtLeast(0L)))
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Avanzar 15 segundos", modifier = Modifier.size(32.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocidad", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        val index = speeds.indexOf(speed).coerceAtLeast(0)
                        speed = speeds[(index + 1) % speeds.size]
                        player.playbackParameters = PlaybackParameters(speed)
                    }) {
                        Text("${speed}x", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainder = seconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}
