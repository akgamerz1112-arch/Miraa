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
    /** The model asked us to run a function (we only offer `save_memory`). */
    class ToolCall(val id: String, val name: String, val args: JSONObject) : ServerEvent
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
    /** The model used in the "Friday" video; Google lists affective dialog + proactive audio only for 2.5 native-audio Live models. */
    const val NATIVE_AUDIO_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"
    const val INPUT_SAMPLE_RATE = 16000
    const val OUTPUT_SAMPLE_RATE = 24000

    /** Emotion mode needs the v1alpha API version (Google docs). */
    fun endpoint(apiKey: String, alpha: Boolean = false): String =
        "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.${if (alpha) "v1alpha" else "v1beta"}.GenerativeService.BidiGenerateContent?key=$apiKey"

    fun buildSetup(
        model: String,
        voiceName: String,
        systemPrompt: String,
        resumeHandle: String? = null,
        vad: VadPreset? = null,
        memoryTool: Boolean = false,
        temperature: Float? = null,
        controlTools: Boolean = false,
        emotion: Boolean = false,
    ): String {
        val fullModel = if (model.startsWith("models/")) model else "models/$model"
        val voice = JSONObject().put(
            "voiceConfig",
            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
        )
        val generation = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put("speechConfig", voice)
        if (temperature != null) generation.put("temperature", temperature.toDouble())
        if (emotion) generation.put("enableAffectiveDialog", true)
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
                        // Catch the very start of soft / quiet speech.
                        .put("startOfSpeechSensitivity", "START_SENSITIVITY_HIGH")
                )
            )
        }
        if (emotion) setup.put("proactivity", JSONObject().put("proactiveAudio", true))
        val decls = JSONArray()
        if (memoryTool) decls.put(saveMemoryDeclaration())
        if (controlTools) screenDeclarations().forEach { decls.put(it) }
        if (decls.length() > 0) {
            setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", decls)))
        }
        return JSONObject().put("setup", setup).toString()
    }

    const val TOOL_SAVE_MEMORY = "save_memory"

    private fun saveMemoryDeclaration(): JSONObject = JSONObject()
        .put("name", TOOL_SAVE_MEMORY)
        .put(
            "description",
            "Save one short fact about the user that is worth remembering on later days " +
                "(a person in their life, a like or dislike, a plan, a preference). " +
                "Never save passwords, OTPs, card, bank or ID numbers."
        )
        .put(
            "parameters",
            JSONObject()
                .put("type", "OBJECT")
                .put(
                    "properties",
                    JSONObject().put(
                        "fact",
                        JSONObject().put("type", "STRING").put("description", "One short fact, in Hinglish or English.")
                    )
                )
                .put("required", JSONArray().put("fact"))
        )

    const val TOOL_TAP = "screen_tap"
    const val TOOL_SWIPE = "screen_swipe"
    const val TOOL_TYPE = "type_text"
    const val TOOL_PRESS = "press_button"
    const val TOOL_OPEN = "open_app"
    const val TOOL_YOUTUBE = "youtube_search"
    const val TOOL_VOLUME = "set_volume"
    const val TOOL_MEDIA = "media_control"
    const val TOOL_WHATSAPP = "whatsapp_message"
    val SCREEN_TOOLS = setOf(
        TOOL_TAP, TOOL_SWIPE, TOOL_TYPE, TOOL_PRESS, TOOL_OPEN,
        TOOL_YOUTUBE, TOOL_VOLUME, TOOL_MEDIA, TOOL_WHATSAPP
    )

    private fun str(desc: String, vararg allowed: String): JSONObject {
        val o = JSONObject().put("type", "STRING").put("description", desc)
        if (allowed.isNotEmpty()) o.put("enum", JSONArray(allowed.toList()))
        return o
    }

    private fun num(desc: String): JSONObject = JSONObject().put("type", "NUMBER").put("description", desc)

    private fun decl(name: String, desc: String, props: JSONObject, vararg required: String): JSONObject =
        JSONObject().put("name", name).put("description", desc).put(
            "parameters",
            JSONObject().put("type", "OBJECT").put("properties", props).put("required", JSONArray(required.toList()))
        )

    /** Phone-control functions. Coordinates are 0-1000 with (0,0) at the top-left of the screen picture. */
    private fun screenDeclarations(): List<JSONObject> = listOf(
        decl(
            TOOL_TAP, "Tap one point on the phone screen.",
            JSONObject()
                .put("x", num("Horizontal position 0-1000, 0 = left edge, 1000 = right edge."))
                .put("y", num("Vertical position 0-1000, 0 = top edge, 1000 = bottom edge.")),
            "x", "y"
        ),
        decl(
            TOOL_SWIPE, "Swipe the screen. The direction is where the finger moves; swiping up scrolls the page down.",
            JSONObject().put("direction", str("Finger direction.", "up", "down", "left", "right")),
            "direction"
        ),
        decl(
            TOOL_TYPE, "Type text into the text box that currently has focus. Never for passwords, OTPs or card numbers.",
            JSONObject().put("text", str("Text to type.")),
            "text"
        ),
        decl(
            TOOL_PRESS, "Press a system button.",
            JSONObject().put("button", str("Which button.", "back", "home", "recents", "notifications")),
            "button"
        ),
        decl(
            TOOL_OPEN, "Open an installed app by its name.",
            JSONObject().put("name", str("App name as shown on the phone, for example WhatsApp or Chrome.")),
            "name"
        ),
        decl(
            TOOL_YOUTUBE, "Open YouTube with a search for the given words. It does not pick or play a video; use the screen functions for that.",
            JSONObject().put("query", str("What to search for.")),
            "query"
        ),
        decl(
            TOOL_VOLUME, "Change the media volume: either an exact percent or one step up/down.",
            JSONObject()
                .put("percent", num("Exact volume 0-100."))
                .put("direction", str("One step.", "up", "down"))
        ),
        decl(
            TOOL_MEDIA, "Play/pause, next or previous for the media that is playing.",
            JSONObject().put("action", str("What to do.", "play_pause", "next", "previous")),
            "action"
        ),
        decl(
            TOOL_WHATSAPP, "Open a WhatsApp chat with the message already typed. It does NOT send it; the user must agree, then you press Send on the screen.",
            JSONObject()
                .put("to", str("Phone number, or a contact name from the user's contacts."))
                .put("text", str("Message text (may be empty).")),
            "to"
        ),
    )

    /** One screen picture as live input (JPEG). */
    fun buildVideoFrame(jpeg: ByteArray): String {
        val video = JSONObject()
            .put("data", Base64.getEncoder().encodeToString(jpeg))
            .put("mimeType", "image/jpeg")
        return JSONObject().put("realtimeInput", JSONObject().put("video", video)).toString()
    }

    fun buildToolResponse(id: String, name: String, ok: Boolean, detail: String? = null): String {
        val resp = JSONObject()
            .put("id", id)
            .put("name", name)
            .put("response", JSONObject().put("ok", ok).put("result", detail ?: if (ok) "saved" else "not saved"))
        return JSONObject()
            .put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(resp)))
            .toString()
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
        root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val c = calls.optJSONObject(i) ?: continue
                val name = c.optString("name")
                if (name.isEmpty()) continue
                out.add(ServerEvent.ToolCall(c.optString("id"), name, c.optJSONObject("args") ?: JSONObject()))
            }
        }
        if (root.has("goAway")) out.add(ServerEvent.GoAway)
        root.optJSONObject("sessionResumptionUpdate")?.let { u ->
            val h = u.optString("newHandle")
            if (u.optBoolean("resumable") && h.isNotEmpty()) out.add(ServerEvent.ResumptionHandle(h))
        }
        return out
    }
}
