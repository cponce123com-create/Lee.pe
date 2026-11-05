package pe.lee.app.ui.library

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pe.lee.core.domain.StorageException
import pe.lee.feature.library.R

class StorageErrorMessageTest {
    @Test
    fun allStorageFailuresHaveLocalizedResources() {
        for (reason in StorageException.Reason.values()) {
            assertNotEquals(0, storageErrorMessage(StorageException(reason), 0))
        }
    }

    @Test
    fun incompleteFileGetsAnActionableMessage() {
        assertEquals(
            R.string.storage_source_changed,
            storageErrorMessage(StorageException(StorageException.Reason.SOURCE_CHANGED), 0),
        )
    }

    @Test
    fun unknownErrorUsesOperationFallbackWithoutExposingItsMessage() {
        assertEquals(R.string.scanner_import_failed, storageErrorMessage(IOException("private URI"), R.string.scanner_import_failed))
    }
}
