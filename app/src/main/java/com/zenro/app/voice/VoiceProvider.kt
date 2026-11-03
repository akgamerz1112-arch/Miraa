package com.zenro.app.voice

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
)

interface VoiceCallbacks {
    fun onState(state: SessionState)
    fun onTranscript(role: Role, text: String)
    fun onTurnComplete()
    fun onLevel(level: Float)
    fun onError(message: String)
}

/** Common interface so other real-time voice providers can be added later. */
interface VoiceProvider {
    fun start(config: VoiceConfig, callbacks: VoiceCallbacks)
    fun stop()
    fun setMuted(muted: Boolean)
}
