package io.github.smyrna_pergamum.exercisetracker.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Encrypts single text fields before they are stored (TECH-01). */
interface FieldCipher {
    fun encrypt(plain: String): String
    /** Throws [KeyLostException] when the device key is gone (SEC-04). */
    fun decrypt(blob: String): String
}

class KeyLostException : Exception("field key unavailable")

/**
 * AES-256-GCM with a random 96-bit nonce per call. Android Keystore keys refuse caller-chosen
 * nonces, so with [keystoreKey] the provider generates it.
 */
class AesGcmFieldCipher(private val key: SecretKey, private val keystoreKey: Boolean = false) : FieldCipher {
    private val random = SecureRandom()

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        if (keystoreKey) c.init(Cipher.ENCRYPT_MODE, key)
        else c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, ByteArray(IV_LEN).also(random::nextBytes)))
        val iv = c.iv
        check(iv.size == IV_LEN)
        return Base64.getEncoder().encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    override fun decrypt(blob: String): String = try {
        val b = Base64.getDecoder().decode(blob)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, IV_LEN))
        String(c.doFinal(b, IV_LEN, b.size - IV_LEN), Charsets.UTF_8)
    } catch (e: Exception) {
        throw KeyLostException()
    }

    companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

/**
 * Field keys: a random 256-bit data key, stored only wrapped by a non-exportable Android Keystore
 * key (TECH-01). Unwrapping happens once per process, so decrypting thousands of set rows stays
 * fast (AT-13). The wrapped key is excluded from device/cloud backup and does not move to a new
 * device (SEC-03, SEC-04); the encrypted JSON backup is the way to move data.
 */
object FieldKeys {
    private const val PREFS = "field_keys"
    private const val WRAPPED = "dek"
    private const val ALIAS = "exercisetracker_master"

    fun open(context: Context): FieldCipher {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(WRAPPED, null)
        if (wrapped != null) {
            val master = existingMaster() ?: throw KeyLostException()
            val dek = try {
                AesGcmFieldCipher(master, keystoreKey = true).decrypt(wrapped)
            } catch (e: Exception) {
                throw KeyLostException()
            }
            return AesGcmFieldCipher(SecretKeySpec(Base64.getDecoder().decode(dek), "AES"))
        }
        val raw = ByteArray(32).also(SecureRandom()::nextBytes)
        val enc = AesGcmFieldCipher(master(), keystoreKey = true).encrypt(Base64.getEncoder().encodeToString(raw))
        prefs.edit().putString(WRAPPED, enc).commit()
        return AesGcmFieldCipher(SecretKeySpec(raw, "AES"))
    }

    /** SEC-10: removes the wrapped data key and the Keystore entry. */
    fun destroy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingMaster(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun master(): SecretKey = existingMaster() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generateKey()
    }
}
