package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.bluetooth.AirPodsTelemetry
import com.example.bluetooth.BatterySample
import com.example.bluetooth.BleScanStatus
import com.example.bluetooth.PodBatteryState
import com.example.ui.PodPulseUiState
import com.example.ui.components.StatusPill
import com.example.ui.components.batteryColor

@Composable
fun StatusScreen(
    state: PodPulseUiState,
    onRequestPermissions: () -> Unit,
    onRestartScan: () -> Unit,
    onToggleMonitorService: (Boolean) -> Unit,
    onOpenPopup: () -> Unit,
    onSpeakBattery: () -> Unit,
    onToggleAutoEarPause: (Boolean) -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onOpenGesturesTab: () -> Unit,
    onOpenEqualizerTab: () -> Unit,
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
            if (!state.permissionStatus.allBluetoothGranted) {
                item {
                    PermissionRequiredCard(
                        onRequestPermissions = onRequestPermissions
                    )
                }
            }

            item {
                HeroDeviceCard(
                    state = state,
                    onRestartScan = onRestartScan,
                    onOpenPopup = onOpenPopup,
                    onSpeakBattery = onSpeakBattery
                )
            }

            item {
                TriPodBatterySection(
                    telemetry = state.telemetry,
                    scanStatus = state.scanStatus
                )
            }

            item {
                LiveServiceAndQuickActionsCard(
                    state = state,
                    onToggleMonitorService = onToggleMonitorService,
                    onOpenGesturesTab = onOpenGesturesTab,
                    onOpenEqualizerTab = onOpenEqualizerTab
                )
            }

            item {
                IosCompanionControlsCard(
                    state = state,
                    onToggleAutoEarPause = onToggleAutoEarPause,
                    onToggleBassBoost = onToggleBassBoost,
                    onVolumeChange = onVolumeChange
                )
            }

            item {
                BatteryHistoryCard(samples = state.batteryHistory)
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(onRequestPermissions: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BluetoothSearching,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Nearby Bluetooth Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Text(
                text = "PodPulse needs Nearby Devices permission to scan AirPods 1 BLE battery broadcasts and monitor connected audio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.testTag("grant_bluetooth_permission_button")
            ) {
                Text("Grant Bluetooth Access")
            }
        }
    }
}

@Composable
private fun HeroDeviceCard(
    state: PodPulseUiState,
    onRestartScan: () -> Unit,
    onOpenPopup: () -> Unit,
    onSpeakBattery: () -> Unit
) {
    val telemetry = state.telemetry
    val connectedAirPods = state.connectedDevices.firstOrNull { it.isLikelyAirPods }
        ?: state.connectedDevices.firstOrNull()

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_airpods_hero),
                    contentDescription = "AirPods 1 studio preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.78f)
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusLabel = when {
                        telemetry != null && telemetry.isLidOpen -> "Case Lid Open"
                        telemetry != null && telemetry.source == com.example.bluetooth.TelemetrySource.CLASSIC_BT ->
                            "Classic BT Linked"
                        telemetry != null -> "BLE Linked • ${telemetry.rssi} dBm"
                        state.isAirPodsAudioRouted -> "Audio Connected"
                        state.scanStatus == BleScanStatus.SCANNING -> "Scanning Nearby..."
                        state.scanStatus == BleScanStatus.BLUETOOTH_DISABLED -> "Bluetooth Off"
                        else -> "Standby"
                    }
                    StatusPill(
                        text = statusLabel,
                        containerColor = if (telemetry != null || state.isAirPodsAudioRouted) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                        },
                        contentColor = if (telemetry != null || state.isAirPodsAudioRouted) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    FilledTonalButton(
                        onClick = onRestartScan,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("rescan_ble_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan BLE",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan")
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = telemetry?.model?.displayName
                            ?: connectedAirPods?.name
                            ?: "AirPods 1 Companion",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val subline = when {
                        telemetry != null -> {
                            val est = telemetry.estimatedListeningMinutes
                            val minsText = est?.let { "~${it / 60}h ${it % 60}m audio left" } ?: ""
                            "${telemetry.proximityZone.label}  •  $minsText"
                        }
                        connectedAirPods != null -> "Classic Audio active • Open lid nearby for BLE charge %"
                        else -> "Open your AirPods case near this phone to detect L / R / Case telemetry"
                    }
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenPopup,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_ios_popup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("iOS Popup")
                }
                FilledTonalButton(
                    onClick = onSpeakBattery,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("speak_battery_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Speak %")
                }
            }
        }
    }
}

