package com.zenro.app.chat

import com.zenro.app.voice.Errors
import com.zenro.app.voice.GeminiLiveProtocol
import com.zenro.app.voice.ServerEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Typed Chat tab over the SAME Gemini Live model as voice (no separate flash model).
 * One short Live session per message: setup, send the typed text, collect the model's spoken-text
 * transcript until the turn ends, then close. The audio the server also sends is thrown away.
 */
class LiveChatClient {
    private val client = OkHttpClient.Builder().build()
    private val timer = Executors.newSingleThreadScheduledExecutor()

    /** Async; [onResult] is called once, on a background thread. */
    fun send(
        apiKey: String,
        model: String,
        voiceName: String,
        system: String,
        userText: String,
        onResult: (ChatResult) -> Unit,
    ) {
        val done = AtomicBoolean(false)
        val reply = StringBuilder()
        var socket: WebSocket? = null
        fun finish(r: ChatResult) {
            if (!done.compareAndSet(false, true)) return
            try { socket?.close(1000, "done") } catch (_: Exception) {}
            onResult(r)
        }
        val timeout = timer.schedule({ finish(ChatResult.Fail("Jawab nahi aaya (time out). Dobara bhejo.")) }, 40, TimeUnit.SECONDS)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    GeminiLiveProtocol.buildSetup(
                        model, voiceName, system, null, null,
                        memoryTool = false, temperature = null, controlTools = false, emotion = false, startHigh = false
                    )
                )
            }

            private fun handle(webSocket: WebSocket, text: String) {
                for (e in GeminiLiveProtocol.parse(text)) {
                    when (e) {
                        is ServerEvent.SetupComplete -> webSocket.send(GeminiLiveProtocol.buildTextInput(userText))
                        is ServerEvent.OutputText -> reply.append(e.text)
                        is ServerEvent.TurnComplete -> {
                            timeout.cancel(false)
                            val t = reply.toString().trim()
                            finish(if (t.isNotEmpty()) ChatResult.Ok(t) else ChatResult.Fail("Jawab khaali aaya. Dobara bhejo."))
                        }
                        is ServerEvent.Error -> { timeout.cancel(false); finish(ChatResult.Fail(Errors.closed(0, e.message))) }
                        else -> {}
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(webSocket, bytes.utf8())
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                timeout.cancel(false)
                finish(ChatResult.Fail(Errors.closed(code, reason)))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                timeout.cancel(false)
                finish(ChatResult.Fail(if (response != null) Errors.http(response.code) else Errors.network()))
            }
        }
        socket = client.newWebSocket(Request.Builder().url(GeminiLiveProtocol.endpoint(apiKey, false)).build(), listener)
    }
}
