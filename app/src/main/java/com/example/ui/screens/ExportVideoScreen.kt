package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AudioTrackMode
import com.example.data.model.ExportResolution
import com.example.data.model.PipelineStage
import com.example.data.model.SubtitleExportMode
import com.example.ui.DubberViewModel
import com.example.ui.ExportUiState
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StudioTopBar
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportVideoScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    var resolution by remember(settings.defaultExportResolution) {
        mutableStateOf(settings.defaultExportResolution)
    }
    var audioMode by remember { mutableStateOf(AudioTrackMode.KHMER_DUBBED) }
    var subtitleMode by remember { mutableStateOf(SubtitleExportMode.BILINGUAL_SRT) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Export Dubbed MP4",
            subtitle = currentProj?.title,
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.EXPORT,
            projectStage = stage,
            onSelectStep = onSelectStep
        )

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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Video Resolution (MP4 / H.264)", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ExportResolution.entries.forEach { res ->
                                FilterChip(
                                    selected = resolution == res,
                                    onClick = { resolution = res },
                                    label = { Text(res.label) }
                                )
                            }
                        }

                        Text("Audio Track Mix", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AudioTrackMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = audioMode == mode,
                                    onClick = { audioMode = mode },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("Subtitle Sidecar Export", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SubtitleExportMode.entries.forEach { sub ->
                                FilterChip(
                                    selected = subtitleMode == sub,
                                    onClick = { subtitleMode = sub },
                                    label = { Text(sub.label) }
                                )
                            }
                        }
                    }
                }

                when (val state = exportState) {
                    is ExportUiState.Exporting -> {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(state.stageLabel, style = MaterialTheme.typography.titleSmall, color = KhmerGold)
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = KhmerGold
                                )
                                Text("${state.progressPercent}%", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                        }
                    }

                    is ExportUiState.Success -> {
                        val res = state.result
                        val sizeMb = res.fileSizeBytes / (1024f * 1024f)
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = EmeraldSync.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, EmeraldSync.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSync)
                                    Text("Exported Successfully", style = MaterialTheme.typography.titleMedium, color = EmeraldSync)
                                }
                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "File: %s (%.2f MB)\nSaved to device Movies/AIKhmerDubberPro",
                                        res.outputFile.name,
                                        sizeMb
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val shareUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            res.outputFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Dubbed Video"))
                                    },
                                    modifier = Modifier.testTag("export_share_video_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share Dubbed MP4")
                                }
                            }
                        }
                    }

                    is ExportUiState.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = RoseAlert.copy(alpha = 0.14f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.message,
                                color = RoseAlert,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    ExportUiState.Idle -> Unit
                }

                Button(
                    onClick = {
                        currentProj?.let {
                            viewModel.exportProjectMp4(it.id, resolution, audioMode, subtitleMode)
                        }
                    },
                    enabled = currentProj != null && exportState !is ExportUiState.Exporting,
                    colors = ButtonDefaults.buttonColors(containerColor = KhmerGold, contentColor = Color(0xFF070A12)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("export_mp4_action_btn")
                ) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export Final Dubbed MP4", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
