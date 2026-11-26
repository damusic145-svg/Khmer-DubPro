package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PipelineStage
import com.example.ui.navigation.PipelineStepDestination
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SpeakerColors
import com.example.ui.theme.TimecodeMonoStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun PipelineStepStrip(
    currentStep: PipelineStepDestination,
    projectStage: PipelineStage,
    onSelectStep: (PipelineStepDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = PipelineStepDestination.entries
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEachIndexed { idx, step ->
            val isSelected = step == currentStep
            val isCompleted = projectStage.stepIndex >= 7 || projectStage.stepIndex > idx
            val bgColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
            val borderColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isCompleted -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                modifier = Modifier
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectStep(step) }
                    .testTag("pipeline_step_${step.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCompleted && !isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Step completed",
                            tint = EmeraldSync,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = step.shortTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun AudioWaveformCanvas(
    bars: List<Float>,
    progressFraction: Float = 0f,
    highlightRanges: List<Triple<Float, Float, Int>> = emptyList(),
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(84.dp)
) {
    val animProgress by animateFloatAsState(targetValue = progressFraction.coerceIn(0f, 1f), label = "wave_prog")
    val safeBars = if (bars.isEmpty()) List(60) { 0.12f } else bars
    val activeColor = KhmerGold
    val futureColor = ElectricCyan.copy(alpha = 0.42f)
    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(trackBg)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        val count = safeBars.size
        val barWidth = (size.width / count) * 0.62f
        val gap = (size.width / count) * 0.38f

        // Draw speaker dialogue region highlights behind waveform
        highlightRanges.forEach { (startFrac, endFrac, colorIdx) ->
            val x1 = (startFrac.coerceIn(0f, 1f)) * size.width
            val x2 = (endFrac.coerceIn(startFrac, 1f)) * size.width
            val c = SpeakerColors[colorIdx % SpeakerColors.size]
            drawRoundRect(
                color = c.copy(alpha = 0.18f),
                topLeft = Offset(x1, 0f),
                size = Size((x2 - x1).coerceAtLeast(3f), size.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        safeBars.forEachIndexed { idx, amp ->
            val x = idx * (barWidth + gap)
            val barH = (amp.coerceIn(0.08f, 1f)) * size.height
            val topY = (size.height - barH) / 2f
            val frac = idx.toFloat() / count.toFloat()
            val color = if (frac <= animProgress) activeColor else futureColor
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f))),
                topLeft = Offset(x, topY),
                size = Size(barWidth.coerceAtLeast(2f), barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        if (animProgress > 0f) {
            val playX = animProgress * size.width
            drawLine(
                color = Color.White,
                start = Offset(playX, 0f),
                end = Offset(playX, size.height),
                strokeWidth = 3f
            )
        }
    }
}

@Composable
fun TimecodeChip(startMs: Long, endMs: Long? = null, modifier: Modifier = Modifier) {
    val text = if (endMs != null) {
        "${formatTimecode(startMs)} → ${formatTimecode(endMs)}"
    } else {
        formatTimecode(startMs)
    }
    Text(
        text = text,
        style = TimecodeMonoStyle,
        color = ElectricCyan,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ElectricCyan.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

fun formatTimecode(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSec = clamped / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    val tenths = (clamped % 1000L) / 100L
    return String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
}

fun stageColor(stage: PipelineStage): Color = when (stage) {
    PipelineStage.READY, PipelineStage.EXPORTED -> EmeraldSync
    PipelineStage.FAILED -> RoseAlert
    PipelineStage.IMPORTED -> ElectricCyan
    else -> KhmerGold
}
