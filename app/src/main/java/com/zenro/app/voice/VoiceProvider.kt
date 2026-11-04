package com.zenro.app.voice

import com.zenro.app.audio.AudioLogic
import com.zenro.app.audio.RoutePref

enum class SessionState { IDLE, CONNECTING, LISTENING, SPEAKING, RECONNECTING, ERROR }

enum class Role { USER, ASSISTANT }

enum class Gender { MALE, FEMALE }

data class VoiceOption(val apiName: String, val gender: Gender)

object VoiceCatalog {
    val all = listOf(
        VoiceOption("Puck", Gender.MALE),
        VoiceOption("Charon", Gender.MALE),
        VoiceOption("Fenrir", Gender.MALE),
        VoiceOption("Orus", Gender.MALE),
        VoiceOption("Kore", Gender.FEMALE),
        VoiceOption("Aoede", Gender.FEMALE),
        VoiceOption("Leda", Gender.FEMALE),
        VoiceOption("Zephyr", Gender.FEMALE),
    )

    fun byName(name: String): VoiceOption = all.firstOrNull { it.apiName == name } ?: all.first()
    fun forGender(g: Gender): List<VoiceOption> = all.filter { it.gender == g }
}

data class VoiceConfig(
    val apiKey: String,
    val model: String,
    val voiceName: String,
    val systemPrompt: String,
    /** Called when a reconnect cannot resume the old session, to rebuild context from the transcript. */
    val promptForNewSession: () -> String = { systemPrompt },
    val routePref: RoutePref = RoutePref.AUTO,
    val gain: Float = AudioLogic.DEFAULT_GAIN,
    val vad: VadPreset = VadPreset.NORMAL,
    val micGain: Float = AudioLogic.MIC_DEFAULT_GAIN,
    /** Offer the model a `save_memory` function so it can remember things across days. */
    val memoryTool: Boolean = false,
    /** Send the phone screen to the model about once a second. */
    val screenShare: Boolean = false,
    /** Let the model tap/type/open apps (only meaningful together with [screenShare]). */
    val control: Boolean = false,
    /** Affective dialog + proactive audio (2.5 native-audio models, v1alpha). */
    val emotion: Boolean = false,
)

interface VoiceCallbacks {
    fun onState(state: SessionState)
    fun onTranscript(role: Role, text: String)
    fun onTurnComplete()
    fun onLevel(level: Float)
    fun onError(message: String)
    /** Human-readable active output, e.g. "Awaaz: Bluetooth Buds (HQ)". */
    fun onRoute(label: String) {}
    /** The model wants to remember [fact]. Return true only if it was really saved. */
    fun onMemory(fact: String): Boolean = false
    /** Short Hinglish line about what ZENRO is doing on the phone right now. */
    fun onAction(text: String) {}
}

/** Common interface so other real-time voice providers can be added later. */
interface VoiceProvider {
    fun start(config: VoiceConfig, callbacks: VoiceCallbacks)
    fun stop()
    fun setMuted(muted: Boolean)
    fun setGain(gain: Float) {}
    fun setMicGain(gain: Float) {}
    fun setScreenShare(on: Boolean) {}
    fun setRoutePref(pref: RoutePref) {}
}
