package pe.lee.feature.sharecard.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.ui.components.QuoteCardCanvas
import java.util.concurrent.atomic.AtomicReference

class QuoteCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longParagraphFitsStoryAndPreservesOriginalText() {
        val text = "Leer nos permite descubrir otras vidas, recordar y mirar el mundo con nuevos ojos. ".repeat(5)
        val fits = AtomicReference<Boolean?>(null)
        compose.setContent {
            QuoteCardCanvas(QuoteCard(text, "Título del libro", "Autora"), Modifier.fillMaxSize(), { fits.set(it) })
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
                QuoteCard("Texto extenso. ".repeat(1000), "Libro", "Autor", format = QuoteCardFormat.SQUARE),
                Modifier.fillMaxSize(),
                { fits.set(it) }
            )
        }
        compose.waitForIdle()
        assertEquals(false, fits.get())
    }
}
