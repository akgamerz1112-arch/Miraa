package com.zenro.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.math.max

/** Streams 16 kHz mono PCM16 from the microphone in ~100 ms chunks. */
class MicStreamer {
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by the UI before a session starts.
    fun start(onChunk: (ByteArray, Int) -> Unit, onLevel: (Float) -> Unit) {
        if (running) return
        val rate = 16000
        val min = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(min, 3200) * 2
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            throw IllegalStateException("Microphone could not be opened")
        }
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also { it.enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            ns = NoiseSuppressor.create(rec.audioSessionId)?.also { it.enabled = true }
        }
        record = rec
        running = true
        rec.startRecording()
        thread = Thread({
            val buf = ByteArray(3200)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    onLevel(Pcm.level(buf, n))
                    onChunk(buf, n)
                } else if (n < 0) {
                    break
                }
            }
        }, "zenro-mic").also { it.start() }
    }

    fun stop() {
        if (!running && record == null) return
        running = false
        try { record?.stop() } catch (_: Exception) {}
        try { thread?.join(500) } catch (_: Exception) {}
        try { aec?.release() } catch (_: Exception) {}
        try { ns?.release() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        aec = null; ns = null; record = null; thread = null
    }
}
