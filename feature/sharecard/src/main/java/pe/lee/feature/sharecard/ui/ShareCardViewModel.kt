package pe.lee.feature.sharecard.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster

/** Progress of PNG generation and handoff to Android's share sheet. */
sealed interface ShareCardStatus {
    data object Idle : ShareCardStatus
    data object Generating : ShareCardStatus
    data object Ready : ShareCardStatus
    data class Error(val message: String) : ShareCardStatus
}

/** Immutable screen state exposed to Compose. */
data class ShareCardUiState(
    val card: QuoteCard,
    val status: ShareCardStatus = ShareCardStatus.Idle
)

/** Owns editable card state and performs pixel conversion and encoding off the UI thread. */
class ShareCardViewModel(
    initialCard: QuoteCard,
    private val generator: ShareCardGenerator,
    private val sharer: ShareCardSharer,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val mutableState = MutableStateFlow(ShareCardUiState(initialCard))
    val state = mutableState.asStateFlow()

    /** Replaces the quote displayed on the card. */
    fun updateText(text: String) = mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(text = text), status = ShareCardStatus.Idle) }

    /** Updates the book title printed in the card footer. */
    fun updateBookTitle(title: String) = mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(bookTitle = title), status = ShareCardStatus.Idle) }

    /** Updates the author printed in the card footer. */
    fun updateAuthor(author: String) = mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(author = author), status = ShareCardStatus.Idle) }

    /** Selects the visual theme used by both preview and exported image. */
    fun selectTheme(theme: pe.lee.feature.sharecard.domain.model.QuoteCardThemeId) =
        mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(themeId = theme), status = ShareCardStatus.Idle) }

    /** Overrides the template font with an installed system family. */
    fun selectFont(font: pe.lee.feature.sharecard.domain.model.QuoteCardFont) =
        mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(font = font), status = ShareCardStatus.Idle) }

    /** Selects the story or square image proportions. */
    fun selectFormat(format: pe.lee.feature.sharecard.domain.model.QuoteCardFormat) =
        mutableState.update { if (it.status == ShareCardStatus.Generating) it else it.copy(card = it.card.copy(format = format), status = ShareCardStatus.Idle) }

    /** Captures on the UI thread; converts pixels and encodes off the UI thread. */
    fun shareCapturedImage(capture: suspend () -> ImageBitmap) = startSharing {
        val image = capture()
        image.toRaster()
    }

    /** Converts a bitmap captured on Compose's UI coroutine and shares it off the main thread. */
    fun shareImage(image: ImageBitmap) = startSharing { image.toRaster() }

    /** Reports a failed UI capture so the user can retry instead of being left waiting. */
    fun shareCaptureFailed() {
        mutableState.update {
            it.copy(status = ShareCardStatus.Error(SHARE_CARD_ERROR_MESSAGE))
        }
    }

    internal fun shareRasterForTest(raster: QuoteCardRaster) = startSharing { raster }

    private fun startSharing(capture: suspend () -> QuoteCardRaster) {
        val current = mutableState.value
        if (current.card.text.isBlank() || current.status == ShareCardStatus.Generating) return
        // Reserve synchronously, before launch, so rapid taps cannot enqueue duplicate exports.
        if (!mutableState.compareAndSet(current, current.copy(status = ShareCardStatus.Generating))) return
        viewModelScope.launch {
            try {
                // The frame may never arrive (app backgrounded, window destroyed,
                // rotation mid-capture): never leave the button spinning forever.
                withTimeout(SHARE_TIMEOUT_MS) {
                    generateAndShare(current.card, capture())
                }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(status = ShareCardStatus.Error(SHARE_TIMEOUT_MESSAGE)) }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(status = ShareCardStatus.Idle) }
                throw cancelled
            } catch (_: OutOfMemoryError) {
                // A low-end device can run out of memory while scaling the card.
                // This is an Error, not an Exception: without this branch the app
                // would crash instead of showing a message (A-21).
                mutableState.update {
                    it.copy(status = ShareCardStatus.Error(SHARE_MEMORY_MESSAGE))
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(status = ShareCardStatus.Error(SHARE_CARD_ERROR_MESSAGE))
                }
            } finally {
                // Defensive: a capture that completed after cancellation must not
                // keep the card disabled.
                mutableState.update {
                    if (it.status == ShareCardStatus.Generating) it.copy(status = ShareCardStatus.Idle) else it
                }
            }
        }
    }

    private suspend fun ImageBitmap.toRaster(): QuoteCardRaster = withContext(workerDispatcher) {
        // toPixelMap() already copies the frame once; its backing buffer holds
        // exactly the ARGB ints Bitmap.createBitmap needs (A-21).
        val pixels = toPixelMap()
        QuoteCardRaster(width, height, pixels.buffer)
    }

    private suspend fun generateAndShare(card: QuoteCard, raster: QuoteCardRaster) {
        val png = generator.generate(card, raster, card.format)
        sharer.share(png)
        mutableState.update { it.copy(status = ShareCardStatus.Ready) }
    }

    private companion object {
        const val SHARE_CARD_ERROR_MESSAGE =
            "No se pudo generar o compartir la tarjeta. Revisa el espacio disponible e inténtalo otra vez."
        const val SHARE_TIMEOUT_MESSAGE =
            "La captura de la tarjeta tardó demasiado. Vuelve a intentarlo."
        const val SHARE_MEMORY_MESSAGE =
            "No hay memoria suficiente para generar la tarjeta. Cierra otras apps e inténtalo otra vez."
        const val SHARE_TIMEOUT_MS = 15_000L
    }
}
