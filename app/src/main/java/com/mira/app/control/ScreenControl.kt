package com.mira.app.control

import android.os.Handler
import android.os.Looper
import org.json.JSONObject

/** Runs the phone actions the MIRA agent asks for over RPC (tap, swipe, type, buttons, open app). Results are reported honestly. */
object ScreenControl {
    private val main = Handler(Looper.getMainLooper())

    fun available(): Boolean = MiraAccessibilityService.instance != null

    /** Short Hinglish line for the screen, so the user always sees what MIRA is doing. */
    fun describe(name: String, args: JSONObject): String = when (name) {
        PhoneTool.TAP -> "MIRA tap kar rahi hai"
        PhoneTool.SWIPE -> "MIRA swipe kar rahi hai (${args.optString("direction")})"
        PhoneTool.TYPE -> "MIRA type kar rahi hai"
        PhoneTool.PRESS -> "MIRA ${args.optString("button")} dabaa rahi hai"
        PhoneTool.OPEN -> "MIRA app khol rahi hai: ${args.optString("name")}"
        PhoneTool.YOUTUBE -> "MIRA YouTube pe dhoondh rahi hai: ${args.optString("query")}"
        PhoneTool.VOLUME -> "MIRA volume badal rahi hai"
        PhoneTool.MEDIA -> "MIRA media control kar rahi hai (${args.optString("action")})"
        PhoneTool.WHATSAPP -> "MIRA WhatsApp chat khol rahi hai"
        else -> "MIRA kuch kar rahi hai"
    }

    /** [reply] gets (ok, detail). ok=true only means Android accepted the action, not that the app changed. */
    fun run(name: String, args: JSONObject, reply: (Boolean, String) -> Unit) {
        main.post {
            val svc = MiraAccessibilityService.instance
            if (svc == null) {
                reply(false, "Accessibility service band hai.")
                return@post
            }
            val (w, h) = svc.screenSize()
            when (name) {
                PhoneTool.TAP -> {
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
                PhoneTool.SWIPE -> {
                    val s = ScreenMath.swipe(args.optString("direction"), w, h)
                    if (s == null) {
                        reply(false, "direction up, down, left ya right hona chahiye.")
                    } else {
                        svc.swipe(s) { ok ->
                            reply(ok, if (ok) "swipe bheja gaya; asar agli frame mein dekho" else "swipe cancel ho gaya")
                        }
                    }
                }
                PhoneTool.TYPE -> {
                    val err = svc.typeText(args.optString("text"))
                    reply(err == null, err ?: "text likh diya gaya; agli frame mein check karo")
                }
                PhoneTool.PRESS -> {
                    val ok = svc.press(args.optString("button"))
                    reply(ok, if (ok) "button dabaya gaya; asar agli frame mein dekho" else "button naam galat ya fail")
                }
                PhoneTool.OPEN -> {
                    val err = svc.openApp(args.optString("name"))
                    reply(err == null, err ?: "app kholne ki command gayi; agli frame mein check karo")
                }
                PhoneTool.YOUTUBE -> {
                    val err = PhoneActions.youtubeSearch(svc, args.optString("query"))
                    reply(err == null, err ?: "YouTube search khul gaya; video ab screen se chuno aur agli frame mein check karo")
                }
                PhoneTool.VOLUME -> {
                    val pct = if (args.has("percent")) args.optInt("percent") else null
                    val err = PhoneActions.setVolume(svc, pct, args.optString("direction"))
                    reply(err == null, err ?: "volume badal diya")
                }
                PhoneTool.MEDIA -> {
                    val err = PhoneActions.media(svc, args.optString("action"))
                    reply(err == null, err ?: "media key bheji gayi; asar sunke/dekhke check karo")
                }
                PhoneTool.WHATSAPP -> {
                    val err = PhoneActions.whatsapp(svc, args.optString("to"), args.optString("text"))
                    reply(err == null, err ?: "WhatsApp chat khul gayi, message likha hua hai; Send abhi NAHI dabaya gaya")
                }
                else -> reply(false, "Ye action MIRA ko nahi aata.")
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
        val svc = MiraAccessibilityService.instance
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
