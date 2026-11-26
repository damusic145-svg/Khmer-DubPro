package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CharacterVoiceEntity
import com.example.data.model.PipelineStage
import com.example.data.repository.KhmerVoiceCatalog
import com.example.ui.DubberViewModel
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KhmerDialogueStyle
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.SpeakerColors
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterVoiceScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val characters by viewModel.activeCharacters.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    var editingCharacter by remember { mutableStateOf<CharacterVoiceEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Character & Voice Detection",
            subtitle = "${characters.size} speaker(s) profiled",
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.CHARACTERS,
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
                if (characters.isEmpty()) {
                    item {
                        Text(
                            "No speakers detected yet. Run AI Video Analysis to identify male, female, young, and elderly character voices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(characters, key = { it.id }) { character ->
                        val color = SpeakerColors[character.colorIndex % SpeakerColors.size]
                        val preset = KhmerVoiceCatalog.findById(character.assignedVoiceId)

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = color)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${character.displayName} (${character.speakerTag})",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "${character.segmentCount} dialogue line(s) • Style: ${character.voiceStyle}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { editingCharacter = character },
                                        modifier = Modifier.testTag("change_voice_btn_${character.id}")
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Voice")
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StatusPill(character.gender, color)
                                    StatusPill(character.ageGroup, ElectricCyan)
                                    StatusPill("Tone: ${character.dominantEmotion}", KhmerGold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Assigned Khmer Voice: ${preset.displayName}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Text(
                                            text = preset.khmerName,
                                            style = KhmerDialogueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "Pitch %.2fx • Speed %.2fx • Gemini Voice: %s",
                                                character.pitchOffset,
                                                character.speedMultiplier,
                                                preset.geminiVoiceName
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.TRANSLATION) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next: Khmer Translation")
                    }
                }
            }
        }
    }

    editingCharacter?.let { targetChar ->
        CharacterVoicePickerDialog(
            character = targetChar,
            onDismiss = { editingCharacter = null },
            onSave = { voiceId, pitch, speed ->
                viewModel.updateCharacterVoice(targetChar, voiceId, pitch, speed)
                editingCharacter = null
            }
        )
    }
}

@Composable
private fun CharacterVoicePickerDialog(
    character: CharacterVoiceEntity,
    onDismiss: () -> Unit,
    onSave: (String, Float, Float) -> Unit
) {
    var selectedVoiceId by remember { mutableStateOf(character.assignedVoiceId) }
    var pitch by remember { mutableFloatStateOf(character.pitchOffset) }
    var speed by remember { mutableFloatStateOf(character.speedMultiplier) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Voice for ${character.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(KhmerVoiceCatalog.presets) { preset ->
                        val isChosen = preset.id == selectedVoiceId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChosen) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedVoiceId = preset.id }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(preset.displayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${preset.khmerName} • ${preset.style}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Text(String.format(Locale.US, "Pitch Offset: %.2fx", pitch), style = MaterialTheme.typography.labelMedium)
                Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.80f..1.25f)
                Text(String.format(Locale.US, "Speed Multiplier: %.2fx", speed), style = MaterialTheme.typography.labelMedium)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.80f..1.25f)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedVoiceId, pitch, speed) }) {
                Text("Save Voice")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
