package com.justgba.input

object InputState {
    @Volatile private var player1Mask = 0

    fun getPlayer1Mask(): Int = player1Mask

    fun setButton(player: Int, bit: Int, pressed: Boolean) {
        if (player != 0) return
        
        if (pressed) {
            // SOCD Cleaner: If pressing a direction, forcefully un-press the opposite direction
            when (bit) {
                4 -> player1Mask = player1Mask and (1 shl 5).inv() // Pressing Right (4), clear Left (5)
                5 -> player1Mask = player1Mask and (1 shl 4).inv() // Pressing Left (5), clear Right (4)
                6 -> player1Mask = player1Mask and (1 shl 7).inv() // Pressing Up (6), clear Down (7)
                7 -> player1Mask = player1Mask and (1 shl 6).inv() // Pressing Down (7), clear Up (6)
            }
            player1Mask = player1Mask or (1 shl bit)
        } else {
            player1Mask = player1Mask and (1 shl bit).inv()
        }
    }

    fun clear() {
        player1Mask = 0
    }

    fun setMask(mask: Int) {
        player1Mask = mask
    }
}
