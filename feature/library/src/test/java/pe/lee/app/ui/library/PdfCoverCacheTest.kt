package pe.lee.app.ui.library

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCoverCacheTest {
    @Test fun cachedThumbnailReturnsWhileBothRenderPermitsAreBusy() = runBlocking {
        val permits = Semaphore(1)
        permits.acquire()
        val result = withTimeout(500) {
            loadPdfCoverAfterCacheCheck(
                permits = permits,
                lookup = { PdfCoverCacheLookup.Hit("cached") },
                render = { error("No debe esperar ni renderizar un resultado en caché") },
            )
        }
        assertEquals("cached", result)
    }
}
