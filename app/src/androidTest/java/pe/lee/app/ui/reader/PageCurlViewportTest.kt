package pe.lee.app.ui.reader

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlViewportTest {
    @Test
    fun verticalEdgeMotionStaysWithNavigator() = withViewport { viewport, controller ->
        controller.onBeginDrag = { error("Vertical gesture must not start a curl") }
        val start = SystemClock.uptimeMillis()
        send(start, 0, MotionEvent.ACTION_DOWN, 390f, 100f, viewport::onInterceptTouchEvent)
        assertFalse(send(start, 60, MotionEvent.ACTION_MOVE, 370f, 200f, viewport::onInterceptTouchEvent))
    }

    @Test
    fun releasingBeforeThresholdCancelsButLongDragCommits() = withViewport { viewport, controller ->
        val completions = mutableListOf<Boolean>()
        controller.onBeginDrag = { it }
        controller.onEndDrag = { commit, _ -> completions += commit }

        for (endX in listOf(325f, 220f)) {
            val start = SystemClock.uptimeMillis()
            send(start, 0, MotionEvent.ACTION_DOWN, 390f, 100f, viewport::onInterceptTouchEvent)
            assertTrue(send(start, 100, MotionEvent.ACTION_MOVE, 335f, 100f, viewport::onInterceptTouchEvent))
            send(start, 500, MotionEvent.ACTION_MOVE, endX, 100f, viewport::onTouchEvent)
            send(start, 750, MotionEvent.ACTION_UP, endX, 100f, viewport::onTouchEvent)
        }

        assertEquals(listOf(false, true), completions)
    }

    @Test
    fun swipeOverTextCancelsChildBeforeItCanScroll() = withViewport { viewport, controller ->
        val received = mutableListOf<Int>()
        val child = View(viewport.context).apply {
            setOnTouchListener { view, event ->
                received += event.actionMasked
                view.parent.requestDisallowInterceptTouchEvent(true)
                true
            }
        }
        viewport.addView(child)
        child.layout(0, 0, 400, 800)
        var forward: Boolean? = null
        controller.onBeginDrag = {
            forward = it
            true
        }
        val start = SystemClock.uptimeMillis()
        send(start, 0, MotionEvent.ACTION_DOWN, 200f, 100f, viewport::dispatchTouchEvent)
        send(start, 60, MotionEvent.ACTION_MOVE, 285f, 100f, viewport::dispatchTouchEvent)
        send(start, 90, MotionEvent.ACTION_CANCEL, 285f, 100f, viewport::dispatchTouchEvent)

        assertEquals(false, forward)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), received)
    }

    private fun withViewport(block: (PageCurlViewport, EpubPageTurnController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val controller = EpubPageTurnController()
            val viewport = PageCurlViewport(instrumentation.targetContext, controller)
            viewport.layout(0, 0, 400, 800)
            viewport.onNavigate = { true }
            block(viewport, controller)
        }
    }

    private fun send(start: Long, elapsed: Long, action: Int, x: Float, y: Float, dispatch: (MotionEvent) -> Boolean): Boolean {
        val event = MotionEvent.obtain(start, start + elapsed, action, x, y, 0)
        return try {
            dispatch(event)
        } finally {
            event.recycle()
        }
    }
}
