package com.justgba.emulator

import android.util.Log
import com.justgba.audio.AudioEngine
import com.justgba.input.InputState
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Semaphore

class EmulatorThread(
    private val audioEngine: AudioEngine,
) {
    companion object {
        private const val TAG = "EmulatorThread"
        private const val GBA_FPS = 59.7275
    }

    private val pauseLock = Object()
    private var thread: Thread? = null
    private var running = false
    @Volatile var fastForward = false
    @Volatile var paused = false
    @Volatile var ffSpeedMultiplier = 0f
    @Volatile var muteFfAudio = true
    var frameChannel: Channel<Unit>? = null
    val frameSync = Semaphore(0)

    fun requestFrame() {
        frameSync.drainPermits()
        frameSync.release()
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ runLoop() }, "emulator").apply { start() }
    }

    fun stop() {
        running = false
        resume()
        thread?.join(500)
        thread = null
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
        synchronized(pauseLock) {
            pauseLock.notifyAll()
        }
    }

    private fun runLoop() {
        while (running) {
            synchronized(pauseLock) {
                while (paused) {
                    pauseLock.wait()
                }
            }

            if (!fastForward) {
                frameSync.acquire()
            }

            audioEngine.setFastForward(fastForward)
            audioEngine.setMuteFfAudio(muteFfAudio)
            NativeBridge.nativeSetInput(InputState.getPlayer1Mask())

            audioEngine.tick()

            NativeBridge.nativeSetSkipRender(false)

            NativeBridge.nativeRunFrame()
            frameChannel?.trySend(Unit)

            if (fastForward && ffSpeedMultiplier > 0) {
                val targetMs = (1000.0 / (GBA_FPS * ffSpeedMultiplier)).toLong()
                Thread.sleep(targetMs)
            }
        }
    }
}
