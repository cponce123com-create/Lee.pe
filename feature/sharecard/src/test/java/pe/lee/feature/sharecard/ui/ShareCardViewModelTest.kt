package pe.lee.feature.sharecard.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.lee.feature.sharecard.domain.ShareCardGenerator
import pe.lee.feature.sharecard.domain.ShareCardSharer
import pe.lee.feature.sharecard.domain.model.QuoteCard
import pe.lee.feature.sharecard.domain.model.QuoteCardFormat
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
            workerDispatcher = dispatcher
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
            workerDispatcher = dispatcher
        )

        viewModel.shareRasterForTest(QuoteCardRaster(1, 1, intArrayOf(0xFFFFFFFF.toInt())))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.status is ShareCardStatus.Error)
    }
}
