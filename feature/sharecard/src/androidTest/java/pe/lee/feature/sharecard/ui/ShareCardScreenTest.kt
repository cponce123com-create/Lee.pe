package pe.lee.feature.sharecard.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster
import pe.lee.feature.sharecard.R
import java.util.concurrent.atomic.AtomicReference

class ShareCardScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysCitationThemesAndShareAction() {
        val viewModel = createViewModel(QuoteCard("Cita de prueba", "Libro de prueba", "Autora"))
        composeRule.setContent { ShareCardScreen(viewModel = viewModel, onBack = {}) }

        composeRule.onNodeWithText("Cita de prueba").assertExists()
        listOf(
            R.string.share_card_theme_minimal,
            R.string.share_card_theme_editorial,
            R.string.share_card_theme_premium,
            R.string.share_card_theme_paper,
            R.string.share_card_theme_night,
            R.string.share_card_theme_accent
        ).forEach { resourceId ->
            composeRule.onNodeWithText(text(resourceId)).assertExists()
        }
        composeRule.onNodeWithText(text(R.string.share_card_share)).assertExists()
    }

    @Test
    fun disablesShareForBlankQuoteAndChangesSelectedTheme() {
        val viewModel = createViewModel(QuoteCard("   ", "Libro", "Autora"))
        composeRule.setContent { ShareCardScreen(viewModel = viewModel, onBack = {}) }

        composeRule.onNodeWithText(text(R.string.share_card_share)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.share_card_theme_night))
            .performScrollTo()
            .assertIsNotSelected()
            .performClick()
            .assertIsSelected()
        composeRule.runOnIdle { assertEquals(QuoteCardThemeId.DARK_OLED, viewModel.state.value.card.themeId) }
    }

    @Test
    fun shareButtonCapturesOnlyTheCardAndHandsOffPng() {
        val captured = AtomicReference<QuoteCardRaster?>(null)
        val handedOff = AtomicReference(false)
        val viewModel = ShareCardViewModel(
            initialCard = QuoteCard("Cita", "Libro", "Autora"),
            generator = ShareCardGenerator { _, raster, _ ->
                captured.set(raster)
                byteArrayOf(1, 2, 3)
            },
            sharer = ShareCardSharer { handedOff.set(true) }
        )
        composeRule.setContent { ShareCardScreen(viewModel = viewModel, onBack = {}) }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.share_card_share))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(30_000) { viewModel.state.value.status == ShareCardStatus.Ready || viewModel.state.value.status is ShareCardStatus.Error }

        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertEquals(1080, captured.get()?.width)
        assertEquals(1920, captured.get()?.height)
        assertTrue(handedOff.get())
    }

    private fun createViewModel(card: QuoteCard) = ShareCardViewModel(
        initialCard = card,
        generator = ShareCardGenerator { _, _, _ -> byteArrayOf(1) },
        sharer = ShareCardSharer { }
    )

    private fun text(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
