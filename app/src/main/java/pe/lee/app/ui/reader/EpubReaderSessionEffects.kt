package pe.lee.app.ui.reader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import android.content.ClipData
import android.content.ClipboardManager
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.core.domain.ReaderAnnotation
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication



@OptIn(ExperimentalReadiumApi::class)
@Composable
internal fun EpubReaderSessionEffects(
    context: Context,
    activity: FragmentActivity,
    uri: Uri,
    title: String,
    viewModel: EpubReaderViewModel,
    settings: SettingsRepository,
    containerId: Int,
    annotations: List<ReaderAnnotation>,
    session: EpubReaderSessionState,
    openPublication: suspend () -> Publication,
    onRequestNotificationPermission: () -> Unit
): EpubReaderSessionActions {
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel, uri) { viewModel.openBook(uri.toString()) }

    DisposableEffect(Unit) {
        onDispose { session.ttsServiceBinding.value?.unbind() }
    }

    // In immersion mode the book occupies the entire screen. System bars return
    // automatically whenever the reader controls are visible or this screen is closed.
    DisposableEffect(activity) {
        onDispose {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(activity, session.chromeVisible.value, session.loading.value, session.errorMessage.value) {
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (session.chromeVisible.value || session.loading.value || session.errorMessage.value != null) {
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

    LaunchedEffect(session.keepScreenOn.value) {
        if (session.keepScreenOn.value) activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    LaunchedEffect(session.readerBrightness.value) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = session.readerBrightness.value }
    }

    LaunchedEffect(uri, containerId) {
        try {
            val opened = openPublication()
            session.publication.value = opened

            val savedBook = viewModel.findBook(uri.toString())
            viewModel.markOpened(uri.toString(), System.currentTimeMillis())
            val legacyLocatorJson = settings.getString(SettingsRepository.Store.LIBRARY, ReaderActivity.locatorPreferenceKey(uri), null)
            val locatorJson = savedBook?.epubLocator ?: legacyLocatorJson
            val initialLocator = locatorJson?.let {
                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
            }
            val preferencesJson = settings.getString(SettingsRepository.Store.LIBRARY, ReaderActivity.preferencesPreferenceKey(uri), null)
            session.epubPreferences.value = preferencesJson?.let { json ->
                runCatching { EpubPreferencesSerializer().deserialize(json) }.getOrNull()
            } ?: EpubPreferences()
            session.progress.value = (
                initialLocator?.locations?.totalProgression
                    ?: savedBook?.readProgress?.toDouble()
                    ?: 0.0
                ).toFloat().coerceIn(0f, 1f)

            if (savedBook?.epubLocator.isNullOrBlank() && legacyLocatorJson != null && initialLocator != null) {
                viewModel.saveProgress(
                    uri = uri.toString(),
                    locatorJson = legacyLocatorJson,
                    progress = session.progress.value,
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
                        val selectedLocator = session.navigator.value?.currentSelection()?.locator
                        if (item.itemId == MENU_HIGHLIGHT && selectedLocator != null) {
                            val now = System.currentTimeMillis()
                            viewModel.saveAnnotation(
                                ReaderAnnotation(
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
                initialPreferences = session.epubPreferences.value,
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
                    session.chromeVisible.value = !session.chromeVisible.value
                    return true
                }
            })
            session.navigator.value = epubNavigator

            session.loading.value = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            session.errorMessage.value = error.message ?: "No se pudo abrir este EPUB."
            session.loading.value = false
        }
    }

    LaunchedEffect(annotations, session.navigator.value) {
        val reader = session.navigator.value ?: return@LaunchedEffect
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

    LaunchedEffect(session.ttsNavigator.value, session.navigator.value) {
        val speech = session.ttsNavigator.value ?: return@LaunchedEffect
        val reader = session.navigator.value ?: return@LaunchedEffect
        speech.location.collect { location ->
            session.ttsSentence.value = location.utterance
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

    LaunchedEffect(session.ttsServiceBinding.value) {
        val binder = session.ttsServiceBinding.value?.binder ?: return@LaunchedEffect
        binder.session.collect { activeSession ->
            if (activeSession?.bookUri == uri.toString()) {
                session.ttsNavigator.value = activeSession.navigator
                session.ttsVoices.value = activeSession.navigator.voices.filter { it.isLocalSpanishVoice() }
                    .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
                session.ttsIsPlaying.value = activeSession.navigator.playback.value.playWhenReady
            } else {
                session.ttsNavigator.value = null
                session.ttsIsPlaying.value = false
                session.ttsSentence.value = ""
            }
        }
    }

    LaunchedEffect(session.ttsNavigator.value) {
        val speech = session.ttsNavigator.value ?: return@LaunchedEffect
        speech.playback.collect { playback ->
            session.ttsIsPlaying.value = playback.playWhenReady
            if (playback.state is TtsNavigator.State.Ended) session.ttsIsPlaying.value = false
            if (playback.state is TtsNavigator.State.Failure) {
                session.ttsError.value = "No se pudo iniciar la voz. Comprueba que haya una voz de Android instalada para el idioma del libro."
            }
        }
    }

    // The media service owns the sleep timer: a second Compose timer would be
    // cancelled whenever the screen is recreated and might pause a different session.
    LaunchedEffect(session.ttsServiceBinding.value, session.ttsNavigator.value) {
        val current = session.ttsServiceBinding.value?.binder?.currentSession ?: return@LaunchedEffect
        if (current.bookUri != uri.toString()) return@LaunchedEffect
        current.sleepTimerMinutes.collect { minutes -> session.ttsTimerMinutes.value = minutes }
    }

    LaunchedEffect(session.navigator.value) {
        val reader = session.navigator.value ?: return@LaunchedEffect
        reader.currentLocator.collect { locator ->
            val totalProgression = locator.locations.totalProgression
            if (totalProgression != null) session.progress.value = totalProgression.toFloat().coerceIn(0f, 1f)
            val locatorJson = locator.toJSON().toString()
            settings.apply(SettingsRepository.Store.LIBRARY) {
                putString(ReaderActivity.locatorPreferenceKey(uri), locatorJson)
            }
            viewModel.saveProgress(
                uri = uri.toString(),
                locatorJson = locatorJson,
                progress = totalProgression?.toFloat() ?: session.progress.value,
                openedAt = System.currentTimeMillis()
            )
        }
    }

    return buildEpubReaderSessionActions(
        coroutineScope, context, uri, title, settings, session, onRequestNotificationPermission
    )
}
