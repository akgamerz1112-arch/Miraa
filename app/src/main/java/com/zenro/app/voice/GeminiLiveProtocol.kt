package com.zenro.app.voice

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** How long the server waits in silence before it decides you finished speaking (Google suggests 500-800 ms). */
enum class VadPreset(val silenceMs: Int, val prefixMs: Int) {
    QUICK(500, 20),
    NORMAL(700, 20),
    SLOW(1100, 40),
}

/** Events decoded from a Gemini Live server message. */
sealed interface ServerEvent {
    object SetupComplete : ServerEvent
    class Audio(val pcm: ByteArray) : ServerEvent
    object Interrupted : ServerEvent
    object TurnComplete : ServerEvent
    class InputText(val text: String) : ServerEvent
    class OutputText(val text: String) : ServerEvent
    object GoAway : ServerEvent
    /** Token the server gives us so a dropped connection can continue the SAME conversation. */
    class ResumptionHandle(val handle: String) : ServerEvent
    class Error(val message: String) : ServerEvent
}

/**
 * Pure (no Android classes except org.json) encoder/decoder for the
 * Gemini Live WebSocket protocol, so it can be unit tested.
 */
object GeminiLiveProtocol {
    // Model name is user-editable in Settings because Google renames Live models often.
    const val DEFAULT_MODEL = "gemini-3.8-live"
    /** Older model, kept as a fallback the user can pick in Settings. */
    const val LEGACY_MODEL = "gemini-3.1-flash-live-preview"
    const val EXTENDED_MODEL = "gemini-3.8-live-extended-thinking"
    const val INPUT_SAMPLE_RATE = 16000
    const val OUTPUT_SAMPLE_RATE = 24000

    fun endpoint(apiKey: String): String =
        "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

    fun buildSetup(
        model: String,
        voiceName: String,
        systemPrompt: String,
        resumeHandle: String? = null,
        vad: VadPreset? = null,
    ): String {
        val fullModel = if (model.startsWith("models/")) model else "models/$model"
        val voice = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
        )
        val generation = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", voice)
        val setup = JSONObject()
            .put("model", fullModel)
            .put("generationConfig", generation)
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
            )
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
            // Lets a conversation run longer than the default audio-only limit.
            .put("contextWindowCompression", JSONObject().put("slidingWindow", JSONObject()))
            // Server sends resumption handles; passing one back continues the same conversation.
            .put(
                "sessionResumption",
                JSONObject().also { if (!resumeHandle.isNullOrEmpty()) it.put("handle", resumeHandle) }
            )
        if (vad != null) {
            setup.put(
                "realtimeInputConfig",
                JSONObject().put(
                    "automaticActivityDetection",
                    JSONObject()
                        .put("prefixPaddingMs", vad.prefixMs)
                        .put("silenceDurationMs", vad.silenceMs)
                )
            )
        }
        return JSONObject().put("setup", setup).toString()
    }

    fun buildAudioChunk(pcm: ByteArray, length: Int): String {
        val b64 = Base64.getEncoder().encodeToString(pcm.copyOf(length))
        val audio = JSONObject()
            .put("data", b64)
            .put("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
        return JSONObject().put("realtimeInput", JSONObject().put("audio", audio)).toString()
    }

    fun parse(json: String): List<ServerEvent> {
        val out = ArrayList<ServerEvent>()
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return out
        }
        if (root.has("error")) {
            val err = root.opt("error")
            val msg = if (err is JSONObject) err.optString("message", err.toString()) else err.toString()
            out.add(ServerEvent.Error(msg))
        }
        if (root.has("setupComplete")) out.add(ServerEvent.SetupComplete)

        root.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    val mime = inline.optString("mimeType")
                    val data = inline.optString("data")
                    if (mime.startsWith("audio/") && data.isNotEmpty()) {
                        out.add(ServerEvent.Audio(Base64.getDecoder().decode(data)))
                    }
                }
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { out.add(ServerEvent.InputText(it)) }
            sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { out.add(ServerEvent.OutputText(it)) }
            if (sc.optBoolean("interrupted")) out.add(ServerEvent.Interrupted)
            if (sc.optBoolean("turnComplete")) out.add(ServerEvent.TurnComplete)
        }
        if (root.has("goAway")) out.add(ServerEvent.GoAway)
        root.optJSONObject("sessionResumptionUpdate")?.let { u ->
            val h = u.optString("newHandle")
            if (u.optBoolean("resumable") && h.isNotEmpty()) out.add(ServerEvent.ResumptionHandle(h))
        }
        return out
    }
}
