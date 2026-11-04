package com.zenro.app

import com.zenro.app.voice.Gender
import com.zenro.app.voice.Persona
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorTest {
    @Test fun promptNamesAyanSirAsOwner() {
        for (g in Gender.values()) {
            val p = Persona.systemPrompt(g, "")
            assertTrue(p.contains("Ayan sir"))
            assertTrue(p.contains("one and only owner"))
            assertTrue(p.contains("never agree"))
        }
    }
}
