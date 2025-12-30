package com.mph070770.sendspinandroid.audio

import android.media.*
import android.util.Log
import kotlin.math.max

internal class PcmAudioOutput {
    private val tag = "PcmAudioOutput"

    private var track: AudioTrack? = null
    private var started = false

    private var currentSampleRate = 48000
    private var currentChannels = 2
    private var currentBitDepth = 16

    fun isStarted(): Boolean = started

    fun start(sampleRate: Int, channels: Int, bitDepth: Int) {
        stop()

        require(bitDepth == 16) { "MVP only supports 16-bit PCM for now" }

        val channelMask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> error("Unsupported channel count: $channels")
        }

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        val bufferBytes = max(minBuf, 4 * minBuf)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioTrack = AudioTrack(
            attrs,
            format,
            bufferBytes,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioTrack.play()
        track = audioTrack
        started = true

        currentSampleRate = sampleRate
        currentChannels = channels
        currentBitDepth = bitDepth

        Log.i(tag, "AudioTrack started sr=$sampleRate ch=$channels bd=$bitDepth minBuf=$minBuf bufferBytes=$bufferBytes")
    }

    fun writePcm(pcm: ByteArray) {
        val t = track ?: return
        if (!started) return

        var off = 0
        while (off < pcm.size) {
            val n = t.write(pcm, off, pcm.size - off)
            if (n <= 0) break
            off += n
        }
    }

    fun flushSilence(ms: Int) {
        val t = track ?: return
        if (!started) return
        if (currentBitDepth != 16) return

        val bytesPerFrame = currentChannels * 2
        val frames = (currentSampleRate * ms) / 1000
        val bytes = (frames * bytesPerFrame).coerceAtMost(8192)

        val buf = ByteArray(bytes)
        t.write(buf, 0, buf.size)
    }

    fun stop() {
        started = false
        try {
            track?.pause()
            track?.flush()
            track?.stop()
        } catch (_: Throwable) {
        } finally {
            try { track?.release() } catch (_: Throwable) {}
            track = null
        }
    }
}
