package com.mira.app.control

import java.net.URLEncoder
import kotlin.math.roundToInt

/** Pure Kotlin helpers for screen sharing / control, so they can be unit tested on a desktop JVM. */
object ScreenMath {
    class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /** The model gives coordinates 0-1000 (0,0 = top-left). Converts one axis to real pixels. */
    fun toPixel(norm: Double, size: Int): Float = (norm.coerceIn(0.0, 1000.0) / 1000.0 * size).toFloat()

    /** [direction] is where the FINGER moves: "up" scrolls the page down. */
    fun swipe(direction: String, w: Int, h: Int): Swipe? = when (direction.trim().lowercase()) {
        "up" -> Swipe(w * 0.5f, h * 0.75f, w * 0.5f, h * 0.25f)
        "down" -> Swipe(w * 0.5f, h * 0.25f, w * 0.5f, h * 0.75f)
        "left" -> Swipe(w * 0.8f, h * 0.5f, w * 0.2f, h * 0.5f)
        "right" -> Swipe(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f)
        else -> null
    }

    /** Keeps the aspect ratio, width at most [maxW] (never enlarges). */
    fun scaledSize(w: Int, h: Int, maxW: Int): Pair<Int, Int> {
        if (w <= 0 || h <= 0 || w <= maxW) return w to h
        return maxW to (h.toDouble() * maxW / w).roundToInt().coerceAtLeast(1)
    }
}

/** Decides which screen frames are worth sending (saves free quota when the screen does not change). */
object FrameGate {
    const val KEEPALIVE_MS = 4000L

    fun shouldSend(hash: Int, lastHash: Int?, now: Long, lastSentAt: Long): Boolean =
        lastHash == null || hash != lastHash || now - lastSentAt >= KEEPALIVE_MS
}

/** Picks an installed app by the name the user said. */
object AppMatch {
    fun best(query: String, labels: List<String>): Int? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val low = labels.map { it.trim().lowercase() }
        low.indexOfFirst { it == q }.takeIf { it >= 0 }?.let { return it }
        low.indexOfFirst { it.startsWith(q) }.takeIf { it >= 0 }?.let { return it }
        low.indexOfFirst { it.contains(q) }.takeIf { it >= 0 }?.let { return it }
        return null
    }
}

/** Links for YouTube / WhatsApp, volume maths and phone-number cleanup. Pure, so testable. */
object LinkMath {
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun youtubeSearchUrl(query: String): String = "https://www.youtube.com/results?search_query=" + enc(query.trim())

    fun whatsappUrl(number: String, text: String): String =
        "https://wa.me/$number" + if (text.isBlank()) "" else "?text=" + enc(text)

    /** True when [raw] is a phone number (digits, spaces, +, -, brackets) and not a name. */
    fun looksLikeNumber(raw: String): Boolean =
        raw.none { it.isLetter() } && raw.count { it.isDigit() } >= 7

    /** Digits with country code. A bare 10-digit number gets [defaultCc] (India by default). Null if too short. */
    fun normalizeNumber(raw: String, defaultCc: String = "91"): String? {
        var d = raw.filter { it.isDigit() }.trimStart('0')
        if (d.length < 7) return null
        if (d.length == 10) d = defaultCc + d
        return d
    }

    fun volumeIndex(percent: Int, max: Int): Int = (percent.coerceIn(0, 100) * max / 100.0).roundToInt().coerceIn(0, max)
}
