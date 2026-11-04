package com.zenro.app.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency streaming playback of 24 kHz mono PCM16.
 * flush() drops everything queued so barge-in stops speech immediately.
 * [gain] is a software volume boost (1.0 - 3.0) with a soft limiter.
 */
class PcmPlayer(private val sampleRate: Int = 24000) {
    private class Chunk(val gen: Int, val data: ByteArray)

    private val queue = LinkedBlockingQueue<Chunk>()
    private val generation = AtomicInteger(0)
    @Volatile private var running = false
    @Volatile var gain: Float = AudioLogic.DEFAULT_GAIN
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start(
        usage: Int = AudioAttributes.USAGE_VOICE_COMMUNICATION,
        preferred: AudioDeviceInfo? = null,
    ) {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minBuf, 4800) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (preferred != null) t.setPreferredDevice(preferred)
        t.play()
        track = t
        running = true
        thread = Thread({
            while (running) {
                val c = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                var off = 0
                while (off < c.data.size && running && c.gen == generation.get()) {
                    val len = min(4800, c.data.size - off)
                    val w = t.write(c.data, off, len)
                    if (w < 0) break
                    if (w == 0) Thread.sleep(5) else off += w
                }
            }
        }, "zenro-play").also { it.start() }
    }

    fun enqueue(pcm: ByteArray) {
        if (running) queue.offer(Chunk(generation.get(), AudioLogic.applyGain(pcm, gain)))
    }

    /** The device Android actually routes this track to (null until playing). */
    fun routedDevice(): AudioDeviceInfo? = try { track?.routedDevice } catch (_: Exception) { null }

    /** Barge-in: discard queued and in-flight audio right now. */
    fun flush() {
        generation.incrementAndGet()
        queue.clear()
        try {
            track?.pause()
            track?.flush()
            track?.play()
        } catch (_: Exception) {}
    }

    /** Let everything already queued finish playing, then release the speaker (used by spoken chat replies). */
    fun stopWhenDrained(maxWaitMs: Long = 60000) {
        Thread({
            val end = System.currentTimeMillis() + maxWaitMs
            while (queue.isNotEmpty() && running && System.currentTimeMillis() < end) {
                try { Thread.sleep(100) } catch (_: Exception) { break }
            }
            try { Thread.sleep(1200) } catch (_: Exception) {}
            stop()
        }, "zenro-play-drain").start()
    }

    fun stop() {
        if (!running && track == null) return
        running = false
        generation.incrementAndGet()
        queue.clear()
        try { thread?.join(500) } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null; thread = null
    }
}
