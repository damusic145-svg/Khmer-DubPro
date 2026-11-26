package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.AudioTrackMode
import com.example.data.model.PipelineStage
import com.example.ui.DubberViewModel
import com.example.ui.components.PipelineStepStrip
import com.example.ui.components.StudioTopBar
import com.example.ui.components.formatTimecode
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.KhmerSubtitleStyle
import kotlinx.coroutines.delay
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPreviewScreen(
    viewModel: DubberViewModel,
    onBack: () -> Unit,
    onSelectStep: (PipelineStepDestination) -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val segments by viewModel.activeSegments.collectAsStateWithLifecycle()

    val currentProj = project
    val stage = currentProj?.let { runCatching { PipelineStage.valueOf(it.stage) }.getOrNull() }
        ?: PipelineStage.IMPORTED

    var audioMode by remember { mutableStateOf(AudioTrackMode.KHMER_DUBBED) }
    var showBilingualSubs by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(currentProj?.durationMs ?: 0L) }

    val videoFile = currentProj?.localVideoPath?.let(::File)?.takeIf { it.exists() }
    val dubbedAudioFile = currentProj?.mixedAudioPath?.let(::File)?.takeIf { it.exists() }

    val videoPlayer = remember(videoFile?.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            if (videoFile != null) {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
                prepare()
            }
        }
    }

    val dubPlayer = remember(dubbedAudioFile?.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            if (dubbedAudioFile != null) {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(dubbedAudioFile)))
                prepare()
            }
        }
    }

    DisposableEffect(videoPlayer, dubPlayer) {
        onDispose {
            videoPlayer.release()
            dubPlayer.release()
        }
    }

    LaunchedEffect(audioMode, dubbedAudioFile) {
        when (audioMode) {
            AudioTrackMode.KHMER_DUBBED -> {
                if (dubbedAudioFile != null) {
                    videoPlayer.volume = 0f
                    dubPlayer.volume = 1f
                } else {
                    videoPlayer.volume = 1f
                }
            }
            AudioTrackMode.ORIGINAL_ONLY -> {
                videoPlayer.volume = 1f
                dubPlayer.volume = 0f
            }
            AudioTrackMode.BILINGUAL_DUAL -> {
                videoPlayer.volume = 0.45f
                dubPlayer.volume = 1f
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (true) {
            currentPosMs = videoPlayer.currentPosition
            if (videoPlayer.duration > 0L) {
                durationMs = videoPlayer.duration
            }
            if (!videoPlayer.isPlaying && isPlaying) {
                isPlaying = false
                dubPlayer.pause()
            }
            delay(150L)
        }
    }

    val activeCue = remember(currentPosMs, segments) {
        segments.firstOrNull { seg ->
            val s = (seg.startMs + seg.syncOffsetMs).coerceAtLeast(0L)
            val e = (seg.endMs + seg.syncOffsetMs).coerceAtLeast(s + 200L)
            currentPosMs in s..e
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Video Preview",
            subtitle = currentProj?.title,
            onBack = onBack
        )
        PipelineStepStrip(
            currentStep = PipelineStepDestination.PREVIEW,
            projectStage = stage,
            onSelectStep = onSelectStep
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                ) {
                    if (videoFile != null) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    player = videoPlayer
                                }
                            },
                            update = { pv -> pv.player = videoPlayer },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Video source unavailable", color = Color.White)
                        }
                    }

                    if (showBilingualSubs && activeCue != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xCC070A12))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (activeCue.khmerText.isNotBlank()) {
                                Text(
                                    text = activeCue.khmerText,
                                    style = KhmerSubtitleStyle,
                                    color = KhmerGold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Text(
                                text = activeCue.originalText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (isPlaying) {
                                        videoPlayer.pause()
                                        dubPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        val pos = videoPlayer.currentPosition
                                        if (dubbedAudioFile != null) dubPlayer.seekTo(pos)
                                        videoPlayer.play()
                                        if (dubbedAudioFile != null) dubPlayer.play()
                                        isPlaying = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = KhmerGold, contentColor = Color(0xFF070A12)),
                                modifier = Modifier.testTag("preview_play_pause_btn")
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play"
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (isPlaying) "Pause" else "Play Preview", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${formatTimecode(currentPosMs)} / ${formatTimecode(durationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricCyan
                            )
                        }

                        Slider(
                            value = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                val target = (frac * durationMs).toLong()
                                videoPlayer.seekTo(target)
                                if (dubbedAudioFile != null) dubPlayer.seekTo(target)
                                currentPosMs = target
                            }
                        )

                        Text("Audio Track Monitor", style = MaterialTheme.typography.titleSmall)
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                Text("Bilingual Subtitles Overlay", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(checked = showBilingualSubs, onCheckedChange = { showBilingualSubs = it })
                        }
                    }
                }

                Button(
                    onClick = { onSelectStep(PipelineStepDestination.EXPORT) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Proceed to Export MP4")
                }
            }
        }
    }
}
