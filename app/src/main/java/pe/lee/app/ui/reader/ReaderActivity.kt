package pe.lee.app.ui.reader

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import pe.lee.app.LeePeApplication
import pe.lee.app.R
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.reader.EpubReaderViewModel
import pe.lee.app.ui.theme.LeePeTheme
import pe.lee.core.domain.ReaderAnnotation

private const val PAGE_TURN_SKIP_TIMEOUT_MS = 2_000L

@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubReaderScreen(
    uri: Uri,
    title: String,
    viewModel: EpubReaderViewModel,
    settings: SettingsRepository,
    openPublication: suspend () -> Publication,
    onReadingVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val annotations by viewModel.annotationState.collectAsStateWithLifecycle()
    val activity = context as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    var readerContainer by remember { mutableStateOf<FrameLayout?>(null) }
    val skipNextPageTurn = remember { mutableStateOf(false) }
    val pageTurnController = remember { EpubPageTurnController() }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
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
    var skipPageTurnResetJob by remember { mutableStateOf<Job?>(null) }
    fun suppressPageTurnForJump() {
        skipNextPageTurn.value = true
        skipPageTurnResetJob?.cancel()
        skipPageTurnResetJob = coroutineScope.launch {
            kotlinx.coroutines.delay(PAGE_TURN_SKIP_TIMEOUT_MS)
            skipNextPageTurn.value = false
        }
    }
    val pageTurnFrame = rememberEpubPageTurnFrame(
        navigator = navigator,
        pageView = readerContainer,
        enabled = !loading && errorMessage == null && epubPreferences.scroll != true,
        preferences = epubPreferences,
        autoFollowingNarration = session.followNarration.value && ttsIsPlaying,
        skipNextTurn = skipNextPageTurn,
        controller = pageTurnController,
    )
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
        },
    )
    val applyReaderPreferences: (EpubPreferences) -> Unit = { updated ->
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
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07111F)),
    ) {
        AndroidView(
            factory = {
                FrameLayout(it).apply {
                    id = containerId
                    readerContainer = this
                }
            },
            update = { readerContainer = it },
            modifier = Modifier.fillMaxSize(),
        )
        pageTurnFrame?.let { frame -> EpubPageTurnOverlay(frame) }

        if (loading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.epub_open_failed), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(message, color = Color(0xFFD1D8E5))
                Button(onClick = onBack) { Text(stringResource(R.string.epub_back_to_library)) }
            }
        }

        if (!loading && errorMessage == null) {
            if (chromeVisible) {
                ReaderChrome(
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
                                        createdAt = now,
                                    ),
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
                                        createdAt = now,
                                    ),
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
                            .getOrNull()?.let {
                                sessionActions.pauseTtsForNavigation()
                                navigator?.go(it)
                            }
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
                    onPreferencesChange = applyReaderPreferences,
                    onToggleChrome = { chromeVisible = false },
                    onBack = onBack,
                    onPrevious = {
                        sessionActions.pauseTtsForNavigation()
                        pageTurnController.start(isForward = false)
                        navigator?.goBackward(animated = false)
                    },
                    onNext = {
                        sessionActions.pauseTtsForNavigation()
                        pageTurnController.start(isForward = true)
                        navigator?.goForward(animated = false)
                    },
                    contents = tableOfContents,
                    searchPublication = publication,
                    onSelectContent = { link ->
                        sessionActions.pauseTtsForNavigation()
                        suppressPageTurnForJump()
                        navigator?.go(link, animated = false)
                    },
                    onSelectSearchResult = { locator ->
                        sessionActions.pauseTtsForNavigation()
                        suppressPageTurnForJump()
                        navigator?.go(locator, animated = false)
                    },
                    onSeek = { progression ->
                        // Narration would drag the page back to the spoken sentence.
                        sessionActions.pauseTtsForNavigation()
                        seekJob?.cancel()
                        seekJob = coroutineScope.launch {
                            val locator = withContext(Dispatchers.IO) {
                                publication?.locateProgression(progression.toDouble())
                            }
                            if (locator != null) {
                                suppressPageTurnForJump()
                                navigator?.go(locator, animated = false)
                            }
                        }
                    },
                    onProgressChange = { progress = it },
                )
            }

            if (!chromeVisible) {
                ReaderQuickThemeButton(
                    preferences = epubPreferences,
                    onPreferencesChange = applyReaderPreferences,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 8.dp, end = 8.dp),
                )
            }

            if (ttsNavigator != null && chromeVisible) {
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
                                    ?: if (ttsSelectedVoiceId == null) null else emptyMap(),
                            ),
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
                                voices = voice?.let { mapOf(it.language to it.id) } ?: emptyMap(),
                            ),
                        )
                        settings.apply(SettingsRepository.Store.LIBRARY) {
                            if (voice == null) {
                                remove(ttsVoicePreferenceKey(uri))
                            } else {
                                putString(ttsVoicePreferenceKey(uri), voice.id.value)
                            }
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
                    // Real insets instead of guessed offsets: with gesture navigation
                    // or a cutout the panel overlapped the system bars (A-19).
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                )
            } else if (ttsNavigator != null) {
                TtsMiniControls(
                    isPlaying = ttsIsPlaying,
                    onTogglePlayback = { sessionActions.toggleTts() },
                    onStop = { sessionActions.stopTts() },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
                )
            } else if (ttsError != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 72.dp, start = 16.dp, end = 16.dp),
                    color = Color(0xEE24152A),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(ttsError.orEmpty(), color = Color.White, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}
