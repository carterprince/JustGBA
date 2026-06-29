package com.justgba.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import com.justgba.emulator.EmulatorThread
import com.justgba.emulator.NativeBridge
import com.justgba.input.GbaButtons
import com.justgba.input.InputState
import com.justgba.input.VirtualGamepad
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    onExit: () -> Unit,
    emulatorThread: EmulatorThread?,
    settingsManager: SettingsManager,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val triggerStates = remember { booleanArrayOf(false, false) }

    DisposableEffect(view) {
        val listener = View.OnGenericMotionListener { _, ev ->
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
                    view.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L2, 0))
                } else if (lTrigger < 0.2f && triggerStates[0]) {
                    triggerStates[0] = false
                    view.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2, 0))
                }

                if (rTrigger > 0.5f && !triggerStates[1]) {
                    triggerStates[1] = true
                    view.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R2, 0))
                } else if (rTrigger < 0.2f && triggerStates[1]) {
                    triggerStates[1] = false
                    view.dispatchKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2, 0))
                }

                val x = ev.getAxisValue(MotionEvent.AXIS_X)
                val y = ev.getAxisValue(MotionEvent.AXIS_Y)
                val hatX = ev.getAxisValue(MotionEvent.AXIS_HAT_X)
                val hatY = ev.getAxisValue(MotionEvent.AXIS_HAT_Y)

                InputState.setButton(0, GbaButtons.LEFT, x < -0.5f || hatX < -0.5f)
                InputState.setButton(0, GbaButtons.RIGHT, x > 0.5f || hatX > 0.5f)
                InputState.setButton(0, GbaButtons.UP, y < -0.5f || hatY < -0.5f)
                InputState.setButton(0, GbaButtons.DOWN, y > 0.5f || hatY > 0.5f)
                true
            } else {
                false
            }
        }
        view.setOnGenericMotionListener(listener)
        onDispose {
            view.setOnGenericMotionListener(null)
        }
    }

    var isFastForwarding by remember { mutableStateOf(false) }
    var isMenuOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val hideButtons by settingsManager.hideButtons.collectAsState(false)
    val ffSpeed by settingsManager.ffSpeed.collectAsState(1f)
    val muteFfAudio by settingsManager.muteFfAudio.collectAsState(false)
    val showFps by settingsManager.showFps.collectAsState(false)
    val lockLandscape by settingsManager.lockLandscape.collectAsState(false)
    val ffHoldKey by settingsManager.ffHoldKey.collectAsState(-1)
    val ffToggleKey by settingsManager.ffToggleKey.collectAsState(-1)

    LaunchedEffect(ffSpeed) {
        NativeBridge.nativeSetFastForwardSpeed(ffSpeed)
        emulatorThread?.ffSpeedMultiplier = ffSpeed
    }

    LaunchedEffect(muteFfAudio) {
        NativeBridge.nativeSetMuteFastForwardAudio(muteFfAudio)
        emulatorThread?.muteFfAudio = muteFfAudio
    }

    val currentFps by (emulatorThread?.fps ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            NativeBridge.nativeSetSurface(holder.surface)
                        }
                        override fun surfaceChanged(
                            holder: SurfaceHolder, format: Int, w: Int, h: Int
                        ) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            NativeBridge.nativeSetSurface(null)
                        }
                    })
                    setOnKeyListener { _, keyCode, event ->
                        if (keyCode == ffHoldKey && ffHoldKey != -1) {
                            when (event.action) {
                                KeyEvent.ACTION_DOWN -> {
                                    isFastForwarding = true
                                    emulatorThread?.fastForward = true
                                }
                                KeyEvent.ACTION_UP -> {
                                    isFastForwarding = false
                                    emulatorThread?.fastForward = false
                                }
                            }
                            return@setOnKeyListener true
                        }
                        if (keyCode == ffToggleKey && ffToggleKey != -1) {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                isFastForwarding = !isFastForwarding
                                emulatorThread?.fastForward = isFastForwarding
                            }
                            return@setOnKeyListener true
                        }
                        val gbaKey = mapKeyToGba(keyCode)
                            ?: return@setOnKeyListener false
                        when (event.action) {
                            KeyEvent.ACTION_DOWN ->
                                InputState.setButton(0, gbaKey, true)
                            KeyEvent.ACTION_UP ->
                                InputState.setButton(0, gbaKey, false)
                        }
                        true
                    }
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(3f / 2f, matchHeightConstraintsFirst = isLandscape)
        )

        Text(
            text = "FPS: $currentFps",
            color = Color.Yellow,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = {
                isMenuOpen = true
                emulatorThread?.pause()
            }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            IconButton(onClick = {
                isFastForwarding = !isFastForwarding
                emulatorThread?.fastForward = isFastForwarding
            }) {
                Icon(
                    imageVector = Icons.Filled.FastForward,
                    contentDescription = "Fast Forward",
                    tint = if (isFastForwarding)
                        Color(0xFF4CAF50) else Color(0xFFB0B0B0)
                )
            }
        }

        VirtualGamepad(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(if (hideButtons) 0f else 1f),
            onButtonDown = { id -> InputState.setButton(0, id, true) },
            onButtonUp = { id -> InputState.setButton(0, id, false) }
        )
    }

    if (isMenuOpen) {
        SettingsDialog(
            hideButtons = hideButtons,
            ffSpeed = ffSpeed,
            muteFfAudio = muteFfAudio,
            showFps = showFps,
            lockLandscape = lockLandscape,
            ffHoldKey = ffHoldKey,
            ffToggleKey = ffToggleKey,
            onHideButtonsChange = { hidden ->
                scope.launch { settingsManager.setHideButtons(hidden) }
            },
            onFfSpeedChange = { speed ->
                scope.launch {
                    settingsManager.setFfSpeed(speed)
                    NativeBridge.nativeSetFastForwardSpeed(speed)
                    emulatorThread?.ffSpeedMultiplier = speed
                }
            },
            onMuteFfAudioChange = { mute ->
                scope.launch {
                    settingsManager.setMuteFfAudio(mute)
                    NativeBridge.nativeSetMuteFastForwardAudio(mute)
                    emulatorThread?.muteFfAudio = mute
                }
            },
            onShowFpsChange = { show ->
                scope.launch { settingsManager.setShowFps(show) }
            },
            onLockLandscapeChange = { locked ->
                scope.launch { settingsManager.setLockLandscape(locked) }
            },
            onFfHoldKeyChange = { keyCode ->
                scope.launch { settingsManager.setFfHoldKey(keyCode) }
            },
            onFfToggleKeyChange = { keyCode ->
                scope.launch { settingsManager.setFfToggleKey(keyCode) }
            },
            onResume = {
                isMenuOpen = false
                emulatorThread?.resume()
            },
            onExit = {
                isMenuOpen = false
                emulatorThread?.resume()
                onExit()
            },
        )
    }


}

private fun mapKeyToGba(keyCode: Int): Int? {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> GbaButtons.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GbaButtons.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GbaButtons.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GbaButtons.RIGHT
        KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_BUTTON_B -> GbaButtons.B
        KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_BUTTON_A -> GbaButtons.A
        KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1 -> GbaButtons.L
        KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_R1 -> GbaButtons.R
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_BUTTON_SELECT -> GbaButtons.SELECT
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_START -> GbaButtons.START
        else -> null
    }
}
