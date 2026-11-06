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

internal data class EpubPageTurnFrame(val bitmap: Bitmap, val isForward: Boolean, val id: Int)

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
    var frame by remember { mutableStateOf<EpubPageTurnFrame?>(null) }

    LaunchedEffect(navigator, pageView, enabled, preferences, autoFollowingNarration) {
        val reader = navigator ?: return@LaunchedEffect
        val view = pageView ?: return@LaunchedEffect
        if (!enabled || autoFollowingNarration) return@LaunchedEffect

        var cachedPage: Bitmap? = null
        var previousLocator = reader.currentLocator.value
        var nextFrameId = 0
        try {
            repeat(3) { withFrameNanos { } }
            cachedPage = capturePage(view)
            reader.currentLocator.collectLatest { locator ->
                val prior = previousLocator
                previousLocator = locator
                if (prior == locator) return@collectLatest

                if (skipNextTurn.value) {
                    skipNextTurn.value = false
                    cachedPage?.let { if (!it.isRecycled) it.recycle() }
                    cachedPage = null
                    delay(PAGE_TURN_RECAPTURE_DELAY_MS)
                    cachedPage = capturePage(view)
                    return@collectLatest
                }

                val outgoingPage = cachedPage
                if (outgoingPage == null) {
                    delay(PAGE_TURN_RECAPTURE_DELAY_MS)
                    cachedPage = capturePage(view)
                    return@collectLatest
                }

                cachedPage = null
                val previousProgress = prior.locations.totalProgression
                val currentProgress = locator.locations.totalProgression
                val isForward = if (previousProgress != null && currentProgress != null) {
                    currentProgress >= previousProgress
                } else {
                    true
                }
                val activeFrame = EpubPageTurnFrame(outgoingPage, isForward, nextFrameId++)
                frame = activeFrame
                try {
                    delay(PAGE_TURN_ANIMATION_MS + PAGE_TURN_RECAPTURE_DELAY_MS)
                } finally {
                    if (frame?.id == activeFrame.id) frame = null
                }
                withFrameNanos { }
                cachedPage = capturePage(view)
            }
        } finally {
            frame = null
            cachedPage?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    return frame
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
        onDispose { if (!frame.bitmap.isRecycled) frame.bitmap.recycle() }
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
                    transformOrigin = TransformOrigin(if (frame.isForward) 0f else 1f, 0.5f)
                    cameraDistance = 16f * density
                }
                .drawWithContent {
                    drawContent()
                    val shadow = (abs(rotation.value) / PAGE_TURN_ANGLE).coerceIn(0f, 1f) * 0.28f
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

private const val MAX_SNAPSHOT_PIXELS = 1_500_000L
private const val PAGE_TURN_ANIMATION_MS = 420L
private const val PAGE_TURN_RECAPTURE_DELAY_MS = 60L
private const val PAGE_TURN_ANGLE = 86f
