package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class EncryptedSecret(
    val ciphertextBase64: String,
    val ivBase64: String
)

/**
 * Encrypts and decrypts user-managed Gemini API keys using an AES-256/GCM key
 * stored exclusively inside the hardware-backed Android Keystore.
 */
class KeystoreCryptoManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "ai_khmer_dubber_gemini_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encrypt(plainText: String): EncryptedSecret {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedSecret(
            ciphertextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(ciphertextBase64: String, ivBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plainBytes = cipher.doFinal(encryptedBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    fun maskKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        return when {
            trimmed.length <= 8 -> "••••••••"
            trimmed.length <= 14 -> "${trimmed.take(4)}••••${trimmed.takeLast(3)}"
            else -> "${trimmed.take(6)}••••••${trimmed.takeLast(4)}"
        }
    }
}
