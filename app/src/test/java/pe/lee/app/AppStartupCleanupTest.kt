package pe.lee.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotSame
import org.junit.Test

class AppStartupCleanupTest {
    @Test fun startupCleanupRunsOffCallingThread() = runBlocking {
        val callingThread = Thread.currentThread()
        var cleanupThread: Thread? = null
        runStartupCleanupOnIo { cleanupThread = Thread.currentThread() }
        assertNotSame(callingThread, cleanupThread)
    }
}
