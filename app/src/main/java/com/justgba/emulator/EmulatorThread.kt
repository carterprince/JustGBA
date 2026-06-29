package com.justgba.emulator

import android.util.Log
import com.justgba.audio.AudioEngine
import com.justgba.input.InputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmulatorThread(
    private val audioEngine: AudioEngine,
) {
    companion object {
        private const val TAG = "EmulatorThread"
        private const val TARGET_FPS = 60.0
        private const val FRAME_TIME_NS = (1_000_000_000.0 / TARGET_FPS).toLong()
    }

    private val pauseLock = Object()
    private var thread: Thread? = null
    private var running = false
    @Volatile var fastForward = false
    @Volatile var paused = false
    @Volatile var ffSpeedMultiplier = 0f
    @Volatile var ffAudioMode = 1

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

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
        var lastFrameTime = System.nanoTime()
        var lastRenderTime = System.nanoTime()
        var renderAccumulator = 0.0
        var framesEmulated = 0
        var lastFpsTime = System.currentTimeMillis()

        while (running) {
            synchronized(pauseLock) {
                while (paused) {
                    pauseLock.wait()
                    lastFrameTime = System.nanoTime()
                    lastRenderTime = System.nanoTime()
                }
            }

            audioEngine.setFastForward(fastForward, ffSpeedMultiplier)
            audioEngine.setFfAudioMode(ffAudioMode)
            NativeBridge.nativeSetInput(InputState.getPlayer1Mask())
            audioEngine.tick()

            if (!fastForward) {
                // Normal 1x Speed
                NativeBridge.nativeSetSkipRender(false)
                NativeBridge.nativeRunFrame()
                framesEmulated++

                val sleepTimeNs = FRAME_TIME_NS - (System.nanoTime() - lastFrameTime)
                if (sleepTimeNs > 1_000_000) {
                    java.util.concurrent.locks.LockSupport.parkNanos(sleepTimeNs - 1_000_000)
                }
                while (System.nanoTime() - lastFrameTime < FRAME_TIME_NS) {}

                lastFrameTime += FRAME_TIME_NS
                if (System.nanoTime() - lastFrameTime > FRAME_TIME_NS * 5) {
                    lastFrameTime = System.nanoTime()
                }

            } else {
                // Fast Forward Active
                if (ffSpeedMultiplier > 0) {
                    renderAccumulator += (1.0 / ffSpeedMultiplier)

                    val shouldRender = if (renderAccumulator >= 1.0) {
                        renderAccumulator -= 1.0
                        true
                    } else {
                        false
                    }

                    NativeBridge.nativeSetSkipRender(!shouldRender)
                    NativeBridge.nativeRunFrame()
                    framesEmulated++

                    val targetFrameTimeNs = (FRAME_TIME_NS / ffSpeedMultiplier).toLong()
                    val sleepTimeNs = targetFrameTimeNs - (System.nanoTime() - lastFrameTime)

                    if (sleepTimeNs > 1_000_000) {
                        java.util.concurrent.locks.LockSupport.parkNanos(sleepTimeNs - 1_000_000)
                    }
                    while (System.nanoTime() - lastFrameTime < targetFrameTimeNs) {}

                    lastFrameTime += targetFrameTimeNs
                    if (System.nanoTime() - lastFrameTime > targetFrameTimeNs * 5) {
                        lastFrameTime = System.nanoTime()
                    }

                } else {
                    // MAX Speed (0)
                    val now = System.nanoTime()
                    val shouldRender = (now - lastRenderTime) >= FRAME_TIME_NS

                    NativeBridge.nativeSetSkipRender(!shouldRender)
                    NativeBridge.nativeRunFrame()
                    framesEmulated++

                    if (shouldRender) {
                        lastRenderTime = System.nanoTime()
                    }
                    lastFrameTime = System.nanoTime()
                }
            }

            val currentMillis = System.currentTimeMillis()
            if (currentMillis - lastFpsTime >= 1000) {
                _fps.value = framesEmulated
                framesEmulated = 0
                lastFpsTime = currentMillis
            }
        }
    }
}
