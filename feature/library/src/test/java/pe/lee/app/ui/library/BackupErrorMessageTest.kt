package pe.lee.app.ui.library

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import pe.lee.core.domain.BackupArchiveException
import pe.lee.core.domain.BackupCipherException
import pe.lee.feature.library.R

class BackupErrorMessageTest {
    @Test
    fun allTypedErrorsHaveAResourceInsteadOfExceptionText() {
        for (reason in BackupCipherException.Reason.values()) {
            assertNotEquals(0, backupErrorMessage(BackupCipherException(reason), 0))
        }
        for (reason in BackupArchiveException.Reason.values()) {
            assertNotEquals(0, backupErrorMessage(BackupArchiveException(reason), 0))
        }
    }

    @Test
    fun corruptDataAndWrongPasswordShareTheSameSafeMessage() {
        assertEquals(
            R.string.backup_password_or_data_invalid,
            backupErrorMessage(BackupCipherException(BackupCipherException.Reason.INVALID_PASSWORD_OR_DATA), 0),
        )
    }

    @Test
    fun unknownStorageErrorsUseTheOperationFallback() {
        assertEquals(R.string.library_backup_invalid, backupErrorMessage(IOException("private"), R.string.library_backup_invalid))
    }
}
