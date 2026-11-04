package pe.lee.core.domain.library

import pe.lee.core.domain.LocalBackupCipher

/** Produces an authenticated encrypted envelope; callers perform file I/O on their I/O dispatcher. */
class ExportBackupUseCase {
    /** Requires at least eight password characters. Never retains the password or plaintext. */
    operator fun invoke(plaintext: ByteArray, password: String): ByteArray =
        LocalBackupCipher.encrypt(plaintext, password)
}
