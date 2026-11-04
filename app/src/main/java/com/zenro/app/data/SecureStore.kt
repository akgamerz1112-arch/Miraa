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

    var routePref: String
        get() = prefs.getString("route_pref", "AUTO") ?: "AUTO"
        set(v) = prefs.edit { putString("route_pref", v) }

    var gain: Float
        get() = prefs.getFloat("gain", 1.6f)
        set(v) = prefs.edit { putFloat("gain", v) }

    var vad: String
        get() = prefs.getString("vad", "NORMAL") ?: "NORMAL"
        set(v) = prefs.edit { putString("vad", v) }

    var style: String
        get() = prefs.getString("style", "DOST") ?: "DOST"
        set(v) = prefs.edit { putString("style", v) }

    /** "", "MALE" or "FEMALE" (empty = not told yet). */
    var userGender: String
        get() = prefs.getString("user_gender", "") ?: ""
        set(v) = prefs.edit { putString("user_gender", v) }

    var micGain: Float
        get() = prefs.getFloat("mic_gain", com.zenro.app.audio.AudioLogic.MIC_DEFAULT_GAIN)
        set(v) = prefs.edit { putFloat("mic_gain", v) }

    /** Model for the typed Chat tab (separate from the Live voice model). */
    var chatModel: String
        get() = prefs.getString("chat_model", com.zenro.app.chat.GeminiTextProtocol.DEFAULT_MODEL)
            ?: com.zenro.app.chat.GeminiTextProtocol.DEFAULT_MODEL
        set(v) = prefs.edit { putString("chat_model", v) }

    /** Auto-save things worth remembering (Yaadein). Default on. */
    var memoryOn: Boolean
        get() = prefs.getBoolean("memory_on", true)
        set(v) = prefs.edit { putBoolean("memory_on", v) }

    /** Yaadein as JSON (see MemoryLogic). Encrypted on the phone. */
    var memoryJson: String
        get() = prefs.getString("memory_json", "") ?: ""
        set(v) = prefs.edit { putString("memory_json", v) }

    /** Show the phone screen to ZENRO during voice sessions. Off by default. */
    var screenShare: Boolean
        get() = prefs.getBoolean("screen_share", false)
        set(v) = prefs.edit { putBoolean("screen_share", v) }

    /** Let ZENRO tap/type/open apps (needs screenShare). Off by default. */
    var screenControl: Boolean
        get() = prefs.getBoolean("screen_control", false)
        set(v) = prefs.edit { putBoolean("screen_control", v) }

    /** Affective dialog + proactive audio. Off by default; needs a 2.5 native-audio model. */
    var emotionMode: Boolean
        get() = prefs.getBoolean("emotion_mode", false)
        set(v) = prefs.edit { putBoolean("emotion_mode", v) }

    companion object {
        private const val FILE = "zenro_secure"
    }
}
