@file:Suppress("MagicNumber")

package pe.lee.app.ui.reader

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Leave taps and vertical movement with Readium; intercept only an inward edge swipe. */
internal class PageCurlViewport(context: Context, private val controller: EpubPageTurnController) : FrameLayout(context) {
    var onNavigate: ((Boolean) -> Boolean)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var startX = 0f
    private var startY = 0f
    private var candidate = false
    private var dragging = false
    private var blocking = false
    private var forward = true
    private var progress = 0f
    private var velocity: VelocityTracker? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val intercept = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                forward = startX >= width * .88f
                candidate = forward || startX <= width * .12f
                blocking = candidate && controller.isBusy?.invoke() == true
                blocking
            }
            MotionEvent.ACTION_MOVE -> candidate && event.pointerCount == 1 && startDragIfHorizontal(event)
            MotionEvent.ACTION_POINTER_DOWN -> {
                candidate = false
                false
            }
            else -> false
        }
        return intercept || super.onInterceptTouchEvent(event)
    }

    private fun startDragIfHorizontal(event: MotionEvent): Boolean {
        val inward = inwardDistance(event.x)
        val vertical = abs(event.y - startY)
        if (vertical > slop && vertical >= inward) candidate = false
        if (!candidate || inward <= slop || inward <= vertical * 1.35f) return false

        candidate = false
        val started = onNavigate != null && controller.beginDrag(forward)
        if (started) {
            dragging = true
            progress = (inward / width.coerceAtLeast(1)).coerceIn(0f, 1f)
            controller.updateDrag(progress)
            velocity = VelocityTracker.obtain().apply { addMovement(event) }
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return started
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when {
        blocking -> {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) blocking = false
            true
        }
        dragging -> {
            handleDrag(event)
            true
        }
        else -> super.onTouchEvent(event)
    }

    private fun handleDrag(event: MotionEvent) {
        velocity?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                progress = (inwardDistance(event.x) / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                controller.updateDrag(progress)
            }
            MotionEvent.ACTION_UP -> {
                velocity?.computeCurrentVelocity(1000)
                val speed = (velocity?.xVelocity ?: 0f) * if (forward) -1 else 1
                finish(shouldCommitPageCurl(progress, speed, width))
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> finish(false)
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) {
            if (dragging) finish(false)
            controller.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        if (dragging) finish(false)
        super.onDetachedFromWindow()
    }

    private fun inwardDistance(x: Float): Float = if (forward) startX - x else x - startX

    private fun finish(commit: Boolean) {
        dragging = false
        velocity?.recycle()
        velocity = null
        controller.endDrag(commit, onNavigate)
        parent?.requestDisallowInterceptTouchEvent(false)
    }
}
