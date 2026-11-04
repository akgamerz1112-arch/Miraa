package com.mira.app

import com.mira.app.control.LinkMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V05Test {

    @Test
    fun links_areEncodedAndWhatsAppHasNoPlusSigns() {
        assertEquals(
            "https://www.youtube.com/results?search_query=arijit%20singh%20songs",
            LinkMath.youtubeSearchUrl("  arijit singh songs ")
        )
        val u = LinkMath.whatsappUrl("919876543210", "Hi Rahul, kal milte hain!")
        assertTrue(u.startsWith("https://wa.me/919876543210?text="))
        assertFalse(u.contains("+"))
        assertFalse(u.contains(" "))
        assertEquals("https://wa.me/919876543210", LinkMath.whatsappUrl("919876543210", " "))
    }

    @Test
    fun numbers_normalizeAndNamesAreNotNumbers() {
        assertTrue(LinkMath.looksLikeNumber("+91 98765-43210"))
        assertFalse(LinkMath.looksLikeNumber("Rahul"))
        assertFalse(LinkMath.looksLikeNumber("Rahul 2"))
        assertEquals("919876543210", LinkMath.normalizeNumber("98765 43210"))
        assertEquals("919876543210", LinkMath.normalizeNumber("+91 98765 43210"))
        assertEquals("919876543210", LinkMath.normalizeNumber("09876543210".substring(1)))
        assertNull(LinkMath.normalizeNumber("12345"))
    }

    @Test
    fun volume_percentMapsToIndex() {
        assertEquals(0, LinkMath.volumeIndex(-5, 15))
        assertEquals(15, LinkMath.volumeIndex(500, 15))
        assertEquals(8, LinkMath.volumeIndex(50, 15))
    }
}
