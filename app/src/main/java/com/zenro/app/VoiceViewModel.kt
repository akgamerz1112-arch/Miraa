package com.zenro.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.RoutePref
import com.zenro.app.data.KeyTester
import com.zenro.app.data.SecureStore
import com.zenro.app.service.VoiceForegroundService
import com.zenro.app.voice.GeminiLiveProvider
import com.zenro.app.voice.Gender
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

    private val callbacks = object : VoiceCallbacks {
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
        val gender = _voice.value.gender
        val name = store.userName
        val style = _style.value
        val ug = _userGender.value
        val p = GeminiLiveProvider(getApplication())
        provider = p
        _state.value = SessionState.CONNECTING
        p.setMuted(_muted.value)
        p.start(
            VoiceConfig(
                apiKey = key,
                model = store.model,
                voiceName = _voice.value.apiName,
                systemPrompt = Persona.systemPrompt(gender, name, recap(), style, ug),
                promptForNewSession = { Persona.systemPrompt(gender, name, recap(), style, ug) },
                routePref = _routePref.value,
                gain = _gain.value,
                vad = _vad.value
            ),
            callbacks
        )
    }

    fun stopSession() {
        provider?.stop()
        provider = null
        VoiceForegroundService.stop(getApplication())
        _level.value = 0f
        _route.value = ""
        _state.value = SessionState.IDLE
        openRole = null
    }

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        provider?.setMuted(m)
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

    fun saveSettings(newKey: String, newModel: String, newName: String) {
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
        provider?.stop()
        provider = null
        VoiceForegroundService.stop(getApplication())
    }
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback
