package pe.lee.app.ui.reader

import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import pe.lee.app.R

class ReaderOpenFailureTest {
    @Test
    fun wrappedPermissionFailureTakesPriority() {
        val error = IOException("provider", SecurityException("revoked"))
        assertEquals(R.string.epub_permission_lost, readerOpenFailureMessage(error))
    }

    @Test
    fun unavailableFileDoesNotBecomeGenericReadFailure() {
        val error = IOException("provider", FileNotFoundException("unavailable"))
        assertEquals(R.string.epub_file_unavailable, readerOpenFailureMessage(error))
    }

    @Test
    fun providerIoFailureHasRetryGuidance() {
        assertEquals(R.string.epub_storage_read_failed, readerOpenFailureMessage(IOException("provider")))
    }

    @Test
    fun invalidPublicationKeepsGenericFallback() {
        assertEquals(R.string.epub_open_failed, readerOpenFailureMessage(IllegalArgumentException("publication")))
    }
}
