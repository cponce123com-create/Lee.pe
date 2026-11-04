package pe.lee.app.ui.reader

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.getOrElse
import pe.lee.app.R
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.audio.TtsPlaybackService

internal data class EpubReaderSessionActions(
    val stopTts: () -> Unit,
    val toggleTts: () -> Unit,
    /**
     * Stops narration before an explicit jump. Otherwise the TTS location
     * collector moves the page back to where it was speaking (A-32).
     */
    val pauseTtsForNavigation: () -> Unit,
)

@OptIn(ExperimentalReadiumApi::class)
internal fun buildEpubReaderSessionActions(
    scope: CoroutineScope,
    context: Context,
    uri: Uri,
    title: String,
    settings: SettingsRepository,
    session: EpubReaderSessionState,
    onRequestNotificationPermission: () -> Unit,
): EpubReaderSessionActions = createEpubReaderSessionActions(
    scope,
    context,
    uri,
    title,
    settings,
    session,
    onRequestNotificationPermission,
)

@OptIn(ExperimentalReadiumApi::class)
private fun createEpubReaderSessionActions(
    coroutineScope: CoroutineScope,
    context: Context,
    uri: Uri,
    title: String,
    settings: SettingsRepository,
    session: EpubReaderSessionState,
    onRequestNotificationPermission: () -> Unit,
): EpubReaderSessionActions {
    fun stopTts() {
        val binding = session.ttsServiceBinding.value
        if (binding != null) {
            binding.binder.stop()
            binding.unbind()
        } else {
            session.ttsNavigator.value?.close()
        }
        session.ttsServiceBinding.value = null
        session.ttsNavigator.value = null
        session.ttsIsPlaying.value = false
        session.ttsSentence.value = ""
        session.ttsTimerMinutes.value = 0
        coroutineScope.launch {
            session.navigator.value?.applyDecorations(emptyList(), TTS_DECORATION_GROUP)
        }
    }

    fun startOrToggleTts() {
        val current = session.ttsNavigator.value
        if (current != null) {
            if (session.ttsIsPlaying.value) current.pause() else current.play()
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            onRequestNotificationPermission()
        }
        coroutineScope.launch {
            val app = context.applicationContext as Application
            val binding = session.ttsServiceBinding.value ?: try {
                TtsPlaybackService.bind(app).also { session.ttsServiceBinding.value = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                session.ttsError.value = context.getString(R.string.tts_background_start_failed)
                return@launch
            }
            val binder = binding.binder
            val existing = binder.currentSession
            if (existing?.bookUri == uri.toString()) {
                session.ttsNavigator.value = existing.navigator
                session.ttsVoices.value = existing.navigator.voices.filter { it.isLocalSpanishVoice() }
                    .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
                if (!existing.navigator.playback.value.playWhenReady) existing.navigator.play()
                return@launch
            }
            val activePublication = session.publication.value
            if (activePublication == null) {
                session.ttsError.value = context.getString(R.string.epub_preparing)
                return@launch
            }
            val factory = AndroidTtsNavigatorFactory(
                context.applicationContext as Application,
                activePublication,
            )
            if (factory == null) {
                session.ttsError.value = context.getString(R.string.tts_not_supported)
                return@launch
            }
            val speech = factory.createNavigator(
                listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() {
                        coroutineScope.launch {
                            binder.stop()
                            binding.unbind()
                            if (session.ttsServiceBinding.value === binding) session.ttsServiceBinding.value = null
                        }
                    }
                },
                initialLocator = (session.navigator.value as? VisualNavigator)?.firstVisibleElementLocator()
                    ?: session.navigator.value?.currentLocator?.value,
                initialPreferences = AndroidTtsPreferences(speed = session.ttsSpeed.value.toDouble()),
            ).getOrElse { failure ->
                session.ttsError.value = context.getString(R.string.tts_prepare_failed)
                return@launch
            }
            val localVoices = speech.voices
                .filter { it.isLocalSpanishVoice() }
                .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
            session.ttsVoices.value = localVoices
            val savedVoice = session.ttsSelectedVoiceId.value?.let { id ->
                localVoices.firstOrNull { it.id.value == id }
            }
            if (session.ttsSelectedVoiceId.value != null && savedVoice == null) {
                session.ttsSelectedVoiceId.value = null
                settings.apply(SettingsRepository.Store.LIBRARY) { remove(ttsVoicePreferenceKey(uri)) }
            }
            if (savedVoice != null) {
                speech.submitPreferences(
                    AndroidTtsPreferences(
                        speed = session.ttsSpeed.value.toDouble(),
                        voices = mapOf(savedVoice.language to savedVoice.id),
                    ),
                )
            }
            val publication = session.publication.value ?: run {
                speech.close()
                session.ttsError.value = context.getString(R.string.tts_publication_unavailable)
                return@launch
            }
            binder.openSession(speech, publication, uri.toString(), title) {
                if (session.publication.value === publication) session.publication.value = null
            }
            session.ttsError.value = null
            session.ttsNavigator.value = speech
            speech.play()
        }
    }

    fun pauseTtsForNavigation() {
        val current = session.ttsNavigator.value ?: return
        if (session.ttsIsPlaying.value) current.pause()
    }

    return EpubReaderSessionActions(::stopTts, ::startOrToggleTts, ::pauseTtsForNavigation)
}
