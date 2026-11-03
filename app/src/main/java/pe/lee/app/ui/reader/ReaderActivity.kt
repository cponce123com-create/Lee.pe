package pe.lee.app.ui.reader

import android.Manifest
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import pe.lee.app.R
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.core.domain.ReaderAnnotation
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import java.util.UUID


@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubReaderScreen(
    uri: Uri,
    title: String,
    viewModel: EpubReaderViewModel,
    settings: SettingsRepository,
    openPublication: suspend () -> Publication,
    onReadingVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val annotations by viewModel.annotationState.collectAsStateWithLifecycle()
    val activity = context as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val session = remember(uri) { EpubReaderSessionState(uri, settings) }
    // Back hides the reading chrome first; a second back leaves the book.
    BackHandler(enabled = session.chromeVisible.value) {
        session.chromeVisible.value = false
    }
    var publication by session.publication
    var navigator by session.navigator
    var ttsNavigator by session.ttsNavigator
    var ttsServiceBinding by session.ttsServiceBinding
    var ttsIsPlaying by session.ttsIsPlaying
    var ttsSentence by session.ttsSentence
    var ttsError by session.ttsError
    var ttsSpeed by session.ttsSpeed
    var ttsVoices by session.ttsVoices
    var ttsSelectedVoiceId by session.ttsSelectedVoiceId
    var ttsTimerMinutes by session.ttsTimerMinutes
    var progress by session.progress
    var epubPreferences by session.epubPreferences
    var readerBrightness by session.readerBrightness
    var keepScreenOn by session.keepScreenOn
    var chromeVisible by session.chromeVisible
    var loading by session.loading
    var errorMessage by session.errorMessage
    var seekJob by remember { mutableStateOf<Job?>(null) }
    // Large books used to re-flatten the whole table of contents on every
    // recomposition (each page turn and each narrated sentence) (A-30).
    val tableOfContents = remember(publication) {
        publication?.let { flattenContents(it.tableOfContents) }.orEmpty()
    }

    // Reading time must not accumulate while the book is loading or failed.
    LaunchedEffect(loading, errorMessage) {
        onReadingVisibilityChange(!loading && errorMessage == null)
    }

    val sessionActions = EpubReaderSessionEffects(
        context = context,
        activity = activity,
        uri = uri,
        title = title,
        viewModel = viewModel,
        settings = settings,
        containerId = containerId,
        annotations = annotations,
        session = session,
        openPublication = openPublication,
        onRequestNotificationPermission = {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F))
    ) {
        AndroidView(
            factory = { FrameLayout(it).apply { id = containerId } },
            modifier = Modifier.fillMaxSize()
        )

        if (loading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                Text(stringResource(R.string.epub_loading), color = Color.White)
            }
        }

        errorMessage?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.epub_open_failed), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(message, color = Color(0xFFD1D8E5))
                Button(onClick = onBack) { Text(stringResource(R.string.epub_back_to_library)) }
            }
        }

        if (!loading && errorMessage == null) {
            if (chromeVisible) ReaderChrome(
                title = title,
                ttsIsActive = ttsNavigator != null,
                ttsIsPlaying = ttsIsPlaying,
                onToggleTts = { sessionActions.toggleTts() },
                progress = progress,
                annotations = annotations,
                onAddBookmark = {
                    navigator?.currentLocator?.value?.let { locator ->
                        val now = System.currentTimeMillis()
                        coroutineScope.launch {
                            viewModel.saveAnnotation(
                                ReaderAnnotation(
                                    id = UUID.randomUUID().toString(),
                                    bookUri = uri.toString(),
                                    kind = "BOOKMARK",
                                    locatorJson = locator.toJSON().toString(),
                                    createdAt = now
                                )
                            )
                        }
                    }
                },
                onAddNote = { note ->
                    navigator?.currentLocator?.value?.let { locator ->
                        val now = System.currentTimeMillis()
                        coroutineScope.launch {
                            viewModel.saveAnnotation(
                                ReaderAnnotation(
                                    id = UUID.randomUUID().toString(),
                                    bookUri = uri.toString(),
                                    kind = "NOTE",
                                    locatorJson = locator.toJSON().toString(),
                                    noteText = note,
                                    createdAt = now
                                )
                            )
                        }
                    }
                },
                onChangeHighlightColor = { annotation, color ->
                    coroutineScope.launch {
                        viewModel.updateAnnotationColor(annotation.id, color, System.currentTimeMillis())
                    }
                },
                onUpdateAnnotation = { annotation, note ->
                    coroutineScope.launch {
                        viewModel.updateAnnotationNote(annotation.id, note, System.currentTimeMillis())
                    }
                },
                onSelectAnnotation = { annotation ->
                    runCatching { Locator.fromJSON(JSONObject(annotation.locatorJson)) }
                        .getOrNull()?.let { navigator?.go(it) }
                },
                onDeleteAnnotation = { annotation ->
                    coroutineScope.launch { viewModel.deleteAnnotation(annotation.id) }
                },
                preferences = epubPreferences,
                brightness = readerBrightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = { enabled ->
                    keepScreenOn = enabled
                    settings.apply(SettingsRepository.Store.LIBRARY) {
                        putBoolean(ReaderActivity.keepScreenOnPreferenceKey(uri), enabled)
                    }
                },
                onBrightnessChange = { brightness ->
                    readerBrightness = brightness
                    settings.apply(SettingsRepository.Store.LIBRARY) {
                        putFloat(ReaderActivity.brightnessPreferenceKey(uri), brightness)
                    }
                },
                onPreferencesChange = { updated ->
                    epubPreferences = updated
                    navigator?.submitPreferences(updated)
                    val serialized = runCatching {
                        EpubPreferencesSerializer().serialize(updated)
                    }.getOrNull()
                    if (serialized != null) {
                        settings.apply(SettingsRepository.Store.LIBRARY) {
                            putString(ReaderActivity.preferencesPreferenceKey(uri), serialized)
                        }
                    }
                },
                onToggleChrome = { chromeVisible = false },
                onBack = onBack,
                onPrevious = { navigator?.goBackward(animated = true) },
                onNext = { navigator?.goForward(animated = true) },
                contents = tableOfContents,
                searchPublication = publication,
                onSelectContent = { link -> navigator?.go(link, animated = true) },
                onSelectSearchResult = { locator -> navigator?.go(locator, animated = true) },
                onSeek = { progression ->
                    // Narration would drag the page back to the spoken sentence.
                    sessionActions.pauseTtsForNavigation()
                    seekJob?.cancel()
                    seekJob = coroutineScope.launch {
                        val locator = withContext(Dispatchers.IO) {
                            publication?.locateProgression(progression.toDouble())
                        }
                        if (locator != null) navigator?.go(locator)
                    }
                },
                onProgressChange = { progress = it }
            )

            if (ttsNavigator != null) {
                TtsControls(
                    sentence = ttsSentence,
                    isPlaying = ttsIsPlaying,
                    speed = ttsSpeed,
                    voices = ttsVoices,
                    selectedVoiceId = ttsSelectedVoiceId,
                    timerMinutes = ttsTimerMinutes,
                    error = ttsError,
                    onTogglePlayback = { sessionActions.toggleTts() },
                    onSpeedChange = { speed ->
                        ttsSpeed = speed
                        val selectedVoice = ttsVoices.firstOrNull { it.id.value == ttsSelectedVoiceId }
                        ttsNavigator?.submitPreferences(
                            AndroidTtsPreferences(
                                speed = speed.toDouble(),
                                voices = selectedVoice?.let { mapOf(it.language to it.id) }
                                    ?: if (ttsSelectedVoiceId == null) null else emptyMap()
                            )
                        )
                        settings.apply(SettingsRepository.Store.LIBRARY) {
                            putFloat(ttsSpeedPreferenceKey(uri), speed)
                        }
                    },
                    onVoiceChange = { voice ->
                        ttsSelectedVoiceId = voice?.id?.value
                        ttsNavigator?.submitPreferences(
                            AndroidTtsPreferences(
                                speed = ttsSpeed.toDouble(),
                                voices = voice?.let { mapOf(it.language to it.id) } ?: emptyMap()
                            )
                        )
                        settings.apply(SettingsRepository.Store.LIBRARY) {
                            if (voice == null) remove(ttsVoicePreferenceKey(uri))
                            else putString(ttsVoicePreferenceKey(uri), voice.id.value)
                        }
                    },
                    onTimerChange = {
                        val nextTimer = when (ttsTimerMinutes) {
                            0 -> 15
                            15 -> 30
                            30 -> 60
                            else -> 0
                        }
                        ttsTimerMinutes = nextTimer
                        ttsServiceBinding?.binder?.setSleepTimer(nextTimer)
                    },
                    onStop = { sessionActions.stopTts() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp)
                )
            } else if (ttsError != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 88.dp, start = 16.dp, end = 16.dp),
                    color = Color(0xEE24152A),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(ttsError.orEmpty(), color = Color.White, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}
