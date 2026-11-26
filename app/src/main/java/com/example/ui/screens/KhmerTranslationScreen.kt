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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.local.DialogueSegmentEntity
import com.example.data.model.EmotionType
import com.example.data.model.PipelineStage
import com.example.ui.ActionUiState
import com.example.ui.DubberViewModel
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TimecodeChip
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KhmerDialogueStyle
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.SpeakerColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KhmerTranslationScreen(
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
    val charMap = remember(characters) { characters.associateBy { it.id } }

    var editingSegment by remember { mutableStateOf<DialogueSegmentEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Khmer Translation",
            subtitle = "Context-aware Gemini script (${segments.size} lines)",
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.TRANSLATION,
            projectStage = stage,
            onSelectStep = onSelectStep
        )

        if (syncAction is ActionUiState.Loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(segments, key = { it.id }) { seg ->
                    val charEntity = charMap[seg.characterId]
                    val speakerColor = SpeakerColors[(charEntity?.colorIndex ?: 0) % SpeakerColors.size]
                    val slotSec = (seg.endMs - seg.startMs) / 1000f
                    val dubSec = seg.ttsDurationMs / 1000f

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        label = charEntity?.displayName ?: seg.speakerTag,
                                        color = speakerColor
                                    )
                                    TimecodeChip(seg.startMs, seg.endMs)
                                }
                                Row {
                                    IconButton(
                                        onClick = { viewModel.retranslateSegmentWithGemini(seg) },
                                        modifier = Modifier.testTag("retranslate_seg_${seg.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = "Re-translate with Gemini",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingSegment = seg },
                                        modifier = Modifier.testTag("edit_seg_${seg.id}")
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit Khmer script",
                                            tint = KhmerGold,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = seg.originalText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = seg.khmerText.ifBlank { "Pending translation..." },
                                style = KhmerDialogueStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Emotion: ${seg.emotion} • Pace: ${String.format(Locale.US, "%.2fx", seg.speakingPace)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "Window %.1fs / Dub %.1fs", slotSec, dubSec),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.AUDITION) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next: Voice Preview & Editing")
                    }
                }
            }
        }
    }

    editingSegment?.let { seg ->
        var draftKhmer by remember(seg.id) { mutableStateOf(seg.khmerText) }
        var draftEmotion by remember(seg.id) {
            mutableStateOf(runCatching { EmotionType.valueOf(seg.emotion) }.getOrDefault(EmotionType.NEUTRAL))
        }

        AlertDialog(
            onDismissRequest = { editingSegment = null },
            title = { Text("Edit Khmer Dialogue") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Original: ${seg.originalText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draftKhmer,
                        onValueChange = { draftKhmer = it },
                        label = { Text("Khmer Text (ភាសាខ្មែរ)") },
                        textStyle = KhmerDialogueStyle,
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Emotional Tone", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EmotionType.entries.forEach { emotion ->
                            FilterChip(
                                selected = draftEmotion == emotion,
                                onClick = { draftEmotion = emotion },
                                label = { Text("${emotion.label} (${emotion.khmerHint})") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateSegmentKhmerText(seg, draftKhmer, draftEmotion)
                    editingSegment = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSegment = null }) { Text("Cancel") }
            }
        )
    }
}
