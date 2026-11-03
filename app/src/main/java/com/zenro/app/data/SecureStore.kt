package com.zenro.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zenro.app.voice.GeminiLiveProtocol

/** Settings + API key stored with Android Keystore-backed encryption. Never written to the repo. */
class SecureStore(private val context: Context) {
    private val prefs: SharedPreferences = create()

    private fun create(): SharedPreferences = try {
        open()
    } catch (e: Exception) {
        // Keystore/prefs corrupted (e.g. after restore): start clean rather than crash.
        context.deleteSharedPreferences(FILE)
        open()
    }

    private fun open(): SharedPreferences {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            context, FILE, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(v) = prefs.edit { putString("api_key", v) }

    var model: String
        get() = prefs.getString("model", GeminiLiveProtocol.DEFAULT_MODEL) ?: GeminiLiveProtocol.DEFAULT_MODEL
        set(v) = prefs.edit { putString("model", v) }

    var voice: String
        get() = prefs.getString("voice", "Kore") ?: "Kore"
        set(v) = prefs.edit { putString("voice", v) }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit { putString("user_name", v) }

    companion object {
        private const val FILE = "zenro_secure"
    }
}
