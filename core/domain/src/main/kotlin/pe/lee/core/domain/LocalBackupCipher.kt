package pe.lee.core.domain

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts local library backups with a password-derived AES-GCM key.
 *
 * Envelope v1: 8-byte magic, one-byte version, 16-byte salt, 12-byte IV,
 * then AES-GCM ciphertext including its 128-bit authentication tag.
 */
object LocalBackupCipher {
    private val magic = "LEEPEBKP".toByteArray(StandardCharsets.US_ASCII)
    private const val VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BYTES = 32
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val MIN_PASSWORD_LENGTH = 8

    /** Returns true only when [data] starts with the Lee.pe encrypted-backup header. */
    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= magic.size && data.copyOfRange(0, magic.size).contentEquals(magic)

    /** Encrypts [plainText] using a fresh random salt and IV. */
    @Throws(IOException::class)
    fun encrypt(plainText: ByteArray, password: String): ByteArray {
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "La contraseña debe tener al menos 8 caracteres."
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            val encrypted = cipher.doFinal(plainText)
            ByteBuffer.allocate(magic.size + 1 + salt.size + iv.size + encrypted.size)
                .order(ByteOrder.BIG_ENDIAN)
                .put(magic)
                .put(VERSION.toByte())
                .put(salt)
                .put(iv)
                .put(encrypted)
                .array()
        } catch (error: GeneralSecurityException) {
            throw IOException("No se pudo cifrar el respaldo.", error)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Decrypts a Lee.pe encrypted backup. Wrong passwords, authentication failures,
     * and damaged payloads deliberately use the same user-safe error.
     */
    @Throws(IOException::class)
    fun decrypt(data: ByteArray, password: String): ByteArray {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw IOException("La contraseña no es correcta o el respaldo está dañado.")
        }
        val headerSize = magic.size + 1 + SALT_BYTES + IV_BYTES
        if (data.size < headerSize + TAG_BITS / 8 || !isEncrypted(data)) {
            throw IOException("La contraseña no es correcta o el respaldo está dañado.")
        }
        val version = data[magic.size].toInt() and 0xff
        if (version != VERSION) {
            throw IOException("Esta versión de respaldo cifrado no es compatible.")
        }
        val saltStart = magic.size + 1
        val ivStart = saltStart + SALT_BYTES
        val cipherStart = ivStart + IV_BYTES
        val salt = data.copyOfRange(saltStart, ivStart)
        val iv = data.copyOfRange(ivStart, cipherStart)
        val encrypted = data.copyOfRange(cipherStart, data.size)
        val key = try {
            deriveKey(password, salt)
        } catch (error: GeneralSecurityException) {
            throw IOException("No se pudo abrir el respaldo cifrado.", error)
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(encrypted)
        } catch (_: GeneralSecurityException) {
            throw IOException("La contraseña no es correcta o el respaldo está dañado.")
        } finally {
            key.fill(0)
            salt.fill(0)
            iv.fill(0)
            encrypted.fill(0)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        val blockInput = ByteArray(salt.size + 4)
        salt.copyInto(blockInput)
        blockInput[blockInput.lastIndex] = 1
        var u = mac.doFinal(blockInput)
        val accumulator = u.copyOf()
        try {
            repeat(ITERATIONS - 1) {
                val previous = u
                u = mac.doFinal(previous)
                previous.fill(0)
                for (index in accumulator.indices) accumulator[index] =
                    (accumulator[index].toInt() xor u[index].toInt()).toByte()
            }
            return accumulator.copyOf(KEY_BYTES)
        } finally {
            passwordBytes.fill(0)
            blockInput.fill(0)
            u.fill(0)
            accumulator.fill(0)
        }
    }
}
