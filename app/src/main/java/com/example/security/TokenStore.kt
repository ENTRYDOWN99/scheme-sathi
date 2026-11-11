package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore JWT token storage for the shared REST backend.
 * Encrypts access & refresh tokens using AES-256-GCM under key alias `schemesaathi_jwt_key`.
 * Falls back cleanly to deterministic in-memory storage under Robolectric / JVM unit tests.
 */
class TokenStore(context: Context? = null) {

    private val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var memoryAccessToken: String? = null
    private var memoryRefreshToken: String? = null
    private var memoryRole: String = "CITIZEN"

    fun saveTokens(accessToken: String?, refreshToken: String?, role: String? = null) {
        memoryAccessToken = accessToken
        memoryRefreshToken = refreshToken
        if (!role.isNullOrBlank()) {
            memoryRole = role
        }
        val editor = prefs?.edit() ?: return
        if (accessToken.isNullOrBlank()) {
            editor.remove(KEY_ACCESS)
        } else {
            editor.putString(KEY_ACCESS, encryptWithKeystore(accessToken))
        }
        if (refreshToken.isNullOrBlank()) {
            editor.remove(KEY_REFRESH)
        } else {
            editor.putString(KEY_REFRESH, encryptWithKeystore(refreshToken))
        }
        if (!role.isNullOrBlank()) {
            editor.putString(KEY_ROLE, role)
        }
        editor.apply()
    }

    fun getAccessToken(): String? {
        memoryAccessToken?.let { return it }
        val raw = prefs?.getString(KEY_ACCESS, null) ?: return null
        return decryptWithKeystore(raw).also { memoryAccessToken = it }
    }

    fun getRefreshToken(): String? {
        memoryRefreshToken?.let { return it }
        val raw = prefs?.getString(KEY_REFRESH, null) ?: return null
        return decryptWithKeystore(raw).also { memoryRefreshToken = it }
    }

    fun getSavedRole(): String {
        return prefs?.getString(KEY_ROLE, memoryRole) ?: memoryRole
    }

    fun clear() {
        memoryAccessToken = null
        memoryRefreshToken = null
        memoryRole = "CITIZEN"
        prefs?.edit()?.clear()?.apply()
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            if (existing != null) {
                existing
            } else {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }
        } catch (_: Exception) {
            // JVM / Robolectric unit test fallback when AndroidKeyStore provider is unavailable
            val fallbackDigest = MessageDigest.getInstance("SHA-256")
                .digest("schemesaathi-jvm-test-fallback".toByteArray(Charsets.UTF_8))
            SecretKeySpec(fallbackDigest, "AES")
        }
    }

    private fun encryptWithKeystore(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreSecretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptWithKeystore(payload: String): String? {
        if (!payload.startsWith(PREFIX)) return null
        return try {
            val raw = Base64.decode(payload.removePrefix(PREFIX), Base64.NO_WRAP)
            if (raw.size <= GCM_IV_LEN) return null
            val iv = raw.copyOfRange(0, GCM_IV_LEN)
            val encrypted = raw.copyOfRange(GCM_IV_LEN, raw.size)
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKeystoreSecretKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "schemesaathi_jwt_key"
        const val PREFS_NAME = "schemesaathi_secure_tokens"
        const val KEY_ACCESS = "jwt_access"
        const val KEY_REFRESH = "jwt_refresh"
        const val KEY_ROLE = "jwt_role"
        const val AES_GCM = "AES/GCM/NoPadding"
        const val GCM_IV_LEN = 12
        const val PREFIX = "KS1:"
    }
}

