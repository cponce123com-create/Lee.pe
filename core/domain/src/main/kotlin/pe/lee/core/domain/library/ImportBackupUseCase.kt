package pe.lee.core.domain.library

import java.io.IOException
import pe.lee.core.domain.LocalBackupCipher

/** Decodes encrypted envelopes and preserves legacy JSON for the schema validator. */
class ImportBackupUseCase {
    /** Missing/wrong passwords fail before parsing; the caller must validate legacy JSON and size. */
    operator fun invoke(payload: ByteArray, password: String?): ByteArray =
        if (LocalBackupCipher.isEncrypted(payload)) {
            LocalBackupCipher.decrypt(payload, password ?: throw IOException("Se necesita la contraseña del respaldo."))
        } else {
            payload
        }
}
