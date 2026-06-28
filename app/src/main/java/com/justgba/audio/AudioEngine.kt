package com.justgba.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.justgba.emulator.NativeBridge
import kotlin.math.min

class AudioEngine {
    companion object {
        private const val TAG = "AudioEngine"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_MS = 40
    }

    private var audioTrack: AudioTrack? = null
    private val readBuf = ShortArray(4096)
    private val convertedBuf = ShortArray(8192)
    private var fastForward = false
    private var muteFfAudio = true

    fun init(): Boolean {
        val bufSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNELS, ENCODING)
        val desiredSize = (SAMPLE_RATE * BUFFER_MS / 1000 * 2 * 2).coerceAtLeast(bufSize)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(ENCODING)
            .setChannelMask(CHANNELS)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(desiredSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val track = audioTrack
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack initialization failed")
            audioTrack?.release()
            audioTrack = null
            return false
        }

        track.play()
        Log.i(TAG, "AudioTrack initialized at ${SAMPLE_RATE}Hz, buffer=$desiredSize")
        return true
    }

    fun setFastForward(enabled: Boolean) {
        fastForward = enabled
    }

    fun setMuteFfAudio(mute: Boolean) {
        muteFfAudio = mute
    }

    fun pause() {
        try {
            audioTrack?.pause()
        } catch (_: Exception) {}
    }

    fun resume() {
        try {
            val track = audioTrack ?: return
            track.play()
        } catch (_: Exception) {}
    }

    fun tick() {
        val track = audioTrack ?: return

        if (fastForward && muteFfAudio) {
            val readable = NativeBridge.nativeAudioReadable()
            if (readable > 0) {
                val toRead = min(readable, readBuf.size)
                NativeBridge.nativeReadAudio(readBuf, toRead)
            }
            return
        }

        val readable = NativeBridge.nativeAudioReadable()
        if (readable <= 0) return

        val toRead = min(readable, readBuf.size)
        val samples = NativeBridge.nativeReadAudio(readBuf, toRead)
        if (samples <= 0) return

        val frames = sampleRateConvert65536to48000(readBuf, samples / 2)
        if (frames <= 0) return

        val writeMode = AudioTrack.WRITE_NON_BLOCKING
        val written = track.write(convertedBuf, 0, frames * 2, writeMode)

        if (fastForward && written < frames * 2) {
            Log.v(TAG, "Fast-forward dropped ${frames * 2 - written} samples")
        }
    }

    private fun sampleRateConvert65536to48000(input: ShortArray, inputFrames: Int): Int {
        val ratio = 65536.0 / 48000.0
        var readIdx = 0.0
        var writeIdx = 0

        while (writeIdx < convertedBuf.size / 2 && (readIdx.toInt() + 1) < inputFrames) {
            val i = readIdx.toInt()
            val frac = readIdx - i

            val s0L = input[i * 2].toInt()
            val s0R = input[i * 2 + 1].toInt()
            val s1L = input[(i + 1) * 2].toInt()
            val s1R = input[(i + 1) * 2 + 1].toInt()

            convertedBuf[writeIdx * 2] = (s0L + ((s1L - s0L) * frac).toInt()).toShort()
            convertedBuf[writeIdx * 2 + 1] = (s0R + ((s1R - s0R) * frac).toInt()).toShort()

            readIdx += ratio
            writeIdx++
        }

        return writeIdx
    }

    fun release() {
        try {
            audioTrack?.stop()
        } catch (_: Exception) {}
        audioTrack?.release()
        audioTrack = null
    }
}
