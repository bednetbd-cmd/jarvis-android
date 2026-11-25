package com.jarvis.assistant.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecureVaultPort {
    fun saveSecret(aliasKey: String, secretValue: String)
    fun getSecret(aliasKey: String): String?
    fun hasSecret(aliasKey: String): Boolean
    fun clearSecret(aliasKey: String)
    fun encryptPayload(plaintext: String): String? = null
    fun decryptPayload(packed: String): String? = null
}

class InMemorySecureVault : SecureVaultPort {
    private val store = linkedMapOf<String, String>()

    override fun saveSecret(aliasKey: String, secretValue: String) {
        val trimmed = secretValue.trim()
        if (trimmed.isEmpty()) {
            store.remove(aliasKey)
        } else {
            store[aliasKey] = trimmed
        }
    }

    override fun getSecret(aliasKey: String): String? = store[aliasKey]

    override fun hasSecret(aliasKey: String): Boolean = !store[aliasKey].isNullOrBlank()

    override fun clearSecret(aliasKey: String) {
        store.remove(aliasKey)
    }
}

class AndroidKeystoreVault(
    context: Context
) : SecureVaultPort {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun saveSecret(aliasKey: String, secretValue: String) {
        val trimmed = secretValue.trim()
        if (trimmed.isEmpty()) {
            clearSecret(aliasKey)
            return
        }
        val packed = encryptPayload(trimmed)
            ?: throw IllegalStateException("Unable to encrypt secret using Android Keystore.")
        prefs.edit().putString(aliasKey, packed).apply()
    }

    override fun getSecret(aliasKey: String): String? {
        val packed = prefs.getString(aliasKey, null) ?: return null
        return decryptPayload(packed)
    }

    override fun hasSecret(aliasKey: String): Boolean {
        return prefs.contains(aliasKey) && !getSecret(aliasKey).isNullOrBlank()
    }

    override fun clearSecret(aliasKey: String) {
        prefs.edit().remove(aliasKey).apply()
    }

    override fun encryptPayload(plaintext: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encodedData = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$encodedIv:$encodedData"
        }.getOrNull()
    }

    override fun decryptPayload(packed: String): String? {
        val parts = packed.split(":", limit = 2)
        if (parts.size != 2) return null

        return runCatching {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            String(cipher.doFinal(encryptedBytes), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jarvis_provider_vault_key"
        private const val PREFS_NAME = "jarvis_secure_vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_OPENAI_TOKEN = "openai_compatible_token"
        const val KEY_GEMINI_TOKEN = "gemini_token"
    }
}
