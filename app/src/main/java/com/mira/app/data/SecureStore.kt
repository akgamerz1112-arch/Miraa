package com.mira.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Settings + server/device keys stored with Android Keystore-backed encryption. Never written to the repo. */
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

    /** MIRA API base URL, https only (e.g. https://mira.example.com). */
    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(v) = prefs.edit { putString("server_url", v) }

    /** Device id registered on the server with `python device_auth.py add <id>`. */
    var deviceId: String
        get() = prefs.getString("device_id", "") ?: ""
        set(v) = prefs.edit { putString("device_id", v) }

    /** Per-device key shown once by device_auth.py. Never logged, never shown again in the UI. */
    var deviceKey: String
        get() = prefs.getString("device_key", "") ?: ""
        set(v) = prefs.edit { putString("device_key", v) }

    /** Last `/v1/config` answer (no secrets in it), kept so Settings can show versions. */
    var configCache: String
        get() = prefs.getString("config_cache", "") ?: ""
        set(v) = prefs.edit { putString("config_cache", v) }

    /** Auto-save things worth remembering (Yaadein list on the phone). Default on. */
    var memoryOn: Boolean
        get() = prefs.getBoolean("memory_on", true)
        set(v) = prefs.edit { putBoolean("memory_on", v) }

    /** Yaadein as JSON (see MemoryLogic). Encrypted on the phone. */
    var memoryJson: String
        get() = prefs.getString("memory_json", "") ?: ""
        set(v) = prefs.edit { putString("memory_json", v) }

    /** Owner's switch: may the MIRA agent tap/type/open apps on this phone? Off by default. */
    var screenControl: Boolean
        get() = prefs.getBoolean("screen_control", false)
        set(v) = prefs.edit { putBoolean("screen_control", v) }

    companion object {
        private const val FILE = "mira_secure"
    }
}
