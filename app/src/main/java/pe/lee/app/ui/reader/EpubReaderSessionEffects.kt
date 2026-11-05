package pe.lee.app.ui.reader

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.navigator.media.tts.TtsNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.BaseActionModeCallback
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import pe.lee.app.R
import pe.lee.app.data.repository.SettingsRepository
import pe.lee.app.ui.audio.TtsPlaybackService
import pe.lee.core.domain.ReaderAnnotation
import pe.lee.feature.sharecard.R as ShareCardR
import pe.lee.feature.sharecard.navigation.ShareCardArgs
import pe.lee.feature.sharecard.navigation.shareCardIntent

/** Minimum interval between locator persists; scroll mode emits continuously. */
private const val LOCATOR_SAVE_INTERVAL_MS = 2_000L

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
    onRequestNotificationPermission: () -> Unit,
): EpubReaderSessionActions {
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(viewModel, uri) { viewModel.openBook(uri.toString()) }

    DisposableEffect(Unit) {
        onDispose { session.ttsServiceBinding.value?.unbind() }
    }

    // UI and media service retain independent owners. Closing voice must not
    // invalidate the displayed book; closing the screen must not stop narration.
    DisposableEffect(uri) {
        onDispose {
            session.publicationOwner.value?.let { runCatching { it.close() } }
            session.publicationOwner.value = null
            session.publication.value = null
            session.navigator.value = null
        }
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
            if (originallyKeptAwake) {
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(session.keepScreenOn.value) {
        if (session.keepScreenOn.value) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(session.readerBrightness.value) {
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = session.readerBrightness.value }
    }

    LaunchedEffect(uri, containerId) {
        try {
            val opened = openPublication()
            session.publicationOwner.value = SharedResourceLease(opened) { it.close() }
            session.publication.value = opened

            val savedBook = viewModel.findBook(uri.toString())
            viewModel.markOpened(uri.toString(), System.currentTimeMillis())
            val legacyLocatorJson = settings.getString(SettingsRepository.Store.LIBRARY, ReaderActivity.locatorPreferenceKey(uri), null)
            val locatorJson = savedBook?.epubLocator ?: legacyLocatorJson
            val parsedLocator = locatorJson?.let {
                runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
            }
            // A locator from another edition or book can parse fine yet point to a
            // resource that does not exist here; opening it leaves a blank page and
            // the bad value stays in Room forever (A-34).
            val initialLocator = parsedLocator?.takeIf { candidate ->
                val target = candidate.href.toString()
                opened.readingOrder.any { link -> link.href.toString() == target }
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
                    openedAt = System.currentTimeMillis(),
                )
            }

            val selectionActionCallback = object : BaseActionModeCallback() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    // Reserve a visible slot for the primary quote-card action.
                    menu.add(Menu.NONE, MENU_SHARE_SELECTION, 0, context.getString(ShareCardR.string.share_card_create_action))
                        .setIcon(android.R.drawable.ic_menu_gallery)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                    menu.add(Menu.NONE, MENU_HIGHLIGHT, 1, context.getString(R.string.epub_selection_highlight))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(Menu.NONE, MENU_COPY_SELECTION, 2, context.getString(R.string.epub_selection_copy))
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    if (item.itemId !in setOf(MENU_HIGHLIGHT, MENU_COPY_SELECTION, MENU_SHARE_SELECTION)) return false
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
                                    selectedText = selectedLocator.text.highlight?.take(12_000),
                                    colorArgb = DEFAULT_HIGHLIGHT_COLOR,
                                    createdAt = now,
                                ),
                            )
                        } else if (item.itemId == MENU_COPY_SELECTION) {
                            selectedLocator?.text?.highlight?.let { selectedText ->
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText(
                                        context.getString(R.string.epub_selection_clip_label),
                                        selectedText,
                                    ),
                                )
                            }
                        } else if (item.itemId == MENU_SHARE_SELECTION) {
                            val selectedText = selectedLocator?.text?.highlight?.trim().orEmpty()
                            if (selectedText.isNotEmpty()) {
                                val author = opened.metadata.authors
                                    .map { it.localizedName.string }
                                    .filter(String::isNotBlank)
                                    .joinToString(", ")
                                context.startActivity(
                                    shareCardIntent(
                                        context,
                                        ShareCardArgs(
                                            text = selectedText,
                                            bookId = uri.toString(),
                                            bookTitle = title,
                                            author = author,
                                            locatorJson = selectedLocator?.toJSON()?.toString(),
                                        ),
                                    ),
                                )
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
                    selectionActionModeCallback = selectionActionCallback,
                ),
            )
            activity.supportFragmentManager.commitNow {
                setReorderingAllowed(true)
                replace(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
            val epubNavigator = activity.supportFragmentManager
                .findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
                ?: error(context.getString(R.string.epub_navigator_create_failed))
            epubNavigator.addInputListener(
                DirectionalNavigationAdapter(epubNavigator, animatedTransition = true),
            )
            epubNavigator.addInputListener(object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    session.chromeVisible.value = !session.chromeVisible.value
                    return true
                }
            })
            session.navigator.value = epubNavigator

            session.loading.value = false

            // A rotation recreates the composition while the TTS service keeps
            // narrating. Re-attach the panel instead of leaving audio orphaned.
            if (session.ttsServiceBinding.value == null &&
                TtsPlaybackService.activeBookUri == uri.toString()
            ) {
                runCatching {
                    TtsPlaybackService.bind(context.applicationContext as Application)
                }.getOrNull()?.let { binding ->
                    val existing = binding.binder.currentSession
                    if (existing != null && existing.bookUri == uri.toString()) {
                        session.ttsServiceBinding.value = binding
                        session.ttsNavigator.value = existing.navigator
                        session.ttsVoices.value = existing.navigator.voices
                            .filter { it.isLocalSpanishVoice() }
                            .sortedWith(compareBy({ it.language.toString() }, { it.id.value }))
                        session.ttsIsPlaying.value =
                            existing.navigator.playback.value.playWhenReady
                    } else {
                        binding.unbind()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            session.errorMessage.value = context.getString(R.string.epub_open_failed)
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
                        tint = annotation.colorArgb ?: DEFAULT_HIGHLIGHT_COLOR,
                    ),
                )
            }
        reader.applyDecorations(highlights, ANNOTATION_DECORATION_GROUP)
    }

    LaunchedEffect(session.ttsNavigator.value, session.navigator.value, session.followNarration.value) {
        val reader = session.navigator.value ?: return@LaunchedEffect
        // Narration is transient UI, never a saved annotation. Remove the old
        // cyan sentence marker on attach, pause, stop, end and service changes.
        reader.applyDecorations(emptyList(), TTS_DECORATION_GROUP)
        val speech = session.ttsNavigator.value ?: return@LaunchedEffect
        if (!session.followNarration.value) return@LaunchedEffect
        val follower = NarrationFollower<Locator>(
            identity = { it.toJSON().toString() },
            navigate = { reader.go(it, animated = false) },
        )
        speech.location.combine(speech.playback) { location, playback -> location to playback }
            .collect { (location, playback) ->
                session.ttsSentence.value = location.utterance
                val playing = playback.playWhenReady &&
                    playback.state !is TtsNavigator.State.Ended &&
                    playback.state !is TtsNavigator.State.Failure &&
                    session.followNarration.value
                follower.update(playing, location.utteranceLocator, location.tokenLocator)
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
        var wasPlaying = session.ttsIsPlaying.value
        speech.playback.collect { playback ->
            val playing = playback.playWhenReady &&
                playback.state !is TtsNavigator.State.Ended &&
                playback.state !is TtsNavigator.State.Failure
            if (playing && !wasPlaying) {
                session.followNarration.value = true
            }
            session.ttsIsPlaying.value = playing
            wasPlaying = playing
            if (playback.state is TtsNavigator.State.Failure) {
                session.ttsError.value = context.getString(R.string.tts_voice_unavailable)
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
        // Scroll mode emits a locator several times per second. Persisting each
        // one wrote to Room and SharedPreferences on every frame; throttle to one
        // write every two seconds. A lifecycle observer below persists the final
        // current locator when the activity pauses.
        var lastSavedAt = 0L
        reader.currentLocator.collect { locator ->
            val now = System.currentTimeMillis()
            if (now - lastSavedAt < LOCATOR_SAVE_INTERVAL_MS) return@collect
            lastSavedAt = now
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
                openedAt = System.currentTimeMillis(),
            )
        }
    }

    DisposableEffect(activity, uri, session.navigator.value) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val locator = session.navigator.value?.currentLocator?.value ?: return@LifecycleEventObserver
                coroutineScope.launch {
                    val locatorJson = locator.toJSON().toString()
                    val progress = locator.locations.totalProgression?.toFloat()
                        ?.coerceIn(0f, 1f) ?: session.progress.value
                    session.progress.value = progress
                    withContext(Dispatchers.IO) {
                        settings.apply(SettingsRepository.Store.LIBRARY) {
                            putString(ReaderActivity.locatorPreferenceKey(uri), locatorJson)
                        }
                        viewModel.saveProgress(
                            uri = uri.toString(),
                            locatorJson = locatorJson,
                            progress = progress,
                            openedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    return buildEpubReaderSessionActions(
        coroutineScope,
        context,
        uri,
        title,
        settings,
        session,
        onRequestNotificationPermission,
    )
}
