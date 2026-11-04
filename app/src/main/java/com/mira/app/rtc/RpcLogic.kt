package com.mira.app.rtc

import com.mira.app.control.PhoneTool
import org.json.JSONObject

/** Pure Kotlin: which RPC methods exist and how their JSON payloads map to phone actions. Unit tested. */
object RpcLogic {
    /** Exactly these nine methods are registered. Everything else is refused. */
    val METHODS: Map<String, String> = linkedMapOf(
        "mira.open_app" to PhoneTool.OPEN,
        "mira.tap" to PhoneTool.TAP,
        "mira.swipe" to PhoneTool.SWIPE,
        "mira.type_text" to PhoneTool.TYPE,
        "mira.press_button" to PhoneTool.PRESS,
        "mira.youtube_search" to PhoneTool.YOUTUBE,
        "mira.whatsapp_message" to PhoneTool.WHATSAPP,
        "mira.set_volume" to PhoneTool.VOLUME,
        "mira.media_control" to PhoneTool.MEDIA,
    )

    const val MAX_TEXT = 1000

    sealed class Parsed {
        data class Call(val tool: String, val args: JSONObject) : Parsed()
        data class Refuse(val message: String) : Parsed()
    }

    fun parse(method: String, payload: String): Parsed {
        val tool = METHODS[method] ?: return Parsed.Refuse("Ye action MIRA ko nahi aata.")
        val args = try {
            if (payload.isBlank()) JSONObject() else JSONObject(payload)
        } catch (e: Exception) {
            return Parsed.Refuse("Payload JSON nahi hai.")
        }
        for (k in listOf("text", "query", "name", "to")) {
            if (args.optString(k).length > MAX_TEXT) return Parsed.Refuse("$k bahut lamba hai.")
        }
        return Parsed.Call(tool, args)
    }

    /** The reply the agent expects: {"ok":bool,"message":str}. */
    fun reply(ok: Boolean, message: String): String =
        JSONObject().put("ok", ok).put("message", message).toString()

    const val CONTROL_OFF = "Control band hai. MIRA app mein 'Control do' ON karo."
    const val TIMEOUT = "Phone ne waqt pe jawab nahi diya."
}
