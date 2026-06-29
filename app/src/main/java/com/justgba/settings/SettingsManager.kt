package com.justgba.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "justgba_settings")

class SettingsManager(private val context: Context) {
    companion object {
        val HIDE_BUTTONS = booleanPreferencesKey("hide_buttons")
        val FF_SPEED = floatPreferencesKey("ff_speed")
        val FF_AUDIO_MODE = intPreferencesKey("ff_audio_mode")
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val FF_HOLD_KEY = intPreferencesKey("ff_hold_key")
        val FF_TOGGLE_KEY = intPreferencesKey("ff_toggle_key")
        val LOCK_LANDSCAPE = booleanPreferencesKey("lock_landscape")
    }

    val hideButtons: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[HIDE_BUTTONS] ?: false
    }

    val ffSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[FF_SPEED] ?: 1f
    }

    val ffAudioMode: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FF_AUDIO_MODE] ?: 1
    }

    val showFps: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_FPS] ?: false
    }

    val ffHoldKey: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FF_HOLD_KEY] ?: -1
    }

    val ffToggleKey: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[FF_TOGGLE_KEY] ?: -1
    }

    val lockLandscape: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[LOCK_LANDSCAPE] ?: false
    }

    suspend fun setHideButtons(hidden: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[HIDE_BUTTONS] = hidden
        }
    }

    suspend fun setFfSpeed(speed: Float) {
        context.dataStore.edit { prefs ->
            prefs[FF_SPEED] = speed
        }
    }

    suspend fun setFfAudioMode(mode: Int) {
        context.dataStore.edit { prefs ->
            prefs[FF_AUDIO_MODE] = mode
        }
    }

    suspend fun setShowFps(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_FPS] = show
        }
    }

    suspend fun setFfHoldKey(keyCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[FF_HOLD_KEY] = keyCode
        }
    }

    suspend fun setFfToggleKey(keyCode: Int) {
        context.dataStore.edit { prefs ->
            prefs[FF_TOGGLE_KEY] = keyCode
        }
    }

    suspend fun setLockLandscape(locked: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[LOCK_LANDSCAPE] = locked
        }
    }
}
