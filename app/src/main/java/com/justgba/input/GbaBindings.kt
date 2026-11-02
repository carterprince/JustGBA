package com.justgba.input

import android.view.KeyEvent

object GbaBindings {
    val buttons = listOf(
        GbaButtons.A to "A",
        GbaButtons.B to "B",
        GbaButtons.L to "L",
        GbaButtons.R to "R",
        GbaButtons.UP to "Up",
        GbaButtons.DOWN to "Down",
        GbaButtons.LEFT to "Left",
        GbaButtons.RIGHT to "Right",
        GbaButtons.START to "Start",
        GbaButtons.SELECT to "Select",
    )

    val defaults = mapOf(
        GbaButtons.A to setOf(KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_A),
        GbaButtons.B to setOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_BUTTON_B),
        GbaButtons.L to setOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1),
        GbaButtons.R to setOf(KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_R1),
        GbaButtons.UP to setOf(KeyEvent.KEYCODE_DPAD_UP),
        GbaButtons.DOWN to setOf(KeyEvent.KEYCODE_DPAD_DOWN),
        GbaButtons.LEFT to setOf(KeyEvent.KEYCODE_DPAD_LEFT),
        GbaButtons.RIGHT to setOf(KeyEvent.KEYCODE_DPAD_RIGHT),
        GbaButtons.START to setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_START),
        GbaButtons.SELECT to setOf(KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BUTTON_SELECT),
    )

    fun encode(keys: Set<Int>): String = keys.sorted().joinToString(",")

    fun decode(value: String): Set<Int> =
        value.split(',').mapNotNull { it.toIntOrNull() }.filter { it >= 0 }.toSet()

    fun mapKey(keyCode: Int, bindings: Map<Int, Set<Int>>): Int? =
        bindings.entries.firstOrNull { keyCode in it.value }?.key
}
