package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PipelineStage
import com.example.ui.DubberViewModel
import com.example.ui.components.AudioWaveformCanvas
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatTimecode
import com.example.ui.components.stageColor
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert

@Composable
fun VideoAnalysisScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit,
    onGoToApiKeys: () -> Unit
) {
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val characters by viewModel.activeCharacters.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()
    val keys by viewModel.allKeys.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "AI Video Analysis",
            subtitle = currentProj?.title ?: "Loading project...",
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.ANALYSIS,
            projectStage = stage,
            onSelectStep = onSelectStep
        )

        if (currentProj == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Project not found", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        val bars = currentProj.waveformCsv
            .split(",")
            .mapNotNull { it.toFloatOrNull() }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pipeline Telemetry", style = MaterialTheme.typography.titleMedium)
                            StatusPill(label = stage.label, color = stageColor(stage))
                        }

                        LinearProgressIndicator(
                            progress = { currentProj.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = stageColor(stage)
                        )

                        Text(
                            text = "${currentProj.progressPercent}% — ${currentProj.statusMessage}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (!currentProj.errorDetail.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RoseAlert.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = currentProj.errorDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RoseAlert,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.startOrRetryPipeline(currentProj.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KhmerGold,
                                    contentColor = Color(0xFF070A12)
                                )
                            ) {
                                Icon(
                                    imageVector = if (stage == PipelineStage.IMPORTED) Icons.Default.PlayArrow else Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (stage == PipelineStage.IMPORTED) "Run AI Analysis" else "Re-run Full Pipeline",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (keys.isEmpty()) {
                                FilledTonalButton(onClick = onGoToApiKeys) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Key")
                                }
                            }
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Extracted Audio Waveform", style = MaterialTheme.typography.titleMedium)
                        AudioWaveformCanvas(
                            bars = bars,
                            progressFraction = currentProj.progressPercent / 100f
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Duration: ${formatTimecode(currentProj.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Resolution: ${currentProj.videoWidth}×${currentProj.videoHeight}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Stage Breakdown", style = MaterialTheme.typography.titleMedium)
                        val stages = listOf(
                            PipelineStage.EXTRACTING_AUDIO to "Audio & Embedded Subtitle Scan (${if (currentProj.hasEmbeddedSubtitles) "Container subs found" else "ASR mode"})",
                            PipelineStage.DETECTING_LANGUAGE to "Multilingual Language ID (${currentProj.detectedLanguagesCsv.ifBlank { "Pending" }})",
                            PipelineStage.DIARIZING_SPEAKERS to "Speaker & Character Voice Profiling (${characters.size} speaker(s))",
                            PipelineStage.TRANSLATING_KHMER to "Gemini Storyline Khmer Translation (${segments.size} line(s))",
                            PipelineStage.SYNTHESIZING_TTS to "Character-Matched Khmer AI Voice Gen",
                            PipelineStage.SYNCHRONIZING_AUDIO to "Dynamic BGM/SFX Ducking & Timeline Sync"
                        )
                        stages.forEach { (itemStage, desc) ->
                            val done = stage.stepIndex >= 7 || stage.stepIndex > itemStage.stepIndex
                            val active = stage == itemStage
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.Subtitles,
                                    contentDescription = null,
                                    tint = when {
                                        done -> EmeraldSync
                                        active -> KhmerGold
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (done || active) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (stage == PipelineStage.READY || stage == PipelineStage.EXPORTED) {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.LANGUAGES) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inspect Detected Languages & Subtitles")
                    }
                }
            }
        }
    }
}
