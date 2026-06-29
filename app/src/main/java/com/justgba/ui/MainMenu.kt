package com.justgba.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.justgba.data.RecentRomEntry
import com.justgba.data.RecentRomsManager
import com.justgba.settings.SettingsManager
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun MainMenu(
    recentRomsManager: RecentRomsManager,
    settingsManager: SettingsManager,
    onRomSelected: (Uri) -> Unit,
    onRecentSelected: (RecentRomEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents by recentRomsManager.recentRoms.collectAsState(initial = emptyList())
    var isSettingsOpen by remember { mutableStateOf(false) }

    val hideButtons by settingsManager.hideButtons.collectAsState(false)
    val ffSpeed by settingsManager.ffSpeed.collectAsState(1f)
    val ffAudioMode by settingsManager.ffAudioMode.collectAsState(1)
    val showFps by settingsManager.showFps.collectAsState(false)
    val lockLandscape by settingsManager.lockLandscape.collectAsState(false)
    val ffHoldKey by settingsManager.ffHoldKey.collectAsState(-1)
    val ffToggleKey by settingsManager.ffToggleKey.collectAsState(-1)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onRomSelected(it) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "JustGBA",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Button(
                        onClick = {
                            launcher.launch(
                                arrayOf(
                                    "application/octet-stream", "application/gba",
                                    "application/zip", "application/x-zip-compressed"
                                )
                            )
                        },
                        modifier = Modifier.widthIn(min = 200.dp)
                    ) {
                        Text("Select ROM File")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Supports .gba, .bin, .agb, .zip files",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
            }

            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(recents, key = { it.uri }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RecentRow(
                                entry = entry,
                                onClick = { onRecentSelected(entry) },
                            )
                        }
                    }
                }
            }
        }

        IconButton(
            onClick = { isSettingsOpen = true },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White,
            )
        }
    }

    if (isSettingsOpen) {
        SettingsDialog(
            hideButtons = hideButtons,
            ffSpeed = ffSpeed,
            ffAudioMode = ffAudioMode,
            showFps = showFps,
            lockLandscape = lockLandscape,
            ffHoldKey = ffHoldKey,
            ffToggleKey = ffToggleKey,
            onHideButtonsChange = { hidden ->
                scope.launch { settingsManager.setHideButtons(hidden) }
            },
            onFfSpeedChange = { speed ->
                scope.launch { settingsManager.setFfSpeed(speed) }
            },
            onFfAudioModeChange = { mode ->
                scope.launch { settingsManager.setFfAudioMode(mode) }
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
            onResume = { isSettingsOpen = false },
            title = "Settings",
        )
    }
}

@Composable
private fun RecentRow(
    entry: RecentRomEntry,
    onClick: () -> Unit,
) {
    val timeString = DateFormat.getDateTimeInstance(
        DateFormat.SHORT, DateFormat.SHORT
    ).format(Date(entry.lastPlayed))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = timeString,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}
