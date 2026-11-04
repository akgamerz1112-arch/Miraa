package com.mira.app

import com.mira.app.audio.AudioLogic
import com.mira.app.audio.OutKind
import com.mira.app.audio.RouteMode
import com.mira.app.audio.RoutePref
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceV2Test {

    @Test
    fun route_autoUsesHeadphonesWhenPresent() {
        assertEquals(RouteMode.HEADPHONE_HQ, AudioLogic.chooseRoute(listOf(OutKind.SPEAKER, OutKind.BLUETOOTH), RoutePref.AUTO))
        assertEquals(RouteMode.HEADPHONE_HQ, AudioLogic.chooseRoute(listOf(OutKind.WIRED), RoutePref.AUTO))
    }

    @Test
    fun route_autoFallsBackToSpeaker() {
        assertEquals(RouteMode.SPEAKER, AudioLogic.chooseRoute(listOf(OutKind.SPEAKER, OutKind.EARPIECE), RoutePref.AUTO))
        assertEquals(RouteMode.SPEAKER, AudioLogic.chooseRoute(emptyList(), RoutePref.HEADPHONES))
    }

    @Test
    fun route_explicitChoicesWin() {
        val withBt = listOf(OutKind.SPEAKER, OutKind.BLUETOOTH)
        assertEquals(RouteMode.SPEAKER, AudioLogic.chooseRoute(withBt, RoutePref.SPEAKER))
        assertEquals(RouteMode.EAR, AudioLogic.chooseRoute(withBt, RoutePref.EAR))
    }

    private fun pcm(vararg samples: Int): ByteArray {
        val b = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            b[2 * i] = (s and 0xFF).toByte()
            b[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return b
    }

    private fun sample(b: ByteArray, i: Int): Int = (b[2 * i + 1].toInt() shl 8) or (b[2 * i].toInt() and 0xFF)

    @Test
    fun gain_scalesAndKeepsSign() {
        val out = AudioLogic.applyGain(pcm(1000, -1000, 0), 2f)
        assertEquals(2000, sample(out, 0))
        assertEquals(-2000, sample(out, 1))
        assertEquals(0, sample(out, 2))
    }

    @Test
    fun gain_neverWrapsAround() {
        val out = AudioLogic.applyGain(pcm(32767, -32768, 30000), 3f)
        assertTrue(sample(out, 0) in 26000..32767)
        assertTrue(sample(out, 1) in -32768..-26000)
        assertTrue(sample(out, 2) > 26000)
    }

    @Test
    fun gain_oneIsUntouched_andClampWorks() {
        val src = pcm(5, 6, 7)
        assertTrue(AudioLogic.applyGain(src, 1f) === src)
        assertEquals(1f, AudioLogic.clampGain(0.2f), 0f)
        assertEquals(3f, AudioLogic.clampGain(9f), 0f)
    }
}
