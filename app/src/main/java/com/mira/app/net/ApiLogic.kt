package com.mira.app.net

import org.json.JSONObject

/** Pure Kotlin (no Android classes): URL checks, config/token parsing, Hinglish error text. Unit tested. */
object ApiLogic {
    /** The newest API version this app understands (server field `api_version`). */
    const val SUPPORTED_API_VERSION = 1

    /** Same rule as the server's device_auth.py. */
    private val DEVICE_ID = Regex("^[a-z0-9][a-z0-9-]{1,31}$")

    data class RemoteConfig(
        val apiVersion: Int,
        val configVersion: Int,
        val minAppVersion: Int,
        val personaVersion: Int
    )

    data class TokenInfo(val serverUrl: String, val room: String, val identity: String, val token: String)

    fun normalizeDeviceId(raw: String): String = raw.trim().lowercase()

    fun isValidDeviceId(raw: String): Boolean = DEVICE_ID.matches(normalizeDeviceId(raw))

    /** Trimmed base URL without trailing slashes, or null when it is empty / not https. */
    fun normalizeBaseUrl(raw: String): String? {
        val t = raw.trim().trimEnd('/')
        if (!t.startsWith("https://", ignoreCase = true)) return null
        if (t.length <= "https://".length || t.contains(' ')) return null
        return t
    }

    /** Hinglish reason why [raw] cannot be used, or null if it is fine. */
    fun urlProblem(raw: String): String? = when {
        raw.isBlank() -> "Server URL daalo (https://...)."
        raw.trim().startsWith("http://", ignoreCase = true) ->
            "Android plain http:// block karta hai. https:// wala URL chahiye (tunnel ya certificate wala host)."
        normalizeBaseUrl(raw) == null -> "URL https:// se shuru hona chahiye."
        else -> null
    }

    fun parseConfig(json: String): RemoteConfig? = try {
        val o = JSONObject(json)
        if (!o.has("api_version") || !o.has("min_app_version")) null
        else RemoteConfig(
            apiVersion = o.getInt("api_version"),
            configVersion = o.optInt("config_version", 0),
            minAppVersion = o.getInt("min_app_version"),
            personaVersion = o.optInt("persona_version", 0)
        )
    } catch (e: Exception) {
        null
    }

    /** True when the server wants a newer app, or speaks a newer API than this app knows. */
    fun needsUpdate(cfg: RemoteConfig, appVersionCode: Int): Boolean =
        appVersionCode < cfg.minAppVersion || cfg.apiVersion > SUPPORTED_API_VERSION

    fun tokenBody(deviceId: String, appVersionCode: Int): String =
        JSONObject().put("device_id", normalizeDeviceId(deviceId)).put("app_version", appVersionCode).toString()

    fun parseToken(json: String): TokenInfo? = try {
        val o = JSONObject(json)
        val url = o.optString("server_url")
        val tok = o.optString("token")
        if (url.isBlank() || tok.isBlank()) null
        else TokenInfo(url, o.optString("room"), o.optString("identity"), tok)
    } catch (e: Exception) {
        null
    }

    const val UPDATE_MESSAGE = "App purani hai. Nayi MIRA APK install karo (update app)."

    fun httpMessage(code: Int): String = when (code) {
        401 -> "Device id ya device key galat hai (401). Settings check karo."
        426 -> UPDATE_MESSAGE
        429 -> "Bahut zyada koshish (429). Ek minute ruk ke try karo."
        503 -> "Server ready nahi hai (503). PC pe agent/API aur LiveKit keys check karo."
        404 -> "Server URL galat lagta hai (404)."
        in 500..599 -> "Server mein dikkat ($code). Thodi der baad try karo."
        else -> "Server ne error diya ($code)."
    }

    fun networkMessage(): String = "Server tak pahunch nahi paaye. Internet aur server URL check karo (PC on hona chahiye)."

    fun badResponseMessage(): String = "Server ka jawab samajh nahi aaya. URL sahi hai?"
}
