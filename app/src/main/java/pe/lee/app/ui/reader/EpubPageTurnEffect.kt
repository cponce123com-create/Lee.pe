@file:Suppress("MagicNumber")
@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlin.math.sqrt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

@Suppress("CyclomaticComplexMethod")
@Composable
internal fun rememberEpubPageTurnFrame(
    navigator: EpubNavigatorFragment?,
    pageView: View?,
    enabled: Boolean,
    preferences: EpubPreferences,
    autoFollowingNarration: Boolean,
    skipNextTurn: MutableState<Boolean>,
    controller: EpubPageTurnController,
    style: PageTurnStyle,
    rightToLeft: Boolean,
): EpubPageTurnFrame? {
    val frame = remember { mutableStateOf<EpubPageTurnFrame?>(null) }
    val scope = rememberCoroutineScope()
    val animationAllowed = pageView?.let {
        Settings.Global.getFloat(it.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } == true
    val active = style != PageTurnStyle.NONE && enabled && !autoFollowingNarration && animationAllowed
    val paper = when (preferences.theme) {
        Theme.DARK -> 0xFF242323.toInt()
        Theme.SEPIA -> 0xFFF0E1C5.toInt()
        else -> 0xFFF8F5EE.toInt()
    }

    DisposableEffect(controller, navigator, pageView, active, preferences, style, rightToLeft) {
        var job: Job? = null
        fun cancel() {
            job?.cancel()
            frame.value = null
        }

        fun finish(turn: EpubPageTurnFrame, navigate: (() -> Boolean)?) {
            job = scope.launch {
                try {
                    val prior = navigator?.currentLocator?.value
                    if (navigate != null) turn.phase.value = PageTurnPhase.NAVIGATING
                    val accepted = navigate?.invoke() == true
                    val confirmed = if (accepted && navigator != null) {
                        withTimeoutOrNull(900L) {
                            navigator.currentLocator.first { it != prior }
                            repeat(2) { withFrameNanos { } }
                            true
                        } == true
                    } else {
                        false
                    }
                    if (accepted && !confirmed) return@launch
                    turn.phase.value = if (accepted) PageTurnPhase.COMPLETING else PageTurnPhase.CANCELLING
                    animatePageCurl(turn, if (accepted) 1f else 0f, if (confirmed) 440 else 220)
                } finally {
                    if (frame.value === turn) frame.value = null
                }
            }
        }

        if (active && pageView != null && navigator != null) {
            controller.onStart = { forward, navigate ->
                if (frame.value == null) {
                    val snapshot = capturePage(pageView)
                    if (snapshot == null) {
                        navigate()
                    } else {
                        val turn = EpubPageTurnFrame(snapshot, forward, paper, style, interactive = true)
                        frame.value = turn
                        finish(turn, navigate)
                    }
                }
            }
            controller.onBeginDrag = { forward ->
                val snapshot = if (frame.value == null) capturePage(pageView) else null
                if (snapshot == null) {
                    false
                } else {
                    frame.value = EpubPageTurnFrame(snapshot, forward, paper, style, interactive = true)
                    true
                }
            }
            controller.onUpdateDrag = { progress ->
                frame.value?.takeIf { it.phase.value == PageTurnPhase.DRAGGING }?.progress?.floatValue = progress
            }
            controller.onEndDrag = { commit, navigate ->
                frame.value?.takeIf { it.phase.value == PageTurnPhase.DRAGGING }?.let { turn ->
                    val action: (() -> Boolean)? = if (commit && navigate != null) ({ navigate(turn.isForward) }) else null
                    finish(turn, action)
                }
            }
        }
        controller.onCancel = ::cancel
        controller.isBusy = { frame.value != null }
        val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) cancel()
        }
        pageView?.addOnLayoutChangeListener(layoutListener)
        onDispose {
            pageView?.removeOnLayoutChangeListener(layoutListener)
            controller.onStart = null
            controller.onBeginDrag = null
            controller.onUpdateDrag = null
            controller.onEndDrag = null
            controller.onCancel = null
            controller.isBusy = null
            cancel()
        }
    }

    // Readium still owns taps, normal swipes and its accessibility actions.
    LaunchedEffect(navigator, pageView, active, preferences, style, rightToLeft) {
        val reader = navigator ?: return@LaunchedEffect
        val view = pageView ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        var prior = reader.currentLocator.value
        repeat(3) { withFrameNanos { } }
        var cached = capturePage(view)
        var cachedWidth = view.width
        var cachedHeight = view.height
        reader.currentLocator.collectLatest { locator ->
            val previous = prior
            prior = locator
            if (locator == previous) return@collectLatest
            val outgoing = cached.takeIf { cachedWidth == view.width && cachedHeight == view.height }
            cached = null
            val skip = skipNextTurn.value
            if (skip) skipNextTurn.value = false
            if (frame.value == null && !skip && outgoing != null) {
                val from = previous.locations.totalProgression
                val to = locator.locations.totalProgression
                val forward = from == null || to == null || to >= from
                val turn = EpubPageTurnFrame(outgoing, forward != rightToLeft, paper, style)
                frame.value = turn
                try {
                    animatePageCurl(turn, 1f, 480)
                } finally {
                    if (frame.value === turn) frame.value = null
                }
            }
            repeat(2) { withFrameNanos { } }
            cachedWidth = view.width
            cachedHeight = view.height
            cached = capturePage(view)
        }
    }

    return frame.value
}

private suspend fun animatePageCurl(frame: EpubPageTurnFrame, target: Float, duration: Int) {
    Animatable(frame.progress.floatValue).animateTo(target, tween(duration, easing = FastOutSlowInEasing)) {
        frame.progress.floatValue = value
    }
}

/** Release references with the frame; do not recycle a bitmap still queued by the renderer. */
private fun capturePage(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0 || !view.isAttachedToWindow) return null
    return try {
        val scale = minOf(1f, sqrt(1_200_000f / (width.toLong() * height).toFloat()))
        val bitmap = Bitmap.createBitmap(
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        view.draw(canvas)
        bitmap.takeIf { android.graphics.Color.alpha(it.getPixel(it.width / 2, it.height / 2)) > 0 }
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}
