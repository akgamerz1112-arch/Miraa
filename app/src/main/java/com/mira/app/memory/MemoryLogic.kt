package com.mira.app.memory

import org.json.JSONArray
import org.json.JSONObject

data class MemoryItem(val text: String, val time: Long)

/** Pure Kotlin (only org.json) so it can be unit tested. Handles Yaadein: clean, add, remove, save, prompt text. */
object MemoryLogic {
    const val MAX_ITEMS = 60
    const val MAX_FACT_CHARS = 200

    private val numberRun = Regex("(?:\\d[\\s-]?){9,}")
    private val secretWords = Regex("\\b(password|passcode|otp|cvv|pin|aadhaar|aadhar)\\b", RegexOption.IGNORE_CASE)
    private val tag = Regex("\\[\\[\\s*YAAD\\s*:(.*?)\\]\\]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** Returns a tidy fact, or null if it is empty, too short, or looks like a secret (long numbers, passwords, OTP). */
    fun clean(raw: String): String? {
        var t = raw.replace(Regex("\\s+"), " ").trim().trim('"', '\'', '-', '*', ' ')
        if (t.length < 3) return null
        if (t.length > MAX_FACT_CHARS) t = t.take(MAX_FACT_CHARS).trimEnd()
        if (numberRun.containsMatchIn(t) || secretWords.containsMatchIn(t)) return null
        return t
    }

    fun add(list: List<MemoryItem>, raw: String, now: Long): List<MemoryItem> {
        val fact = clean(raw) ?: return list
        if (list.any { it.text.equals(fact, ignoreCase = true) }) return list
        val next = list + MemoryItem(fact, now)
        return if (next.size > MAX_ITEMS) next.takeLast(MAX_ITEMS) else next
    }

    fun remove(list: List<MemoryItem>, index: Int): List<MemoryItem> =
        if (index in list.indices) list.filterIndexed { i, _ -> i != index } else list

    fun toJson(list: List<MemoryItem>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.text).put("ts", it.time)) }
        return arr.toString()
    }

    fun fromJson(json: String): List<MemoryItem> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<MemoryItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = o.optString("t")
                if (t.isNotBlank()) out.add(MemoryItem(t, o.optLong("ts")))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Facts as prompt lines, newest kept first if the text has to be cut. Empty string if none. */
    fun promptBlock(list: List<MemoryItem>, maxChars: Int = 1500): String {
        val lines = ArrayList<String>()
        var used = 0
        for (m in list.asReversed()) {
            val line = "- " + m.text
            if (used + line.length + 1 > maxChars) break
            lines.add(line)
            used += line.length + 1
        }
        return lines.asReversed().joinToString("\n")
    }

    /** Text chat: the model ends a reply with [[YAAD: fact]]. Returns the reply without tags, and the facts found. */
    fun extractTags(reply: String): Pair<String, List<String>> {
        val facts = tag.findAll(reply).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        return tag.replace(reply, "").trim() to facts
    }
}
