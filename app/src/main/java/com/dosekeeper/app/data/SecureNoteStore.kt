package com.dosekeeper.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureNoteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dose_keeper_secure_notes", Context.MODE_PRIVATE)

    fun readNote(itemId: String): String {
        val encoded = prefs.getString(keyFor(itemId), null) ?: return ""
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).decodeToString()
    }

    fun saveNote(itemId: String, note: String) {
        if (note.isBlank()) {
            prefs.edit { remove(keyFor(itemId)) }
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(note.encodeToByteArray())
        val packed = cipher.iv + ciphertext
        prefs.edit {
            putString(keyFor(itemId), Base64.encodeToString(packed, Base64.NO_WRAP))
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    private fun keyFor(itemId: String): String = "note_$itemId"

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dose_keeper_notes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
