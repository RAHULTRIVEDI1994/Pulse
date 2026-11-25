package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.features.EqBandUi
import com.example.features.EqPreset
import com.example.ui.PodPulseUiState
import com.example.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    state: PodPulseUiState,
    onSetEqEnabled: (Boolean) -> Unit,
    onApplyPreset: (EqPreset) -> Unit,
    onUpdateBand: (Int, Float) -> Unit,
    onSetBassBoostEnabled: (Boolean) -> Unit,
    onSetBassBoostStrength: (Int) -> Unit,
    onSetLoudnessEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val eq = state.equalizerState

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
                EqualizerHeaderCard(
                    eqEnabled = eq.eqEnabled,
                    hardwareAttached = eq.hardwareAttached,
                    statusNote = eq.statusNote,
                    onSetEqEnabled = onSetEqEnabled
                )
            }

            item {
                BassBoostModeCard(
                    bassBoostEnabled = eq.bassBoostEnabled,
                    bassStrength = eq.bassBoostStrength,
                    loudnessEnabled = eq.loudnessEnabled,
                    onToggleBassBoost = onSetBassBoostEnabled,
                    onStrengthChange = onSetBassBoostStrength,
                    onToggleLoudness = onSetLoudnessEnabled
                )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Acoustic Presets",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = { onApplyPreset(EqPreset.FLAT) },
                                modifier = Modifier.testTag("eq_reset_flat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flat")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EqPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = eq.selectedPreset == preset,
                                    onClick = { onApplyPreset(preset) },
                                    label = { Text(preset.displayName) },
                                    modifier = Modifier.testTag("eq_preset_${preset.name.lowercase()}")
                                )
                            }
                        }

                        Text(
                            text = eq.selectedPreset.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                MultiBandEqualizerCard(
                    eqEnabled = eq.eqEnabled,
                    bands = eq.bands,
                    bassBoostEnabled = eq.bassBoostEnabled,
                    bassStrength = eq.bassBoostStrength,
                    onUpdateBand = onUpdateBand
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun EqualizerHeaderCard(
    eqEnabled: Boolean,
    hardwareAttached: Boolean,
    statusNote: String,
    onSetEqEnabled: (Boolean) -> Unit
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_acoustic_banner),
                contentDescription = "Acoustic frequency wave banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.45f)
                            )
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(
                        text = if (hardwareAttached) "System Audio DSP Linked" else "Preset Profile Mode",
                        containerColor = if (hardwareAttached) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (hardwareAttached) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "Audio Equalizer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = statusNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = onSetEqEnabled,
                    modifier = Modifier.testTag("eq_master_switch")
                )
            }
        }
    }
}

@Composable
private fun BassBoostModeCard(
    bassBoostEnabled: Boolean,
    bassStrength: Int,
    loudnessEnabled: Boolean,
    onToggleBassBoost: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    onToggleLoudness: (Boolean) -> Unit
) {
    val containerColor = if (bassBoostEnabled) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor)
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
                        imageVector = Icons.Default.Speaker,
                        contentDescription = null,
                        tint = if (bassBoostEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Bass Boost Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Hardware low-frequency driver enhancement for AirPods 1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = bassBoostEnabled,
                    onCheckedChange = onToggleBassBoost,
                    modifier = Modifier.testTag("bass_boost_mode_switch")
                )
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bass Boost Intensity",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${bassStrength / 10}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = bassStrength.toFloat(),
                    onValueChange = { onStrengthChange(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = bassBoostEnabled,
                    modifier = Modifier.testTag("bass_boost_strength_slider")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

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
                        imageVector = Icons.Default.SurroundSound,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Loudness Enhancer (+3.5 dB)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Adds dynamic headroom in noisy outdoor environments",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = loudnessEnabled,
                    onCheckedChange = onToggleLoudness,
                    modifier = Modifier.testTag("loudness_enhancer_switch")
                )
            }
        }
    }
}

@Composable
private fun MultiBandEqualizerCard(
    eqEnabled: Boolean,
    bands: List<EqBandUi>,
    bassBoostEnabled: Boolean,
    bassStrength: Int,
    onUpdateBand: (Int, Float) -> Unit
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Frequency Curve & 5-Band EQ",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            FrequencyCurvePreview(
                bands = bands,
                eqEnabled = eqEnabled,
                bassBoostEnabled = bassBoostEnabled,
                bassStrength = bassStrength
            )

            bands.forEach { band ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = band.label,
                            style = MaterialTheme.typography.labelLarge
                        )
                        val sign = if (band.levelDb > 0f) "+" else ""
                        Text(
                            text = "$sign${"%.1f".format(band.levelDb)} dB",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eqEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Slider(
                        value = band.levelDb,
                        onValueChange = { onUpdateBand(band.index, it) },
                        valueRange = band.minDb..band.maxDb,
                        enabled = eqEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("eq_band_slider_${band.index}")
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencyCurvePreview(
    bands: List<EqBandUi>,
    eqEnabled: Boolean,
    bassBoostEnabled: Boolean,
    bassStrength: Int
) {
    val curveColor = if (eqEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fillBrushColor = curveColor.copy(alpha = 0.22f)
    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val bassOverlayColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        val midY = size.height / 2f
        drawLine(
            color = zeroLineColor,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (bands.isEmpty()) return@Canvas

        val points = bands.mapIndexed { index, band ->
            val x = if (bands.size == 1) size.width / 2f else {
                size.width * (index.toFloat() / (bands.size - 1))
            }
            val effectiveDb = if (bassBoostEnabled && index == 0) {
                (band.levelDb + (bassStrength / 1000f) * 4f).coerceIn(band.minDb, band.maxDb)
            } else {
                band.levelDb
            }
            val span = (band.maxDb - band.minDb).coerceAtLeast(1f)
            val norm = (effectiveDb - band.minDb) / span
            val y = size.height * (1f - norm)
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(path = fillPath, color = fillBrushColor)
        drawPath(
            path = curvePath,
            color = curveColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEachIndexed { idx, pt ->
            drawCircle(
                color = if (bassBoostEnabled && idx == 0) bassOverlayColor else curveColor,
                radius = 4.5.dp.toPx(),
                center = pt
            )
        }
    }
}
