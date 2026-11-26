package com.example.ui.screens

import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EmotionType
import com.example.data.model.PipelineStage
import com.example.data.repository.KhmerVoiceCatalog
import com.example.ui.AuditionUiState
import com.example.ui.DubberViewModel
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TimecodeChip
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerDialogueStyle
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePreviewScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()
    val auditionState by viewModel.auditionState.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    var auditionText by remember {
        mutableStateOf("សួស្តី! នេះជាការសាកល្បងសំឡេងបញ្ចូលខ្មែរដ៏ធម្មជាតិ។")
    }
    var selectedEmotion by remember { mutableStateOf(EmotionType.NEUTRAL) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Voice Preview & Editing",
            subtitle = "Audition AI Khmer voices & regenerate lines",
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.AUDITION,
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Khmer AI Voice Audition Lab", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = auditionText,
                                onValueChange = { auditionText = it },
                                label = { Text("Sample Khmer Line") },
                                textStyle = KhmerDialogueStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                EmotionType.entries.forEach { emo ->
                                    FilterChip(
                                        selected = selectedEmotion == emo,
                                        onClick = { selectedEmotion = emo },
                                        label = { Text(emo.label) }
                                    )
                                }
                            }
                            if (auditionState is AuditionUiState.Error) {
                                Text(
                                    text = (auditionState as AuditionUiState.Error).message,
                                    color = RoseAlert,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (auditionState is AuditionUiState.Playing) {
                                StatusPill(
                                    label = "Playing via ${(auditionState as AuditionUiState.Playing).engineUsed}",
                                    color = EmeraldSync,
                                    icon = Icons.Default.GraphicEq
                                )
                            }
                        }
                    }
                }

                items(KhmerVoiceCatalog.presets, key = { it.id }) { preset ->
                    val isBusy = (auditionState as? AuditionUiState.Synthesizing)?.targetId == preset.id
                    val isPlaying = (auditionState as? AuditionUiState.Playing)?.targetId == preset.id

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
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(preset.khmerName, style = KhmerDialogueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize), color = KhmerGold)
                                Text(
                                    "${preset.gender.label} • ${preset.ageGroup.label} • ${preset.style}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (isPlaying) viewModel.stopAudioPreview()
                                    else viewModel.auditionVoicePreset(preset, auditionText, selectedEmotion)
                                },
                                enabled = !isBusy,
                                modifier = Modifier.testTag("audition_preset_${preset.id}")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Audition voice",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(if (isPlaying) "Stop" else "Audition")
                            }
                        }
                    }
                }

                if (segments.isNotEmpty()) {
                    item {
                        Text(
                            text = "Generated Line Audio (${segments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(segments, key = { "voice_seg_${it.id}" }) { seg ->
                        val targetId = "seg_${seg.id}"
                        val isBusy = (auditionState as? AuditionUiState.Synthesizing)?.targetId == targetId
                        val isPlaying = (auditionState as? AuditionUiState.Playing)?.targetId == targetId

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimecodeChip(seg.startMs, seg.endMs)
                                    StatusPill(seg.emotion, ElectricCyan)
                                }
                                Text(seg.khmerText, style = KhmerDialogueStyle)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            if (isPlaying) viewModel.stopAudioPreview()
                                            else viewModel.playExistingSegmentAudio(seg)
                                        },
                                        enabled = !isBusy
                                    ) {
                                        Icon(
                                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = "Play dubbed line",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isPlaying) "Stop" else "Play Dub")
                                    }
                                    FilledTonalButton(
                                        onClick = { viewModel.resynthesizeSegment(seg) },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate voice", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Regenerate")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.SYNC) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next: Audio Synchronization")
                    }
                }
            }
        }
    }
}
