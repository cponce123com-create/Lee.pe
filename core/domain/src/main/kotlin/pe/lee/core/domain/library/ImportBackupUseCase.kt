package pe.lee.core.domain.library

import pe.lee.core.domain.BackupCipherException
import pe.lee.core.domain.LocalBackupCipher

/** Decodes encrypted envelopes and preserves legacy JSON for the schema validator. */
class ImportBackupUseCase {
    /** Missing/wrong passwords fail before parsing; the caller must validate legacy JSON and size. */
    operator fun invoke(payload: ByteArray, password: String?): ByteArray = if (LocalBackupCipher.isEncrypted(payload)) {
        LocalBackupCipher.decrypt(payload, password ?: throw BackupCipherException(BackupCipherException.Reason.PASSWORD_REQUIRED))
    } else {
        payload
    }
}
