package pe.lee.app.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EpubPageTurnControllerTest {
    @Test
    fun disabledEffectNavigatesOnceAndLeavesDragToTheReader() {
        val controller = EpubPageTurnController()
        var navigations = 0

        controller.turn(isForward = true) {
            navigations++
            true
        }

        assertEquals(1, navigations)
        assertFalse(controller.beginDrag(forward = true))
    }
}
