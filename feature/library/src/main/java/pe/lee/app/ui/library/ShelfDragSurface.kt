package pe.lee.app.ui.library

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Gesture ownership stays on the parent when a preview reparents an object to another shelf. */
@Stable
internal class ShelfDragState {
    val bounds = mutableMapOf<String, Rect>()
    var activeKey by mutableStateOf<String?>(null)
        private set
    var initialBounds by mutableStateOf(Rect.Zero)
        private set
    var delta by mutableStateOf(Offset.Zero)
        private set
    var origin by mutableStateOf(Offset.Zero)
    private var lastTarget: String? = null

    val center: Offset get() = initialBounds.center + delta

    fun start(localPoint: Offset) {
        val rootPoint = localPoint + origin
        val target = bounds.entries.firstOrNull { !it.key.startsWith("zone:") && it.value.contains(rootPoint) } ?: return
        activeKey = target.key
        initialBounds = target.value
        delta = Offset.Zero
        lastTarget = null
    }

    fun move(change: Offset, onHover: (String, String) -> Unit) {
        val active = activeKey ?: return
        delta += change
        val target = bounds.entries.filter { it.key != active && it.value.contains(center) }
            .minByOrNull { it.value.width * it.value.height }?.key
        if (target != null && target != lastTarget) onHover(active, target)
        lastTarget = target
    }

    fun reset() {
        activeKey = null
        delta = Offset.Zero
        lastTarget = null
    }
}

@Composable
internal fun ShelfDragState.itemModifier(key: String): Modifier {
    DisposableEffect(this, key) { onDispose { bounds.remove(key) } }
    return Modifier.onGloballyPositioned { bounds[key] = it.boundsInRoot() }
        .graphicsLayer { alpha = if (activeKey == key) 0f else 1f }
}

@Composable
internal fun ShelfDragState.targetModifier(key: String): Modifier {
    DisposableEffect(this, key) { onDispose { bounds.remove(key) } }
    return Modifier.onGloballyPositioned { bounds[key] = it.boundsInRoot() }
}

@Composable
internal fun ShelfDragSurface(
    state: ShelfDragState,
    onStart: () -> Unit,
    onHover: (String, String) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onDragPosition: (Offset) -> Unit = {},
    overlay: @Composable (String) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val start by rememberUpdatedState(onStart)
    val hover by rememberUpdatedState(onHover)
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onCancel)
    val position by rememberUpdatedState(onDragPosition)
    val pixelDensity = LocalDensity.current
    Box(
        modifier.onGloballyPositioned { state.origin = it.boundsInRoot().topLeft }
            .pointerInput(state) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        state.start(it)
                        if (state.activeKey != null) start()
                    },
                    onDrag = { change, amount ->
                        if (state.activeKey != null) {
                            change.consume()
                            state.move(amount, hover)
                            position(state.center - state.origin)
                        }
                    },
                    onDragEnd = {
                        if (state.activeKey != null) drop()
                        state.reset()
                    },
                    onDragCancel = {
                        if (state.activeKey != null) cancel()
                        state.reset()
                    },
                )
            },
    ) {
        content()
        state.activeKey?.let { key ->
            val bounds = state.initialBounds
            Box(
                Modifier.zIndex(100f)
                    .offset {
                        val p = bounds.topLeft + state.delta - state.origin
                        IntOffset(p.x.roundToInt(), p.y.roundToInt())
                    }
                    .size(with(pixelDensity) { bounds.width.toDp() }, with(pixelDensity) { bounds.height.toDp() })
                    .graphicsLayer {
                        scaleX = 1.07f
                        scaleY = 1.07f
                        rotationZ = -2f
                        shadowElevation = 16f * pixelDensity.density
                    },
            ) { overlay(key) }
        }
    }
}
