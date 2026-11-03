package pe.lee.app.ui.reader

import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Publication
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.audio.TtsPlaybackService

internal class EpubReaderSessionState(uri: Uri, settings: SettingsRepository) {
    val publication = mutableStateOf<Publication?>(null)
    val navigator = mutableStateOf<EpubNavigatorFragment?>(null)
    val ttsNavigator = mutableStateOf<AndroidTtsNavigator?>(null)
    val ttsServiceBinding = mutableStateOf<TtsPlaybackService.ServiceBinding?>(null)
    val ttsIsPlaying = mutableStateOf(false)
    val ttsSentence = mutableStateOf("")
    val ttsError = mutableStateOf<String?>(null)
    val ttsSpeed = mutableFloatStateOf(settings.getFloat(SettingsRepository.Store.LIBRARY, ttsSpeedPreferenceKey(uri), 1f).coerceIn(0.5f, 2f))
    val ttsVoices = mutableStateOf<List<AndroidTtsEngine.Voice>>(emptyList())
    val ttsSelectedVoiceId = mutableStateOf(settings.getString(SettingsRepository.Store.LIBRARY, ttsVoicePreferenceKey(uri), null))
    val ttsTimerMinutes = mutableIntStateOf(0)
    val progress = mutableFloatStateOf(0f)
    val epubPreferences = mutableStateOf(EpubPreferences())
    val readerBrightness = mutableFloatStateOf(settings.getFloat(SettingsRepository.Store.LIBRARY, ReaderActivity.brightnessPreferenceKey(uri), -1f))
    val keepScreenOn = mutableStateOf(settings.getBoolean(SettingsRepository.Store.LIBRARY, ReaderActivity.keepScreenOnPreferenceKey(uri), false))
    val chromeVisible = mutableStateOf(false)
    val loading = mutableStateOf(true)
    val errorMessage = mutableStateOf<String?>(null)
}
