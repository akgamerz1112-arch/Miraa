package com.zenro.app.control

import android.os.Handler
import android.os.Looper
import com.zenro.app.voice.GeminiLiveProtocol
import org.json.JSONObject

/** Runs the tool calls the model makes (tap, swipe, type, buttons, open app). Results are reported honestly. */
object ScreenControl {
    private val main = Handler(Looper.getMainLooper())

    fun available(): Boolean = ZenroAccessibilityService.instance != null

    /** Short Hinglish line for the screen, so the user always sees what ZENRO is doing. */
    fun describe(name: String, args: JSONObject): String = when (name) {
        GeminiLiveProtocol.TOOL_TAP -> "ZENRO tap kar rahi hai"
        GeminiLiveProtocol.TOOL_SWIPE -> "ZENRO swipe kar rahi hai (${args.optString("direction")})"
        GeminiLiveProtocol.TOOL_TYPE -> "ZENRO type kar rahi hai"
        GeminiLiveProtocol.TOOL_PRESS -> "ZENRO ${args.optString("button")} dabaa rahi hai"
        GeminiLiveProtocol.TOOL_OPEN -> "ZENRO app khol rahi hai: ${args.optString("name")}"
        GeminiLiveProtocol.TOOL_YOUTUBE -> "ZENRO YouTube pe dhoondh rahi hai: ${args.optString("query")}"
        GeminiLiveProtocol.TOOL_VOLUME -> "ZENRO volume badal rahi hai"
        GeminiLiveProtocol.TOOL_MEDIA -> "ZENRO media control kar rahi hai (${args.optString("action")})"
        GeminiLiveProtocol.TOOL_WHATSAPP -> "ZENRO WhatsApp chat khol rahi hai"
        else -> "ZENRO kuch kar rahi hai"
    }

    /** [reply] gets (ok, detail). ok=true only means Android accepted the action, not that the app changed. */
    fun run(name: String, args: JSONObject, reply: (Boolean, String) -> Unit) {
        main.post {
            val svc = ZenroAccessibilityService.instance
            if (svc == null) {
                reply(false, "Accessibility service band hai.")
                return@post
            }
            val (w, h) = svc.screenSize()
            when (name) {
                GeminiLiveProtocol.TOOL_TAP -> {
                    val x = args.optDouble("x", -1.0)
                    val y = args.optDouble("y", -1.0)
                    if (x < 0 || y < 0) {
                        reply(false, "x aur y chahiye (0 se 1000).")
                    } else {
                        svc.tap(ScreenMath.toPixel(x, w), ScreenMath.toPixel(y, h)) { ok ->
                            reply(ok, if (ok) "tap bheja gaya; asar agli frame mein dekho" else "tap cancel ho gaya")
                        }
                    }
                }
                GeminiLiveProtocol.TOOL_SWIPE -> {
                    val s = ScreenMath.swipe(args.optString("direction"), w, h)
                    if (s == null) {
                        reply(false, "direction up, down, left ya right hona chahiye.")
                    } else {
                        svc.swipe(s) { ok ->
                            reply(ok, if (ok) "swipe bheja gaya; asar agli frame mein dekho" else "swipe cancel ho gaya")
                        }
                    }
                }
                GeminiLiveProtocol.TOOL_TYPE -> {
                    val err = svc.typeText(args.optString("text"))
                    reply(err == null, err ?: "text likh diya gaya; agli frame mein check karo")
                }
                GeminiLiveProtocol.TOOL_PRESS -> {
                    val ok = svc.press(args.optString("button"))
                    reply(ok, if (ok) "button dabaya gaya; asar agli frame mein dekho" else "button naam galat ya fail")
                }
                GeminiLiveProtocol.TOOL_OPEN -> {
                    val err = svc.openApp(args.optString("name"))
                    reply(err == null, err ?: "app kholne ki command gayi; agli frame mein check karo")
                }
                GeminiLiveProtocol.TOOL_YOUTUBE -> {
                    val err = PhoneActions.youtubeSearch(svc, args.optString("query"))
                    reply(err == null, err ?: "YouTube search khul gaya; video ab screen se chuno aur agli frame mein check karo")
                }
                GeminiLiveProtocol.TOOL_VOLUME -> {
                    val pct = if (args.has("percent")) args.optInt("percent") else null
                    val err = PhoneActions.setVolume(svc, pct, args.optString("direction"))
                    reply(err == null, err ?: "volume badal diya")
                }
                GeminiLiveProtocol.TOOL_MEDIA -> {
                    val err = PhoneActions.media(svc, args.optString("action"))
                    reply(err == null, err ?: "media key bheji gayi; asar sunke/dekhke check karo")
                }
                GeminiLiveProtocol.TOOL_WHATSAPP -> {
                    val err = PhoneActions.whatsapp(svc, args.optString("to"), args.optString("text"))
                    reply(err == null, err ?: "WhatsApp chat khul gayi, message likha hua hai; Send abhi NAHI dabaya gaya")
                }
                else -> reply(false, "Ye action ZENRO ko nahi aata.")
            }
        }
    }
}

/** Takes a screenshot about once a second and hands the JPEG over. Skips identical frames. */
class ScreenStreamer(private val send: (ByteArray) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var busy = false
    private var lastHash: Int? = null
    private var lastSentAt = 0L

    private val tickTask = Runnable { tick() }

    fun start() {
        if (running) return
        running = true
        lastHash = null
        handler.post(tickTask)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickTask)
    }

    private fun tick() {
        if (!running) return
        val svc = ZenroAccessibilityService.instance
        if (svc == null || busy) {
            handler.postDelayed(tickTask, INTERVAL_MS)
            return
        }
        busy = true
        svc.screenshotJpeg(MAX_WIDTH, QUALITY) { jpeg ->
            busy = false
            if (jpeg != null && running) {
                val now = System.currentTimeMillis()
                val h = jpeg.contentHashCode()
                if (FrameGate.shouldSend(h, lastHash, now, lastSentAt)) {
                    lastHash = h
                    lastSentAt = now
                    send(jpeg)
                }
            }
            if (running) handler.postDelayed(tickTask, INTERVAL_MS)
        }
    }

    companion object {
        private const val INTERVAL_MS = 1000L
        private const val MAX_WIDTH = 720
        private const val QUALITY = 60
    }
}
