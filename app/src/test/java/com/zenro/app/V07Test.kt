package com.zenro.app

import com.zenro.app.voice.Errors
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.VadPreset
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V07Test {
    @Test
    fun closedError_showsShortServerReason() {
        val m = Errors.closed(1007, "Invalid JSON payload received.\n Unknown name \"foo\" at 'setup'")
        assertTrue(m.contains("(1007)"))
        assertTrue(m.contains("Server ne kaha: Invalid JSON payload received. Unknown name"))
        assertFalse(m.contains("\n"))
        assertTrue(Errors.closed(1007, "").endsWith("check karo."))
        assertTrue(Errors.closed(1006, "x".repeat(500)).length < 400)
        assertTrue(Errors.closed(1011, "quota exceeded").contains("quota"))
    }

    @Test
    fun setup_canBeReducedToPlainShape() {
        val full = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, VadPreset.NORMAL, true, 1.0f, true, false, true)).getJSONObject("setup")
        assertTrue(full.has("tools"))
        assertTrue(full.getJSONObject("generationConfig").has("temperature"))
        val aad = full.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertTrue(aad.has("startOfSpeechSensitivity"))
        val noHigh = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, VadPreset.NORMAL, false, null, false, false, false)).getJSONObject("setup")
        val aad2 = noHigh.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertFalse(aad2.has("startOfSpeechSensitivity"))
        assertFalse(noHigh.has("tools"))
        assertFalse(noHigh.getJSONObject("generationConfig").has("temperature"))
        val plain = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, null)).getJSONObject("setup")
        assertFalse(plain.has("realtimeInputConfig"))
        assertTrue(plain.has("sessionResumption"))
    }
}
