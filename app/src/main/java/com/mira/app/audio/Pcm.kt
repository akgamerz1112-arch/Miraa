package com.mira.app.audio

import kotlin.math.min
import kotlin.math.sqrt

object Pcm {
    /** RMS loudness of 16-bit little-endian PCM, scaled to 0..1 for the orb animation. */
    fun level(buf: ByteArray, length: Int): Float {
        val samples = length / 2
        if (samples == 0) return 0f
        var sum = 0.0
        for (i in 0 until samples) {
            val lo = buf[2 * i].toInt() and 0xFF
            val hi = buf[2 * i + 1].toInt()
            val s = (hi shl 8) or lo
            sum += s.toDouble() * s
        }
        val rms = sqrt(sum / samples).toFloat() / 32768f
        return min(1f, rms * 6f)
    }
}
