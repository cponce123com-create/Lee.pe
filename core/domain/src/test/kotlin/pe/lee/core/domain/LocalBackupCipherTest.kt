package pe.lee.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        assertEquals(2, envelope[8].toInt())
        val iterations = ByteBuffer.wrap(envelope, 9, 4).int
        assertEquals(600_000, iterations)
        val saltStart = 13
        val ivStart = saltStart + 16
        val cipherStart = ivStart + 12
        val salt = envelope.copyOfRange(saltStart, ivStart)
        val iv = envelope.copyOfRange(ivStart, cipherStart)
        val encrypted = envelope.copyOfRange(cipherStart, envelope.size)
        val specification = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(specification).encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))

        cipher.updateAAD(envelope, 0, cipherStart)
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

    @Test
    fun readsIndependentLegacyV1FixtureWithUnicodePassword() {
        // Python hashlib.pbkdf2_hmac + AESGCM; not produced by the code under test.
        val fixture = Base64.getDecoder().decode(
            "TEVFUEVCS1ABAAECAwQFBgcICQoLDA0ODwABAgMEBQYHCAkKC3wp2I54MMPivNSF/z8/tz7xLQNYX1sfxO+rlV/+U88UZInMivZORpryJd5cDw==",
        )
        assertArrayEquals(
            """{"notes":["legado español"]}""".toByteArray(),
            LocalBackupCipher.decrypt(fixture, "Contraseña-📚-2026"),
        )
    }

    @Test
    fun nativeAndFallbackDerivationAgreeForUnicodeAndLongPasswords() {
        val salt = ByteArray(16) { it.toByte() }
        for (password in listOf("Clave-2026", "Contraseña-📚-2026", "e\u0301".repeat(50))) {
            assertArrayEquals(
                BackupKeyDerivation.derive(password, salt, 2_000),
                BackupKeyDerivation.derive(password, salt, 2_000, forceFallback = true),
            )
        }
    }

    @Test
    fun v2RoundTripSupportsUnicodeAndUnpairedSurrogates() {
        for (password in listOf("Contraseña-📚-2026", "password-\uD800-2026")) {
            val data = "texto español".toByteArray()
            assertArrayEquals(data, LocalBackupCipher.decrypt(LocalBackupCipher.encrypt(data, password), password))
        }
    }

    @Test
    fun changingAnyHeaderFieldIsRejectedIncludingAnotherAllowedCost() {
        val encrypted = LocalBackupCipher.encrypt("private".toByteArray(), "Clave-segura-2026")
        for (index in listOf(0, 8, 13, 29)) {
            val damaged = encrypted.copyOf()
            damaged[index] = (damaged[index].toInt() xor 0x04).toByte()
            assertThrows(BackupCipherException::class.java) { LocalBackupCipher.decrypt(damaged, "Clave-segura-2026") }
        }
        val changedCost = encrypted.copyOf()
        ByteBuffer.wrap(changedCost).putInt(9, 600_001)
        assertThrows(BackupCipherException::class.java) { LocalBackupCipher.decrypt(changedCost, "Clave-segura-2026") }
    }

    @Test(timeout = 2_000)
    fun invalidCostsAreRejectedBeforeExpensiveDerivation() {
        // Complete sized payload, no KDF needed to create hostile input.
        for (cost in listOf(Int.MIN_VALUE, -1, 0, 120_000, 599_999, 1_200_001, Int.MAX_VALUE)) {
            val hostile = ByteArray(57)
            "LEEPEBKP".toByteArray().copyInto(hostile)
            hostile[8] = 2
            ByteBuffer.wrap(hostile).putInt(9, cost)
            val error = assertThrows(BackupCipherException::class.java) {
                LocalBackupCipher.decrypt(hostile, "Clave-segura-2026")
            }
            assertEquals(BackupCipherException.Reason.INVALID_PASSWORD_OR_DATA, error.reason)
        }
    }

    @Test
    fun failedAuthenticationDoesNotDistinguishWrongPasswordFromDamage() {
        val encrypted = LocalBackupCipher.encrypt(byteArrayOf(1), "Clave-segura-2026")
        val wrongPassword = assertThrows(BackupCipherException::class.java) {
            LocalBackupCipher.decrypt(encrypted, "incorrect-password")
        }
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        val damaged = assertThrows(BackupCipherException::class.java) {
            LocalBackupCipher.decrypt(encrypted, "Clave-segura-2026")
        }
        assertEquals(wrongPassword.reason, damaged.reason)
    }
}
