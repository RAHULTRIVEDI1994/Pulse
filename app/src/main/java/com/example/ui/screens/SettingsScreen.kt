package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.PodPulseUiState

@Composable
fun SettingsScreen(
    state: PodPulseUiState,
    onRequestPermissions: () -> Unit,
    onTogglePopupOnLidOpen: (Boolean) -> Unit,
    onToggleAutoEarPause: (Boolean) -> Unit,
    onToggleAutoEarResume: (Boolean) -> Unit,
    onToggleAnnounceBattery: (Boolean) -> Unit,
    onSelectLowBatteryThreshold: (Int) -> Unit,
    onResetGestures: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Permissions & System Access",
                            style = MaterialTheme.typography.titleMedium
                        )
                        PermissionStatusRow(
                            title = "Bluetooth Scan (BLE 0x004C)",
                            granted = state.permissionStatus.hasBluetoothScan
                        )
                        PermissionStatusRow(
                            title = "Bluetooth Connect (A2DP / HFP)",
                            granted = state.permissionStatus.hasBluetoothConnect
                        )
                        PermissionStatusRow(
                            title = "Notifications (Live Battery & Alerts)",
                            granted = state.permissionStatus.hasNotifications
                        )
                        if (!state.permissionStatus.allBluetoothGranted || !state.permissionStatus.hasNotifications) {
                            Button(
                                onClick = onRequestPermissions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_request_permissions_button")
                            ) {
                                Text("Grant Missing Permissions")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "iOS Companion Automation",
                            style = MaterialTheme.typography.titleMedium
                        )

                        SettingToggleRow(
                            title = "Popup Card on Case Lid Open",
                            subtitle = "Automatically display iOS-style pairing sheet when AirPods lid opens",
                            checked = state.companionSettings.popupOnLidOpen,
                            onCheckedChange = onTogglePopupOnLidOpen,
                            testTag = "settings_lid_popup_switch"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingToggleRow(
                            title = "Auto-Pause on Ear Removal",
                            subtitle = "Pause media when AirPods are taken out of both ears",
                            checked = state.companionSettings.autoEarDetectionPause,
                            onCheckedChange = onToggleAutoEarPause,
                            testTag = "settings_ear_pause_switch"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingToggleRow(
                            title = "Auto-Resume on Ear Insert",
                            subtitle = "Resume media automatically when placed back in ear",
                            checked = state.companionSettings.autoEarDetectionResume,
                            onCheckedChange = onToggleAutoEarResume,
                            testTag = "settings_ear_resume_switch"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingToggleRow(
                            title = "Voice Battery on Lid Open",
                            subtitle = "Speak Left, Right, and Case percentages using TTS",
                            checked = state.companionSettings.announceBatteryOnConnect,
                            onCheckedChange = onToggleAnnounceBattery,
                            testTag = "settings_voice_battery_switch"
                        )
                    }
                }
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Low Battery Alert Threshold",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10, 15, 20, 30).forEach { threshold ->
                                FilterChip(
                                    selected = state.companionSettings.lowBatteryAlertPercent == threshold,
                                    onClick = { onSelectLowBatteryThreshold(threshold) },
                                    label = { Text("$threshold%") },
                                    modifier = Modifier.testTag("low_battery_chip_$threshold")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = onResetGestures,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_reset_gestures_button")
                        ) {
                            Text("Restore Default Gesture Mappings")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = if (granted) "Granted" else "Missing",
            tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
