package com.zenro.app.voice

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.zenro.app.audio.MicStreamer
import com.zenro.app.audio.Pcm
import com.zenro.app.audio.PcmPlayer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Real-time streaming voice over the Gemini Live WebSocket:
 * mic PCM goes up continuously, model audio comes back continuously,
 * and the server's "interrupted" signal flushes playback (barge-in).
 *
 * Reconnect uses the server's session-resumption handle when we have one, so the same
 * conversation continues. If there is no handle, a new session starts and the app re-sends
 * a short recap of the recent transcript so the assistant is not starting blind.
 */
class GeminiLiveProvider(context: Context) : VoiceProvider {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val mic = MicStreamer()
    private val player = PcmPlayer(GeminiLiveProtocol.OUTPUT_SAMPLE_RATE)
    private val muted = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var stopped = true
    @Volatile private var ready = false
    @Volatile private var speaking = false
    @Volatile private var ws: WebSocket? = null
    @Volatile private var resumeHandle: String? = null
    private var attempts = 0
    private var prevMode = AudioManager.MODE_NORMAL
    private var prevSpeaker = false
    private var audioRouted = false
    private lateinit var config: VoiceConfig
    private lateinit var cb: VoiceCallbacks

    override fun start(config: VoiceConfig, callbacks: VoiceCallbacks) {
        this.config = config
        this.cb = callbacks
        stopped = false
        attempts = 0
        resumeHandle = null
        routeAudio()
        player.start()
        connect()
    }

    override fun setMuted(muted: Boolean) {
        this.muted.set(muted)
        if (muted && this::cb.isInitialized) cb.onLevel(0f)
    }

    override fun stop() {
        stopped = true
        cleanup()
    }

    @Suppress("DEPRECATION")
    private fun routeAudio() {
        prevMode = audioManager.mode
        prevSpeaker = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = true
        audioRouted = true
    }

    @Suppress("DEPRECATION")
    private fun restoreAudio() {
        if (!audioRouted) return
        audioManager.isSpeakerphoneOn = prevSpeaker
        audioManager.mode = prevMode
        audioRouted = false
    }

    private fun connect() {
        ready = false
        cb.onState(if (attempts == 0) SessionState.CONNECTING else SessionState.RECONNECTING)
        val req = Request.Builder().url(GeminiLiveProtocol.endpoint(config.apiKey)).build()
        ws = client.newWebSocket(req, listener)
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        ready = false
        speaking = false
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        mic.stop()
        player.stop()
        restoreAudio()
    }

    private fun fail(message: String) {
        if (stopped) return
        stopped = true
        cleanup()
        cb.onError(message)
    }

    private fun handleDrop(message: String, httpCode: Int?, closeCode: Int?) {
        if (stopped) return
        ready = false
        speaking = false
        val fatal = (httpCode != null && httpCode in 400..499) ||
            closeCode == 1007 || closeCode == 1008 || closeCode == 1003
        if (fatal || attempts >= MAX_ATTEMPTS) {
            fail(message)
            return
        }
        attempts++
        player.flush()
        cb.onState(SessionState.RECONNECTING)
        val delay = min(1000L * (1 shl (attempts - 1)), 8000L)
        handler.postDelayed({ if (!stopped) connect() }, delay)
    }

    private fun startMicIfNeeded() {
        if (mic.isRunning) return
        try {
            mic.start(
                onChunk = { buf, len ->
                    if (ready && !muted.get()) {
                        ws?.send(GeminiLiveProtocol.buildAudioChunk(buf, len))
                    }
                },
                onLevel = { lvl ->
                    if (!speaking) cb.onLevel(if (muted.get()) 0f else lvl)
                }
            )
        } catch (e: Exception) {
            fail("Mic start failed: ${e.message}")
        }
    }

    private fun handle(text: String) {
        for (e in GeminiLiveProtocol.parse(text)) {
            when (e) {
                is ServerEvent.SetupComplete -> {
                    ready = true
                    attempts = 0
                    cb.onState(SessionState.LISTENING)
                    startMicIfNeeded()
                }
                is ServerEvent.Audio -> {
                    speaking = true
                    player.enqueue(e.pcm)
                    cb.onLevel(Pcm.level(e.pcm, e.pcm.size))
                    cb.onState(SessionState.SPEAKING)
                }
                is ServerEvent.Interrupted -> {
                    speaking = false
                    player.flush()
                    cb.onState(SessionState.LISTENING)
                }
                is ServerEvent.TurnComplete -> {
                    speaking = false
                    cb.onTurnComplete()
                    cb.onState(SessionState.LISTENING)
                }
                is ServerEvent.InputText -> cb.onTranscript(Role.USER, e.text)
                is ServerEvent.OutputText -> cb.onTranscript(Role.ASSISTANT, e.text)
                is ServerEvent.GoAway -> { /* server will close soon; reconnect happens on drop */ }
                is ServerEvent.ResumptionHandle -> resumeHandle = e.handle
                is ServerEvent.Error -> fail("Server error: ${e.message}")
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val handle = resumeHandle
            val prompt = if (handle == null && attempts > 0) config.promptForNewSession() else config.systemPrompt
            webSocket.send(
                GeminiLiveProtocol.buildSetup(config.model, config.voiceName, prompt, handle)
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket === ws) handle(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            if (webSocket === ws) handle(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== ws) return
            handleDrop("Connection closed ($code) $reason", null, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== ws) return
            handleDrop("Network error: ${t.message ?: t.javaClass.simpleName}", response?.code, null)
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
    }
}
