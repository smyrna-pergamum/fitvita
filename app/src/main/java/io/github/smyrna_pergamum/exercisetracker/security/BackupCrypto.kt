package io.github.smyrna_pergamum.exercisetracker.security

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupException(val reason: String) : Exception(reason)

/**
 * SEC-05: password-protected backup container. PBKDF2-HMAC-SHA256 with a fresh 16-byte salt per
 * file, AES-256-GCM with a fresh nonce; all parameters live in the versioned header, which is also
 * authenticated as GCM associated data. Only standard JCA primitives are used.
 */
object BackupCrypto {
    const val FORMAT = "exercisetracker.backup"
    const val VERSION = 1
    const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 5_000_000

    @Serializable
    data class Header(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val kdf: String = "PBKDF2WithHmacSHA256",
        val iterations: Int,
        val salt: String,
        val cipher: String = "AES-256-GCM",
        val nonce: String,
    )

    @Serializable
    data class Envelope(val header: Header, val data: String)

    // encodeDefaults: format, version and algorithm names must be written into every header.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = Header(iterations = iterations, salt = b64(salt), nonce = b64(nonce))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(128, nonce))
        c.updateAAD(aad(header))
        val env = Envelope(header, b64(c.doFinal(plain)))
        return json.encodeToString(Envelope.serializer(), env).toByteArray(Charsets.UTF_8)
    }

    /** Wrong password and tampering both fail GCM authentication; nothing partial is returned. */
    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        val env = try {
            json.decodeFromString(Envelope.serializer(), file.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupException("bad_format")
        }
        val h = env.header
        if (h.format != FORMAT) throw BackupException("bad_format")
        if (h.version > VERSION) throw BackupException("newer_version")
        if (h.iterations !in 10_000..MAX_ITERATIONS) throw BackupException("bad_format")
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, derive(password, unb64(h.salt), h.iterations), GCMParameterSpec(128, unb64(h.nonce)))
            c.updateAAD(aad(h))
            c.doFinal(unb64(env.data))
        } catch (e: Exception) {
            throw BackupException("wrong_password")
        }
    }

    private fun aad(h: Header) = json.encodeToString(Header.serializer(), h).toByteArray(Charsets.UTF_8)

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = try { Base64.getDecoder().decode(s) } catch (e: Exception) { throw BackupException("bad_format") }
}
