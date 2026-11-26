package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExportResolution
import com.example.data.model.ThemePreference
import com.example.data.model.TtsEngineMode
import com.example.ui.DubberViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatTimecode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsHistoryScreen(
    viewModel: DubberViewModel,
    onOpenProject: (Long) -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val exports by viewModel.exportHistory.collectAsStateWithLifecycle()

    var ttsModelDraft by remember(settings.ttsModel) { mutableStateOf(settings.ttsModel) }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Settings & Export History",
            subtitle = "Pipeline defaults, TTS engine & saved MP4s"
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Studio Appearance & Engine Settings", style = MaterialTheme.typography.titleMedium)

                            Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ThemePreference.entries.forEach { pref ->
                                    FilterChip(
                                        selected = settings.themePreference == pref,
                                        onClick = { viewModel.updateSettings { it.copy(themePreference = pref) } },
                                        label = { Text(pref.label) }
                                    )
                                }
                            }

                            Text("Khmer Voice Synthesis Engine", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TtsEngineMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.ttsEngineMode == mode,
                                        onClick = { viewModel.updateSettings { it.copy(ttsEngineMode = mode) } },
                                        label = { Text(mode.label) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = ttsModelDraft,
                                onValueChange = {
                                    ttsModelDraft = it
                                    viewModel.updateSettings { cur -> cur.copy(ttsModel = it.trim()) }
                                },
                                label = { Text("Gemini TTS Model ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Fit Dubbing Speed (SOLA)", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Pitch-preserving tempo fit to match character mouth timing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.autoAdjustSpeed,
                                    onCheckedChange = { enabled ->
                                        viewModel.updateSettings { it.copy(autoAdjustSpeed = enabled) }
                                    }
                                )
                            }

                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Long-Video Chunk Window", style = MaterialTheme.typography.bodySmall)
                                    Text("${settings.chunkDurationSeconds}s", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                }
                                Slider(
                                    value = settings.chunkDurationSeconds.toFloat(),
                                    onValueChange = { v ->
                                        viewModel.updateSettings { it.copy(chunkDurationSeconds = v.roundToInt()) }
                                    },
                                    valueRange = 60f..360f
                                )
                            }

                            Text("Default Export Resolution", style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExportResolution.entries.forEach { res ->
                                    FilterChip(
                                        selected = settings.defaultExportResolution == res,
                                        onClick = { viewModel.updateSettings { it.copy(defaultExportResolution = res) } },
                                        label = { Text(res.label) }
                                    )
                                }
                            }
                        }
                    }
                }

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
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = KhmerGold)
                                Text("Setup & Gemini API Guide", style = MaterialTheme.typography.titleSmall)
                            }
                            Text(
                                "1. Built-in Firebase AI Logic works automatically with no API key required.\n" +
                                    "2. Optionally add extra Gemini API keys in the API Keys tab (encrypted via Android Keystore AES-256) for multi-key quota rotation.\n" +
                                    "3. Import any video — WorkManager runs ASR, speaker diarization, Gemini Khmer translation, AI TTS, and Media3 MP4 export automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = KhmerGold)
                        Text("Export History (${exports.size})", style = MaterialTheme.typography.titleMedium)
                    }
                }

                if (exports.isEmpty()) {
                    item {
                        Text(
                            "No exported MP4 videos yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(exports, key = { it.id }) { exp ->
                        val sizeMb = exp.fileSizeBytes / (1024f * 1024f)
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(exp.projectTitle, style = MaterialTheme.typography.titleSmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        StatusPill(exp.resolutionLabel, EmeraldSync)
                                        StatusPill(formatTimecode(exp.durationMs), ElectricCyan)
                                    }
                                    Text(
                                        text = String.format(Locale.US, "%s • %.2f MB", exp.audioModeLabel, sizeMb),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    val file = File(exp.outputPath)
                                    if (file.exists()) {
                                        IconButton(onClick = {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Dubbed Video"))
                                        }) {
                                            Icon(Icons.Default.Share, contentDescription = "Share video", tint = KhmerGold)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteExportHistory(exp.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete record", tint = RoseAlert)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
