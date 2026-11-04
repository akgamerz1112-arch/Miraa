package com.zenro.app.chat

import android.media.AudioAttributes
import com.zenro.app.audio.PcmPlayer
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
 * One short Live session per message: setup, send the typed text, then the reply is BOTH spoken
 * (audio plays through the speaker) and returned as text (the transcript of what was spoken).
 * If the server refuses the setup, simpler setups are tried automatically:
 * emotion + memory -> memory only -> plain.
 */
class LiveChatClient {
    private val client = OkHttpClient.Builder().build()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var player: PcmPlayer? = null

    /** Stop any reply that is still being spoken (a new message or leaving the tab). */
    fun stopSpeaking() {
        try { player?.stop() } catch (_: Exception) {}
        player = null
    }

    /** Async; [onResult] is called once, on a background thread. */
    fun send(
        apiKey: String,
        model: String,
        voiceName: String,
        system: String,
        userText: String,
        emotion: Boolean,
        speak: Boolean,
        gain: Float,
        onMemory: (String) -> Boolean,
        onResult: (ChatResult) -> Unit,
    ) {
        stopSpeaking()
        val done = AtomicBoolean(false)
        var socket: WebSocket? = null
        var step = 0 // 0 = emotion + memory, 1 = memory only, 2 = plain
        val spk = if (speak) PcmPlayer().also {
            it.gain = gain
            try { it.start(AudioAttributes.USAGE_MEDIA) } catch (_: Exception) {}
            player = it
        } else null
        fun finish(r: ChatResult) {
            if (!done.compareAndSet(false, true)) return
            try { socket?.close(1000, "done") } catch (_: Exception) {}
            if (r is ChatResult.Ok) spk?.stopWhenDrained() else spk?.stop()
            onResult(r)
        }
        val timeout = timer.schedule(Runnable { finish(ChatResult.Fail("Jawab nahi aaya (time out). Dobara bhejo.")) }, 45, TimeUnit.SECONDS)

        lateinit var connect: () -> Unit
        fun retryOrFail(fail: ChatResult.Fail, setupDone: Boolean): Boolean {
            if (setupDone || step >= 2 || done.get()) return false
            step++
            // Emotion is dropped first, then the memory tool.
            connect()
            return true
        }

        connect = {
            val reply = StringBuilder()
            var setupDone = false
            val useEmotion = emotion && step == 0
            val useTools = step <= 1
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(
                        GeminiLiveProtocol.buildSetup(
                            model, voiceName, system, null, null,
                            memoryTool = useTools, temperature = null, controlTools = false,
                            emotion = useEmotion, startHigh = false,
                            compression = false, resumption = false
                        )
                    )
                }

                private fun handle(webSocket: WebSocket, text: String) {
                    for (e in GeminiLiveProtocol.parse(text)) {
                        when (e) {
                            is ServerEvent.SetupComplete -> {
                                setupDone = true
                                webSocket.send(GeminiLiveProtocol.buildTextInput(userText))
                            }
                            is ServerEvent.Audio -> spk?.enqueue(e.pcm)
                            is ServerEvent.OutputText -> reply.append(e.text)
                            is ServerEvent.ToolCall -> {
                                val ok = if (e.name == GeminiLiveProtocol.TOOL_SAVE_MEMORY) onMemory(e.args.optString("fact")) else false
                                webSocket.send(GeminiLiveProtocol.buildToolResponse(e.id, e.name, ok, null))
                            }
                            is ServerEvent.TurnComplete -> {
                                timeout.cancel(false)
                                val t = reply.toString().trim()
                                finish(if (t.isNotEmpty()) ChatResult.Ok(t) else ChatResult.Fail("Jawab khaali aaya. Dobara bhejo."))
                            }
                            is ServerEvent.Error -> {
                                val f = ChatResult.Fail(Errors.closed(0, e.message))
                                if (!retryOrFail(f, setupDone)) { timeout.cancel(false); finish(f) }
                            }
                            else -> {}
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(webSocket, bytes.utf8())
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (webSocket !== socket || done.get()) return
                    val f = ChatResult.Fail(Errors.closed(code, reason))
                    if (!retryOrFail(f, setupDone)) { timeout.cancel(false); finish(f) }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (webSocket !== socket || done.get()) return
                    val f = ChatResult.Fail(if (response != null) Errors.http(response.code) else Errors.network())
                    if (!retryOrFail(f, setupDone)) { timeout.cancel(false); finish(f) }
                }
            }
            val old = socket
            val ws = client.newWebSocket(
                Request.Builder().url(GeminiLiveProtocol.endpoint(apiKey, false)).build(), listener
            )
            socket = ws
            try { if (old !== ws) old?.close(1000, "retry") } catch (_: Exception) {}
        }
        connect()
    }
}
