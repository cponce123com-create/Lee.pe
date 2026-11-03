package pe.lee.app.ui.reader

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pe.lee.app.data.local.LeeDatabase
import pe.lee.app.data.local.OfflineBookStore
import pe.lee.app.data.local.ReadingAnnotationEntity
import pe.lee.app.ui.audio.TtsPlaybackService
import org.readium.navigator.media.tts.AndroidTtsNavigator
import org.readium.navigator.media.tts.AndroidTtsNavigatorFactory
import org.readium.navigator.media.tts.android.AndroidTtsEngine
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.navigator.media.tts.android.AndroidTtsPreferences
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import pe.lee.app.ui.theme.LeePeTheme
import java.io.IOException
import java.util.UUID

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

        readingSessionRecorder = ReadingSessionRecorder(
            bookUri = uri.toString(),
            dao = LeeDatabase.get(this).readingSessionDao()
        )
        val offlineCopyUri = intent.getStringExtra(OfflineBookStore.EXTRA_COPY_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Lee.pe"
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            LeePeTheme {
                EpubReaderScreen(
                    uri = uri,
                    title = title,
                    openPublication = { openPublication(uri, offlineCopyUri) },
                    onBack = { finish() }
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
                ?: throw IOException("No se pudo acceder al archivo seleccionado.")

            val httpClient = DefaultHttpClient()
            val assetRetriever = AssetRetriever(contentResolver, httpClient)
            val asset = assetRetriever.retrieve(url).getOrElse {
                throw IOException("No se pudo leer el EPUB: ${it.message ?: "archivo inaccesible"}")
            }

            val parser = DefaultPublicationParser(
                context = this@ReaderActivity,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
            return PublicationOpener(parser).open(asset, allowUserInteraction = false).getOrElse {
                asset.close()
                throw IOException("El archivo no parece ser un EPUB compatible: ${it.message ?: "formato inválido"}")
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
        throw lastError ?: IOException("No se pudo abrir el EPUB.")
    }

    companion object {
        const val EXTRA_TITLE = "pe.lee.app.reader.TITLE"
        private const val PREFS_NAME = "lee_pe_library"
        private const val KEY_DOCUMENT_URIS = "document_uris"

        fun locatorPreferenceKey(uri: Uri): String = "locator:$uri"
        fun preferencesPreferenceKey(uri: Uri): String = "epub-preferences:$uri"
        fun brightnessPreferenceKey(uri: Uri): String = "reader-brightness:$uri"
        fun keepScreenOnPreferenceKey(uri: Uri): String = "keep-screen-on:$uri"
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubReaderScreen(
    uri: Uri,
    title: String,
    openPublication: suspend () -> Publication,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context) { LeeDatabase.get(context) }
    val bookDao = remember(database) { database.bookDao() }
    val annotationDao = remember(database) { database.readingAnnotationDao() }
    val annotations by annotationDao.observeByBook(uri.toString()).collectAsState(initial = emptyList())
    val activity = context as? FragmentActivity ?: return
    val containerId = remember { View.generateViewId() }
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var publication by remember { mutableStateOf<Publication?>(null) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    var ttsNavigator by remember(uri) { mutableStateOf<AndroidTtsNavigator?>(null) }
    var ttsServiceBinding by remember { mutableStateOf<TtsPlaybackService.ServiceBinding?>(null) }
    var ttsIsPlaying by remember { mutableStateOf(false) }
    var ttsSentence by remember { mutableStateOf("") }
    var ttsError by remember { mutableStateOf<String?>(null) }
    var ttsSpeed by remember(uri) {
        mutableFloatStateOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getFloat(ttsSpeedPreferenceKey(uri), 1f).coerceIn(0.5f, 2f)
        )
    }
    var ttsVoices by remember(uri) { mutableStateOf<List<AndroidTtsEngine.Voice>>(emptyList()) }
    var ttsSelectedVoiceId by remember(uri) {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ttsVoicePreferenceKey(uri), null))
    }
    var ttsTimerMinutes by remember(uri) { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var epubPreferences by remember { mutableStateOf(EpubPreferences()) }
    var readerBrightness by remember(uri) {
        mutableFloatStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(ReaderActivity.brightnessPreferenceKey(uri), -1f))
    }
    var keepScreenOn by remember(uri) {
        mutableStateOf(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ReaderActivity.keepScreenOnPreferenceKey(uri), false))
    }
    // Open directly on the page, without overlaying the book with toolbars.
    // A tap on the EPUB restores the controls when needed.
    var chromeVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { ttsServiceBinding?.unbind() }
    }

    // In immersion mode the book occupies the entire screen. System bars return
    // automatically whenever the reader controls are visible or this screen is closed.
    DisposableEffect(activity) {
        onDispose {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(activity, chromeVisible, loading, errorMessage) {
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (chromeVisible || loading || errorMessage != null) {
            bars.show(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(activity, uri) {
        val originalBrightness = activity.window.attributes.screenBrightness
        val originallyKeptAwake = activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
            if (originallyKeptAwake) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(readerBrightness) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = readerBrightness }
    }

    LaunchedEffect(uri, containerId) {
        try {
            val opened = openPublication()
            publication = opened

            val savedBook = bookDao.findByUri(uri.toString())
            bookDao.markOpened(uri.toString(), System.currentTimeMillis())
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val legacyLocatorJson = preferences.getString(ReaderActivity.locatorPreferenceKey(uri), null)
            val locatorJson = savedBook?.epubLocator ?: legacyLocatorJson
            val initialLocator = locatorJson?.let {
                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
            }
            val preferencesJson = preferences.getString(ReaderActivity.preferencesPreferenceKey(uri), null)
            epubPreferences = preferencesJson?.let { json ->
                runCatching { EpubPreferencesSerializer().deserialize(json) }.getOrNull()
            } ?: EpubPreferences()
            progress = (
                initialLocator?.locations?.totalProgression
                    ?: savedBook?.readProgress?.toDouble()
                    ?: 0.0
                ).toFloat().coerceIn(0f, 1f)

            if (savedBook?.epubLocator.isNullOrBlank() && legacyLocatorJson != null && initialLocator != null) {
                bookDao.saveEpubProgress(
                    uri = uri.toString(),
                    locatorJson = legacyLocatorJson,
                    progress = progress,
                    openedAt = System.currentTimeMillis()
                )
            }

            val selectionActionCallback = object : BaseActionModeCallback() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(Menu.NONE, MENU_HIGHLIGHT, Menu.NONE, "Resaltar")
                    menu.add(Menu.NONE, MENU_COPY_SELECTION, Menu.NONE, "Copiar")
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId != MENU_HIGHLIGHT && item.itemId != MENU_COPY_SELECTION) return false
                    coroutineScope.launch {
                        val selectedLocator = navigator?.currentSelection()?.locator
                        if (item.itemId == MENU_HIGHLIGHT && selectedLocator != null) {
                            val now = System.currentTimeMillis()
                            annotationDao.upsert(
                                ReadingAnnotationEntity(
                                    id = UUID.randomUUID().toString(),
                                    bookUri = uri.toString(),
                                    kind = "HIGHLIGHT",
                                    locatorJson = selectedLocator.toJSON().toString(),
                                    selectedText = selectedLocator.text.highlight,
                                    colorArgb = DEFAULT_HIGHLIGHT_COLOR,
                                    createdAt = now
                                )
                            )
                        } else if (item.itemId == MENU_COPY_SELECTION) {
                            selectedLocator?.text?.highlight?.let { selectedText ->
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Texto seleccionado", selectedText))
                            }
                        }
                        mode.finish()
                    }
                    return true
                }
            }
            val factory = EpubNavigatorFactory(opened)
            activity.supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = epubPreferences,
                configuration = EpubNavigatorFragment.Configuration(
                    selectionActionModeCallback = selectionActionCallback
                )
            )
            activity.supportFragmentManager.commitNow {
                setReorderingAllowed(true)
                replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
            val epubNavigator = activity.supportFragmentManager
                .findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
                ?: error("No se pudo crear el lector EPUB.")
            epubNavigator.addInputListener(
                DirectionalNavigationAdapter(epubNavigator, animatedTransition = true)
            )
            epubNavigator.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    chromeVisible = !chromeVisible
                    return true
                }
            })
            navigator = epubNavigator

            loading = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            errorMessage = error.message ?: "No se pudo abrir este EPUB."
            loading = false
        }
    }

    LaunchedEffect(annotations, navigator) {
        val reader = navigator ?: return@LaunchedEffect
        val highlights = annotations
            .filter { it.kind == "HIGHLIGHT" }
            .mapNotNull { annotation ->
                val locator = runCatching {
                    Locator.fromJSON(JSONObject(annotation.locatorJson))
                }.getOrNull() ?: return@mapNotNull null
                Decoration(
                    id = annotation.id,
                    locator = locator,
                    style = Decoration.Style.Highlight(
                        tint = annotation.colorArgb ?: DEFAULT_HIGHLIGHT_COLOR
                    )
                )
            }
        reader.applyDecorations(highlights, ANNOTATION_DECORATION_GROUP)
    }

    LaunchedEffect(ttsNavigator, navigator) {
        val speech = ttsNavigator ?: return@LaunchedEffect
        val reader = navigator ?: return@LaunchedEffect
        speech.location.collect { location ->
            ttsSentence = location.utterance
            reader.applyDecorations(
                listOf(
                    Decoration(
                        id = "tts-current-utterance",
                        locator = location.utteranceLocator,
                        style = Decoration.Style.Highlight(tint = 0xFF56D7FF.toInt())
                    )
                ),
                TTS_DECORATION_GROUP
            )
            reader.go(location.utteranceLocator, animated = false)
        }
    }

    LaunchedEffect(ttsServiceBinding) {
        val binder = ttsServiceBinding?.binder ?: return@LaunchedEffect
        binder.session.collect { session ->
            if (session?.bookUri == uri.toString()) {
                ttsNavigator = session.navigator
                ttsVoices = session.navigator.voices.filter { it.isLocalSpanishVoice() }
                    .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
                ttsIsPlaying = session.navigator.playback.value.playWhenReady
            } else {
                ttsNavigator = null
                ttsIsPlaying = false
                ttsSentence = ""
            }
        }
    }

    LaunchedEffect(ttsNavigator) {
        val speech = ttsNavigator ?: return@LaunchedEffect
        speech.playback.collect { playback ->
            ttsIsPlaying = playback.playWhenReady
            if (playback.state is TtsNavigator.State.Ended) ttsIsPlaying = false
            if (playback.state is TtsNavigator.State.Failure) {
                ttsError = "No se pudo iniciar la voz. Comprueba que haya una voz de Android instalada para el idioma del libro."
            }
        }
    }

    // The media service owns the sleep timer: a second Compose timer would be
    // cancelled whenever the screen is recreated and might pause a different session.
    LaunchedEffect(ttsServiceBinding, ttsNavigator) {
        val current = ttsServiceBinding?.binder?.currentSession ?: return@LaunchedEffect
        if (current.bookUri != uri.toString()) return@LaunchedEffect
        current.sleepTimerMinutes.collect { minutes -> ttsTimerMinutes = minutes }
    }

    LaunchedEffect(navigator) {
        val reader = navigator ?: return@LaunchedEffect
        reader.currentLocator.collect { locator ->
            val totalProgression = locator.locations.totalProgression
            if (totalProgression != null) progress = totalProgression.toFloat().coerceIn(0f, 1f)
            val locatorJson = locator.toJSON().toString()
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(ReaderActivity.locatorPreferenceKey(uri), locatorJson)
                .apply()
            bookDao.saveEpubProgress(
                uri = uri.toString(),
                locatorJson = locatorJson,
                progress = totalProgression?.toFloat() ?: progress,
                openedAt = System.currentTimeMillis()
            )
        }
    }

    fun stopTts() {
        val binding = ttsServiceBinding
        if (binding != null) {
            binding.binder.stop()
            binding.unbind()
        } else {
            ttsNavigator?.close()
        }
        ttsServiceBinding = null
        ttsNavigator = null
        ttsIsPlaying = false
        ttsSentence = ""
        ttsTimerMinutes = 0
        coroutineScope.launch {
            navigator?.applyDecorations(emptyList(), TTS_DECORATION_GROUP)
        }
    }

    fun startOrToggleTts() {
        val current = ttsNavigator
        if (current != null) {
            if (ttsIsPlaying) current.pause() else current.play()
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        coroutineScope.launch {
            val app = context.applicationContext as Application
            val binding = ttsServiceBinding ?: try {
                TtsPlaybackService.bind(app).also { ttsServiceBinding = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ttsError = "No se pudo iniciar la voz en segundo plano."
                return@launch
            }
            val binder = binding.binder
            val existing = binder.currentSession
            if (existing?.bookUri == uri.toString()) {
                ttsNavigator = existing.navigator
                ttsVoices = existing.navigator.voices.filter { it.isLocalSpanishVoice() }
                    .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
                if (!existing.navigator.playback.value.playWhenReady) existing.navigator.play()
                return@launch
            }
            val activePublication = publication
            if (activePublication == null) {
                ttsError = "El EPUB todavía se está preparando."
                return@launch
            }
            val factory = AndroidTtsNavigatorFactory(
                context.applicationContext as Application,
                activePublication
            )
            if (factory == null) {
                ttsError = "Este EPUB no permite narración por voz."
                return@launch
            }
            val speech = factory.createNavigator(
                listener = object : TtsNavigator.Listener {
                    override fun onStopRequested() {
                        coroutineScope.launch {
                            binder.stop()
                            binding.unbind()
                            if (ttsServiceBinding === binding) ttsServiceBinding = null
                        }
                    }
                },
                initialLocator = (navigator as? VisualNavigator)?.firstVisibleElementLocator()
                    ?: navigator?.currentLocator?.value,
                initialPreferences = AndroidTtsPreferences(speed = ttsSpeed.toDouble())
            ).getOrElse { failure ->
                ttsError = "No se pudo preparar la narración: ${failure.message ?: "error de voz"}"
                return@launch
            }
            val localVoices = speech.voices
                .filter { it.isLocalSpanishVoice() }
                .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
            ttsVoices = localVoices
            val savedVoice = ttsSelectedVoiceId?.let { id ->
                localVoices.firstOrNull { it.id.value == id }
            }
            if (ttsSelectedVoiceId != null && savedVoice == null) {
                ttsSelectedVoiceId = null
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(ttsVoicePreferenceKey(uri)).apply()
            }
            if (savedVoice != null) {
                speech.submitPreferences(
                    AndroidTtsPreferences(
                        speed = ttsSpeed.toDouble(),
                        voices = mapOf(savedVoice.language to savedVoice.id)
                    )
                )
            }
            binder.openSession(speech, uri.toString(), title)
            ttsError = null
            ttsNavigator = speech
            speech.play()
        }
    }

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
                Text("Abriendo tu libro…", color = Color.White)
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
                Text("No se pudo abrir el libro", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(message, color = Color(0xFFD1D8E5))
                Button(onClick = onBack) { Text("Volver a la biblioteca") }
            }
        }

        if (!loading && errorMessage == null) {
            if (chromeVisible) ReaderChrome(
                title = title,
                ttsIsActive = ttsNavigator != null,
                ttsIsPlaying = ttsIsPlaying,
                onToggleTts = { startOrToggleTts() },
                progress = progress,
                annotations = annotations,
                onAddBookmark = {
                    navigator?.currentLocator?.value?.let { locator ->
                        val now = System.currentTimeMillis()
                        coroutineScope.launch {
                            annotationDao.upsert(
                                ReadingAnnotationEntity(
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
                            annotationDao.upsert(
                                ReadingAnnotationEntity(
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
                        annotationDao.updateColor(annotation.id, color, System.currentTimeMillis())
                    }
                },
                onUpdateAnnotation = { annotation, note ->
                    coroutineScope.launch {
                        annotationDao.updateNote(annotation.id, note, System.currentTimeMillis())
                    }
                },
                onSelectAnnotation = { annotation ->
                    runCatching { Locator.fromJSON(JSONObject(annotation.locatorJson)) }
                        .getOrNull()?.let { navigator?.go(it) }
                },
                onDeleteAnnotation = { annotation ->
                    coroutineScope.launch { annotationDao.deleteById(annotation.id) }
                },
                preferences = epubPreferences,
                brightness = readerBrightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = { enabled ->
                    keepScreenOn = enabled
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putBoolean(ReaderActivity.keepScreenOnPreferenceKey(uri), enabled).apply()
                },
                onBrightnessChange = { brightness ->
                    readerBrightness = brightness
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                        .putFloat(ReaderActivity.brightnessPreferenceKey(uri), brightness).apply()
                },
                onPreferencesChange = { updated ->
                    epubPreferences = updated
                    navigator?.submitPreferences(updated)
                    val serialized = runCatching {
                        EpubPreferencesSerializer().serialize(updated)
                    }.getOrNull()
                    if (serialized != null) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putString(ReaderActivity.preferencesPreferenceKey(uri), serialized)
                            .apply()
                    }
                },
                onToggleChrome = { chromeVisible = false },
                onBack = onBack,
                onPrevious = { navigator?.goBackward(animated = true) },
                onNext = { navigator?.goForward(animated = true) },
                contents = publication?.let { flattenContents(it.tableOfContents) }.orEmpty(),
                searchPublication = publication,
                onSelectContent = { link -> navigator?.go(link, animated = true) },
                onSelectSearchResult = { locator -> navigator?.go(locator, animated = true) },
                onSeek = {
                    coroutineScope.launch {
                        publication?.locateProgression(it.toDouble())?.let { locator ->
                            navigator?.go(locator)
                        }
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
                    onTogglePlayback = { startOrToggleTts() },
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
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putFloat(ttsSpeedPreferenceKey(uri), speed).apply()
                    },
                    onVoiceChange = { voice ->
                        ttsSelectedVoiceId = voice?.id?.value
                        ttsNavigator?.submitPreferences(
                            AndroidTtsPreferences(
                                speed = ttsSpeed.toDouble(),
                                voices = voice?.let { mapOf(it.language to it.id) } ?: emptyMap()
                            )
                        )
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                            if (voice == null) remove(ttsVoicePreferenceKey(uri))
                            else putString(ttsVoicePreferenceKey(uri), voice.id.value)
                        }.apply()
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
                    onStop = { stopTts() },
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

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun TtsControls(
    sentence: String,
    isPlaying: Boolean,
    speed: Float,
    voices: List<AndroidTtsEngine.Voice>,
    selectedVoiceId: String?,
    timerMinutes: Int,
    error: String?,
    onTogglePlayback: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVoiceChange: (AndroidTtsEngine.Voice?) -> Unit,
    onTimerChange: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val selectedVoice = voices.firstOrNull { it.id.value == selectedVoiceId }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = Color(0xF20A1423),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                sentence.ifBlank { "Narración del libro" },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp
            )
            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar voz" else "Continuar voz",
                        tint = Color.White
                    )
                }
                TextButton(onClick = {
                    val index = speeds.indexOf(speed).coerceAtLeast(0)
                    onSpeedChange(speeds[(index + 1) % speeds.size])
                }) { Text("${speed}x") }
                Box {
                    TextButton(onClick = { voiceMenuExpanded = true }) {
                        Text(selectedVoice?.let(::spanishVoiceLabel) ?: "Español")
                    }
                    DropdownMenu(
                        expanded = voiceMenuExpanded,
                        onDismissRequest = { voiceMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Automática (español)") },
                            onClick = {
                                onVoiceChange(null)
                                voiceMenuExpanded = false
                            }
                        )
                        if (voices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No hay voces locales en español") },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text("${spanishVoiceLabel(voice)} · ${voice.id.value}") },
                                    onClick = {
                                        onVoiceChange(voice)
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("Gestionar voces del dispositivo") },
                            onClick = {
                                voiceMenuExpanded = false
                                try {
                                    context.startActivity(
                                        Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Busca Español (México) en Ajustes > Accesibilidad > Salida de texto a voz.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
                TextButton(onClick = onTimerChange) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                    Text(if (timerMinutes == 0) "Temporizador" else "${timerMinutes} min")
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener narración", tint = Color.White)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
private fun ReaderChrome(
    title: String,
    ttsIsActive: Boolean,
    ttsIsPlaying: Boolean,
    onToggleTts: () -> Unit,
    progress: Float,
    annotations: List<ReadingAnnotationEntity>,
    onAddBookmark: () -> Unit,
    onAddNote: (String) -> Unit,
    onUpdateAnnotation: (ReadingAnnotationEntity, String) -> Unit,
    onChangeHighlightColor: (ReadingAnnotationEntity, Int) -> Unit,
    onSelectAnnotation: (ReadingAnnotationEntity) -> Unit,
    onDeleteAnnotation: (ReadingAnnotationEntity) -> Unit,
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit,
    onToggleChrome: () -> Unit,
    contents: List<TocEntry>,
    searchPublication: Publication?,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectContent: (Link) -> Unit,
    onSelectSearchResult: (Locator) -> Unit,
    onSeek: (Float) -> Unit,
    onProgressChange: (Float) -> Unit
) {
    var showContents by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var editingAnnotation by remember { mutableStateOf<ReadingAnnotationEntity?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var showAppearance by remember { mutableStateOf(false) }
    var showGoTo by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Locator>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchLimitReached by remember { mutableStateOf(false) }
    val searchScope = rememberCoroutineScope()
    var targetPercent by remember { mutableStateOf("") }
    val targetProgress = targetPercent.replace(',', '.').toFloatOrNull()
        ?.takeIf { it in 0f..100f }

    if (showSearch) {
        ModalBottomSheet(onDismissRequest = { showSearch = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Buscar en el EPUB", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            hasSearched = false
                            searchResults = emptyList()
                            searchError = null
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Palabra o frase") },
                        singleLine = true
                    )
                    IconButton(
                        enabled = searchQuery.isNotBlank() && !searching,
                        onClick = {
                            val query = searchQuery.trim()
                            val targetPublication = searchPublication
                            if (query.isNotBlank() && targetPublication != null) {
                                searching = true
                                searchError = null
                                searchResults = emptyList()
                                searchLimitReached = false
                                searchScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            val iterator = targetPublication.search(
                                                query,
                                                SearchService.Options(
                                                    caseSensitive = false,
                                                    diacriticSensitive = false
                                                )
                                            ) ?: throw IOException("Este EPUB no permite búsquedas.")
                                            val locators = mutableListOf<Locator>()
                                            var pagesRead = 0
                                            var limitReached = false
                                            try {
                                                while (locators.size < 100 && pagesRead < 20) {
                                                    val page = iterator.next().getOrElse { error ->
                                                        throw IOException(error.message)
                                                    } ?: break
                                                    pagesRead++
                                                    locators += page.locators.take(100 - locators.size)
                                                }
                                                limitReached = locators.size >= 100 ||
                                                    (iterator.resultCount?.let { it > locators.size } == true)
                                            } finally {
                                                iterator.close()
                                            }
                                            locators to limitReached
                                        }
                                        searchResults = result.first
                                        searchLimitReached = result.second
                                        hasSearched = true
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        searchError = error.message ?: "No se pudo buscar en este EPUB."
                                    } finally {
                                        searching = false
                                    }
                                }
                            } else if (targetPublication == null) {
                                searchError = "El libro todavía se está preparando."
                            }
                        }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                }
                if (searching) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Buscando en el libro…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!searching && searchResults.isNotEmpty()) {
                    Text(
                        if (searchLimitReached) "Mostrando hasta 100 coincidencias" else "${searchResults.size} coincidencias",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults) { locator ->
                            val excerpt = listOfNotNull(locator.text.before, locator.text.highlight, locator.text.after)
                                .filter(String::isNotBlank)
                                .joinToString(" ")
                                .ifBlank { "Coincidencia en el texto" }
                            val percent = locator.locations.totalProgression
                                ?.let { "${(it.coerceIn(0.0, 1.0) * 100).toInt()}%" }
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showSearch = false
                                        onSelectSearchResult(locator)
                                    },
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(excerpt, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    percent?.let {
                                        Text(it, color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else if (!searching && searchError == null && hasSearched) {
                    Text("No se encontraron coincidencias.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!searching && searchError == null && searchQuery.isNotBlank()) {
                    Text("Toca la lupa para iniciar la búsqueda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (!searching && searchResults.isEmpty() && searchError == null && searchQuery.isBlank()) {
                    Text("Busca una palabra o frase en el contenido del libro.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showAnnotations) {
        ModalBottomSheet(onDismissRequest = { showAnnotations = false }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Marcadores, notas y resaltados", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = {
                        editingAnnotation = null
                        noteDraft = ""
                        showNoteEditor = true
                    }) { Text("Añadir nota") }
                    TextButton(onClick = onAddBookmark) { Text("Guardar") }
                }
            }
            if (annotations.isEmpty()) {
                Text(
                    "Todavía no guardaste marcadores, notas ni resaltados en este libro.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(annotations, key = { it.id }) { annotation ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f).clickable {
                                    onSelectAnnotation(annotation)
                                    showAnnotations = false
                                }.padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Text(
                                    annotation.noteText?.takeIf { it.isNotBlank() }
                                        ?: annotation.selectedText?.takeIf { it.isNotBlank() }
                                        ?: if (annotation.kind == "HIGHLIGHT") {
                                            "Resaltado · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(annotation.createdAt))}"
                                        } else {
                                            "Marcador · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(annotation.createdAt))}"
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (annotation.kind == "HIGHLIGHT") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf(
                                            "Amarillo" to 0xFFFFD54F.toInt(),
                                            "Azul" to 0xFF64B5F6.toInt(),
                                            "Verde" to 0xFF81C784.toInt(),
                                            "Rosa" to 0xFFF48FB1.toInt()
                                        ).forEach { (label, color) ->
                                            IconButton(
                                                onClick = { onChangeHighlightColor(annotation, color) },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Circle,
                                                    contentDescription = "Resaltado $label",
                                                    tint = Color(color),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    when (annotation.kind) {
                                        "NOTE" -> "Nota · abrir posición"
                                        "HIGHLIGHT" -> "Resaltado · abrir texto"
                                        else -> "Marcador · abrir posición"
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            if (annotation.kind == "NOTE") {
                                IconButton(onClick = {
                                    editingAnnotation = annotation
                                    noteDraft = annotation.noteText.orEmpty()
                                    showNoteEditor = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar nota")
                                }
                            }
                            IconButton(onClick = { onDeleteAnnotation(annotation) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar marcador")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showNoteEditor) {
        AlertDialog(
            onDismissRequest = { showNoteEditor = false },
            title = { Text(if (editingAnnotation == null) "Nueva nota" else "Editar nota") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    label = { Text("Nota para esta posición") },
                    minLines = 3,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(
                    enabled = noteDraft.isNotBlank(),
                    onClick = {
                        val existing = editingAnnotation
                        if (existing == null) onAddNote(noteDraft.trim())
                        else onUpdateAnnotation(existing, noteDraft.trim())
                        showNoteEditor = false
                    }
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteEditor = false }) { Text("Cancelar") }
            }
        )
    }

    if (showAppearance) {
        ModalBottomSheet(onDismissRequest = { showAppearance = false }) {
            EpubAppearanceSheet(
                preferences = preferences,
                brightness = brightness,
                keepScreenOn = keepScreenOn,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onBrightnessChange = onBrightnessChange,
                onPreferencesChange = onPreferencesChange
            )
        }
    }

    if (showGoTo) {
        AlertDialog(
            onDismissRequest = { showGoTo = false },
            title = { Text("Ir a una posición") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Indica el porcentaje del libro. La paginación EPUB puede cambiar con la letra y la pantalla."
                    )
                    OutlinedTextField(
                        value = targetPercent,
                        onValueChange = { input ->
                            targetPercent = input.filter { it.isDigit() || it == '.' || it == ',' }
                        },
                        label = { Text("Progreso") },
                        suffix = { Text("%") },
                        singleLine = true,
                        isError = targetProgress == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = targetProgress != null,
                    onClick = {
                        targetProgress?.let { value ->
                            val normalized = value / 100f
                            onProgressChange(normalized)
                            onSeek(normalized)
                            showGoTo = false
                        }
                    }
                ) { Text("Ir") }
            },
            dismissButton = {
                TextButton(onClick = { showGoTo = false }) { Text("Cancelar") }
            }
        )
    }

    if (showContents) {
        ModalBottomSheet(onDismissRequest = { showContents = false }) {
            Text(
                "Contenido del libro",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (contents.isEmpty()) {
                Text(
                    "Este EPUB no incluye una tabla de contenidos.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(contents) { entry ->
                        Text(
                            text = entry.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectContent(entry.link)
                                    showContents = false
                                }
                                .padding(
                                    start = (22 + entry.depth * 16).dp,
                                    end = 20.dp,
                                    top = 13.dp,
                                    bottom = 13.dp
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Compact toolbar: the original seven actions could not fit on narrow phones.
        // Keep frequent actions visible and put everything else in an accessible menu.
        Surface(
            color = Color(0xD90A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                }
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onToggleTts) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = if (ttsIsActive && ttsIsPlaying) "Pausar narración" else "Leer en voz alta",
                        tint = if (ttsIsActive) Color(0xFF56D7FF) else Color.White
                    )
                }
                Box {
                    IconButton(onClick = { showActionsMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Más opciones de lectura",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Buscar en el libro") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = { showActionsMenu = false; showSearch = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcadores y notas") },
                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            onClick = { showActionsMenu = false; showAnnotations = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Apariencia de lectura") },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                            onClick = { showActionsMenu = false; showAppearance = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Índice del libro") },
                            leadingIcon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                            onClick = { showActionsMenu = false; showContents = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Ocultar controles") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                            onClick = { showActionsMenu = false; onToggleChrome() }
                        )
                    }
                }
            }
        }

        Surface(
            color = Color(0xE60A1423),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Slider(
                    value = progress,
                    onValueChange = onProgressChange,
                    onValueChangeFinished = { onSeek(progress.coerceIn(0f, 1f)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior", tint = Color.White)
                    }
                    TextButton(
                        onClick = {
                            targetPercent = (progress * 100).toInt().toString()
                            showGoTo = true
                        }
                    ) {
                        Text(
                            "Ir a ${(progress * 100).toInt()}%",
                            color = Color(0xFFD3DCEC),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpubAppearanceSheet(
    preferences: EpubPreferences,
    brightness: Float,
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onPreferencesChange: (EpubPreferences) -> Unit
) {
    var fontSize by remember(preferences.fontSize) {
        mutableFloatStateOf((preferences.fontSize ?: 1.0).toFloat().coerceIn(0.75f, 1.75f))
    }
    var lineHeight by remember(preferences.lineHeight) {
        mutableFloatStateOf((preferences.lineHeight ?: 1.2).toFloat().coerceIn(1f, 2f))
    }
    var pageMargins by remember(preferences.pageMargins) {
        mutableFloatStateOf((preferences.pageMargins ?: 1.0).toFloat().coerceIn(0.5f, 2f))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Apariencia de lectura", fontSize = 21.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Claro", "Sepia", "Oscuro", "OLED").forEach { label ->
                    FilterChip(
                        selected = selectedReaderTheme(preferences) == label,
                        onClick = {
                            val updated = when (label) {
                                "Sepia" -> preferences.copy(theme = ReadiumTheme.SEPIA, backgroundColor = null, textColor = null)
                                "Oscuro" -> preferences.copy(theme = ReadiumTheme.DARK, backgroundColor = null, textColor = null)
                                "OLED" -> preferences.copy(
                                    theme = ReadiumTheme.DARK,
                                    backgroundColor = ReadiumColor(0xFF000000.toInt()),
                                    textColor = ReadiumColor(0xFFFFFFFF.toInt())
                                )
                                else -> preferences.copy(theme = ReadiumTheme.LIGHT, backgroundColor = null, textColor = null)
                            }
                            onPreferencesChange(updated)
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Tamaño de letra · ${(fontSize * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(fontSize = fontSize.toDouble()))
                },
                valueRange = 0.75f..1.75f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Estilo del texto", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.publisherStyles != false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = true)) },
                    label = { Text("Estilo del libro") }
                )
                FilterChip(
                    selected = preferences.publisherStyles == false,
                    onClick = { onPreferencesChange(preferences.copy(publisherStyles = false)) },
                    label = { Text("Ajustes Lee.pe") }
                )
            }
            Text(
                "Interlineado y alineación necesitan los ajustes Lee.pe para reemplazar el formato definido por el EPUB.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Interlineado · ${(lineHeight * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = lineHeight,
                onValueChange = { lineHeight = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(lineHeight = lineHeight.toDouble(), publisherStyles = false))
                },
                valueRange = 1f..2f,
                steps = 9
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Márgenes laterales · ${(pageMargins * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Slider(
                value = pageMargins,
                onValueChange = { pageMargins = it },
                onValueChangeFinished = {
                    onPreferencesChange(preferences.copy(pageMargins = pageMargins.toDouble()))
                },
                valueRange = 0.5f..2f,
                steps = 14
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alineación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Inicio" to ReadiumTextAlign.START,
                    "Justificada" to ReadiumTextAlign.JUSTIFY,
                    "Centro" to ReadiumTextAlign.CENTER
                ).forEach { (label, alignment) ->
                    FilterChip(
                        selected = (preferences.textAlign ?: ReadiumTextAlign.START) == alignment,
                        onClick = {
                            onPreferencesChange(preferences.copy(textAlign = alignment, publisherStyles = false))
                        },
                        label = { Text(label) }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Navegación", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = preferences.scroll != true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = false)) },
                    label = { Text("Por páginas") }
                )
                FilterChip(
                    selected = preferences.scroll == true,
                    onClick = { onPreferencesChange(preferences.copy(scroll = true)) },
                    label = { Text("Desplazamiento") }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Durante la lectura", fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mantener pantalla encendida")
                    Text("Evita que se apague mientras lees.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Brillo de pantalla", fontWeight = FontWeight.SemiBold)
            FilterChip(
                selected = brightness < 0f,
                onClick = { onBrightnessChange(-1f) },
                label = { Text("Usar brillo del sistema") }
            )
            Slider(
                value = if (brightness < 0f) 0.7f else brightness.coerceIn(0.15f, 1f),
                onValueChange = onBrightnessChange,
                valueRange = 0.15f..1f,
                enabled = brightness >= 0f
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tipografía", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Original" to null,
                    "Serif" to ReadiumFontFamily.SERIF,
                    "Sans" to ReadiumFontFamily.SANS_SERIF,
                    "Dislexia" to ReadiumFontFamily.OPEN_DYSLEXIC
                ).forEach { (label, family) ->
                    FilterChip(
                        selected = preferences.fontFamily?.name == family?.name,
                        onClick = { onPreferencesChange(preferences.copy(fontFamily = family)) },
                        label = { Text(label) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun selectedReaderTheme(preferences: EpubPreferences): String = when {
    preferences.theme == ReadiumTheme.DARK &&
        preferences.backgroundColor?.int == 0xFF000000.toInt() -> "OLED"
    preferences.theme == ReadiumTheme.SEPIA -> "Sepia"
    preferences.theme == ReadiumTheme.DARK -> "Oscuro"
    else -> "Claro"
}

private const val PREFS_NAME = "lee_pe_library"
private const val NAVIGATOR_TAG = "lee_pe_epub_navigator"
private const val MENU_HIGHLIGHT = 1
private const val MENU_COPY_SELECTION = 2
private const val ANNOTATION_DECORATION_GROUP = "lee_pe_annotations"
private fun AndroidTtsEngine.Voice.isLocalSpanishVoice(): Boolean {
    if (requiresNetwork) return false
    val languageCode = language.toString()
        .substringBefore('-')
        .substringBefore('_')
    return languageCode.equals("es", ignoreCase = true)
}

private fun spanishVoiceLabel(voice: AndroidTtsEngine.Voice): String {
    val tag = voice.language.toString().uppercase()
    return when {
        tag.contains("MX") -> "Español (México)"
        tag.contains("ES") -> "Español (España)"
        tag.contains("AR") -> "Español (Argentina)"
        tag.contains("CO") -> "Español (Colombia)"
        tag.contains("US") -> "Español (Estados Unidos)"
        else -> "Español"
    }
}

private const val TTS_DECORATION_GROUP = "lee_pe_tts"
private fun ttsSpeedPreferenceKey(uri: Uri): String = "tts-speed:$uri"
private fun ttsVoicePreferenceKey(uri: Uri): String = "tts-voice:$uri"
private const val DEFAULT_HIGHLIGHT_COLOR = 0xFFFFD54F.toInt()

private data class TocEntry(
    val title: String,
    val link: Link,
    val depth: Int
)

private fun flattenContents(links: List<Link>, depth: Int = 0): List<TocEntry> =
    buildList {
        links.forEach { link ->
            add(TocEntry(link.title?.takeIf { it.isNotBlank() } ?: link.href.toString(), link, depth))
            addAll(flattenContents(link.children, depth + 1))
        }
    }
