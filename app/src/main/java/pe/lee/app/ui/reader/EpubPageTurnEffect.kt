@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator

internal class EpubPageTurnController {
    var onStart: ((Boolean) -> Unit)? = null

    fun start(isForward: Boolean) {
        onStart?.invoke(isForward)
    }
}

/** Keeps one page snapshot and flips it away whenever a paginated locator changes. */
@Composable
internal fun rememberEpubPageTurnFrame(
    navigator: EpubNavigatorFragment?,
    pageView: View?,
    enabled: Boolean,
    preferences: EpubPreferences,
    autoFollowingNarration: Boolean,
    skipNextTurn: MutableState<Boolean>,
    controller: EpubPageTurnController,
): EpubPageTurnFrame? {
    val frame = remember { mutableStateOf<EpubPageTurnFrame?>(null) }
    val manualTurnInProgress = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(controller, pageView, enabled, autoFollowingNarration) {
        val view = pageView
        controller.onStart = if (view != null && enabled && !autoFollowingNarration) {
            { isForward ->
                val snapshot = capturePage(view)
                if (snapshot != null) {
                    frame.value?.bitmap.recycleIfNeeded()
                    val activeFrame = EpubPageTurnFrame(snapshot, isForward, frame.value?.id?.plus(1) ?: 0)
                    manualTurnInProgress.value = true
                    frame.value = activeFrame
                    scope.launch {
                        delay(PAGE_TURN_ANIMATION_MS + PAGE_TURN_RECAPTURE_DELAY_MS)
                        if (frame.value?.id == activeFrame.id) frame.value = null
                        manualTurnInProgress.value = false
                    }
                }
            }
        } else {
            null
        }
        onDispose {
            if (controller.onStart != null) controller.onStart = null
        }
    }

    LaunchedEffect(navigator, pageView, enabled, preferences, autoFollowingNarration) {
        val reader = navigator ?: return@LaunchedEffect
        val view = pageView ?: return@LaunchedEffect
        if (!enabled || autoFollowingNarration) return@LaunchedEffect

        var cachedPage: Bitmap? = null
        var previousLocator = reader.currentLocator.value
        var nextFrameId = 0
        try {
            repeat(PAGE_TURN_SNAPSHOT_STABILIZATION_FRAMES) { withFrameNanos { } }
            cachedPage = capturePage(view)
            reader.currentLocator.collectLatest { locator ->
                val prior = previousLocator
                previousLocator = locator
                if (prior == locator) return@collectLatest

                if (manualTurnInProgress.value) {
                    manualTurnInProgress.value = false
                    cachedPage?.recycleIfNeeded()
                    cachedPage = null
                    delay(PAGE_TURN_RECAPTURE_DELAY_MS)
                    cachedPage = capturePage(view)
                    return@collectLatest
                }

                val outgoingPage = cachedPage
                cachedPage = null
                val (updatedPage, updatedFrameId) = processPageTurn(
                    prior = prior,
                    current = locator,
                    outgoingPage = outgoingPage,
                    view = view,
                    frame = frame,
                    skipNextTurn = skipNextTurn,
                    nextFrameId = nextFrameId,
                )
                cachedPage = updatedPage
                nextFrameId = updatedFrameId
            }
        } finally {
            frame.value = null
            cachedPage?.recycleIfNeeded()
        }
    }

    return frame.value
}

private suspend fun processPageTurn(
    prior: Locator,
    current: Locator,
    outgoingPage: Bitmap?,
    view: View,
    frame: MutableState<EpubPageTurnFrame?>,
    skipNextTurn: MutableState<Boolean>,
    nextFrameId: Int,
): Pair<Bitmap?, Int> {
    if (skipNextTurn.value || outgoingPage == null) {
        if (skipNextTurn.value) {
            skipNextTurn.value = false
            outgoingPage.recycleIfNeeded()
        }
        delay(PAGE_TURN_RECAPTURE_DELAY_MS)
        return capturePage(view) to nextFrameId
    }

    val previousProgress = prior.locations.totalProgression
    val currentProgress = current.locations.totalProgression
    val isForward = if (previousProgress != null && currentProgress != null) {
        currentProgress >= previousProgress
    } else {
        true
    }
    val activeFrame = EpubPageTurnFrame(outgoingPage, isForward, nextFrameId)
    frame.value = activeFrame
    try {
        delay(PAGE_TURN_ANIMATION_MS + PAGE_TURN_RECAPTURE_DELAY_MS)
    } finally {
        if (frame.value?.id == activeFrame.id) frame.value = null
    }
    withFrameNanos { }
    return capturePage(view) to nextFrameId + 1
}

