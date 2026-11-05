package pe.lee.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderInsetsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun safeDrawingInsetsKeepTopAndBottomContentInsideSystemBars() {
        var topInset = 0f
        var bottomInset = 0f
        composeRule.setContent {
            val density = LocalDensity.current
            topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
            bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat()
            Column(
                Modifier.fillMaxSize().readerSafeDrawingInsets(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Back", Modifier.testTag("reader-back"))
                Text("Bottom controls", Modifier.testTag("reader-bottom"))
            }
        }
        composeRule.waitForIdle()
        val root = composeRule.onNodeWithTag("reader-back").fetchSemanticsNode().boundsInRoot
        val bottom = composeRule.onNodeWithTag("reader-bottom").fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("La barra superior invade el área segura", root.top >= topInset)
        assertTrue(
            "Los controles inferiores invaden la barra de navegación",
            bottom.bottom <= rootBounds.bottom - bottomInset,
        )
    }
}
