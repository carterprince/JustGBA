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
        private const val BUFFER_MS = 80
    }

    private var audioTrack: AudioTrack? = null
    private val readBuf = ShortArray(4096)
    private val convertedBuf = ShortArray(8192)
    private var fastForward = false
    private var ffAudioMode = 1
    private var resamplePhase = 0.0
    private var lastSampleL = 0.toShort()
    private var lastSampleR = 0.toShort()
    private val leftoverBuf = ShortArray(8192)
    private var leftoverFrames = 0

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

    fun setFastForward(enabled: Boolean, speedMultiplier: Float) {
        fastForward = enabled
        val track = audioTrack ?: return

        try {
            if (enabled && speedMultiplier > 1f && ffAudioMode == 1) {
                val safeMultiplier = minOf(speedMultiplier, 4f)
                track.playbackRate = (SAMPLE_RATE * safeMultiplier).toInt()
            } else {
                track.playbackRate = SAMPLE_RATE
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback rate", e)
        }
    }

    fun setFfAudioMode(mode: Int) {
        ffAudioMode = mode
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

        // 1. ALWAYS drain the native buffer first! 
        // This prevents gpSP from building up a 1-second backlog in C++ memory.
        val readable = NativeBridge.nativeAudioReadable()
        var newFrames = 0
        
        if (readable > 0) {
            val toRead = min(readable, readBuf.size)
            val samples = NativeBridge.nativeReadAudio(readBuf, toRead)
            
            if (fastForward && ffAudioMode == 0) {
                leftoverFrames = 0 // Clear any old audio
                return
            }
            
            if (samples > 0) {
                newFrames = sampleRateConvert65536to48000(readBuf, samples / 2)
            }
        }

        // 2. Try to write any leftovers from previous frames
        if (leftoverFrames > 0) {
            val written = track.write(leftoverBuf, 0, leftoverFrames * 2, AudioTrack.WRITE_NON_BLOCKING)
            if (written > 0) {
                val framesWritten = written / 2
                leftoverFrames -= framesWritten
                if (leftoverFrames > 0) {
                    System.arraycopy(leftoverBuf, written, leftoverBuf, 0, leftoverFrames * 2)
                }
            }
        }

        // 3. Write the newly generated audio
        if (newFrames > 0) {
            // If we still have leftovers, the AudioTrack is 100% full (happens during FF).
            // We MUST drop this new audio to stay in sync with the video.
            if (leftoverFrames > 0) {
                return 
            }

            val written = track.write(convertedBuf, 0, newFrames * 2, AudioTrack.WRITE_NON_BLOCKING)
            
            // Save any unwritten samples to prevent crackling at 1x speed
            if (written < newFrames * 2) {
                val unwrittenFrames = newFrames - (written / 2)
                val framesToSave = min(unwrittenFrames, leftoverBuf.size / 2)
                System.arraycopy(convertedBuf, written, leftoverBuf, 0, framesToSave * 2)
                leftoverFrames = framesToSave
            }
        }
    }

    private fun sampleRateConvert65536to48000(input: ShortArray, inputFrames: Int): Int {
        val ratio = 65536.0 / 48000.0
        var writeIdx = 0

        while (writeIdx < convertedBuf.size / 2 && resamplePhase.toInt() < inputFrames) {
            val i = resamplePhase.toInt()
            val frac = resamplePhase - i

            val s0L = if (i == 0) lastSampleL.toInt() else input[(i - 1) * 2].toInt()
            val s0R = if (i == 0) lastSampleR.toInt() else input[(i - 1) * 2 + 1].toInt()
            val s1L = input[i * 2].toInt()
            val s1R = input[i * 2 + 1].toInt()

            convertedBuf[writeIdx * 2] = (s0L + ((s1L - s0L) * frac).toInt()).toShort()
            convertedBuf[writeIdx * 2 + 1] = (s0R + ((s1R - s0R) * frac).toInt()).toShort()

            resamplePhase += ratio
            writeIdx++
        }

        lastSampleL = input[(inputFrames - 1) * 2]
        lastSampleR = input[(inputFrames - 1) * 2 + 1]

        resamplePhase -= inputFrames

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
