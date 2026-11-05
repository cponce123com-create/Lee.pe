package pe.lee.app.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsChromeVisibilityTest {
    @Test fun firstPlayHidesChromeOnlyWithoutTouchExploration() {
        assertTrue(shouldHideReaderChromeAfterTtsStart(isFirstPlay = true, touchExplorationEnabled = false))
        assertFalse(shouldHideReaderChromeAfterTtsStart(isFirstPlay = true, touchExplorationEnabled = true))
    }

    @Test fun resumeNeverHidesChrome() {
        assertFalse(shouldHideReaderChromeAfterTtsStart(isFirstPlay = false, touchExplorationEnabled = false))
        assertFalse(shouldHideReaderChromeAfterTtsStart(isFirstPlay = false, touchExplorationEnabled = true))
    }
}
