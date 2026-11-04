package com.mira.app

import com.mira.app.control.PhoneTool
import com.mira.app.net.ApiLogic
import com.mira.app.rtc.ConnLogic
import com.mira.app.rtc.ConnState
import com.mira.app.rtc.RpcLogic
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class S3Test {
    // ---- URL + device id ----
    @Test fun url_onlyHttpsAccepted() {
        assertEquals("https://mira.example.com", ApiLogic.normalizeBaseUrl("  https://mira.example.com/ "))
        assertNull(ApiLogic.normalizeBaseUrl("http://192.168.1.5:8080"))
        assertNull(ApiLogic.normalizeBaseUrl("https://"))
        assertNull(ApiLogic.normalizeBaseUrl("mira.example.com"))
        assertTrue(ApiLogic.urlProblem("http://192.168.1.5:8080")!!.contains("https"))
        assertNull(ApiLogic.urlProblem("https://x.trycloudflare.com"))
    }

    @Test fun deviceId_followsServerRule() {
        assertTrue(ApiLogic.isValidDeviceId("My-Phone"))   // lower-cased like the server does
        assertTrue(ApiLogic.isValidDeviceId("a1"))
        assertFalse(ApiLogic.isValidDeviceId("a"))
        assertFalse(ApiLogic.isValidDeviceId("-abc"))
        assertFalse(ApiLogic.isValidDeviceId("my phone"))
        assertFalse(ApiLogic.isValidDeviceId("x".repeat(33)))
    }

    // ---- config / version ----
    @Test fun config_parsedAndVersionsCompared() {
        val c = ApiLogic.parseConfig(
            """{"api_version":1,"config_version":3,"min_app_version":8,"persona_version":2,"features":{"phone_tools":true}}"""
        )!!
        assertEquals(8, c.minAppVersion)
        assertFalse(ApiLogic.needsUpdate(c, 8))
        assertTrue(ApiLogic.needsUpdate(c, 7))
        assertTrue(ApiLogic.needsUpdate(c.copy(apiVersion = 2), 8))
        assertNull(ApiLogic.parseConfig("not json"))
        assertNull(ApiLogic.parseConfig("""{"foo":1}"""))
    }

    // ---- token ----
    @Test fun tokenBody_hasDeviceIdAndIntVersion() {
        val o = JSONObject(ApiLogic.tokenBody(" My-Phone ", 8))
        assertEquals("my-phone", o.getString("device_id"))
        assertEquals(8, o.getInt("app_version"))
    }

    @Test fun token_parsedOrRejected() {
        val t = ApiLogic.parseToken("""{"server_url":"wss://x.livekit.cloud","room":"mira-a-1","identity":"dev-a","token":"jwt"}""")!!
        assertEquals("dev-a", t.identity)
        assertNull(ApiLogic.parseToken("""{"server_url":"","token":"jwt"}"""))
        assertNull(ApiLogic.parseToken("oops"))
    }

    @Test fun httpErrors_areSpecificAndUpdateIsClear() {
        assertTrue(ApiLogic.httpMessage(426).contains("update"))
        assertTrue(ApiLogic.httpMessage(401).contains("401"))
        assertTrue(ApiLogic.httpMessage(429).contains("429"))
        assertTrue(ApiLogic.httpMessage(503).contains("503"))
    }

    // ---- RPC ----
    @Test fun rpc_exactlyNineMethodsAllMapToPhoneTools() {
        assertEquals(9, RpcLogic.METHODS.size)
        assertEquals(9, RpcLogic.METHODS.values.toSet().size)
        assertTrue(RpcLogic.METHODS.keys.all { it.startsWith("mira.") })
        assertEquals(PhoneTool.OPEN, RpcLogic.METHODS["mira.open_app"])
        assertEquals(PhoneTool.WHATSAPP, RpcLogic.METHODS["mira.whatsapp_message"])
    }

    @Test fun rpc_unknownMethodIsRefused() {
        for (m in listOf("mira.shell", "mira.delete_all", "open_app", "", "mira.tap ")) {
            assertTrue(m, RpcLogic.parse(m, "{}") is RpcLogic.Parsed.Refuse)
        }
    }

    @Test fun rpc_payloadsPassThroughWithSameKeysAsControlCode() {
        val tap = RpcLogic.parse("mira.tap", """{"x":500,"y":250}""") as RpcLogic.Parsed.Call
        assertEquals(PhoneTool.TAP, tap.tool)
        assertEquals(500.0, tap.args.optDouble("x"), 0.0)
        val wa = RpcLogic.parse("mira.whatsapp_message", """{"to":"Amma","text":"hi"}""") as RpcLogic.Parsed.Call
        assertEquals("Amma", wa.args.getString("to"))
        val vol = RpcLogic.parse("mira.set_volume", """{"direction":"up"}""") as RpcLogic.Parsed.Call
        assertFalse(vol.args.has("percent"))
    }

    @Test fun rpc_badOrHugePayloadRefused() {
        assertTrue(RpcLogic.parse("mira.tap", "{{") is RpcLogic.Parsed.Refuse)
        val huge = JSONObject().put("text", "a".repeat(RpcLogic.MAX_TEXT + 1)).toString()
        assertTrue(RpcLogic.parse("mira.type_text", huge) is RpcLogic.Parsed.Refuse)
    }

    @Test fun rpc_replyIsOkMessageJson() {
        val o = JSONObject(RpcLogic.reply(false, "Accessibility service band hai."))
        assertFalse(o.getBoolean("ok"))
        assertNotNull(o.getString("message"))
        assertEquals(2, o.length())
    }

    // ---- connection labels ----
    @Test fun connection_everyStateHasHinglishLabelAndActiveFlag() {
        for (s in ConnState.values()) assertTrue(ConnLogic.label(s, false).isNotBlank())
        assertFalse(ConnLogic.active(ConnState.IDLE))
        assertFalse(ConnLogic.active(ConnState.ERROR))
        assertFalse(ConnLogic.active(ConnState.UPDATE_NEEDED))
        assertTrue(ConnLogic.active(ConnState.WAITING_AGENT))
        assertTrue(ConnLogic.label(ConnState.AGENT_JOINED, true).contains("muted"))
    }
}
