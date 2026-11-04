package com.zenro.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.AudioRouter
import com.zenro.app.audio.MicStreamer
import com.zenro.app.audio.Pcm
import com.zenro.app.audio.PcmPlayer
import com.zenro.app.audio.RoutePref
import com.zenro.app.control.ScreenControl
import com.zenro.app.control.ScreenStreamer
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
    private val router = AudioRouter(appContext)
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
    @Volatile private var micGain = AudioLogic.MIC_DEFAULT_GAIN
    @Volatile private var toolsOn = false
    @Volatile private var everReady = false
    @Volatile private var emotionOn = false
    @Volatile private var emotionAlpha = false
    private var emotionStage = 0
    // Tiered safety net: if the server rejects the first setup, drop optional parts step by step.
    @Volatile private var level = 0
    @Volatile private var tempOn = true
    @Volatile private var startHigh = true
    @Volatile private var vadOn = true
    @Volatile private var shareOn = false
    private val streamer = ScreenStreamer { jpeg ->
        if (ready && !stopped) ws?.send(GeminiLiveProtocol.buildVideoFrame(jpeg))
    }
    private var attempts = 0
    @Volatile private var route: AudioRouter.Applied? = null
    @Volatile private var routeChecked = false
    private lateinit var config: VoiceConfig
    private lateinit var cb: VoiceCallbacks

    override fun start(config: VoiceConfig, callbacks: VoiceCallbacks) {
        this.config = config
        this.cb = callbacks
        stopped = false
        attempts = 0
        resumeHandle = null
        micGain = AudioLogic.clampMicGain(config.micGain)
        toolsOn = config.memoryTool || (config.control && config.screenShare)
        shareOn = config.screenShare
        emotionOn = config.emotion
        emotionAlpha = false
        emotionStage = 0
        level = 0
        tempOn = true
        startHigh = true
        vadOn = true
        everReady = false
        player.gain = config.gain
        applyRoute()
        router.listen(handler) { scheduleRerouteCheck() }
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

    override fun setGain(gain: Float) {
        player.gain = gain
    }

    override fun setScreenShare(on: Boolean) {
        shareOn = on
        if (on && ready && !stopped) streamer.start() else if (!on) streamer.stop()
    }

    override fun setMicGain(gain: Float) {
        micGain = AudioLogic.clampMicGain(gain)
    }

    override fun setRoutePref(pref: RoutePref) {
        if (!this::config.isInitialized) return
        config = config.copy(routePref = pref)
        if (!stopped) handler.post { if (!stopped) applyRoute() }
    }

    /** Applies the chosen output/mic and restarts playback (and mic) on it. Session stays connected. */
    private fun applyRoute() {
        val r = router.apply(config.routePref)
        route = r
        routeChecked = false
        player.stop()
        player.start(r.usage, r.output)
        if (mic.isRunning) {
            mic.stop()
            startMicIfNeeded()
        }
        cb.onRoute(r.label)
    }

    private val rerouteTask = Runnable {
        if (stopped) return@Runnable
        val p = router.plan(config.routePref)
        val cur = route
        if (cur == null || p.mode != cur.mode || p.output?.id != cur.output?.id) applyRoute()
    }

    private fun scheduleRerouteCheck() {
        if (stopped) return
        handler.removeCallbacks(rerouteTask)
        handler.postDelayed(rerouteTask, 500)
    }

    private fun connect() {
        ready = false
        cb.onState(if (attempts == 0) SessionState.CONNECTING else SessionState.RECONNECTING)
        val req = Request.Builder().url(GeminiLiveProtocol.endpoint(config.apiKey, emotionOn && emotionAlpha)).build()
        ws = client.newWebSocket(req, listener)
    }

    private fun cleanup() {
        handler.removeCallbacksAndMessages(null)
        ready = false
        speaking = false
        streamer.stop()
        try { ws?.close(1000, "bye") } catch (_: Exception) {}
        ws = null
        mic.stop()
        player.stop()
        router.unlisten()
        router.restore()
        route = null
    }

    /**
     * The server refused the very first setup. Drop optional parts one step at a time and reconnect:
     * 1) memory/control tools + emotion, 2) temperature + high start sensitivity, 3) the VAD block.
     * Level 3 is the plain v0.1-style setup. Returns false when nothing is left to drop.
     */
    private fun degrade(): Boolean {
        if (stopped || everReady || level >= 3) return false
        level++
        when (level) {
            1 -> { toolsOn = false; emotionOn = false }
            2 -> { tempOn = false; startHigh = false }
            else -> vadOn = false
        }
        cb.onAction("Server ne setup mana kiya, simple setup try kar rahi hoon (step $level/3).")
        val old = ws
        ws = null // so the old socket's close callback is ignored
        try { old?.close(1000, "retry") } catch (_: Exception) {}
        handler.post { if (!stopped) connect() }
        return true
    }

    /**
     * Emotion mode failed before the session ever started (server refusal OR a dropped handshake, which looks like
     * "no internet"). Try the other API version once, then fall back to plain voice instead of showing an error.
     */
    private fun emotionFallback(): Boolean {
        if (stopped || everReady || !emotionOn) return false
        emotionStage++
        if (emotionStage == 1) {
            emotionAlpha = !emotionAlpha
            cb.onAction("Emotion mode ke liye doosra API version try kar rahi hoon.")
        } else {
            emotionOn = false
            emotionAlpha = false
            cb.onAction("Emotion mode is model pe nahi chala, bina emotion ke voice chalu kar rahi hoon.")
        }
        val old = ws
        ws = null
        try { old?.close(1000, "retry") } catch (_: Exception) {}
        handler.post { if (!stopped) connect() }
        return true
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
        if (emotionFallback()) return
        val fatal = (httpCode != null && httpCode in 400..499) ||
            closeCode == 1007 || closeCode == 1008 || closeCode == 1003
        if (fatal && degrade()) return
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
                        val data = if (micGain > 1.01f) AudioLogic.applyGain(buf.copyOf(len), micGain) else buf
                        ws?.send(GeminiLiveProtocol.buildAudioChunk(data, len))
                    }
                },
                onLevel = { lvl ->
                    if (!speaking) cb.onLevel(if (muted.get()) 0f else lvl)
                },
                source = route?.micSource ?: android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                preferred = route?.micDevice
            )
        } catch (e: Exception) {
            fail(Errors.mic())
        }
    }

    private fun handle(text: String) {
        for (e in GeminiLiveProtocol.parse(text)) {
            when (e) {
                is ServerEvent.SetupComplete -> {
                    ready = true
                    everReady = true
                    if (shareOn) streamer.start()
                    attempts = 0
                    cb.onState(SessionState.LISTENING)
                    startMicIfNeeded()
                }
                is ServerEvent.Audio -> {
                    speaking = true
                    if (!routeChecked) {
                        routeChecked = true
                        player.routedDevice()?.let { cb.onRoute("Awaaz: " + AudioRouter.describe(it)) }
                    }
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
                is ServerEvent.ToolCall -> handleTool(e)
                is ServerEvent.GoAway -> { /* server will close soon; reconnect happens on drop */ }
                is ServerEvent.ResumptionHandle -> resumeHandle = e.handle
                is ServerEvent.Error -> if (!emotionFallback() && !degrade()) fail(Errors.closed(0, e.message))
            }
        }
    }

    private fun handleTool(e: ServerEvent.ToolCall) {
        fun reply(ok: Boolean, detail: String?) {
            ws?.send(GeminiLiveProtocol.buildToolResponse(e.id, e.name, ok, detail))
        }
        when {
            e.name == GeminiLiveProtocol.TOOL_SAVE_MEMORY ->
                reply(cb.onMemory(e.args.optString("fact")), null)
            e.name in GeminiLiveProtocol.SCREEN_TOOLS -> {
                if (!(config.control && config.screenShare && toolsOn)) {
                    reply(false, "Control band hai. User ne allow nahi kiya.")
                } else {
                    cb.onAction(ScreenControl.describe(e.name, e.args))
                    ScreenControl.run(e.name, e.args) { ok, detail -> reply(ok, detail) }
                }
            }
            else -> reply(false, "Unknown function.")
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val handle = resumeHandle
            val prompt = if (handle == null && attempts > 0) config.promptForNewSession() else config.systemPrompt
            webSocket.send(
                GeminiLiveProtocol.buildSetup(
                    config.model, config.voiceName, prompt, handle, if (vadOn) config.vad else null,
                    memoryTool = toolsOn && config.memoryTool, temperature = if (tempOn) 1.0f else null,
                    controlTools = toolsOn && config.control && config.screenShare,
                    emotion = emotionOn,
                    startHigh = startHigh
                )
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
            handleDrop(Errors.closed(code, reason), null, code)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== ws) return
            handleDrop(if (response != null) Errors.http(response.code) else Errors.network(), response?.code, null)
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
    }
}
