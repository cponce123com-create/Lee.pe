package pe.lee.app.ui.reader

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pe.lee.app.R

@RunWith(AndroidJUnit4::class)
class TtsMiniControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun activeNarrationKeepsPauseAndStopActionsAccessible() {
        composeRule.setContent { TtsMiniControls(isPlaying = true, onTogglePlayback = {}, onStop = {}) }
        val appContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.reader_tts_pause_voice)).assertHasClickAction()
        composeRule.onNodeWithContentDescription(appContext.getString(R.string.reader_tts_stop)).assertHasClickAction()
    }
}
