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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ApiKeyEntity
import com.example.data.model.KeyStatus
import com.example.ui.ActionUiState
import com.example.ui.DubberViewModel
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioTopBar
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSync
import com.example.ui.theme.KhmerGold
import com.example.ui.theme.RoseAlert

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiKeyManagementScreen(viewModel: DubberViewModel) {
    val keys by viewModel.allKeys.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val keyTestState by viewModel.keyTestState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<ApiKeyEntity?>(null) }
    var customModelInput by remember(settings.primaryModel) { mutableStateOf(settings.primaryModel) }

    val modelPresets = listOf(
        "gemini-3.8-flash",
        "gemini-3.5-flash-lite",
        "gemini-3.1-flash-preview"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(
            title = "Gemini API Key Pool",
            subtitle = "Android Keystore AES-256 + automatic failover"
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldSync)
                                    Text("Primary Translation & ASR Model", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text(
                                "Built-in Firebase AI Logic (gemini-3.8-flash) works automatically with no API key required. You can also add custom Gemini API keys below for multi-key quota rotation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                modelPresets.forEach { preset ->
                                    FilterChip(
                                        selected = settings.primaryModel == preset,
                                        onClick = {
                                            customModelInput = preset
                                            viewModel.updateSettings { it.copy(primaryModel = preset) }
                                        },
                                        label = { Text(preset) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { customModelInput = it },
                                    label = { Text("Active Model ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.updateSettings { it.copy(primaryModel = customModelInput.trim()) }
                                    }
                                ) {
                                    Text("Apply")
                                }
                            }
                        }
                    }
                }

                item {
                    when (val state = keyTestState) {
                        is ActionUiState.Loading -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(state.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is ActionUiState.Success -> {
                            StatusPill(state.message, EmeraldSync, Icons.Default.CheckCircle)
                        }
                        is ActionUiState.Error -> {
                            Text(state.message, color = RoseAlert, style = MaterialTheme.typography.bodySmall)
                        }
                        ActionUiState.Idle -> Unit
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configured Keys (${keys.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = {
                                viewModel.resetKeyTestState()
                                showAddDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = KhmerGold, contentColor = Color(0xFF070A12)),
                            modifier = Modifier.testTag("add_gemini_key_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add API Key", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (keys.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, tint = EmeraldSync)
                                Text("Using Built-In Firebase AI Logic (No Key Needed)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Your app is already connected to Gemini via Firebase AI Logic & App Check. Optionally add extra Gemini API keys from Google AI Studio below (encrypted via Android Keystore) for dedicated quota pooling.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(keys, key = { it.id }) { keyItem ->
                        val status = runCatching { KeyStatus.valueOf(keyItem.status) }.getOrDefault(KeyStatus.ACTIVE)
                        val statusColor = when (status) {
                            KeyStatus.ACTIVE -> EmeraldSync
                            KeyStatus.RATE_LIMITED -> AmberWarn
                            KeyStatus.QUOTA_EXCEEDED, KeyStatus.ERROR -> RoseAlert
                            KeyStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(keyItem.label, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Priority #${keyItem.priorityOrder + 1} • ${keyItem.maskedPreview}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan
                                        )
                                    }
                                    StatusPill(status.displayName, statusColor)
                                }

                                Text(
                                    text = "Requests: ${keyItem.requestsCount} • Tokens: ${keyItem.tokensUsed} • Errors: ${keyItem.failureCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!keyItem.lastError.isNullOrBlank()) {
                                    Text(
                                        text = "Last error: ${keyItem.lastError}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = RoseAlert
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FilledTonalButton(onClick = { viewModel.testExistingKey(keyItem) }) {
                                            Text("Test")
                                        }
                                        if (status != KeyStatus.ACTIVE && keyItem.isEnabled) {
                                            FilledTonalButton(onClick = { viewModel.resetKeyStatus(keyItem.id) }) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Reset")
                                            }
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { editingKey = keyItem }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit key")
                                        }
                                        IconButton(onClick = { viewModel.deleteApiKey(keyItem.id) }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete key", tint = RoseAlert)
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

    if (showAddDialog) {
        ApiKeyEditorDialog(
            title = "Add Gemini API Key",
            initialLabel = "Gemini Key #${keys.size + 1}",
            initialPriority = keys.size,
            initialEnabled = true,
            isEditing = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { label, rawKey, priority, _, validate ->
                viewModel.addApiKey(label, rawKey, priority, validate)
                showAddDialog = false
            }
        )
    }

    editingKey?.let { existing ->
        ApiKeyEditorDialog(
            title = "Edit ${existing.label}",
            initialLabel = existing.label,
            initialPriority = existing.priorityOrder,
            initialEnabled = existing.isEnabled,
            isEditing = true,
            onDismiss = { editingKey = null },
            onConfirm = { label, rawKey, priority, enabled, _ ->
                viewModel.updateApiKey(existing.id, label, rawKey.takeIf { it.isNotBlank() }, priority, enabled)
                editingKey = null
            }
        )
    }
}

@Composable
private fun ApiKeyEditorDialog(
    title: String,
    initialLabel: String,
    initialPriority: Int,
    initialEnabled: Boolean,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, Boolean, Boolean) -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var rawKey by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(initialPriority) }
    var enabled by remember { mutableStateOf(initialEnabled) }
    var validateBeforeSave by remember { mutableStateOf(!isEditing) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Key Label (e.g., Studio Primary)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawKey,
                    onValueChange = { rawKey = it },
                    label = { Text(if (isEditing) "New API Key (leave blank to keep)" else "Gemini API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_api_key_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Failover Priority Order: #${priority + 1}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { if (priority > 0) priority-- }) { Text("-") }
                        TextButton(onClick = { priority++ }) { Text("+") }
                    }
                }
                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Key Enabled", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Verify key online before saving", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = validateBeforeSave, onCheckedChange = { validateBeforeSave = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label, rawKey, priority, enabled, validateBeforeSave) },
                enabled = isEditing || rawKey.trim().length >= 16,
                modifier = Modifier.testTag("dialog_save_key_btn")
            ) {
                Text("Save Securely")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
