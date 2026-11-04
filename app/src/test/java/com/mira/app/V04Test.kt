package com.mira.app

import com.mira.app.control.AppMatch
import com.mira.app.control.FrameGate
import com.mira.app.control.ScreenMath
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V04Test {

    @Test
    fun screenMath_mapsNormalizedToPixelsAndClamps() {
        assertEquals(540f, ScreenMath.toPixel(500.0, 1080), 0.01f)
        assertEquals(0f, ScreenMath.toPixel(-20.0, 1080), 0.01f)
        assertEquals(2400f, ScreenMath.toPixel(5000.0, 2400), 0.01f)
    }

    @Test
    fun screenMath_swipeDirectionsAndScaling() {
        val up = ScreenMath.swipe("UP", 1000, 2000)!!
        assertTrue(up.y2 < up.y1) // finger moves up
        val right = ScreenMath.swipe("right", 1000, 2000)!!
        assertTrue(right.x2 > right.x1)
        assertNull(ScreenMath.swipe("diagonal", 1000, 2000))
        assertEquals(720 to 1600, ScreenMath.scaledSize(1080, 2400, 720))
        assertEquals(500 to 900, ScreenMath.scaledSize(500, 900, 720)) // never enlarges
    }

    @Test
    fun frameGate_skipsSameFramesButKeepsAlive() {
        assertTrue(FrameGate.shouldSend(1, null, 1000, 0))
        assertFalse(FrameGate.shouldSend(7, 7, 2000, 1000))
        assertTrue(FrameGate.shouldSend(8, 7, 2000, 1000))
        assertTrue(FrameGate.shouldSend(7, 7, 1000 + FrameGate.KEEPALIVE_MS, 1000))
    }

    @Test
    fun appMatch_prefersExactThenPrefixThenContains() {
        val apps = listOf("WhatsApp Business", "WhatsApp", "Chrome", "YouTube Music")
        assertEquals(1, AppMatch.best("whatsapp", apps))
        assertEquals(2, AppMatch.best("chr", apps))
        assertEquals(3, AppMatch.best("music", apps))
        assertNull(AppMatch.best("instagram", apps))
        assertNull(AppMatch.best("  ", apps))
    }
}
