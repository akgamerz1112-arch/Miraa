package com.mira.app

import com.mira.app.audio.AudioLogic
import com.mira.app.memory.MemoryItem
import com.mira.app.memory.MemoryLogic
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
}
