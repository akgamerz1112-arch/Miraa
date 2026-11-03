package com.zenro.app.audio

import kotlin.math.abs
import kotlin.math.roundToInt

enum class RoutePref { AUTO, HEADPHONES, SPEAKER, EAR }
enum class OutKind { BLUETOOTH, WIRED, SPEAKER, EARPIECE, OTHER }
enum class RouteMode { HEADPHONE_HQ, SPEAKER, EAR }

/** Pure Kotlin (no Android classes) so it can be unit tested on a desktop JVM. */
object AudioLogic {
    const val MIN_GAIN = 1.0f
    const val MAX_GAIN = 3.0f
    const val DEFAULT_GAIN = 1.6f
    private const val KNEE = 26000f

    fun chooseRoute(devices: List<OutKind>, pref: RoutePref): RouteMode {
        val hasHeadphones = devices.any { it == OutKind.BLUETOOTH || it == OutKind.WIRED }
        return when (pref) {
            RoutePref.AUTO, RoutePref.HEADPHONES ->
                if (hasHeadphones) RouteMode.HEADPHONE_HQ else RouteMode.SPEAKER
            RoutePref.SPEAKER -> RouteMode.SPEAKER
            RoutePref.EAR -> RouteMode.EAR
        }
    }

    fun clampGain(g: Float): Float = g.coerceIn(MIN_GAIN, MAX_GAIN)

    /** PCM16 little-endian gain with a soft knee and hard clamp (no wrap-around distortion). */
    fun applyGain(pcm: ByteArray, gain: Float): ByteArray {
        if (gain in 0.99f..1.01f) return pcm
        val out = ByteArray(pcm.size)
        val n = pcm.size / 2
        for (i in 0 until n) {
            val s = (pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)
            var v = s * gain
            val a = abs(v)
            if (a > KNEE) v = (if (v < 0) -1f else 1f) * (KNEE + (a - KNEE) * 0.25f)
            val c = v.roundToInt().coerceIn(-32768, 32767)
            out[2 * i] = (c and 0xFF).toByte()
            out[2 * i + 1] = ((c shr 8) and 0xFF).toByte()
        }
        if (pcm.size % 2 == 1) out[pcm.size - 1] = pcm[pcm.size - 1]
        return out
    }
}
