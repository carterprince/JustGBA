package com.justgba.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

fun View.createJoystickMotionListener(
    triggerStates: BooleanArray,
    onAxisMove: ((MotionEvent) -> Unit)? = null,
): View.OnGenericMotionListener {
    return View.OnGenericMotionListener { _, ev ->
        if ((ev.source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && ev.action == MotionEvent.ACTION_MOVE) {
            val lTrigger = maxOf(
                ev.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                ev.getAxisValue(MotionEvent.AXIS_BRAKE),
                ev.getAxisValue(MotionEvent.AXIS_Z)
            )
            val rTrigger = maxOf(
                ev.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                ev.getAxisValue(MotionEvent.AXIS_GAS),
                ev.getAxisValue(MotionEvent.AXIS_RZ)
            )
            val time = SystemClock.uptimeMillis()

            if (lTrigger > 0.5f && !triggerStates[0]) {
                triggerStates[0] = true
                dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2, 0))
            } else if (lTrigger < 0.2f && triggerStates[0]) {
                triggerStates[0] = false
                dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2, 0))
            }

            if (rTrigger > 0.5f && !triggerStates[1]) {
                triggerStates[1] = true
                dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R2, 0))
            } else if (rTrigger < 0.2f && triggerStates[1]) {
                triggerStates[1] = false
                dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2, 0))
            }

            onAxisMove?.invoke(ev)
            true
        } else {
            false
        }
    }
}
