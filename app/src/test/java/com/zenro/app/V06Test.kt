package com.zenro.app

import com.zenro.app.voice.GeminiLiveProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V06Test {
    @Test
    fun endpoint_usesAlphaOnlyForEmotionMode() {
        assertTrue(GeminiLiveProtocol.endpoint("K").contains(".v1beta."))
        assertTrue(GeminiLiveProtocol.endpoint("K", true).contains(".v1alpha."))
    }

    @Test
    fun setup_emotionAddsAffectiveDialogAndProactiveAudio() {
        val off = JSONObject(GeminiLiveProtocol.buildSetup("m", "Aoede", "p")).getJSONObject("setup")
        assertFalse(off.has("proactivity"))
        assertFalse(off.getJSONObject("generationConfig").has("enableAffectiveDialog"))
        val on = JSONObject(
            GeminiLiveProtocol.buildSetup(GeminiLiveProtocol.NATIVE_AUDIO_MODEL, "Aoede", "p", null, null, false, 1.0f, false, true)
        ).getJSONObject("setup")
        assertTrue(on.getJSONObject("generationConfig").getBoolean("enableAffectiveDialog"))
        assertTrue(on.getJSONObject("proactivity").getBoolean("proactiveAudio"))
        assertEquals("models/" + GeminiLiveProtocol.NATIVE_AUDIO_MODEL, on.getString("model"))
    }
}
