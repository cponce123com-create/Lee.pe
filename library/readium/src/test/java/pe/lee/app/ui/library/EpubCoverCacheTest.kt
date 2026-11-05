package pe.lee.app.ui.library

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class EpubCoverCacheTest {
    @Test fun cachedCoverReturnsWhileBothParsePermitsAreBusy() = runBlocking {
        val permits = Semaphore(1)
        permits.acquire()
        val result = withTimeout(500) {
            loadEpubCoverAfterCacheCheck(
                permits = permits,
                lookup = { EpubCoverCacheLookup.Hit("cached") },
                extract = { error("No debe esperar ni volver a parsear una portada en caché") },
            )
        }
        assertEquals("cached", result)
    }
}
