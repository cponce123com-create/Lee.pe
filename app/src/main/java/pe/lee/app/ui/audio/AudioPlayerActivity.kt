package pe.lee.app.ui.audio

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
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
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pe.lee.app.ui.theme.LeePeTheme
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.LeePeApplication
import pe.lee.app.data.repository.AudioProgressStore

class AudioPlayerActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)
    private val connectionError = mutableStateOf<String?>(null)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        val container = (application as LeePeApplication).container
        val audioViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(AudioPlayerViewModel::class.java)
        val playbackUri = intent.getStringExtra(OfflineBookStore.EXTRA_COPY_URI)?.let(Uri::parse) ?: uri
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Audiolibro"
        setContent {
            LeePeTheme {
                AudioPlayerScreen(
                    title = title,
                    controller = controllerState.value,
                    viewModel = audioViewModel,
                    error = connectionError.value,
                    onClose = { finish() }
                )
            }
        }
        requestNotificationPermissionIfNeeded()

        val token = SessionToken(this, ComponentName(this, AudioPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val controller = future.get()
                controllerState.value = controller
                audioViewModel.attach(controller)
                val mediaId = uri.toString()
                if (controller.currentMediaItem?.mediaId != mediaId) {
                    val item = MediaItem.Builder()
                        .setMediaId(mediaId)
                        .setUri(playbackUri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist("Lee.pe")
                                .build()
                        )
                        .build()
                    controller.setMediaItem(item, AudioProgressStore.get(this, mediaId))
                    controller.prepare()
                    controller.play()
                } else if (!controller.isPlaying) {
                    controller.play()
                }
            } catch (_: Exception) {
                connectionError.value = "No se pudo iniciar el reproductor. Vuelve a abrir el audiolibro."
                audioViewModel.showError(connectionError.value)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controllerState.value = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.audio.TITLE"
    }
}

@Composable
private fun AudioPlayerScreen(
    title: String,
    controller: MediaController?,
    viewModel: AudioPlayerViewModel,
    error: String?,
    onClose: () -> Unit
) {
    val playerState by viewModel.state.collectAsState()
    var sliderPosition by remember(controller) { mutableFloatStateOf(0f) }
    var scrubbing by remember(controller) { mutableStateOf(false) }
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    LaunchedEffect(playerState.positionMs, scrubbing) {
        if (!scrubbing) sliderPosition = playerState.positionMs.toFloat()
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
                if (controller == null && error == null) {
                    Text("Conectando el reproductor…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
                (playerState.error ?: error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(
                    value = sliderPosition.coerceAtMost(playerState.durationMs.toFloat()),
                    onValueChange = { scrubbing = true; sliderPosition = it },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderPosition.toLong().coerceIn(0L, playerState.durationMs.coerceAtLeast(0L)))
                        scrubbing = false
                    },
                    valueRange = 0f..playerState.durationMs.coerceAtLeast(1L).toFloat(),
                    enabled = controller != null && playerState.durationMs > 0L
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(if (scrubbing) sliderPosition.toLong() else playerState.positionMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("-${formatTime((playerState.durationMs - (if (scrubbing) sliderPosition.toLong() else playerState.positionMs)).coerceAtLeast(0L))}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = controller != null, onClick = {
                        viewModel.seekTo((playerState.positionMs - 15_000L).coerceAtLeast(0L))
                    }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Retroceder 15 segundos", modifier = Modifier.size(32.dp))
                    }
                    IconButton(
                        enabled = controller != null,
                        onClick = { viewModel.playPause() },
                        modifier = Modifier.padding(horizontal = 22.dp).size(76.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            if (playerState.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.playing) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(enabled = controller != null, onClick = {
                        val target = playerState.positionMs + 15_000L
                        viewModel.seekTo(if (playerState.durationMs > 0L) target.coerceAtMost(playerState.durationMs) else target)
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Avanzar 15 segundos", modifier = Modifier.size(32.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocidad", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(enabled = controller != null, onClick = {
                    val index = speeds.indexOf(playerState.speed).coerceAtLeast(0)
                    viewModel.setSpeed(speeds[(index + 1) % speeds.size])
                    }) {
                        Text("${playerState.speed}x", fontWeight = FontWeight.Bold)
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