/** Draws the outgoing page as a physical sheet turning around the book spine. */
@Composable
internal fun EpubPageTurnOverlay(frame: EpubPageTurnFrame, modifier: Modifier = Modifier) {
    val rotation = remember(frame.id) { Animatable(0f) }
    val density = LocalDensity.current.density
    val image = remember(frame.bitmap) { frame.bitmap.asImageBitmap() }

    LaunchedEffect(frame.id) {
        rotation.animateTo(
            targetValue = if (frame.isForward) -PAGE_TURN_FULL_TURN else PAGE_TURN_FULL_TURN,
            animationSpec = tween(
                durationMillis = PAGE_TURN_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
        )
    }
    DisposableEffect(frame.bitmap) {
        onDispose { frame.bitmap.recycleIfNeeded() }
    }

    val turn = rotation.value
    val absoluteTurn = kotlin.math.abs(turn)
    val edgeLight = sin(Math.toRadians(absoluteTurn.toDouble())).toFloat().coerceIn(0f, 1f)
    val shadowAlpha = edgeLight * PAGE_TURN_MAX_SHADOW_ALPHA
    val hinge = if (frame.isForward) 0f else 1f

    Box(modifier.fillMaxSize().clipToBounds()) {
        // A soft cast shadow anchors the sheet to the page underneath it.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = if (frame.isForward) -PAGE_TURN_SHADOW_OFFSET * density else PAGE_TURN_SHADOW_OFFSET * density
                    scaleX = 1f - edgeLight * PAGE_TURN_SHADOW_SCALE
                    transformOrigin = TransformOrigin(hinge, PAGE_TURN_VERTICAL_CENTER)
                },
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shadowAlpha)))
        }

        // Front of the sheet: it becomes edge-on at 90 degrees.
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = turn
                    alpha = (1f - absoluteTurn / 90f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(hinge, PAGE_TURN_VERTICAL_CENTER)
                    cameraDistance = PAGE_TURN_CAMERA_DISTANCE_MULTIPLIER * density
                },
        )

        // Reverse of the paper. The reverse is deliberately warm and subtle;
        // the faint mirrored ink prevents the black/empty-card look at 90-180°.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = turn + if (frame.isForward) PAGE_TURN_FULL_TURN else -PAGE_TURN_FULL_TURN
                    transformOrigin = TransformOrigin(hinge, PAGE_TURN_VERTICAL_CENTER)
                    cameraDistance = PAGE_TURN_CAMERA_DISTANCE_MULTIPLIER * density
                    alpha = (absoluteTurn / PAGE_TURN_FULL_TURN).coerceIn(0f, 1f)
                },
        ) {
            Box(Modifier.fillMaxSize().background(PAGE_TURN_PAPER_COLOR))
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = -1f
                        alpha = PAGE_TURN_BACK_CONTENT_ALPHA
                    },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = edgeLight * PAGE_TURN_EDGE_HIGHLIGHT_ALPHA)),
            )
        }
    }
}

private fun capturePage(view: View): Bitmap? {
    val viewWidth = view.width
    val viewHeight = view.height
    if (viewWidth <= 0 || viewHeight <= 0) return null

    return try {
        val scale = minOf(
            1f,
            sqrt(MAX_SNAPSHOT_PIXELS.toFloat() / (viewWidth.toLong() * viewHeight).toFloat()),
        )
        val bitmap = Bitmap.createBitmap(
            (viewWidth * scale).toInt().coerceAtLeast(1),
            (viewHeight * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        view.draw(canvas)
        bitmap
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun Bitmap?.recycleIfNeeded() {
    if (this != null && !isRecycled) recycle()
}

private const val MAX_SNAPSHOT_PIXELS = 1_500_000L
private const val PAGE_TURN_ANIMATION_MS = 560L
private const val PAGE_TURN_RECAPTURE_DELAY_MS = 60L
private const val PAGE_TURN_FULL_TURN = 180f
private const val PAGE_TURN_SNAPSHOT_STABILIZATION_FRAMES = 3
private const val PAGE_TURN_VERTICAL_CENTER = 0.5f
private const val PAGE_TURN_CAMERA_DISTANCE_MULTIPLIER = 24f
private const val PAGE_TURN_MAX_SHADOW_ALPHA = 0.22f
private const val PAGE_TURN_SHADOW_OFFSET = 12f
private const val PAGE_TURN_SHADOW_SCALE = 0.06f
private const val PAGE_TURN_BACK_CONTENT_ALPHA = 0.12f
private const val PAGE_TURN_EDGE_HIGHLIGHT_ALPHA = 0.16f
private val PAGE_TURN_PAPER_COLOR = Color(0xFFF6F0E4)
