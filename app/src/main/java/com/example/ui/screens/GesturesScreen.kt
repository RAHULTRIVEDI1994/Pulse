package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gesture.AirPodsInputGesture
import com.example.gesture.GestureEventRecord
import com.example.gesture.SystemIntentAction
import com.example.ui.PodPulseUiState
import com.example.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GesturesScreen(
    state: PodPulseUiState,
    onSetGesturesEnabled: (Boolean) -> Unit,
    onSetInterceptMediaButtons: (Boolean) -> Unit,
    onSetRequireAirPodsConnected: (Boolean) -> Unit,
    onSetMultiPressWindowMs: (Int) -> Unit,
    onSetVolumeSteps: (Int) -> Unit,
    onUpdateMapping: (AirPodsInputGesture, SystemIntentAction) -> Unit,
    onResetMappings: () -> Unit,
    onTestGesture: (AirPodsInputGesture) -> Unit,
    onClearEventLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingGesture by remember { mutableStateOf<AirPodsInputGesture?>(null) }

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
                GestureEngineHeaderCard(
                    state = state,
                    onSetGesturesEnabled = onSetGesturesEnabled,
                    onSetInterceptMediaButtons = onSetInterceptMediaButtons,
                    onSetRequireAirPodsConnected = onSetRequireAirPodsConnected,
                    onResetMappings = onResetMappings
                )
            }

            item {
                Text(
                    text = "AirPods Gesture → System Intent Mappings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(AirPodsInputGesture.entries, key = { it.name }) { gesture ->
                val mappedAction = state.gestureConfig.actionFor(gesture)
                GestureMappingCard(
                    gesture = gesture,
                    mappedAction = mappedAction,
                    onClick = { editingGesture = gesture }
                )
            }

            item {
                CadenceTuningCard(
                    windowMs = state.gestureConfig.multiPressWindowMs,
                    volumeSteps = state.gestureConfig.volumeStepCount,
                    onWindowChange = onSetMultiPressWindowMs,
                    onVolumeStepsChange = onSetVolumeSteps
                )
            }

            item {
                GestureTesterAndLogCard(
                    lastRecord = state.lastRecognizedGesture,
                    logs = state.gestureEventLog,
                    onTestGesture = onTestGesture,
                    onClearLog = onClearEventLog
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    editingGesture?.let { targetGesture ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val currentAction = state.gestureConfig.actionFor(targetGesture)

        ModalBottomSheet(
            onDismissRequest = { editingGesture = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Map \"${targetGesture.title}\"",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = targetGesture.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SystemIntentAction.entries, key = { it.name }) { action ->
                        val selected = action == currentAction
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateMapping(targetGesture, action)
                                    editingGesture = null
                                }
                                .testTag("action_option_${action.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = action.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureEngineHeaderCard(
    state: PodPulseUiState,
    onSetGesturesEnabled: (Boolean) -> Unit,
    onSetInterceptMediaButtons: (Boolean) -> Unit,
    onSetRequireAirPodsConnected: (Boolean) -> Unit,
    onResetMappings: () -> Unit
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
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Custom Gesture Remapper",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Intercepts AirPods double-tap & press key events",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = state.gestureConfig.enabled,
                    onCheckedChange = onSetGesturesEnabled,
                    modifier = Modifier.testTag("gestures_master_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Priority MediaSession Interception",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Capture AVRCP media buttons when Monitor Service is active",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.gestureConfig.interceptMediaButtons,
                    onCheckedChange = onSetInterceptMediaButtons,
                    modifier = Modifier.testTag("intercept_media_buttons_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AirPods-Only Guard",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Only remap when AirPods BLE/Audio is actively connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.gestureConfig.requireAirPodsConnected,
                    onCheckedChange = onSetRequireAirPodsConnected,
                    modifier = Modifier.testTag("airpods_only_guard_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onResetMappings,
                    modifier = Modifier.testTag("reset_gestures_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults")
                }
            }
        }
    }
}

@Composable
private fun GestureMappingCard(
    gesture: AirPodsInputGesture,
    mappedAction: SystemIntentAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("gesture_card_${gesture.name.lowercase()}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = gesture.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = gesture.triggerSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(
                    text = mappedAction.title,
                    containerColor = if (mappedAction == SystemIntentAction.NO_ACTION) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (mappedAction == SystemIntentAction.NO_ACTION) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Edit gesture mapping",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CadenceTuningCard(
    windowMs: Int,
    volumeSteps: Int,
    onWindowChange: (Int) -> Unit,
    onVolumeStepsChange: (Int) -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cadence & Volume Step Tuning",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Multi-Press Detection Window", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$windowMs ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = windowMs.toFloat(),
                    onValueChange = { onWindowChange(it.toInt()) },
                    valueRange = 250f..750f,
                    modifier = Modifier.testTag("cadence_window_slider")
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume Steps per Gesture", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "$volumeSteps step${if (volumeSteps > 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = volumeSteps.toFloat(),
                    onValueChange = { onVolumeStepsChange(it.toInt().coerceIn(1, 3)) },
                    valueRange = 1f..3f,
                    steps = 1,
                    modifier = Modifier.testTag("volume_steps_slider")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GestureTesterAndLogCard(
    lastRecord: GestureEventRecord?,
    logs: List<GestureEventRecord>,
    onTestGesture: (AirPodsInputGesture) -> Unit,
    onClearLog: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Interactive Gesture Tester & Log",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Simulate incoming AirPods KeyEvents to verify your mapped intents",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onClearLog,
                    modifier = Modifier.testTag("clear_gesture_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear gesture log"
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AirPodsInputGesture.entries.forEach { gesture ->
                    AssistChip(
                        onClick = { onTestGesture(gesture) },
                        label = {
                            Text(
                                text = when (gesture) {
                                    AirPodsInputGesture.DOUBLE_TAP_PLAY_PAUSE -> "Test Double-Tap"
                                    AirPodsInputGesture.RAPID_DOUBLE_PRESS -> "Test 2x Press"
                                    AirPodsInputGesture.TRIPLE_PRESS -> "Test 3x Press"
                                    AirPodsInputGesture.SINGLE_PRESS_HOOK -> "Test Hook Press"
                                    AirPodsInputGesture.POD_NEXT_SIGNAL -> "Test Next"
                                    AirPodsInputGesture.POD_PREVIOUS_SIGNAL -> "Test Prev"
                                    AirPodsInputGesture.VOICE_ASSIST_SIGNAL -> "Test Voice"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("test_gesture_${gesture.name.lowercase()}")
                    )
                }
            }

            if (lastRecord != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Last Triggered: ${lastRecord.recognizedGesture.title} → ${lastRecord.executedAction.title}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "${lastRecord.outcome} (${lastRecord.keyCodeName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (logs.isEmpty()) {
                Text(
                    text = "No gesture events captured yet. Tap an AirPod or use a test chip above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    logs.take(8).forEach { record ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${record.recognizedGesture.title} → ${record.executedAction.title}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${record.sourceLabel} • ${record.outcome}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = timeFormatter.format(Date(record.timestampMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
