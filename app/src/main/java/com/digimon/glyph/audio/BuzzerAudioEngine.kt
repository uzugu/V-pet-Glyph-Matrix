package com.digimon.glyph.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.roundToInt

/**
 * Lightweight square-wave buzzer output driven by E0C6200 buzzer callbacks.
 */
class BuzzerAudioEngine {

    companion object {
        // 24kHz is enough for the Digimon buzzer range and lowers CPU/wakeup cost.
        private const val SAMPLE_RATE = 24_000
        private const val CHUNK_MS = 8
        private const val AMPLITUDE = 2400
        private const val AUDIO_TRACK_RELEASE_IDLE_NS = 2_500_000_000L
    }

    @Volatile private var running = false
    @Volatile private var enabled = false
    @Volatile private var toneOn = false
    @Volatile private var freqHz = 1024.0
    @Volatile private var gain = 1.0
    @Volatile private var holdToneUntilNs = 0L

    private var thread: Thread? = null
    private val stateLock = Object()

    fun start() {
        if (running) return
        running = true
        thread = Thread(::audioLoop, "DigimonBuzzerAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        synchronized(stateLock) {
            stateLock.notifyAll()
        }
        thread?.join(300)
        thread = null
        toneOn = false
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        synchronized(stateLock) {
            stateLock.notifyAll()
        }
    }

    fun onBuzzerChange(on: Boolean, frequencyHz: Int, level: Float) {
        val now = System.nanoTime()
        toneOn = on
        if (on) {
            // Preserve very short buzzer pulses that could otherwise be missed
            // between audio write chunks.
            holdToneUntilNs = now + 20_000_000L // 20ms
        }
        if (frequencyHz > 0) {
            freqHz = frequencyHz.toDouble().coerceIn(80.0, 10_000.0)
        }
        gain = level.coerceIn(0f, 1f).toDouble()
        synchronized(stateLock) {
            stateLock.notifyAll()
        }
    }

    private fun audioLoop() {
        val samplesPerChunk = ((SAMPLE_RATE * CHUNK_MS) / 1000.0).roundToInt().coerceAtLeast(64)
        val buffer = ShortArray(samplesPerChunk)
        var phase = 0.0
        var track: AudioTrack? = null
        var trackPlaying = false
        var lastActiveNs = 0L

        while (running) {
            val now = System.nanoTime()
            val active = enabled && (toneOn || now < holdToneUntilNs)
            if (!active) {
                if (trackPlaying) {
                    track?.pause()
                    track?.flush()
                    trackPlaying = false
                }
                if (track != null && now - lastActiveNs >= AUDIO_TRACK_RELEASE_IDLE_NS) {
                    track.release()
                    track = null
                }
                val waitMs = ((holdToneUntilNs - now) / 1_000_000L).coerceIn(5L, 50L)
                try {
                    synchronized(stateLock) {
                        if (running) {
                            stateLock.wait(waitMs)
                        }
                    }
                } catch (_: InterruptedException) {
                    break
                }
                continue
            }

            if (track == null) {
                track = createTrack()
                if (track == null) {
                    try {
                        synchronized(stateLock) {
                            if (running) stateLock.wait(40L)
                        }
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
            }

            if (!trackPlaying) {
                track.play()
                trackPlaying = true
            }
            lastActiveNs = now

            val step = (freqHz / SAMPLE_RATE).coerceIn(0.0001, 0.49)
            val amp = (AMPLITUDE * gain).roundToInt().coerceIn(0, AMPLITUDE).toShort()
            for (i in buffer.indices) {
                phase += step
                if (phase >= 1.0) phase -= 1.0
                buffer[i] = if (phase < 0.5) amp else (-amp).toShort()
            }
            val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                track.pause()
                track.flush()
                track.release()
                track = null
                trackPlaying = false
            }
        }

        track?.pause()
        track?.flush()
        track?.release()
    }

    private fun createTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuffer)
            .build()
    }
}
