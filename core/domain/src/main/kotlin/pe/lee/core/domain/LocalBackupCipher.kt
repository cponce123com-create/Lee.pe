package pe.lee.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Machine-readable failure; presentation belongs to the caller's resources. */
class BackupCipherException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason { PASSWORD_REQUIRED, INVALID_PASSWORD_OR_DATA, UNSUPPORTED_VERSION, ENCRYPTION_FAILED }
}

/**
 * Password-derived AES-256-GCM envelopes, integers in big-endian order.
 * v1 (read only): magic[8], version[1], salt[16], IV[12], ciphertext + tag[16].
 * v2: magic[8], version[1], iterations[4], salt[16], IV[12], ciphertext + tag[16].
 * The entire v2 header is authenticated as AAD. v1 retains its original UTF-8
 * PBKDF2 semantics and cost. New exports always use v2.
 */
object LocalBackupCipher {
    private val magic = "LEEPEBKP".toByteArray(StandardCharsets.US_ASCII)
    private const val VERSION = 2
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val LEGACY_ITERATIONS = 120_000
    private const val ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 1_200_000
    private const val MIN_PASSWORD_LENGTH = 8
    private const val V1_HEADER_BYTES = 8 + 1 + SALT_BYTES + IV_BYTES
    private const val V2_HEADER_BYTES = V1_HEADER_BYTES + 4

    fun isEncrypted(data: ByteArray): Boolean = data.size >= magic.size && data.copyOfRange(0, magic.size).contentEquals(magic)

    /** CPU intensive: callers must run export/import outside the UI thread. */
    @Throws(IOException::class)
    fun encrypt(plainText: ByteArray, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must contain at least 8 characters" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(V2_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
            .put(magic).put(VERSION.toByte()).putInt(ITERATIONS).put(salt).put(iv).array()
        var key: ByteArray? = null
        return try {
            val derived = BackupKeyDerivation.derive(password, salt, ITERATIONS)
            key = derived
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            header + cipher.doFinal(plainText)
        } catch (error: GeneralSecurityException) {
            throw BackupCipherException(BackupCipherException.Reason.ENCRYPTION_FAILED, error)
        } finally {
            key?.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    /** Wrong password and authentication failure deliberately share one error. */
    @Throws(IOException::class)
    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (password.length < MIN_PASSWORD_LENGTH || data.size < V1_HEADER_BYTES + TAG_BITS / 8 || !isEncrypted(data)) {
            throw invalidData()
        }
        val version = data[magic.size].toInt() and 0xff
        val headerSize = when (version) {
            1 -> V1_HEADER_BYTES
            VERSION -> V2_HEADER_BYTES
            else -> throw BackupCipherException(BackupCipherException.Reason.UNSUPPORTED_VERSION)
        }
        if (data.size < headerSize + TAG_BITS / 8) throw invalidData()
        val iterations = if (version == 1) LEGACY_ITERATIONS else {
            ByteBuffer.wrap(data, magic.size + 1, 4).order(ByteOrder.BIG_ENDIAN).int.also {
                // Validate before KDF work; bound both downgraded and hostile costs.
                if (it !in ITERATIONS..MAX_ITERATIONS) throw invalidData()
            }
        }
        val saltStart = if (version == 1) magic.size + 1 else magic.size + 1 + 4
        val ivStart = saltStart + SALT_BYTES
        val salt = data.copyOfRange(saltStart, ivStart)
        val iv = data.copyOfRange(ivStart, headerSize)
        var key: ByteArray? = null
        return try {
            // v1 used UTF-8 bytes directly; preserve even unusual UTF-16 passwords.
            val derived = BackupKeyDerivation.derive(password, salt, iterations, forceFallback = version == 1)
            key = derived
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, iv))
            if (version == VERSION) cipher.updateAAD(data, 0, headerSize)
            cipher.doFinal(data, headerSize, data.size - headerSize)
        } catch (error: GeneralSecurityException) {
            throw invalidData(error)
        } finally {
            key?.fill(0)
            salt.fill(0)
            iv.fill(0)
        }
    }

    private fun invalidData(cause: Throwable? = null) =
        BackupCipherException(BackupCipherException.Reason.INVALID_PASSWORD_OR_DATA, cause)
}

/** Native where available; the fallback keeps API 24–25 compatible without Android dependencies. */
internal object BackupKeyDerivation {
    fun derive(password: String, salt: ByteArray, iterations: Int, forceFallback: Boolean = false): ByteArray {
        require(iterations > 0)
        // Invalid UTF-16 is replaced differently by providers. Retain explicit UTF-8
        // bytes for those inputs so every supported Android version agrees.
        val canonicalUtf8 = password.toByteArray(StandardCharsets.UTF_8)
        val roundTrips = canonicalUtf8.toString(StandardCharsets.UTF_8) == password
        canonicalUtf8.fill(0)
        if (!forceFallback && roundTrips) {
            val factory = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            } catch (_: NoSuchAlgorithmException) {
                null
            }
            if (factory != null) {
                val chars = password.toCharArray()
                val specification = PBEKeySpec(chars, salt, iterations, 256)
                chars.fill('\u0000')
                return try {
                    factory.generateSecret(specification).encoded
                } finally {
                    specification.clearPassword()
                }
            }
        }
        return deriveFallback(password, salt, iterations)
    }

    private fun deriveFallback(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        var u = ByteArray(0)
        var accumulator = ByteArray(0)
        val blockInput = ByteArray(salt.size + 4)
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            salt.copyInto(blockInput)
            blockInput[blockInput.lastIndex] = 1
            u = mac.doFinal(blockInput)
            accumulator = u.copyOf()
            repeat(iterations - 1) {
                val previous = u
                u = mac.doFinal(previous)
                previous.fill(0)
                for (index in accumulator.indices) {
                    accumulator[index] = (accumulator[index].toInt() xor u[index].toInt()).toByte()
                }
            }
            accumulator.copyOf()
        } finally {
            passwordBytes.fill(0)
            blockInput.fill(0)
            u.fill(0)
            accumulator.fill(0)
        }
    }
}
