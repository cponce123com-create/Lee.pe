package pe.lee.feature.sharecard.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.lee.feature.sharecard.R
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFont
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
import pe.lee.feature.sharecard.domain.model.QuoteCardThemeId
import pe.lee.feature.sharecard.domain.model.QuoteCardRaster

@OptIn(ExperimentalCoroutinesApi::class)
class ShareCardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setMainDispatcher() = kotlinx.coroutines.Dispatchers.setMain(dispatcher)

    @After
    fun resetMainDispatcher() = kotlinx.coroutines.Dispatchers.resetMain()

    @Test
    fun shareTransitionsFromGeneratingToReady() = runTest(dispatcher) {
        val generated = CompletableDeferred<ByteArray>()
        var sharedBytes: ByteArray? = null
        val generator = ShareCardGenerator { _, _, _ -> generated.await() }
        val sharer = ShareCardSharer { sharedBytes = it }
        val viewModel = ShareCardViewModel(
            initialCard = QuoteCard("Una frase", "Libro", "Autora"),
            generator = generator,
            sharer = sharer,
            workerDispatcher = dispatcher,
        )

        assertEquals(ShareCardStatus.Idle, viewModel.state.value.status)
        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0xFFFFFFFF.toInt())))
        runCurrent()
        assertEquals(ShareCardStatus.Generating, viewModel.state.value.status)

        generated.complete(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertTrue(sharedBytes!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun generationFailureIsReportedAsError() = runTest(dispatcher) {
        val viewModel = ShareCardViewModel(
            initialCard = QuoteCard("Una frase", "Libro", "Autora", format = QuoteCardFormat.SQUARE),
            generator = ShareCardGenerator { _, _, _ -> error("falló") },
            sharer = ShareCardSharer { },
            workerDispatcher = dispatcher,
        )

        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0xFFFFFFFF.toInt())))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.status is ShareCardStatus.Error)
    }

    @Test
    fun captureFailureIsReportedAndCanBeRetried() = runTest(dispatcher) {
        var generated = 0
        val viewModel = ShareCardViewModel(
            QuoteCard("Frase", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ ->
                generated++
                byteArrayOf(1)
            },
            ShareCardSharer { },
            dispatcher,
        )
        viewModel.shareCapturedImage { error("capture failed") }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.status is ShareCardStatus.Error)
        assertEquals(0, generated)
        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0)))
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertEquals(1, generated)
    }

    @Test
    fun outOfMemoryIsReportedInsteadOfCrashingTheApp() = runTest(dispatcher) {
        val viewModel = ShareCardViewModel(
            QuoteCard("Frase", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ -> throw OutOfMemoryError("sin memoria") },
            ShareCardSharer { },
            dispatcher,
        )

        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0)))
        advanceUntilIdle()

        val status = viewModel.state.value.status
        assertTrue("esperaba Error, fue $status", status is ShareCardStatus.Error)
        assertEquals(R.string.share_card_memory_error, (status as ShareCardStatus.Error).messageRes)
    }

    @Test
    fun rapidTapsAndEditsDoNotDuplicateOrChangePendingExport() = runTest(dispatcher) {
        val encoded = CompletableDeferred<ByteArray>()
        var generated = 0
        var shared = 0
        val viewModel = ShareCardViewModel(
            QuoteCard("Original", "Libro", "Autora"),
            ShareCardGenerator { card, _, _ ->
                assertEquals("Original", card.text)
                generated++
                encoded.await()
            },
            ShareCardSharer { shared++ },
            dispatcher,
        )
        val raster = QuoteCardRaster(1, 1, intArrayOf(0))
        viewModel.shareRasterForTest(raster)
        viewModel.updateText("Cambio durante captura")
        viewModel.shareRasterForTest(raster)
        runCurrent()
        assertEquals(1, generated)
        assertEquals("Original", viewModel.state.value.card.text)
        encoded.complete(byteArrayOf(1))
        advanceUntilIdle()
        assertEquals(1, shared)
        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
    }

    @Test
    fun blankQuoteDoesNotStartExport() = runTest(dispatcher) {
        val viewModel = ShareCardViewModel(
            QuoteCard(" \n\t", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ -> error("Must not generate") },
            ShareCardSharer { error("Must not share") },
            dispatcher,
        )
        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0)))
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Idle, viewModel.state.value.status)
    }

    @Test
    fun stalledGenerationTimesOutAndAllowsRetry() = runTest(dispatcher) {
        var attempts = 0
        var shared = 0
        val viewModel = ShareCardViewModel(
            QuoteCard("Frase", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ ->
                if (attempts++ == 0) CompletableDeferred<ByteArray>().await() else byteArrayOf(1)
            },
            ShareCardSharer { shared++ },
            dispatcher,
        )
        val raster = QuoteCardRaster(1, 1, intArrayOf(0))
        viewModel.shareRasterForTest(raster)
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Error(R.string.share_card_timeout), viewModel.state.value.status)
        assertEquals(0, shared)
        viewModel.shareRasterForTest(raster)
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertEquals(1, shared)
    }

    @Test
    fun cancelledGenerationReturnsToIdleWithoutSharing() = runTest(dispatcher) {
        var shared = false
        val viewModel = ShareCardViewModel(
            QuoteCard("Frase", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ -> throw CancellationException("screen closed") },
            ShareCardSharer { shared = true },
            dispatcher,
        )
        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0)))
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Idle, viewModel.state.value.status)
        assertTrue(!shared)
    }

    @Test
    fun shareSheetFailureAllowsRetry() = runTest(dispatcher) {
        var attempts = 0
        val viewModel = ShareCardViewModel(
            QuoteCard("Frase", "Libro", "Autora"),
            ShareCardGenerator { _, _, _ -> byteArrayOf(1) },
            ShareCardSharer { if (attempts++ == 0) error("No receiving activity") },
            dispatcher,
        )
        val raster = QuoteCardRaster(1, 1, intArrayOf(0))
        viewModel.shareRasterForTest(raster)
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Error(R.string.share_card_error_detail), viewModel.state.value.status)
        viewModel.shareRasterForTest(raster)
        advanceUntilIdle()
        assertEquals(ShareCardStatus.Ready, viewModel.state.value.status)
        assertEquals(2, attempts)
    }

    @Test
    fun allMetadataAndStyleEditsAreFrozenUntilExportCompletes() = runTest(dispatcher) {
        val encoded = CompletableDeferred<ByteArray>()
        val original = QuoteCard("Original", "Libro", "Autora")
        val viewModel = ShareCardViewModel(
            original,
            ShareCardGenerator { card, _, format ->
                assertEquals(original, card)
                assertEquals(original.format, format)
                encoded.await()
            },
            ShareCardSharer { },
            dispatcher,
        )
        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0)))
        viewModel.updateBookTitle("Otro libro")
        viewModel.updateAuthor("Otra autora")
        viewModel.selectTheme(QuoteCardThemeId.entries.last())
        viewModel.selectFont(QuoteCardFont.SERIF)
        viewModel.selectFormat(QuoteCardFormat.SQUARE)
        assertEquals(original, viewModel.state.value.card)
        encoded.complete(byteArrayOf(1))
        advanceUntilIdle()
        viewModel.updateBookTitle("Otro libro")
        viewModel.updateAuthor("Otra autora")
        viewModel.selectTheme(QuoteCardThemeId.entries.last())
        viewModel.selectFont(QuoteCardFont.SERIF)
        viewModel.selectFormat(QuoteCardFormat.SQUARE)
        assertEquals("Otro libro", viewModel.state.value.card.bookTitle)
        assertEquals("Otra autora", viewModel.state.value.card.author)
        assertEquals(QuoteCardThemeId.entries.last(), viewModel.state.value.card.themeId)
        assertEquals(QuoteCardFont.SERIF, viewModel.state.value.card.font)
        assertEquals(QuoteCardFormat.SQUARE, viewModel.state.value.card.format)
        assertEquals(ShareCardStatus.Idle, viewModel.state.value.status)
    }
}
