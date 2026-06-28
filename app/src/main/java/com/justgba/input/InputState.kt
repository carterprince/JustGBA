package com.justgba.input

object InputState {
    @Volatile private var player1Mask = 0

    fun getPlayer1Mask(): Int = player1Mask

    fun setButton(player: Int, bit: Int, pressed: Boolean) {
        if (player != 0) return
        if (pressed) player1Mask = player1Mask or (1 shl bit)
        else player1Mask = player1Mask and (1 shl bit).inv()
    }

    fun clear() {
        player1Mask = 0
    }

    fun setMask(mask: Int) {
        player1Mask = mask
    }
}