@Composable
private fun TriPodBatterySection(
    telemetry: AirPodsTelemetry?,
    scanStatus: BleScanStatus
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Live Battery Telemetry",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = telemetry?.source?.label ?: scanStatus.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PodBatteryCard(
                label = "Left Pod",
                pod = telemetry?.leftPod,
                modifier = Modifier
                    .weight(1f)
                    .testTag("left_pod_battery_card")
            )
            PodBatteryCard(
                label = "Right Pod",
                pod = telemetry?.rightPod,
                modifier = Modifier
                    .weight(1f)
                    .testTag("right_pod_battery_card")
            )
            PodBatteryCard(
                label = "Case",
                pod = telemetry?.caseBattery,
                isCase = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("case_battery_card")
            )
        }
    }
}

@Composable
private fun PodBatteryCard(
    label: String,
    pod: PodBatteryState?,
    isCase: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pct = pod?.percentage
    val ringColor = batteryColor(pct)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(64.dp)) {
                    val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke
                    )
                    if (pct != null) {
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = 360f * (pct / 100f),
                            useCenter = false,
                            style = stroke
                        )
                    }
                }
                Text(
                    text = pct?.let { "$it%" } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            val statusText = when {
                pod == null || pct == null -> "No Signal"
                pod.isCharging -> "Charging"
                !isCase && pod.isInEar -> "In Ear"
                !isCase -> "Out of Ear"
                else -> "Standby"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (pod?.isCharging == true) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Charging",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (!isCase && pod?.isInEar == true) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "In ear",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pod?.isCharging == true) {
                        MaterialTheme.colorScheme.secondary
                    } else if (!isCase && pod?.isInEar == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveServiceAndQuickActionsCard(
    state: PodPulseUiState,
    onToggleMonitorService: (Boolean) -> Unit,
    onOpenGesturesTab: () -> Unit,
    onOpenEqualizerTab: () -> Unit
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Background Monitor & MediaSession",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Live status bar battery + background AirPods gesture capture",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.companionSettings.monitorServiceEnabled,
                    onCheckedChange = onToggleMonitorService,
                    modifier = Modifier.testTag("monitor_service_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenGesturesTab,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quick_gestures_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Custom Gestures")
                }
                FilledTonalButton(
                    onClick = onOpenEqualizerTab,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quick_equalizer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EQ & Bass Boost")
                }
            }
        }
    }
}

@Composable
private fun IosCompanionControlsCard(
    state: PodPulseUiState,
    onToggleAutoEarPause: (Boolean) -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onVolumeChange: (Int) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "iOS-Style AirPods Controls",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Automatic Ear Detection",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Pause music when AirPods are removed; resume in ear",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.companionSettings.autoEarDetectionPause,
                    onCheckedChange = onToggleAutoEarPause,
                    modifier = Modifier.testTag("auto_ear_detection_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bass Boost Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Sub-bass enhancement (${state.equalizerState.bassBoostStrength / 10}% strength)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.equalizerState.bassBoostEnabled,
                    onCheckedChange = onToggleBassBoost,
                    modifier = Modifier.testTag("status_bass_boost_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Media Output Volume",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${state.musicVolume} / ${state.musicMaxVolume}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = state.musicVolume.toFloat(),
                    onValueChange = { onVolumeChange(it.toInt()) },
                    valueRange = 0f..state.musicMaxVolume.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.testTag("status_volume_slider")
                )
            }
        }
    }
}

@Composable
private fun BatteryHistoryCard(samples: List<BatterySample>) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Session Battery Trend",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "${samples.size} samples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (samples.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Battery trend chart populates automatically as AirPods BLE telemetry updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                val leftColor = MaterialTheme.colorScheme.primary
                val rightColor = MaterialTheme.colorScheme.secondary
                val caseColor = MaterialTheme.colorScheme.tertiary
                val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    for (step in 0..4) {
                        val y = size.height * (step / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    fun drawSeries(values: List<Int?>, lineColor: Color) {
                        val path = Path()
                        var started = false
                        val denom = (values.size - 1).coerceAtLeast(1)
                        values.forEachIndexed { idx, pct ->
                            if (pct != null) {
                                val x = size.width * (idx.toFloat() / denom)
                                val y = size.height * (1f - (pct.coerceIn(0, 100) / 100f))
                                if (!started) {
                                    path.moveTo(x, y)
                                    started = true
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                        }
                        if (started) {
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    drawSeries(samples.map { it.leftPercent }, leftColor)
                    drawSeries(samples.map { it.rightPercent }, rightColor)
                    drawSeries(samples.map { it.casePercent }, caseColor)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LegendDot("Left", MaterialTheme.colorScheme.primary)
                    LegendDot("Right", MaterialTheme.colorScheme.secondary)
                    LegendDot("Case", MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
