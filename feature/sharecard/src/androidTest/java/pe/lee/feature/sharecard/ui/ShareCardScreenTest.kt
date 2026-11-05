package pe.lee.feature.sharecard.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId

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
            R.string.share_card_theme_accent,
        ).forEach { resourceId ->
            composeRule.onNodeWithTag("quote-theme-gallery").performScrollTo().performScrollToNode(hasText(text(resourceId)))
            composeRule.onNodeWithText(text(resourceId)).assertExists()
        }
        composeRule.onNodeWithText(text(R.string.share_card_share)).assertExists()
    }

    @Test
    fun disablesShareForBlankQuoteAndChangesSelectedTheme() {
        val viewModel = createViewModel(QuoteCard("   ", "Libro", "Autora"))
        composeRule.setContent { ShareCardScreen(viewModel = viewModel, onBack = {}) }

        composeRule.onNodeWithText(text(R.string.share_card_share)).assertIsNotEnabled()
        composeRule.onNodeWithTag("quote-theme-gallery").performScrollTo()
            .performScrollToNode(hasText(text(R.string.share_card_theme_night)))
        composeRule.onNodeWithText(text(R.string.share_card_theme_night))
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
            sharer = ShareCardSharer { handedOff.set(true) },
        )
        composeRule.setContent { ShareCardScreen(viewModel = viewModel, onBack = {}) }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.share_card_share))
            .assertIsEnabled()
            .performClick()
        composeRule.waitUntil(30_000) {
            viewModel.state.value.status == ShareCardStatus.Ready ||
                viewModel.state.value.status is ShareCardStatus.Error
        }

        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertEquals(1080, captured.get()?.width)
        assertEquals(1920, captured.get()?.height)
        assertTrue(handedOff.get())
    }

    @Test
    fun premiumCardsExportFullResolutionWithAuthorAndCornerLogo() {
        val captured = AtomicReference<QuoteCardRaster?>(null)
        val viewModel = ShareCardViewModel(
            initialCard = QuoteCard("Incluso la noche más larga deja paso a un nuevo amanecer.", "Libro", "Autor de ejemplo"),
            generator = ShareCardGenerator { _, raster, _ ->
                captured.set(raster)
                byteArrayOf(1)
            },
            sharer = ShareCardSharer {},
        )
        composeRule.setContent { ShareCardScreen(viewModel, {}) }
        for ((theme, name) in listOf(
            QuoteCardThemeId.PAPYRUS to "quote-papyrus",
            QuoteCardThemeId.NIGHT_SKY to "quote-night",
            QuoteCardThemeId.SUNSET to "quote-sunset",
        )) {
            composeRule.runOnIdle { viewModel.selectTheme(theme) }
            composeRule.waitForIdle()
            val shareButton = composeRule.onNodeWithText(text(R.string.share_card_share))
            composeRule.waitUntil(30_000) {
                runCatching { shareButton.assertIsEnabled() }.isSuccess
            }
            shareButton.performClick()
            composeRule.waitUntil(30_000) { viewModel.state.value.status == ShareCardStatus.Ready }
            val raster = requireNotNull(captured.get())
            assertEquals(1080, raster.width)
            assertEquals(1920, raster.height)
            saveExportPreview(name, raster)
        }
    }

    @Test
    fun editorialThemesExportWithoutPanelInAllSupportedFormats() {
        val captured = AtomicReference<QuoteCardRaster?>(null)
        val viewModel = ShareCardViewModel(
            initialCard = QuoteCard("Hay historias que nos enseñan a volver a empezar.", "Libro", "Autor de ejemplo"),
            generator = ShareCardGenerator { _, raster, _ ->
                captured.set(raster)
                byteArrayOf(1)
            },
            sharer = ShareCardSharer {},
        )
        composeRule.setContent { ShareCardScreen(viewModel, {}) }
        val themes = pe.lee.feature.sharecard.ui.theme.editorialThemes
        val cases = themes.map { it to QuoteCardFormat.STORY } + listOf(
            QuoteCardThemeId.BOTANICAL_IVORY to QuoteCardFormat.SQUARE,
            QuoteCardThemeId.INK_MOON to QuoteCardFormat.PORTRAIT,
        )
        cases.forEach { (theme, format) ->
            captured.set(null)
            composeRule.runOnIdle {
                viewModel.selectTheme(theme)
                viewModel.selectFormat(format)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithText(text(R.string.share_card_share)).assertIsEnabled().performClick()
            composeRule.waitUntil(30_000) { captured.get() != null && viewModel.state.value.status == ShareCardStatus.Ready }
            val raster = requireNotNull(captured.get())
            assertEquals(format.widthPx, raster.width)
            assertEquals(format.heightPx, raster.height)
            if (format == QuoteCardFormat.STORY && theme == QuoteCardThemeId.GOLDEN_NIGHT) saveExportPreview("editorial-night", raster)
            if (format == QuoteCardFormat.STORY && theme == QuoteCardThemeId.BOTANICAL_IVORY) saveExportPreview("editorial-ivory", raster)
        }
    }

    private fun saveExportPreview(name: String, raster: QuoteCardRaster) {
        if (Build.VERSION.SDK_INT < 29) return
        val bitmap = Bitmap.createBitmap(raster.argbPixels, raster.width, raster.height, Bitmap.Config.ARGB_8888)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LeepeUiPreview")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun createViewModel(card: QuoteCard) = ShareCardViewModel(
        initialCard = card,
        generator = ShareCardGenerator { _, _, _ -> byteArrayOf(1) },
        sharer = ShareCardSharer { },
    )

    private fun text(resourceId: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
