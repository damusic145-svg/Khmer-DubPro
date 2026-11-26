package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PipelineStage
import com.example.ui.ActionUiState
import com.example.ui.DubberViewModel
import com.example.ui.components.AudioWaveformCanvas
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TimecodeChip
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AudioSyncScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val characters by viewModel.activeCharacters.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()
    val syncAction by viewModel.syncActionState.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    var duckingDb by remember(currentProj?.id, currentProj?.duckingDb) {
        mutableIntStateOf(currentProj?.duckingDb ?: -18)
    }
    var originalGain by remember(currentProj?.id, currentProj?.originalAudioGain) {
        mutableFloatStateOf(currentProj?.originalAudioGain ?: 0.25f)
    }
    var dubbedGain by remember(currentProj?.id, currentProj?.dubbedAudioGain) {
        mutableFloatStateOf(currentProj?.dubbedAudioGain ?: 1.0f)
    }

    val bars = currentProj?.waveformCsv?.split(",")?.mapNotNull { it.toFloatOrNull() }.orEmpty()
    val charMap = remember(characters) { characters.associateBy { it.id } }
    val totalDur = (currentProj?.durationMs ?: 1L).coerceAtLeast(1L).toFloat()
    val highlightRanges = remember(segments, totalDur, charMap) {
        segments.map { seg ->
            val start = ((seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L) / totalDur).coerceIn(0f, 1f)
            val end = ((seg.endMs + seg.syncOffsetMs).coerceAtLeast(seg.startMs + 200L) / totalDur).coerceIn(start, 1f)
            val cIdx = charMap[seg.characterId]?.colorIndex ?: 0
            Triple(start, end, cIdx)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Audio Synchronization",
            subtitle = "Multi-speaker timeline & BGM/SFX ducking",
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.SYNC,
            projectStage = stage,
            onSelectStep = onSelectStep
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Timeline & Ducking Envelope", style = MaterialTheme.typography.titleMedium)
                                StatusPill(
                                    label = if (!currentProj?.mixedAudioPath.isNullOrBlank()) "Mix Ready" else "Needs Mix",
                                    color = if (!currentProj?.mixedAudioPath.isNullOrBlank()) EmeraldSync else KhmerGold,
                                    icon = Icons.Default.GraphicEq
                                )
                            }

                            AudioWaveformCanvas(
                                bars = bars,
                                progressFraction = 0f,
                                highlightRanges = highlightRanges
                            )

                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Dialogue Ducking (preserves BGM & SFX)", style = MaterialTheme.typography.bodySmall)
                                    Text("$duckingDb dB", style = MaterialTheme.typography.labelMedium, color = KhmerGold)
                                }
                                Slider(
                                    value = duckingDb.toFloat(),
                                    onValueChange = { duckingDb = it.roundToInt() },
                                    valueRange = -40f..0f
                                )
                            }

                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Original Background Ambience Level", style = MaterialTheme.typography.bodySmall)
                                    Text("${(originalGain * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                }
                                Slider(
                                    value = originalGain,
                                    onValueChange = { originalGain = it },
                                    valueRange = 0f..1f
                                )
                            }

                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Khmer Dubbed Voice Level", style = MaterialTheme.typography.bodySmall)
                                    Text("${(dubbedGain * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = EmeraldSync)
                                }
                                Slider(
                                    value = dubbedGain,
                                    onValueChange = { dubbedGain = it },
                                    valueRange = 0.4f..1.5f
                                )
                            }

                            if (syncAction is ActionUiState.Error) {
                                Text((syncAction as ActionUiState.Error).message, color = RoseAlert, style = MaterialTheme.typography.bodySmall)
                            } else if (syncAction is ActionUiState.Success) {
                                Text((syncAction as ActionUiState.Success).message, color = EmeraldSync, style = MaterialTheme.typography.bodySmall)
                            }

                            Button(
                                onClick = {
                                    currentProj?.let {
                                        viewModel.rebuildSynchronizedMix(it.id, duckingDb, originalGain, dubbedGain)
                                    }
                                },
                                enabled = currentProj != null && syncAction !is ActionUiState.Loading,
                                colors = ButtonDefaults.buttonColors(containerColor = KhmerGold, contentColor = Color(0xFF070A12)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rebuild_sync_mix_btn")
                            ) {
                                if (syncAction is ActionUiState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mixing...")
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Rebuild Synchronized Audio Mix", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                items(segments, key = { "sync_${it.id}" }) { seg ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TimecodeChip(
                                    startMs = (seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L),
                                    endMs = (seg.endMs + seg.syncOffsetMs).coerceAtLeast(200L)
                                )
                                Text(
                                    text = seg.khmerText.ifBlank { seg.originalText },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                                Text(
                                    text = String.format(Locale.US, "Offset: %+d ms • Tempo: %.2fx", seg.syncOffsetMs, seg.speedRatio),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalIconButton(onClick = { viewModel.nudgeSegmentTiming(seg, -100L) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Shift earlier 100ms")
                                }
                                FilledTonalIconButton(onClick = { viewModel.nudgeSegmentTiming(seg, +100L) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Shift later 100ms")
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.PREVIEW) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next: Video Preview")
                    }
                }
            }
        }
    }
}
