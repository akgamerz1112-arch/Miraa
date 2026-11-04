package com.zenro.app

import com.zenro.app.voice.Gender
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.MemoryMode
import com.zenro.app.voice.Persona
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V08Test {
    @Test fun setupCanDropCompressionAndResumption() {
        val s = JSONObject(
            GeminiLiveProtocol.buildSetup("m", "Kore", "p", compression = false, resumption = false)
        ).getJSONObject("setup")
        assertFalse(s.has("contextWindowCompression"))
        assertFalse(s.has("sessionResumption"))
        assertTrue(s.has("outputAudioTranscription"))
    }

    @Test fun personaHasEmotionRange() {
        val p = Persona.systemPrompt(Gender.FEMALE, "", memoryMode = MemoryMode.TOOL)
        assertTrue(p.contains("Emotional protocol"))
        assertTrue(p.contains("Emotional continuity"))
        assertTrue(p.contains("Ayan sir"))
    }
}
