package com.justgba

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.justgba.audio.AudioEngine
import kotlinx.coroutines.launch
import com.justgba.emulator.EmulatorThread
import com.justgba.emulator.NativeBridge
import com.justgba.settings.SettingsManager
import com.justgba.ui.FilePicker
import com.justgba.ui.GameScreen
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private var audioEngine: AudioEngine? = null
    private var emulatorThread: EmulatorThread? = null
    private var currentSavePath: String? = null
    private lateinit var settingsManager: SettingsManager
    private var currentRomPath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(applicationContext)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsManager.lockLandscape.collect { locked ->
                    requestedOrientation = if (locked) {
                        ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        handleIntent(intent)

        setContent {
            if (currentRomPath == null) {
                FilePicker(
                    onRomSelected = { uri ->
                        val path = resolveRomPath(uri)
                        if (path != null) {
                            val originalName = getOriginalFilename(uri)
                            val savePath = File(
                                getExternalFilesDir(null) ?: filesDir,
                                "${File(originalName).nameWithoutExtension}.sav"
                            ).absolutePath
                            currentSavePath = savePath
                            startEmulation(path, savePath)
                            currentRomPath = path
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GameScreen(
                    onExit = {
                        stopEmulation()
                        currentRomPath = null
                    },
                    emulatorThread = emulatorThread,
                    settingsManager = settingsManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            stopEmulation()
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val path = resolveRomPath(uri)
        if (path != null) {
            val originalName = getOriginalFilename(uri)
            val savePath = File(
                getExternalFilesDir(null) ?: filesDir,
                "${File(originalName).nameWithoutExtension}.sav"
            ).absolutePath
            currentSavePath = savePath
            startEmulation(path, savePath)
            currentRomPath = path
        }
    }

    override fun onPause() {
        super.onPause()
        emulatorThread?.pause()
        audioEngine?.pause()
        currentSavePath?.let { NativeBridge.nativeBatterySave(it) }
    }

    override fun onResume() {
        super.onResume()
        audioEngine?.resume()
        emulatorThread?.resume()
    }

    override fun onDestroy() {
        stopEmulation()
        super.onDestroy()
    }

    private fun resolveRomPath(uri: Uri): String? {
        try {
            val docId = if (uri.scheme == "content") {
                val parts = uri.lastPathSegment?.split(":")
                if (parts != null && parts.size > 1) parts[1] else null
            } else null

            if (docId != null && docId.startsWith("/")) {
                val file = File(docId)
                if (file.exists()) return file.absolutePath
            }

            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val originalName = getOriginalFilename(uri)
            val cacheFile = File(cacheDir, originalName)
            cacheFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            if (originalName.lowercase().endsWith(".zip")) {
                return extractRomFromZip(cacheFile)
            }

            return cacheFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve ROM path", e)
            return null
        }
    }

    private fun getOriginalFilename(uri: Uri): String {
        try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    cursor.close()
                    return name ?: "rom.gba"
                }
                cursor.close()
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "rom.gba"
    }

    private val romExtensions = setOf("gba", "bin", "agb")

    private fun extractRomFromZip(zipFile: File): String? {
        try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .firstOrNull { e ->
                        !e.isDirectory &&
                            romExtensions.contains(e.name.substringAfterLast('.').lowercase())
                    } ?: run {
                    Log.e(TAG, "No ROM file found in zip archive")
                    return null
                }

                val outputName = File(entry.name).name
                val outputFile = File(cacheDir, outputName)
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Extracted ${entry.name} from zip to $outputFile")
                return outputFile.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract ROM from zip", e)
            return null
        }
    }

    private fun startEmulation(romPath: String, savePath: String) {
        val sysDir = filesDir.absolutePath
        val saveDir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath

        if (!NativeBridge.nativeInit(sysDir, saveDir)) {
            Log.e(TAG, "nativeInit failed")
            return
        }

        if (!NativeBridge.nativeLoadGame(romPath)) {
            Log.e(TAG, "nativeLoadGame failed")
            NativeBridge.nativeDeinit()
            return
        }

        NativeBridge.nativeBatteryLoad(savePath)
        Log.i(TAG, "Battery save loaded from $savePath")

        val audio = AudioEngine()
        if (!audio.init()) {
            Log.w(TAG, "AudioEngine init failed, continuing without audio")
        }
        audioEngine = audio

        val thread = EmulatorThread(audio)
        thread.start()
        emulatorThread = thread
    }

    private fun stopEmulation() {
        currentSavePath?.let { NativeBridge.nativeBatterySave(it) }
        emulatorThread?.stop()
        emulatorThread = null
        audioEngine?.release()
        audioEngine = null
        currentSavePath = null
        try {
            NativeBridge.nativeDeinit()
            Log.i(TAG, "Emulation stopped")
        } catch (e: Exception) {
            Log.e(TAG, "nativeDeinit failed", e)
        }
    }
}
