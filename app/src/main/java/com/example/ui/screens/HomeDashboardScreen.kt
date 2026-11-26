package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DubbingProjectEntity
import com.example.data.model.KeyStatus
import com.example.data.model.PipelineStage
import com.example.ui.DubberViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimecode
import com.example.ui.components.stageColor
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerDialogueStyle
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import java.io.File

@Composable
fun HomeDashboardScreen(
    viewModel: DubberViewModel,
    onImportClick: () -> Unit,
    onManageKeysClick: () -> Unit,
    onOpenProject: (Long, PipelineStepDestination) -> Unit
) {
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    val keys by viewModel.allKeys.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeKeyCount = keys.count { it.isEnabled && it.status == KeyStatus.ACTIVE.name }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroStudioCard(
                    activeKeyCount = activeKeyCount,
                    totalKeyCount = keys.size,
                    primaryModel = settings.primaryModel,
                    onImportClick = onImportClick,
                    onManageKeysClick = onManageKeysClick
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CapabilityMetricCard(
                        icon = Icons.Default.AutoAwesome,
                        title = "Auto ASR + Diarize",
                        subtitle = "Multi-speaker AI",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityMetricCard(
                        icon = Icons.Default.Translate,
                        title = "Natural Khmer",
                        subtitle = "Storyline context",
                        accent = KhmerGold,
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityMetricCard(
                        icon = Icons.Default.GraphicEq,
                        title = "Duck & Sync",
                        subtitle = "SFX preserved",
                        accent = EmeraldSync,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dubbing Studio Projects",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "${projects.size} project(s) on device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier.testTag("dashboard_new_project_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Video")
                    }
                }
            }

            if (projects.isEmpty()) {
                item {
                    EmptyStudioCard(
                        hasApiKeys = keys.isNotEmpty(),
                        onImportClick = onImportClick,
                        onManageKeysClick = onManageKeysClick
                    )
                }
            } else {
                items(projects, key = { it.id }) { project ->
                    ProjectStudioCard(
                        project = project,
                        onOpen = {
                            val stage = runCatching { PipelineStage.valueOf(project.stage) }
                                .getOrDefault(PipelineStage.IMPORTED)
                            val targetStep = when (stage) {
                                PipelineStage.READY, PipelineStage.EXPORTED -> PipelineStepDestination.PREVIEW
                                else -> PipelineStepDestination.ANALYSIS
                            }
                            onOpenProject(project.id, targetStep)
                        },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStudioCard(
    activeKeyCount: Int,
    totalKeyCount: Int,
    primaryModel: String,
    onImportClick: () -> Unit,
    onManageKeysClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero),
                contentDescription = "AI Khmer Dubber Pro studio artwork",
                contentScale = ContentScale.Crop,
                alpha = 0.42f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xCC070A12),
                                Color(0xAA0D121F),
                                Color(0xEE070A12)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = if (activeKeyCount > 0) "$activeKeyCount/$totalKeyCount Custom Keys" else "Firebase AI Ready",
                        color = EmeraldSync,
                        icon = Icons.Default.Key,
                        modifier = Modifier
                            .clickable(onClick = onManageKeysClick)
                            .testTag("dashboard_key_status_pill")
                    )
                    StatusPill(
                        label = primaryModel,
                        color = ElectricCyan
                    )
                }

                Text(
                    text = "AI Khmer Dubber Pro",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Text(
                    text = "បកប្រែ និងបញ្ចូលសំឡេងខ្មែរដោយស្វ័យប្រវត្តិ — Automatic ASR, character voice matching, natural Khmer translation & MP4 export.",
                    style = KhmerDialogueStyle.copy(fontSize = 13.5.sp),
                    color = Color(0xFFD6E0F0),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Button(
                        onClick = onImportClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KhmerGold,
                            contentColor = Color(0xFF070A12)
                        ),
                        modifier = Modifier.testTag("hero_import_video_button")
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import & Dub Video", fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = onManageKeysClick,
                        modifier = Modifier.testTag("hero_manage_keys_button")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Keys")
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityMetricCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EmptyStudioCard(
    hasApiKeys: Boolean,
    onImportClick: () -> Unit,
    onManageKeysClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_studio),
                contentDescription = "Empty dubbing studio illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Text(
                text = "Ready for Your First Video",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Powered by Firebase AI Logic (no API key required) plus optional multi-key failover. Pick any video in any language to automatically transcribe, profile speakers, translate into natural Khmer, and export a dubbed MP4.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier.testTag("empty_import_video_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import Video")
                }
                if (!hasApiKeys) {
                    FilledTonalButton(
                        onClick = onManageKeysClick,
                        modifier = Modifier.testTag("empty_add_key_btn")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Custom Keys")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectStudioCard(
    project: DubbingProjectEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val stage = runCatching { PipelineStage.valueOf(project.stage) }.getOrDefault(PipelineStage.IMPORTED)
    val badgeColor = stageColor(stage)

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!project.thumbnailPath.isNullOrBlank() && File(project.thumbnailPath).exists()) {
                    AsyncImage(
                        model = File(project.thumbnailPath),
                        contentDescription = project.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xBB000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatTimecode(project.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(label = stage.label, color = badgeColor)
                    if (project.detectedLanguagesCsv.isNotBlank()) {
                        Text(
                            text = project.detectedLanguagesCsv,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (stage != PipelineStage.READY && stage != PipelineStage.EXPORTED && stage != PipelineStage.IMPORTED && stage != PipelineStage.FAILED) {
                    LinearProgressIndicator(
                        progress = { project.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = badgeColor
                    )
                } else {
                    Text(
                        text = project.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (stage == PipelineStage.FAILED) RoseAlert else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = onOpen, modifier = Modifier.testTag("open_project_${project.id}")) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Open project", tint = KhmerGold)
                }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_project_${project.id}")) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete project", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
