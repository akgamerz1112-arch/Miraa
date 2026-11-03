package com.zenro.app

import com.zenro.app.audio.Pcm
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.Gender
import com.zenro.app.voice.Persona
import com.zenro.app.voice.Role
import com.zenro.app.voice.ServerEvent
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.Base64
import org.junit.Test

class GeminiLiveProtocolTest {

    @Test
    fun setup_hasModelVoiceAndAudioModality() {
        val json = JSONObject(GeminiLiveProtocol.buildSetup("test-model", "Kore", "hello"))
        val setup = json.getJSONObject("setup")
        assertEquals("models/test-model", setup.getString("model"))
        val gen = setup.getJSONObject("generationConfig")
        assertEquals("AUDIO", gen.getJSONArray("responseModalities").getString(0))
        val voice = gen.getJSONObject("speechConfig").getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig").getString("voiceName")
        assertEquals("Kore", voice)
    }

    @Test
    fun setup_doesNotDoublePrefixModel() {
        val json = JSONObject(GeminiLiveProtocol.buildSetup("models/abc", "Puck", "x"))
        assertEquals("models/abc", json.getJSONObject("setup").getString("model"))
    }

    @Test
    fun audioChunk_isBase64WithPcm16kMime() {
        val pcm = byteArrayOf(1, 2, 3, 4, 9, 9)
        val json = JSONObject(GeminiLiveProtocol.buildAudioChunk(pcm, 4))
        val audio = json.getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), Base64.getDecoder().decode(audio.getString("data")))
    }

    @Test
    fun parse_setupComplete() {
        val ev = GeminiLiveProtocol.parse("""{"setupComplete":{}}""")
        assertTrue(ev.single() is ServerEvent.SetupComplete)
    }

    @Test
    fun parse_audioAndTurnComplete() {
        val data = Base64.getEncoder().encodeToString(byteArrayOf(5, 6, 7, 8))
        val msg = """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$data"}}]},"turnComplete":true}}"""
        val ev = GeminiLiveProtocol.parse(msg)
        assertEquals(2, ev.size)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), (ev[0] as ServerEvent.Audio).pcm)
        assertTrue(ev[1] is ServerEvent.TurnComplete)
    }

    @Test
    fun parse_interruptedAndTranscripts() {
        val msg = """{"serverContent":{"interrupted":true,"inputTranscription":{"text":"namaste"},"outputTranscription":{"text":"hello"}}}"""
        val ev = GeminiLiveProtocol.parse(msg)
        assertTrue(ev.any { it is ServerEvent.Interrupted })
        assertEquals("namaste", ev.filterIsInstance<ServerEvent.InputText>().single().text)
        assertEquals("hello", ev.filterIsInstance<ServerEvent.OutputText>().single().text)
    }

    @Test
    fun parse_errorMessage() {
        val ev = GeminiLiveProtocol.parse("""{"error":{"message":"bad key"}}""")
        assertEquals("bad key", (ev.single() as ServerEvent.Error).message)
    }

    @Test
    fun parse_garbageIsIgnored() {
        assertTrue(GeminiLiveProtocol.parse("not json").isEmpty())
    }

    @Test
    fun pcmLevel_silenceIsZero_loudIsPositive() {
        assertEquals(0f, Pcm.level(ByteArray(3200), 3200), 0.0001f)
        val loud = ByteArray(3200) { if (it % 2 == 1) 0x40 else 0 } // ~16384 amplitude
        assertTrue(Pcm.level(loud, loud.size) > 0.5f)
    }

    @Test
    fun setup_hasResumptionAndCompression_withoutHandleByDefault() {
        val setup = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p")).getJSONObject("setup")
        assertTrue(setup.has("contextWindowCompression"))
        assertTrue(setup.getJSONObject("sessionResumption").length() == 0)
    }

    @Test
    fun setup_passesResumeHandle() {
        val setup = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", "abc123")).getJSONObject("setup")
        assertEquals("abc123", setup.getJSONObject("sessionResumption").getString("handle"))
    }

    @Test
    fun parse_resumptionHandle_onlyWhenResumable() {
        val ok = GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h1","resumable":true}}""")
        assertEquals("h1", (ok.single() as ServerEvent.ResumptionHandle).handle)
        val no = GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h2","resumable":false}}""")
        assertTrue(no.isEmpty())
    }

    @Test
    fun parse_goAway() {
        assertTrue(GeminiLiveProtocol.parse("""{"goAway":{"timeLeft":"10s"}}""").single() is ServerEvent.GoAway)
    }

    @Test
    fun persona_followsVoiceGenderAndName() {
        val f = Persona.systemPrompt(Gender.FEMALE, "Aman")
        val m = Persona.systemPrompt(Gender.MALE, "")
        assertTrue(f.contains("rahi hoon"))
        assertTrue(f.contains("Aman"))
        assertTrue(m.contains("raha hoon"))
        assertTrue(!m.contains("user's name"))
        assertTrue(f.contains("cannot open apps"))
    }

    @Test
    fun persona_recapKeepsLastLinesAndIsBounded() {
        val lines = (1..30).map { Role.USER to "line $it" }
        val r = Persona.recap(lines, maxLines = 3)
        assertEquals("User: line 28\nUser: line 29\nUser: line 30", r)
        assertTrue(Persona.recap(listOf(Role.USER to "x".repeat(5000)), maxChars = 100).length <= 100)
        assertTrue(Persona.systemPrompt(Gender.MALE, "", r).contains("RECENT CONVERSATION"))
    }
}