class ReaderActivity : FragmentActivity() {
    private var readingSessionRecorder: ReadingSessionRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // The Readium navigator owns a Publication held in memory. Restoring the old
        // Fragment after rotation/process death would restore it without that Publication.
        // Recreate a clean navigator instead; its persisted locator and per-book settings
        // are restored from Room / preferences in EpubReaderScreen.
        super.onCreate(null)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        val container = (application as LeePeApplication).container
        val readerViewModel = ViewModelProvider(this, container.viewModelFactory)
            .get(EpubReaderViewModel::class.java)

        readingSessionRecorder = ReadingSessionRecorder(
            bookUri = uri.toString(),
            repository = container.readingStats,
        )
        val offlineCopyUri = intent.getStringExtra(OfflineBookStore.EXTRA_COPY_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lee.pe"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LeePeTheme {
                EpubReaderScreen(
                    uri = uri,
                    title = title,
                    viewModel = readerViewModel,
                    settings = container.settings,
                    openPublication = { openPublication(uri, offlineCopyUri) },
                    onReadingVisibilityChange = { active ->
                        if (active) {
                            readingSessionRecorder?.onResume()
                        } else {
                            readingSessionRecorder?.onPause()
                        }
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readingSessionRecorder?.onResume()
    }

    override fun onPause() {
        readingSessionRecorder?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        readingSessionRecorder?.close()
        readingSessionRecorder = null
        super.onDestroy()
    }

    private suspend fun openPublication(uri: Uri, offlineCopyUri: Uri?): Publication = withContext(Dispatchers.IO) {
        suspend fun openFrom(sourceUri: Uri): Publication {
            val url: AbsoluteUrl = sourceUri.toAbsoluteUrl()
                ?: throw IOException(getString(R.string.reader_file_unavailable))

            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(contentResolver, httpClient)
            val asset = assetRetriever.retrieve(url).getOrElse {
                throw IOException(getString(R.string.epub_open_failed))
            }

            val parser = DefaultPublicationParser(
                context = this@ReaderActivity,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            )
            return PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrElse {
                asset.close()
                throw IOException(getString(R.string.epub_invalid_file))
            }
        }

        val candidates = listOfNotNull(offlineCopyUri, uri).distinct()
        var lastError: IOException? = null
        for (candidate in candidates) {
            try {
                return@withContext openFrom(candidate)
            } catch (error: IOException) {
                lastError = error
            }
        }
        throw lastError ?: IOException(getString(R.string.epub_open_failed))
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.reader.TITLE"

        fun locatorPreferenceKey(uri: Uri): String = "locator:$uri"
        fun preferencesPreferenceKey(uri: Uri): String = "epub-preferences:$uri"
        fun brightnessPreferenceKey(uri: Uri): String = "reader-brightness:$uri"
        fun keepScreenOnPreferenceKey(uri: Uri): String = "keep-screen-on:$uri"
    }
}
