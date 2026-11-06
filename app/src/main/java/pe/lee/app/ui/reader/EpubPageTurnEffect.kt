@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package pe.lee.app.ui.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Locator

/** Keeps one page snapshot and flips it away whenever a paginated locator changes. */
@Composable
internal fun rememberEpubPageTurnFrame(
    navigator: EpubNavigatorFragment?,
    pageView: View?,
    enabled: Boolean,
    preferences: EpubPreferences,
    autoFollowingNarration: Boolean,
    skipNextTurn: MutableState<Boolean>,
): EpubPageTurnFrame? {
    val frame = remember { mutableStateOf<EpubPageTurnFrame?>(null) }

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

/** Draws the outgoing page with perspective and a moving crease shadow. */
@Composable
internal fun EpubPageTurnOverlay(frame: EpubPageTurnFrame, modifier: Modifier = Modifier) {
    val rotation = remember(frame.id) { Animatable(0f) }
    val density = LocalDensity.current.density
    val image = remember(frame.bitmap) { frame.bitmap.asImageBitmap() }

    LaunchedEffect(frame.id) {
        rotation.animateTo(
            targetValue = if (frame.isForward) -PAGE_TURN_ANGLE else PAGE_TURN_ANGLE,
            animationSpec = tween(
                durationMillis = PAGE_TURN_ANIMATION_MS.toInt(),
                easing = FastOutSlowInEasing,
            ),
        )
    }
    DisposableEffect(frame.bitmap) {
        onDispose { frame.bitmap.recycleIfNeeded() }
    }

    Box(modifier.fillMaxSize().clipToBounds()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = if (frame.isForward) -rotation.value else rotation.value
                    transformOrigin = TransformOrigin(
                        if (frame.isForward) 0f else 1f,
                        PAGE_TURN_VERTICAL_CENTER,
                    )
                    cameraDistance = PAGE_TURN_CAMERA_DISTANCE_MULTIPLIER * density
                }
                .drawWithContent {
                    drawContent()
                    val shadow = (abs(rotation.value) / PAGE_TURN_ANGLE).coerceIn(0f, 1f) *
                        PAGE_TURN_MAX_SHADOW_ALPHA
                    val colors = if (frame.isForward) {
                        listOf(Color.Black.copy(alpha = shadow), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, Color.Black.copy(alpha = shadow))
                    }
                    drawRect(Brush.horizontalGradient(colors), size = size)
                },
        )
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
private const val PAGE_TURN_ANIMATION_MS = 420L
private const val PAGE_TURN_RECAPTURE_DELAY_MS = 60L
private const val PAGE_TURN_ANGLE = 86f
private const val PAGE_TURN_SNAPSHOT_STABILIZATION_FRAMES = 3
private const val PAGE_TURN_VERTICAL_CENTER = 0.5f
private const val PAGE_TURN_CAMERA_DISTANCE_MULTIPLIER = 16f
private const val PAGE_TURN_MAX_SHADOW_ALPHA = 0.28f
