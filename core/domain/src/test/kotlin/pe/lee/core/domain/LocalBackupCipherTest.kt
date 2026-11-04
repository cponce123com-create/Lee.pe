package pe.lee.core.domain

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
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
    fun envelopeDecryptsWithStandardPbkdf2HmacSha256Implementation() {
        val password = "Clave-segura-2026"
        val original = "prueba de interoperabilidad".toByteArray()
        val envelope = LocalBackupCipher.encrypt(original, password)
        val saltStart = 9
        val ivStart = saltStart + 16
        val cipherStart = ivStart + 12
        val salt = envelope.copyOfRange(saltStart, ivStart)
        val iv = envelope.copyOfRange(ivStart, cipherStart)
        val encrypted = envelope.copyOfRange(cipherStart, envelope.size)
        val specification = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(specification).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))

        assertArrayEquals(original, cipher.doFinal(encrypted))
        specification.clearPassword()
        keyBytes.fill(0)
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
