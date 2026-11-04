package com.zenro.app

import com.zenro.app.audio.AudioLogic
import com.zenro.app.chat.GeminiTextProtocol
import com.zenro.app.memory.MemoryItem
import com.zenro.app.memory.MemoryLogic
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.Gender
import com.zenro.app.voice.MemoryMode
import com.zenro.app.voice.Persona
import com.zenro.app.voice.Role
import com.zenro.app.voice.ServerEvent
import com.zenro.app.voice.VadPreset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V03Test {

    // ---- memory ----
    @Test
    fun memory_cleanRejectsSecretsAndJunk() {
        assertNull(MemoryLogic.clean("  "))
        assertNull(MemoryLogic.clean("ab"))
        assertNull(MemoryLogic.clean("mera card 4111 1111 1111 1111"))
        assertNull(MemoryLogic.clean("my OTP is 4821"))
        assertNull(MemoryLogic.clean("wifi password hai abc"))
        assertEquals("Rahul uska chhota bhai hai", MemoryLogic.clean("  \"Rahul uska   chhota bhai hai\" "))
        assertTrue(MemoryLogic.clean("x".repeat(500))!!.length <= MemoryLogic.MAX_FACT_CHARS)
    }

    @Test
    fun memory_addDedupesAndCaps() {
        var l = emptyList<MemoryItem>()
        l = MemoryLogic.add(l, "Chai pasand hai", 1)
        l = MemoryLogic.add(l, "chai PASAND hai", 2)
        assertEquals(1, l.size)
        repeat(MemoryLogic.MAX_ITEMS + 10) { l = MemoryLogic.add(l, "fact number $it here", it.toLong()) }
        assertEquals(MemoryLogic.MAX_ITEMS, l.size)
        assertEquals("fact number ${MemoryLogic.MAX_ITEMS + 9} here", l.last().text)
    }

    @Test
    fun memory_removeAndJsonRoundTrip() {
        val l = MemoryLogic.add(MemoryLogic.add(emptyList(), "pehla fact", 5), "doosra fact", 6)
        assertEquals(listOf("doosra fact"), MemoryLogic.remove(l, 0).map { it.text })
        assertEquals(l, MemoryLogic.remove(l, 9))
        assertEquals(l, MemoryLogic.fromJson(MemoryLogic.toJson(l)))
        assertTrue(MemoryLogic.fromJson("not json").isEmpty())
        assertTrue(MemoryLogic.fromJson("").isEmpty())
    }

    @Test
    fun memory_promptBlockKeepsNewestWhenCut() {
        val l = (1..20).map { MemoryItem("fact $it " + "z".repeat(40), it.toLong()) }
        val block = MemoryLogic.promptBlock(l, maxChars = 200)
        assertTrue(block.length <= 200)
        assertTrue(block.contains("fact 20 "))
        assertTrue(!block.contains("fact 1 "))
        assertEquals("", MemoryLogic.promptBlock(emptyList()))
    }

    @Test
    fun memory_extractTagsStripsAndReturnsFacts() {
        val (clean, facts) = MemoryLogic.extractTags("Arre wah, mubarak!\n[[YAAD: Aman ka exam Friday ko hai]]")
        assertEquals("Arre wah, mubarak!", clean)
        assertEquals(listOf("Aman ka exam Friday ko hai"), facts)
        val (c2, f2) = MemoryLogic.extractTags("Bas aise hi baat.")
        assertEquals("Bas aise hi baat.", c2)
        assertTrue(f2.isEmpty())
    }

    // ---- persona ----
    @Test
    fun persona_memoryModesAreHonest() {
        val none = Persona.systemPrompt(Gender.FEMALE, "")
        assertTrue(none.contains("do not have saved memories"))
        val tool = Persona.systemPrompt(Gender.FEMALE, "", "", memoryBlock = "- Chai pasand hai", memoryMode = MemoryMode.TOOL)
        assertTrue(tool.contains("save_memory"))
        assertTrue(tool.contains("Chai pasand hai"))
        val empty = Persona.systemPrompt(Gender.MALE, "", memoryMode = MemoryMode.TOOL)
        assertTrue(empty.contains("nothing saved yet"))
        val tag = Persona.chatPrompt(Gender.MALE, "")
        assertTrue(tag.contains("[[YAAD:"))
        assertTrue(tag.contains("TYPED text chat"))
    }

    @Test
    fun persona_moodChangesWithSeedAndIsStable() {
        assertEquals(Persona.moodFor(3), Persona.moodFor(3))
        assertNotEquals(Persona.moodFor(1), Persona.moodFor(2))
        assertTrue(Persona.moodFor(-5).isNotBlank())
        val a = Persona.systemPrompt(Gender.MALE, "", moodSeed = 1, dayPart = "night")
        assertTrue(a.contains(Persona.moodFor(1)))
        assertTrue(a.contains("night"))
        assertTrue(a.contains("very softly"))
        assertTrue(!Persona.systemPrompt(Gender.MALE, "").contains("Your vibe right now"))
    }

    // ---- live protocol: soft speech + tools ----
    @Test
    fun setup_vadAsksForHighStartSensitivity() {
        val s = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, VadPreset.SLOW)).getJSONObject("setup")
        val aad = s.getJSONObject("realtimeInputConfig").getJSONObject("automaticActivityDetection")
        assertEquals("START_SENSITIVITY_HIGH", aad.getString("startOfSpeechSensitivity"))
        assertEquals(1100, aad.getInt("silenceDurationMs"))
    }

    @Test
    fun setup_toolsAndTemperatureOnlyWhenAsked() {
        val plain = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p")).getJSONObject("setup")
        assertTrue(!plain.has("tools"))
        assertTrue(!plain.getJSONObject("generationConfig").has("temperature"))
        val s = JSONObject(GeminiLiveProtocol.buildSetup("m", "Kore", "p", null, null, true, 1.0f)).getJSONObject("setup")
        val decl = s.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals("save_memory", decl.getString("name"))
        assertEquals("fact", decl.getJSONObject("parameters").getJSONArray("required").getString(0))
        assertEquals(1.0, s.getJSONObject("generationConfig").getDouble("temperature"), 0.001)
    }

    @Test
    fun toolCall_parsedAndAnswered() {
        val msg = """{"toolCall":{"functionCalls":[{"id":"c1","name":"save_memory","args":{"fact":"Chai pasand hai"}}]}}"""
        val ev = GeminiLiveProtocol.parse(msg).filterIsInstance<ServerEvent.ToolCall>()
        assertEquals(1, ev.size)
        assertEquals("c1", ev[0].id)
        assertEquals("Chai pasand hai", ev[0].args.getString("fact"))
        val resp = JSONObject(GeminiLiveProtocol.buildToolResponse("c1", "save_memory", true))
        val fr = resp.getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("c1", fr.getString("id"))
        assertEquals("saved", fr.getJSONObject("response").getString("result"))
    }

    @Test
    fun micGain_boostsQuietSpeechAndClamps() {
        assertEquals(AudioLogic.MIC_MAX_GAIN, AudioLogic.clampMicGain(9f), 0f)
        assertEquals(AudioLogic.MIC_MIN_GAIN, AudioLogic.clampMicGain(0.1f), 0f)
        // one quiet sample (value 1000) -> about 2000 at 2x
        val pcm = byteArrayOf((1000 and 0xFF).toByte(), (1000 shr 8).toByte())
        val out = AudioLogic.applyGain(pcm, 2f)
        val v = (out[1].toInt() shl 8) or (out[0].toInt() and 0xFF)
        assertEquals(2000, v)
    }

    // ---- text chat protocol ----
    @Test
    fun chat_requestMergesTurnsAndStartsWithUser() {
        val h = listOf(
            Role.ASSISTANT to "purana jawab",
            Role.USER to "hi", Role.USER to "sunno",
            Role.ASSISTANT to "haan bolo",
            Role.USER to "kaisi ho"
        )
        val turns = GeminiTextProtocol.turns(h)
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER), turns.map { it.first })
        assertEquals("hi\nsunno", turns[0].second)
        val req = JSONObject(GeminiTextProtocol.buildRequest("sys", h))
        assertEquals("sys", req.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val c = req.getJSONArray("contents")
        assertEquals("user", c.getJSONObject(0).getString("role"))
        assertEquals("model", c.getJSONObject(1).getString("role"))
    }

    @Test
    fun chat_parseReplySkipsThoughtsAndHandlesEmpty() {
        val ok = """{"candidates":[{"content":{"parts":[{"text":"soch","thought":true},{"text":"Hello "},{"text":"dost"}]}}]}"""
        assertEquals("Hello dost", GeminiTextProtocol.parseReply(ok))
        assertNull(GeminiTextProtocol.parseReply("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertNull(GeminiTextProtocol.parseReply("garbage"))
        assertNotNull(GeminiTextProtocol.url("models/x"))
        assertTrue(GeminiTextProtocol.url("models/x").endsWith("/models/x:generateContent"))
    }
}
