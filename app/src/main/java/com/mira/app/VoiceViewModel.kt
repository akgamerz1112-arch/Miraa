package com.mira.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mira.app.control.MiraAccessibilityService
import com.mira.app.data.SecureStore
import com.mira.app.memory.MemoryItem
import com.mira.app.memory.MemoryLogic
import com.mira.app.net.ApiLogic
import com.mira.app.net.ApiResult
import com.mira.app.net.MiraApi
import com.mira.app.rtc.ConnLogic
import com.mira.app.rtc.ConnState
import com.mira.app.rtc.LiveKitSession
import com.mira.app.rtc.PhoneRpc
import com.mira.app.service.NotifActions
import com.mira.app.service.VoiceForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the LiveKit session, the connection settings and the Yaadein list. */
class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app)
    private val api = MiraApi()
    private var session: LiveKitSession? = null
    private var startJob: Job? = null

    private val _state = MutableStateFlow(ConnState.IDLE)
    val state: StateFlow<ConnState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private val _action = MutableStateFlow("")
    val action: StateFlow<String> = _action.asStateFlow()
    private val _a11y = MutableStateFlow(MiraAccessibilityService.instance != null)
    val a11y: StateFlow<Boolean> = _a11y.asStateFlow()
    private val _control = MutableStateFlow(store.screenControl)
    val control: StateFlow<Boolean> = _control.asStateFlow()

    private val _serverUrl = MutableStateFlow(store.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    private val _deviceId = MutableStateFlow(store.deviceId)
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()
    private val _hasDeviceKey = MutableStateFlow(store.deviceKey.isNotBlank())
    val hasDeviceKey: StateFlow<Boolean> = _hasDeviceKey.asStateFlow()
    private val _configInfo = MutableStateFlow(configSummary(store.configCache))
    val configInfo: StateFlow<String> = _configInfo.asStateFlow()

    private val _memoryOn = MutableStateFlow(store.memoryOn)
    val memoryOn: StateFlow<Boolean> = _memoryOn.asStateFlow()
    private val _memories = MutableStateFlow(MemoryLogic.fromJson(store.memoryJson))
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    init {
        // Buttons in the notification (mic on/off, stop) come back here.
        NotifActions.handler = { action ->
            main.post {
                when (action) {
                    NotifActions.MUTE -> toggleMute()
                    NotifActions.STOP -> stopSession()
                    NotifActions.SCREEN_OFF -> setControl(false)
                }
            }
        }
    }

    private fun appVersionCode(): Int = try {
        val pm = getApplication<Application>().packageManager
        PackageInfoCompat.getLongVersionCode(pm.getPackageInfo(getApplication<Application>().packageName, 0)).toInt()
    } catch (e: Exception) {
        0
    }

    fun startSession() {
        if (ConnLogic.active(_state.value)) return
        val url = store.serverUrl
        val id = store.deviceId
        val key = store.deviceKey
        val problem = when {
            ApiLogic.urlProblem(url) != null -> ApiLogic.urlProblem(url)
            !ApiLogic.isValidDeviceId(id) -> "Device id daalo (chhote letters/numbers/-, jaise my-phone)."
            key.isBlank() -> "Device key daalo (device_auth.py add se mili thi)."
            else -> null
        }
        if (problem != null) {
            _error.value = "$problem Settings mein jaake bharo."
            _state.value = ConnState.ERROR
            return
        }
        _error.value = null
        _action.value = ""
        _state.value = ConnState.CHECKING
        val version = appVersionCode()
        startJob = viewModelScope.launch {
            val cfg = withContext(Dispatchers.IO) { api.fetchConfig(url) }
            when (cfg) {
                is ApiResult.Fail -> return@launch failStart(cfg.message, cfg.updateNeeded)
                is ApiResult.Ok -> {
                    store.configCache = cfg.raw
                    _configInfo.value = configSummary(cfg.raw)
                    if (ApiLogic.needsUpdate(cfg.value, version)) return@launch failStart(ApiLogic.UPDATE_MESSAGE, true)
                }
            }
            val tok = withContext(Dispatchers.IO) { api.fetchToken(url, key, id, version) }
            when (tok) {
                is ApiResult.Fail -> failStart(tok.message, tok.updateNeeded)
                is ApiResult.Ok -> join(tok.value)
            }
        }
    }

    private fun failStart(message: String, updateNeeded: Boolean) {
        _error.value = message
        _state.value = if (updateNeeded) ConnState.UPDATE_NEEDED else ConnState.ERROR
    }

    private fun join(t: ApiLogic.TokenInfo) {
        val ctx = getApplication<Application>()
        VoiceForegroundService.start(ctx)
        VoiceForegroundService.setMuted(ctx, _muted.value)
        VoiceForegroundService.setSharing(ctx, _control.value)
        val rpc = PhoneRpc(allowed = { store.screenControl }, onAction = { text -> main.post { _action.value = text } })
        val s = LiveKitSession(
            ctx, rpc,
            onState = { st -> _state.value = st },
            onError = { msg ->
                session = null
                VoiceForegroundService.stop(ctx)
                _action.value = ""
                _error.value = msg
                _state.value = ConnState.ERROR
            }
        )
        session = s
        try {
            s.start(t.serverUrl, t.token, _muted.value)
        } catch (e: Exception) {
            s.stop()
            session = null
            VoiceForegroundService.stop(ctx)
            _error.value = "LiveKit shuru nahi hua: ${e.message?.take(120) ?: "unknown"}"
            _state.value = ConnState.ERROR
        }
    }

    fun stopSession() {
        startJob?.cancel()
        startJob = null
        session?.stop()
        session = null
        VoiceForegroundService.stop(getApplication())
        _action.value = ""
        _state.value = ConnState.IDLE
    }

    fun toggleMute() {
        val m = !_muted.value
        _muted.value = m
        session?.setMuted(m)
        VoiceForegroundService.setMuted(getApplication(), m)
    }

    /** Call when the screen comes back to the front: has the user switched the accessibility service on? */
    fun refreshAccessibility() {
        _a11y.value = MiraAccessibilityService.instance != null
    }

    /** Owner's switch for phone control by the agent (tap/type/open apps). */
    fun setControl(on: Boolean) {
        if (on && MiraAccessibilityService.instance == null) {
            _error.value = "Pehle Accessibility mein MIRA ko ON karo (Voice tab ka 'Accessibility ON karo' button)."
            return
        }
        store.screenControl = on
        _control.value = on
        VoiceForegroundService.setSharing(getApplication(), on)
        if (!on) _action.value = ""
    }

    /** Saves the connection settings. A blank [newKey] keeps the saved key. */
    fun saveConnection(url: String, id: String, newKey: String) {
        store.serverUrl = url.trim()
        store.deviceId = ApiLogic.normalizeDeviceId(id)
        _serverUrl.value = store.serverUrl
        _deviceId.value = store.deviceId
        if (newKey.isNotBlank()) {
            store.deviceKey = newKey.trim()
            _hasDeviceKey.value = true
        }
        if (_state.value == ConnState.ERROR || _state.value == ConnState.UPDATE_NEEDED) {
            _state.value = ConnState.IDLE
            _error.value = null
        }
    }

    fun setMemoryOn(on: Boolean) {
        if (_memoryOn.value == on) return
        store.memoryOn = on
        _memoryOn.value = on
    }

    private fun persistMemories(list: List<MemoryItem>) {
        store.memoryJson = MemoryLogic.toJson(list)
        _memories.value = list
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

    fun reportError(message: String) { _error.value = message }

    override fun onCleared() {
        NotifActions.handler = null
        startJob?.cancel()
        session?.stop()
        session = null
        VoiceForegroundService.stop(getApplication())
    }

    private fun configSummary(raw: String): String {
        val c = if (raw.isBlank()) null else ApiLogic.parseConfig(raw)
        return if (c == null) "Server config abhi fetch nahi hui."
        else "Server: api v${c.apiVersion}, config v${c.configVersion}, persona v${c.personaVersion}, min app ${c.minAppVersion}"
    }
}
