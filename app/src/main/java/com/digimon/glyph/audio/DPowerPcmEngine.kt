package com.digimon.glyph.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Playback only: firmware timing and mixing are already represented in PCM samples. */
class DPowerPcmEngine {
    private data class Packet(val epoch: Long, val data: ByteArray)
    private val queue = ArrayBlockingQueue<Packet>(8)
    @Volatile private var running = false
    @Volatile private var enabled = false
    @Volatile private var epoch = 0L
    private var worker: Thread? = null

    fun start() {
        if (running) return
        running = true
        worker = Thread(::playback, "DPowerPCM").apply { isDaemon = true; start() }
    }
    @Synchronized fun setEnabled(value: Boolean) {
        if (enabled != value) { enabled = value; flush() }
    }
    @Synchronized fun flush() { epoch++; queue.clear() }
    @Synchronized fun submit(data: ByteArray) {
        if (!running || !enabled || data.isEmpty()) return
        if (queue.remainingCapacity() == 0) flush() // Prefer current sound over old catch-up audio.
        queue.offer(Packet(epoch, data))
    }
    fun stop() {
        running = false
        flush()
        worker?.interrupt()
        try { worker?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        worker = null
    }
    private fun playback() {
        var track: AudioTrack? = null
        var trackEpoch = -1L
        try {
            while (running) {
                if (trackEpoch != epoch) {
                    track?.let { runCatching { it.pause(); it.flush() } }
                    trackEpoch = epoch
                }
                val packet = queue.poll(250, TimeUnit.MILLISECONDS)
                if (packet == null) {
                    // No audible data: release the device instead of leaving a
                    // playing AudioTrack (and its audio power path) open all night.
                    track?.let { runCatching { it.pause(); it.flush(); it.release() } }
                    track = null
                    continue
                }
                if (!enabled || packet.epoch != epoch) continue
                try {
                    if (track == null) {
                        val minimum = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        check(minimum > 0)
                        track = AudioTrack.Builder()
                            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                            .setAudioFormat(AudioFormat.Builder().setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, 1920)).build()
                        check(track.state == AudioTrack.STATE_INITIALIZED)
                    }
                    // A mute/reset can happen while waiting for the previous packet.
                    if (packet.epoch != trackEpoch) {
                        track.pause(); track.flush(); trackEpoch = packet.epoch
                    }
                    if (!enabled || packet.epoch != epoch) continue
                    track.play()
                    var offset = 0
                    while (running && enabled && packet.epoch == epoch && offset < packet.data.size) {
                        val count = track.write(packet.data, offset, packet.data.size - offset, AudioTrack.WRITE_BLOCKING)
                        check(count > 0) { "AudioTrack write failed: $count" }
                        offset += count
                    }
                } catch (e: Exception) {
                    Log.w("DPowerPCM", "Playback unavailable", e)
                    track?.let { runCatching { it.release() } }
                    track = null
                    queue.clear()
                    Thread.sleep(100)
                }
            }
        } catch (_: InterruptedException) {
            // Stop wakes the worker; only this thread owns/releases AudioTrack.
        } finally {
            track?.let { runCatching { it.pause(); it.flush() }; runCatching { it.release() } }
        }
    }
}
