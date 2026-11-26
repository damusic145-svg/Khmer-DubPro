package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KeyStatus
import com.example.ui.ActionUiState
import com.example.ui.DubberViewModel
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import kotlin.math.roundToInt

@Composable
fun ImportVideoScreen(
    viewModel: DubberViewModel,
    onNavigateToKeys: () -> Unit,
    onProjectCreated: (Long) -> Unit
) {
    val keys by viewModel.allKeys.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var projectTitle by remember { mutableStateOf("") }
    var duckingDb by remember(settings.defaultDuckingDb) { mutableIntStateOf(settings.defaultDuckingDb) }
    var autoStartPipeline by remember { mutableStateOf(true) }

    val activeKeyCount = keys.count { it.isEnabled && it.status == KeyStatus.ACTIVE.name }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            viewModel.resetImportState()
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            viewModel.resetImportState()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Import Video",
            subtitle = "Zero-manual-subtitle AI Khmer dubbing"
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (activeKeyCount == 0) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ElectricCyan.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ElectricCyan)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Firebase AI Logic Active (No Key Needed)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Using built-in ${settings.primaryModel} via App Check. Optionally add custom Gemini API keys for extra quota rotation.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            FilledTonalButton(
                                onClick = onNavigateToKeys,
                                modifier = Modifier.testTag("import_add_key_btn")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Keys")
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("1. Select Source Video", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Supports MP4, MKV, MOV, and WebM in any spoken language. Embedded subtitles are extracted automatically when present; otherwise Gemini ASR transcribes directly from audio.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_video_gallery_btn")
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Video Gallery")
                            }
                            FilledTonalButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("video/*")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pick_video_files_btn")
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Browse Files")
                            }
                        }

                        if (selectedUri != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ElectricCyan.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Selected source: ${selectedUri?.lastPathSegment ?: selectedUri.toString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricCyan,
                                    modifier = Modifier.padding(12.dp)
                                )
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("2. Dubbing Configuration", style = MaterialTheme.typography.titleMedium)

                        OutlinedTextField(
                            value = projectTitle,
                            onValueChange = { projectTitle = it },
                            label = { Text("Project Title (optional)") },
                            placeholder = { Text("Auto-filled from video filename") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_project_title_input")
                        )

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Original Dialogue Ducking", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${duckingDb} dB (preserves BGM & SFX)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KhmerGold
                                )
                            }
                            Slider(
                                value = duckingDb.toFloat(),
                                onValueChange = { duckingDb = it.roundToInt() },
                                valueRange = -36f..-6f,
                                modifier = Modifier.testTag("import_ducking_slider")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Run Full Auto Pipeline Immediately", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "ASR → Speaker Diarization → ${settings.primaryModel} Translation → Khmer TTS → Sync",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoStartPipeline,
                                onCheckedChange = { autoStartPipeline = it },
                                modifier = Modifier.testTag("import_autostart_switch")
                            )
                        }
                    }
                }

                if (importState is ActionUiState.Error) {
                    Text(
                        text = (importState as ActionUiState.Error).message,
                        color = RoseAlert,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        viewModel.importVideoAndStart(
                            uri = uri,
                            customTitle = projectTitle,
                            duckingDb = duckingDb,
                            autoStartPipeline = autoStartPipeline,
                            onCreated = onProjectCreated
                        )
                    },
                    enabled = selectedUri != null && importState !is ActionUiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KhmerGold,
                        contentColor = Color(0xFF070A12)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_dubbing_pipeline_btn")
                ) {
                    if (importState is ActionUiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text((importState as ActionUiState.Loading).message)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import & Start AI Khmer Dubbing", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
