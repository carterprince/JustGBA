package com.justgba.emulator

import java.nio.ByteBuffer

object NativeBridge {
    init {
        System.loadLibrary("justgba")
    }

    external fun nativeInit(sysDir: String, saveDir: String): Boolean
    external fun nativeLoadGame(romPath: String): Boolean
    external fun nativeRunFrame()
    external fun nativeReset()
    external fun nativeFrameReady(): Boolean
    external fun nativeGetVideoWidth(): Int
    external fun nativeGetVideoHeight(): Int
    external fun nativeReadAudio(buffer: ShortArray, capacity: Int): Int
    external fun nativeAudioReadable(): Int
    external fun nativeSetInput(mask: Int)
    external fun nativeSetSkipRender(skip: Boolean)
    external fun nativeSetFastForwardSpeed(multiplier: Float)
    external fun nativeSetMuteFastForwardAudio(mute: Boolean)
    external fun nativeBatterySave(savePath: String): Boolean
    external fun nativeBatteryLoad(savePath: String): Boolean
    external fun nativeDeinit()

    private external fun nativeGetVideoBufferImpl(): ByteBuffer?
    private var cachedVideoBuffer: ByteBuffer? = null

    fun getVideoBuffer(): ByteBuffer? {
        if (cachedVideoBuffer == null) {
            cachedVideoBuffer = nativeGetVideoBufferImpl()
        }
        return cachedVideoBuffer
    }
}
