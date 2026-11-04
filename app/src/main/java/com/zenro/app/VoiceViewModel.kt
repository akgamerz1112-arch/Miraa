package com.zenro.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.RoutePref
import com.zenro.app.chat.ChatResult
import com.zenro.app.control.ZenroAccessibilityService
import com.zenro.app.chat.TextChatClient
import com.zenro.app.data.KeyTester
import com.zenro.app.data.SecureStore
import com.zenro.app.memory.MemoryItem
import com.zenro.app.memory.MemoryLogic
import com.zenro.app.service.NotifActions
import com.zenro.app.service.VoiceForegroundService
import com.zenro.app.voice.GeminiLiveProvider
import com.zenro.app.voice.Gender
import com.zenro.app.voice.MemoryMode
import com.zenro.app.voice.Persona
import com.zenro.app.voice.Role
import com.zenro.app.voice.SessionState
import com.zenro.app.voice.Style
import com.zenro.app.voice.UserGender
import com.zenro.app.voice.VadPreset
import com.zenro.app.voice.VoiceCallbacks
import com.zenro.app.voice.VoiceCatalog
import com.zenro.app.voice.VoiceConfig
import com.zenro.app.voice.VoiceOption
import com.zenro.app.voice.VoiceProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import kotlin.random.Random

data class ChatLine(val role: Role, val text: String)

