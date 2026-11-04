package pe.lee.feature.sharecard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.ui.components.QuoteCardCanvas

class QuoteCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longParagraphFitsStoryAndPreservesOriginalText() {
        val text = "Leer nos permite descubrir otras vidas, recordar y mirar el mundo con nuevos ojos. ".repeat(5)
        val fits = AtomicReference<Boolean?>(null)
        compose.setContent {
            QuoteCardCanvas(
                card = QuoteCard(text, "Título del libro", "Autora"),
                captureModifier = Modifier,
                onFitsChanged = { fits.set(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        compose.waitForIdle()
        assertEquals(true, fits.get())
        compose.onNodeWithText(text.trim()).assertExists()
    }

    @Test
    fun unreasonablyLongQuoteIsNotMarkedExportable() {
        val fits = AtomicReference<Boolean?>(null)
        compose.setContent {
            QuoteCardCanvas(
                card = QuoteCard("Texto extenso. ".repeat(1000), "Libro", "Autor", format = QuoteCardFormat.SQUARE),
                captureModifier = Modifier,
                onFitsChanged = { fits.set(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        compose.waitForIdle()
        assertEquals(false, fits.get())
    }
}
