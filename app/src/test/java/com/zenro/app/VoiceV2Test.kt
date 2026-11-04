package com.zenro.app

import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.OutKind
import com.zenro.app.audio.RouteMode
import com.zenro.app.audio.RoutePref
import com.zenro.app.voice.Errors
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.Gender
import com.zenro.app.voice.Persona
import com.zenro.app.voice.Style
import com.zenro.app.voice.UserGender
import com.zenro.app.voice.VadPreset
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

    @Test
    fun setup_vadPresetIsWritten_andOptional() {
        val s = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, VadPreset.QUICK)).getJSONObject("setup")
        val a = s.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertEquals(500, a.getInt("silenceDurationMs"))
        assertEquals(20, a.getInt("prefixPaddingMs"))
        val none = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p")).getJSONObject("setup")
        assertTrue(!none.has("realtimeInputConfig"))
    }

    @Test
    fun persona_userGenderAndStyle() {
        val f = Persona.systemPrompt(Gender.FEMALE, "", "", Style.PYAARI, UserGender.FEMALE)
        assertTrue(f.contains("tum aayi"))
        assertTrue(f.contains("pyaari"))
        val m = Persona.systemPrompt(Gender.MALE, "", "", Style.PADHAI, UserGender.MALE)
        assertTrue(m.contains("tum aaye"))
        assertTrue(m.contains("Study-helper"))
        assertTrue(!Persona.systemPrompt(Gender.MALE, "").contains("The user is a"))
    }

    @Test
    fun persona_everyStyleHasPronunciationAndNoSexualContentRule() {
        Style.values().forEach { st ->
            val p = Persona.systemPrompt(Gender.FEMALE, "", "", st)
            assertTrue(p.contains("Indian Hindi pronunciation"))
            assertTrue(p.contains("no sexual or explicit content"))
        }
    }

    @Test
    fun errors_areHinglishAndSpecific() {
        assertTrue(Errors.http(429).contains("quota"))
        assertTrue(Errors.http(403).contains("Key"))
        assertTrue(Errors.closed(1007, "").contains("Settings"))
        assertTrue(Errors.closed(1011, "quota exhausted").contains("quota"))
        assertTrue(Errors.keyTest(200).contains("sahi"))
        assertTrue(Errors.keyTest(null).contains("Internet"))
    }
}