class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private var provider: VoiceProvider? = null
    private var openRole: Role? = null

    private val _state = MutableStateFlow(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _voice = MutableStateFlow(VoiceCatalog.byName(store.voice))
    val voice: StateFlow<VoiceOption> = _voice.asStateFlow()
    private val _hasKey = MutableStateFlow(store.apiKey.isNotBlank())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()
    private val _userName = MutableStateFlow(store.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()
    private val _model = MutableStateFlow(store.model)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _routePref = MutableStateFlow(enumOr(store.routePref, RoutePref.AUTO))
    val routePref: StateFlow<RoutePref> = _routePref.asStateFlow()
    private val _gain = MutableStateFlow(AudioLogic.clampGain(store.gain))
    val gain: StateFlow<Float> = _gain.asStateFlow()
    private val _vad = MutableStateFlow(enumOr(store.vad, VadPreset.NORMAL))
    val vad: StateFlow<VadPreset> = _vad.asStateFlow()
    private val _style = MutableStateFlow(enumOr(store.style, Style.DOST))
    val style: StateFlow<Style> = _style.asStateFlow()
    private val _userGender = MutableStateFlow(store.userGender.takeIf { it.isNotEmpty() }?.let { enumOr(it, UserGender.MALE) })
    val userGender: StateFlow<UserGender?> = _userGender.asStateFlow()
    private val _route = MutableStateFlow("")
    val route: StateFlow<String> = _route.asStateFlow()
    private val _keyTest = MutableStateFlow("")
    val keyTest: StateFlow<String> = _keyTest.asStateFlow()

    private val _micGain = MutableStateFlow(AudioLogic.clampMicGain(store.micGain))
    val micGain: StateFlow<Float> = _micGain.asStateFlow()
    private val _memoryOn = MutableStateFlow(store.memoryOn)
    val memoryOn: StateFlow<Boolean> = _memoryOn.asStateFlow()
    private val _memories = MutableStateFlow(MemoryLogic.fromJson(store.memoryJson))
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()
    private val _chatModel = MutableStateFlow(store.chatModel)
    val chatModel: StateFlow<String> = _chatModel.asStateFlow()

    // Typed Chat tab (separate from the voice transcript).
    private val chatClient = TextChatClient()
    private val chatSeed = Random.nextInt().let { if (it == 0) 1 else it }
    private val _chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val chat: StateFlow<List<ChatLine>> = _chat.asStateFlow()
    private val _chatBusy = MutableStateFlow(false)
    val chatBusy: StateFlow<Boolean> = _chatBusy.asStateFlow()
    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    private val _screenShare = MutableStateFlow(store.screenShare)
    val screenShare: StateFlow<Boolean> = _screenShare.asStateFlow()
    private val _control = MutableStateFlow(store.screenControl)
    val control: StateFlow<Boolean> = _control.asStateFlow()
    private val _emotion = MutableStateFlow(store.emotionMode)
    val emotion: StateFlow<Boolean> = _emotion.asStateFlow()
    private val _a11y = MutableStateFlow(ZenroAccessibilityService.instance != null)
    val a11y: StateFlow<Boolean> = _a11y.asStateFlow()
    private val _action = MutableStateFlow("")
    val action: StateFlow<String> = _action.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    init {
        // Buttons in the notification (mic on/off, stop) come back here.
        NotifActions.handler = { action ->
            main.post {
                when (action) {
                    NotifActions.MUTE -> toggleMute()
                    NotifActions.STOP -> stopSession()
                    NotifActions.SCREEN_OFF -> screenOff()
                }
            }
        }
    }

    private val callbacks = object : VoiceCallbacks {
        override fun onMemory(fact: String): Boolean = saveFact(fact)
        override fun onAction(text: String) { _action.value = text }
        override fun onRoute(label: String) { _route.value = label }
        override fun onState(state: SessionState) {
            if (provider != null) _state.value = state
        }
        override fun onTranscript(role: Role, text: String) = appendTranscript(role, text)
        override fun onTurnComplete() { openRole = null }
        override fun onLevel(level: Float) { _level.value = level }
        override fun onError(message: String) {
            provider = null
            VoiceForegroundService.stop(getApplication())
            _level.value = 0f
            _error.value = message
            _state.value = SessionState.ERROR
        }
    }

    fun startSession() {
        if (_state.value != SessionState.IDLE && _state.value != SessionState.ERROR) return
        val key = store.apiKey
        if (key.isBlank()) {
            _error.value = "Pehle Settings mein Gemini API key daalo."
            return
        }
        _error.value = null
        VoiceForegroundService.start(getApplication())
        VoiceForegroundService.setMuted(getApplication(), _muted.value)
        val gender = _voice.value.gender
        val name = store.userName
        val style = _style.value
        val ug = _userGender.value
        val memOn = store.memoryOn
        val mode = if (memOn) MemoryMode.TOOL else MemoryMode.NONE
        val seed = Random.nextInt().let { if (it == 0) 1 else it }
        val part = dayPart()
        val share = _screenShare.value && ZenroAccessibilityService.instance != null
        val ctrl = share && _control.value
        VoiceForegroundService.setSharing(getApplication(), share)
        _action.value = ""
        fun prompt(recap: String) = Persona.systemPrompt(
            gender, name, recap, style, ug,
            MemoryLogic.promptBlock(_memories.value), mode, seed, part, share, ctrl
        )
        val p = GeminiLiveProvider(getApplication())
        provider = p
        _state.value = SessionState.CONNECTING
        p.setMuted(_muted.value)
        p.start(
            VoiceConfig(
                apiKey = key,
                model = store.model,
                voiceName = _voice.value.apiName,
                systemPrompt = prompt(recap()),
                promptForNewSession = { prompt(recap()) },
                routePref = _routePref.value,
                gain = _gain.value,
                vad = _vad.value,
                micGain = _micGain.value,
                memoryTool = memOn,
                screenShare = share,
                control = ctrl,
                emotion = store.emotionMode
            ),
            callbacks
        )
    }

    fun stopSession() {
        provider?.stop()
        provider = null
        VoiceForegroundService.stop(getApplication())
        _level.value = 0f
        _action.value = ""
        _route.value = ""
        _state.value = SessionState.IDLE
        openRole = null
    }

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        provider?.setMuted(m)
        VoiceForegroundService.setMuted(getApplication(), m)
    }

    fun setVoice(v: VoiceOption) {
        store.voice = v.apiName
        _voice.value = v
        restartIfActive()
    }

    fun setGender(g: Gender) {
        if (_voice.value.gender == g) return
        setVoice(VoiceCatalog.forGender(g).first())
    }

    fun setRoutePref(p: RoutePref) {
        store.routePref = p.name
        _routePref.value = p
        provider?.setRoutePref(p)
    }

    /** Live volume boost while the slider moves; saved when the finger lifts. */
    fun setGainLive(g: Float) {
        val v = AudioLogic.clampGain(g)
        _gain.value = v
        provider?.setGain(v)
    }

    fun saveGain() { store.gain = _gain.value }

    fun setVad(v: VadPreset) {
        if (_vad.value == v) return
        store.vad = v.name
        _vad.value = v
        restartIfActive()
    }

    fun setStyle(s: Style) {
        if (_style.value == s) return
        store.style = s.name
        _style.value = s
        restartIfActive()
    }

    fun setUserGender(g: UserGender) {
        if (_userGender.value == g) return
        store.userGender = g.name
        _userGender.value = g
        restartIfActive()
    }

    /** Call when the screen comes back to the front: has the user switched the accessibility service on? */
    fun refreshAccessibility() {
        _a11y.value = ZenroAccessibilityService.instance != null
    }

    fun setScreenShare(on: Boolean) {
        if (on && ZenroAccessibilityService.instance == null) {
            _error.value = "Pehle Accessibility mein ZENRO ko ON karo (Voice tab ka 'Accessibility ON karo' button)."
            return
        }
        store.screenShare = on
        _screenShare.value = on
        if (!on && _control.value) {
            store.screenControl = false
            _control.value = false
        }
        // Tools are fixed when a session is created, so a change restarts the session.
        restartIfActive()
    }

    fun setEmotionMode(on: Boolean) {
        store.emotionMode = on
        _emotion.value = on
        restartIfActive()
    }

    fun setControl(on: Boolean) {
        if (on && !_screenShare.value) {
            _error.value = "Control ke liye pehle 'Screen dikhao' ON karo."
            return
        }
        store.screenControl = on
        _control.value = on
        restartIfActive()
    }

    /** Notification button: stop looking at and controlling the phone right away. */
    private fun screenOff() {
        store.screenShare = false
        store.screenControl = false
        _screenShare.value = false
        _control.value = false
        VoiceForegroundService.setSharing(getApplication(), false)
        _action.value = ""
        restartIfActive()
    }

    /** Mic boost for quiet / soft speech; applies live, saved when the finger lifts. */
    fun setMicGainLive(g: Float) {
        val v = AudioLogic.clampMicGain(g)
        _micGain.value = v
        provider?.setMicGain(v)
    }

    fun saveMicGain() { store.micGain = _micGain.value }

    fun setMemoryOn(on: Boolean) {
        if (_memoryOn.value == on) return
        store.memoryOn = on
        _memoryOn.value = on
        restartIfActive()
    }

    private fun persistMemories(list: List<MemoryItem>) {
        store.memoryJson = MemoryLogic.toJson(list)
        _memories.value = list
    }

    /** Called by the model (voice tool or chat tag). True only if the fact is acceptable and kept. */
    @Synchronized
    private fun saveFact(raw: String): Boolean {
        if (!store.memoryOn) return false
        return addMemoryInternal(raw)
    }

    @Synchronized
    private fun addMemoryInternal(raw: String): Boolean {
        if (MemoryLogic.clean(raw) == null) return false
        val cur = _memories.value
        val next = MemoryLogic.add(cur, raw, System.currentTimeMillis())
        if (next != cur) persistMemories(next)
        return true
    }

    /** Typed in by the user on the Yaadein screen. False if too short or it looks like a password/number. */
    fun addMemoryManual(raw: String): Boolean = addMemoryInternal(raw)

    @Synchronized
    fun deleteMemory(index: Int) = persistMemories(MemoryLogic.remove(_memories.value, index))

    @Synchronized
    fun clearMemories() = persistMemories(emptyList())

    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _chatBusy.value) return
        val key = store.apiKey
        if (key.isBlank()) {
            _chatError.value = "Pehle Settings mein Gemini API key daalo."
            return
        }
        _chatError.value = null
        _chat.update { (it + ChatLine(Role.USER, t)).takeLast(200) }
        _chatBusy.value = true
        val system = Persona.chatPrompt(
            _voice.value.gender, store.userName, _style.value, _userGender.value,
            MemoryLogic.promptBlock(_memories.value),
            if (store.memoryOn) MemoryMode.TAG else MemoryMode.NONE,
            chatSeed, dayPart()
        )
        val history = _chat.value.map { it.role to it.text }
        chatClient.send(key, store.chatModel, system, history) { res ->
            when (res) {
                is ChatResult.Ok -> {
                    val (clean, facts) = MemoryLogic.extractTags(res.text)
                    if (store.memoryOn) facts.forEach { saveFact(it) }
                    _chat.update { (it + ChatLine(Role.ASSISTANT, clean.ifEmpty { "Ok" })).takeLast(200) }
                }
                is ChatResult.Fail -> _chatError.value = res.message
            }
            _chatBusy.value = false
        }
    }

    fun clearChat() {
        _chat.value = emptyList()
        _chatError.value = null
    }

    private fun dayPart(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..20 -> "evening"
        else -> "night"
    }

    fun testKey(typed: String) {
        val key = typed.ifBlank { store.apiKey }
        if (key.isBlank()) {
            _keyTest.value = "Pehle key daalo."
            return
        }
        _keyTest.value = "Key test ho raha hai..."
        Thread { _keyTest.value = KeyTester.run(key) }.start()
    }

    private fun recap(): String = Persona.recap(_lines.value.map { it.role to it.text })

    fun saveSettings(newKey: String, newModel: String, newName: String, newChatModel: String) {
        if (newChatModel.isNotBlank()) {
            store.chatModel = newChatModel
            _chatModel.value = newChatModel
        }
        store.userName = newName
        _userName.value = newName
        if (newKey.isNotBlank()) {
            store.apiKey = newKey
            _hasKey.value = true
        }
        if (newModel.isNotBlank()) {
            store.model = newModel
            _model.value = newModel
        }
        _error.value = null
    }

    fun reportError(message: String) { _error.value = message }

    // Voice is fixed when a Gemini session is created, so a change needs a fresh session.
    private fun restartIfActive() {
        val s = _state.value
        if (s != SessionState.IDLE && s != SessionState.ERROR) {
            stopSession()
            startSession()
        }
    }

    @Synchronized
    private fun appendTranscript(role: Role, text: String) {
        _lines.update { cur ->
            val next = if (openRole == role && cur.isNotEmpty() && cur.last().role == role) {
                cur.dropLast(1) + ChatLine(role, cur.last().text + text)
            } else {
                cur + ChatLine(role, text.trimStart())
            }
            if (next.size > 200) next.takeLast(200) else next
        }
        openRole = role
    }

    override fun onCleared() {
        NotifActions.handler = null
        provider?.stop()
        provider = null
        VoiceForegroundService.stop(getApplication())
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
