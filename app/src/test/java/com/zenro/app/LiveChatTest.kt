package com.zenro.app

import com.zenro.app.voice.GeminiLiveProtocol
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveChatTest {
    @Test fun textInputIsRealtimeInputText() {
        val o = JSONObject(GeminiLiveProtocol.buildTextInput("hello"))
        assertEquals("hello", o.getJSONObject("realtimeInput").getString("text"))
    }

    @Test fun emotionUsesAlphaOnlyWhenAsked() {
        assertTrue(GeminiLiveProtocol.endpoint("k", true).contains("v1alpha"))
        assertTrue(GeminiLiveProtocol.endpoint("k", false).contains("v1beta"))
    }
}
