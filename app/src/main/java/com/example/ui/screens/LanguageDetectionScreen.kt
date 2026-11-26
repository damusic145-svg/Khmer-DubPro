package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PipelineStage
import com.example.ui.DubberViewModel
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.TimecodeChip
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguageDetectionScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    val detectedLangs = currentProj?.detectedLanguagesCsv
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Automatic Language Detection",
            subtitle = currentProj?.title,
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.LANGUAGES,
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
                            Text("Detected Spoken Languages & Subtitle Source", style = MaterialTheme.typography.titleMedium)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (detectedLangs.isEmpty()) {
                                    StatusPill("Pending ASR Analysis", KhmerGold, Icons.Default.Language)
                                } else {
                                    detectedLangs.forEach { lang ->
                                        StatusPill(lang, ElectricCyan, Icons.Default.Language)
                                    }
                                }
                                StatusPill(
                                    label = if (currentProj?.hasEmbeddedSubtitles == true) "Embedded Subtitles + ASR" else "Pure AI Audio ASR",
                                    color = EmeraldSync,
                                    icon = Icons.Default.Subtitles
                                )
                            }

                            if (!currentProj?.storySummary.isNullOrBlank()) {
                                Text("AI Storyline Understanding", style = MaterialTheme.typography.titleSmall, color = KhmerGold)
                                Text(
                                    text = currentProj?.storySummary.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (segments.isEmpty()) {
                    item {
                        Text(
                            text = "Run AI Video Analysis to auto-detect spoken languages and generate time-aligned dialogue cues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(segments, key = { it.id }) { seg ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimecodeChip(startMs = seg.startMs, endMs = seg.endMs)
                                    StatusPill(label = seg.originalLanguage, color = ElectricCyan)
                                }
                                Text(
                                    text = seg.originalText,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = { onSelectStep(PipelineStepDestination.CHARACTERS) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next: Character & Voice Detection")
                    }
                }
            }
        }
    }
}
