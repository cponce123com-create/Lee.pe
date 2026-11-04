package pe.lee.core.domain

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupCipherTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val original = """{"version":2,"notes":["privado"]}""".toByteArray()
        val encrypted = LocalBackupCipher.encrypt(original, "Clave-segura-2026")
        assertTrue(LocalBackupCipher.isEncrypted(encrypted))
        assertArrayEquals(original, LocalBackupCipher.decrypt(encrypted, "Clave-segura-2026"))
    }

    @Test
    fun eachEncryptionUsesFreshSaltAndIv() {
        val original = "mismo respaldo".toByteArray()
        val first = LocalBackupCipher.encrypt(original, "Clave-segura-2026")
        val second = LocalBackupCipher.encrypt(original, "Clave-segura-2026")
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun wrongPasswordIsRejected() {
        val encrypted = LocalBackupCipher.encrypt("datos".toByteArray(), "Clave-segura-2026")
        assertThrows(IOException::class.java) {
            LocalBackupCipher.decrypt(encrypted, "otra-clave-2026")
        }
    }

    @Test
    fun modifiedCiphertextIsRejected() {
        val encrypted = LocalBackupCipher.encrypt("datos".toByteArray(), "Clave-segura-2026")
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()
        assertThrows(IOException::class.java) {
            LocalBackupCipher.decrypt(encrypted, "Clave-segura-2026")
        }
    }

    @Test
    fun malformedAndTruncatedPayloadsAreRejected() {
        assertThrows(IOException::class.java) {
            LocalBackupCipher.decrypt("LEEPEBKP".toByteArray(), "Clave-segura-2026")
        }
        val encrypted = LocalBackupCipher.encrypt("datos".toByteArray(), "Clave-segura-2026")
        assertThrows(IOException::class.java) {
            LocalBackupCipher.decrypt(encrypted.copyOfRange(0, encrypted.size - 3), "Clave-segura-2026")
        }
    }

    @Test
    fun rejectsWeakPasswordAndLeavesLegacyJsonUnmarked() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalBackupCipher.encrypt(byteArrayOf(1), "1234567")
        }
        assertFalse(LocalBackupCipher.isEncrypted("""{"version":2}""".toByteArray()))
    }
}
