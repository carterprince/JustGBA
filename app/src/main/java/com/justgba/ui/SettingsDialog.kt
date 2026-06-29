package com.justgba.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    hideButtons: Boolean,
    ffSpeed: Float,
    muteFfAudio: Boolean,
    showFps: Boolean,
    lockLandscape: Boolean,
    ffHoldKey: Int,
    ffToggleKey: Int,
    onHideButtonsChange: (Boolean) -> Unit,
    onFfSpeedChange: (Float) -> Unit,
    onMuteFfAudioChange: (Boolean) -> Unit,
    onShowFpsChange: (Boolean) -> Unit,
    onLockLandscapeChange: (Boolean) -> Unit,
    onFfHoldKeyChange: (Int) -> Unit,
    onFfToggleKeyChange: (Int) -> Unit,
    onResume: () -> Unit,
    onExit: (() -> Unit)? = null,
    title: String = "Game Paused",
) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text(title) },
        text = {
            val view = LocalView.current
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

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hide Virtual Gamepad")
                    Switch(checked = hideButtons, onCheckedChange = onHideButtonsChange)
                }

                Text("Fast Forward Speed", style = MaterialTheme.typography.bodyMedium)

                val speeds = listOf(
                    1.25f to "1.25x",
                    1.5f to "1.5x",
                    2f to "2x",
                    3f to "3x",
                    4f to "4x",
                    5f to "5x",
                    6f to "6x",
                    7f to "7x",
                    8f to "8x",
                    0f to "Max",
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    speeds.forEach { (value, label) ->
                        FilterChip(
                            selected = ffSpeed == value,
                            onClick = { onFfSpeedChange(value) },
                            label = { Text(label) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mute during Fast-Forward")
                    Switch(checked = muteFfAudio, onCheckedChange = onMuteFfAudioChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show FPS Counter")
                    Switch(checked = showFps, onCheckedChange = onShowFpsChange)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lock to Landscape")
                    Switch(checked = lockLandscape, onCheckedChange = onLockLandscapeChange)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                KeyBindingRow(
                    label = "Fast Forward (Hold)",
                    currentKey = ffHoldKey,
                    onBind = onFfHoldKeyChange,
                )

                KeyBindingRow(
                    label = "Fast Forward (Toggle)",
                    currentKey = ffToggleKey,
                    onBind = onFfToggleKeyChange,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (onExit == null) "Close" else "Resume Game")
                }

                if (onExit != null) {
                    TextButton(
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Exit Game")
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun KeyBindingRow(
    label: String,
    currentKey: Int,
    onBind: (Int) -> Unit,
) {
    var isListening by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isListening) {
        if (isListening) {
            focusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isListening) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                                return@onKeyEvent true
                            }
                            val actualCode = event.nativeKeyEvent.keyCode
                            if (actualCode == KeyEvent.KEYCODE_BACK) {
                                isListening = false
                                focusManager.clearFocus()
                                true
                            } else {
                                onBind(actualCode)
                                isListening = false
                                focusManager.clearFocus()
                                true
                            }
                        }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Button(
            onClick = {
                if (isListening) {
                    isListening = false
                    focusManager.clearFocus()
                } else {
                    isListening = true
                }
            }
        ) {
            if (isListening) {
                Text("Press any key...")
            } else {
                val display = if (currentKey == -1) {
                    "Unbound"
                } else {
                    KeyEvent.keyCodeToString(currentKey)
                }
                Text(display)
            }
        }
    }
}
