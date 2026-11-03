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
import kotlinx.coroutines.withContext
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
    fun updateText(text: String) = mutableState.update { it.copy(card = it.card.copy(text = text), status = ShareCardStatus.Idle) }

    /** Updates the book title printed in the card footer. */
    fun updateBookTitle(title: String) = mutableState.update { it.copy(card = it.card.copy(bookTitle = title), status = ShareCardStatus.Idle) }

    /** Updates the author printed in the card footer. */
    fun updateAuthor(author: String) = mutableState.update { it.copy(card = it.card.copy(author = author), status = ShareCardStatus.Idle) }

    /** Selects the visual theme used by both preview and exported image. */
    fun selectTheme(theme: pe.lee.feature.sharecard.domain.model.QuoteCardThemeId) =
        mutableState.update { it.copy(card = it.card.copy(themeId = theme), status = ShareCardStatus.Idle) }

    /** Selects the story or square image proportions. */
    fun selectFormat(format: pe.lee.feature.sharecard.domain.model.QuoteCardFormat) =
        mutableState.update { it.copy(card = it.card.copy(format = format), status = ShareCardStatus.Idle) }

    /** Captures the on-screen card, encodes it off the main thread, and opens the share sheet. */
    fun shareCapturedImage(image: ImageBitmap) {
        viewModelScope.launch {
            val card = mutableState.value.card
            if (card.text.isBlank()) return@launch
            mutableState.update { it.copy(status = ShareCardStatus.Generating) }
            try {
                val raster = withContext(workerDispatcher) {
                    val pixels = image.toPixelMap()
                    val argb = IntArray(image.width * image.height) { index ->
                        pixels[index % image.width, index / image.width].toArgb()
                    }
                    QuoteCardRaster(image.width, image.height, argb)
                }
                generateAndShare(card, raster)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(status = ShareCardStatus.Error(SHARE_CARD_ERROR_MESSAGE))
                }
            }
        }
    }

    internal fun shareRasterForTest(raster: QuoteCardRaster) {
        viewModelScope.launch {
            val card = mutableState.value.card
            if (card.text.isBlank()) return@launch
            mutableState.update { it.copy(status = ShareCardStatus.Generating) }
            try {
                generateAndShare(card, raster)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(status = ShareCardStatus.Error(SHARE_CARD_ERROR_MESSAGE))
                }
            }
        }
    }

    private suspend fun generateAndShare(card: QuoteCard, raster: QuoteCardRaster) {
        val png = generator.generate(card, raster, card.format)
        sharer.share(png)
        mutableState.update { it.copy(status = ShareCardStatus.Ready) }
    }

    private companion object {
        const val SHARE_CARD_ERROR_MESSAGE =
            "No se pudo generar o compartir la tarjeta. Revisa el espacio disponible e inténtalo otra vez."
    }
}
