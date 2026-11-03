package com.zenro.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler

/**
 * Chooses where the voice plays and which mic is used.
 * Headphone HQ mode: normal media stream to the headphones + the phone's own mic,
 * so Bluetooth stays in high-quality mode and the media volume buttons work.
 */
class AudioRouter(context: Context) {
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    class Plan(val mode: RouteMode, val output: AudioDeviceInfo?)
    class Applied(
        val mode: RouteMode,
        val output: AudioDeviceInfo?,
        val usage: Int,
        val micSource: Int,
        val micDevice: AudioDeviceInfo?,
        val label: String,
    )

    private var saved = false
    private var prevMode = AudioManager.MODE_NORMAL
    private var prevSpeaker = false
    private var prevVoiceVol = -1
    private var devCb: AudioDeviceCallback? = null

    fun plan(pref: RoutePref): Plan {
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val mode = AudioLogic.chooseRoute(outs.map { kindOf(it) }, pref)
        return Plan(mode, if (mode == RouteMode.HEADPHONE_HQ) pickHeadphones(outs) else null)
    }

    @Suppress("DEPRECATION")
    fun apply(pref: RoutePref): Applied {
        if (!saved) {
            prevMode = am.mode
            prevSpeaker = am.isSpeakerphoneOn
            saved = true
        }
        val p = plan(pref)
        return when (p.mode) {
            RouteMode.HEADPHONE_HQ -> {
                clearComm()
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = false
                val name = p.output?.let { describe(it) } ?: "Headphones"
                Applied(
                    p.mode, p.output, AudioAttributes.USAGE_MEDIA,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, builtinMic(),
                    "Awaaz: $name (HQ)"
                )
            }
            RouteMode.SPEAKER -> {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (!setComm(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)) am.isSpeakerphoneOn = true
                raiseCallVolume()
                Applied(
                    p.mode, null, AudioAttributes.USAGE_VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, null,
                    "Awaaz: Phone speaker"
                )
            }
            RouteMode.EAR -> {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (!setComm(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)) am.isSpeakerphoneOn = false
                raiseCallVolume()
                Applied(
                    p.mode, null, AudioAttributes.USAGE_VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION, null,
                    "Awaaz: Phone ear"
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    fun restore() {
        if (!saved) return
        clearComm()
        am.isSpeakerphoneOn = prevSpeaker
        am.mode = prevMode
        if (prevVoiceVol >= 0) {
            try { am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, prevVoiceVol, 0) } catch (_: Exception) {}
        }
        prevVoiceVol = -1
        saved = false
    }

    fun listen(handler: Handler, onChange: () -> Unit) {
        unlisten()
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = onChange()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = onChange()
        }
        am.registerAudioDeviceCallback(cb, handler)
        devCb = cb
    }

    fun unlisten() {
        devCb?.let { try { am.unregisterAudioDeviceCallback(it) } catch (_: Exception) {} }
        devCb = null
    }

    private fun raiseCallVolume() {
        try {
            if (prevVoiceVol < 0) prevVoiceVol = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL), 0
            )
        } catch (_: Exception) {
            // Some phones refuse (e.g. Do Not Disturb). Software gain still applies.
        }
    }

    private fun setComm(type: Int): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val d = am.availableCommunicationDevices.firstOrNull { it.type == type } ?: return false
        return am.setCommunicationDevice(d)
    }

    private fun clearComm() {
        if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice()
    }

    private fun builtinMic(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }

    private fun pickHeadphones(outs: List<AudioDeviceInfo>): AudioDeviceInfo? =
        outs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: outs.firstOrNull { Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
            ?: outs.firstOrNull { kindOf(it) == OutKind.WIRED }
            ?: outs.firstOrNull { kindOf(it) == OutKind.BLUETOOTH }

    companion object {
        fun kindOf(d: AudioDeviceInfo): OutKind = when (d.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> OutKind.BLUETOOTH
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET -> OutKind.WIRED
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutKind.SPEAKER
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutKind.EARPIECE
            else -> if (Build.VERSION.SDK_INT >= 31 && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET) OutKind.BLUETOOTH
            else OutKind.OTHER
        }

        fun describe(d: AudioDeviceInfo): String {
            val name = d.productName?.toString().orEmpty()
            return when (kindOf(d)) {
                OutKind.BLUETOOTH -> "Bluetooth $name".trim()
                OutKind.WIRED -> "Wired headphones"
                OutKind.SPEAKER -> "Phone speaker"
                OutKind.EARPIECE -> "Phone ear"
                OutKind.OTHER -> name.ifBlank { "Unknown device" }
            }
        }
    }
}
