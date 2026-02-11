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
        private const val SAMPLE_RATE = 48_000
        private const val CHUNK_MS = 4
        private const val AMPLITUDE = 2400
    }

    @Volatile private var running = false
    @Volatile private var enabled = false
    @Volatile private var toneOn = false
    @Volatile private var freqHz = 1024.0
    @Volatile private var gain = 1.0
    @Volatile private var holdToneUntilNs = 0L

    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        audioTrack = AudioTrack.Builder()
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

        audioTrack?.play()
        running = true
        thread = Thread(::audioLoop, "DigimonBuzzerAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread?.join(300)
        thread = null
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.release()
        audioTrack = null
        toneOn = false
    }

    fun setEnabled(value: Boolean) {
        enabled = value
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
    }

    private fun audioLoop() {
        val samplesPerChunk = ((SAMPLE_RATE * CHUNK_MS) / 1000.0).roundToInt().coerceAtLeast(64)
        val buffer = ShortArray(samplesPerChunk)
        var phase = 0.0

        while (running) {
            val track = audioTrack ?: break
            val now = System.nanoTime()
            val active = enabled && (toneOn || now < holdToneUntilNs)
            if (!active) {
                buffer.fill(0)
            } else {
                val step = (freqHz / SAMPLE_RATE).coerceIn(0.0001, 0.49)
                val amp = (AMPLITUDE * gain).roundToInt().coerceIn(0, AMPLITUDE).toShort()
                for (i in buffer.indices) {
                    phase += step
                    if (phase >= 1.0) phase -= 1.0
                    buffer[i] = if (phase < 0.5) amp else (-amp).toShort()
                }
            }
            val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                break
            }
        }
    }
}
