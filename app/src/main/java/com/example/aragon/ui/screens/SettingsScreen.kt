package com.example.aragon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.domain.model.AutonomyLevel
import com.example.ui.theme.AragonObsidianBg
import com.example.ui.theme.AragonSuccess
import com.example.ui.theme.AragonSurface
import com.example.ui.theme.AragonSurfaceVariant

@Composable
fun SettingsScreen(
    currentApiKey: String,
    currentEndpoint: String,
    selectedModel: String,
    autonomyLevel: AutonomyLevel,
    maxIterations: Int,
    temperature: Float,
    onSaveApiKey: (String) -> Unit,
    onSaveEndpoint: (String) -> Unit,
    onTestConnection: suspend (apiKey: String, endpoint: String) -> Result<String>,
    onSelectModelClick: () -> Unit,
    onSaveAutonomyLevel: (AutonomyLevel) -> Unit,
    onSaveMaxIterations: (Int) -> Unit,
    onSaveTemperature: (Float) -> Unit,
    onRunBenchmark: () -> Unit,
    onRunHealthCheck: () -> Unit
) {
    var apiKeyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var endpointInput by remember(currentEndpoint) { mutableStateOf(currentEndpoint) }
    var showPassword by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isConnectionError by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var currentAutonomy by remember(autonomyLevel) { mutableStateOf(autonomyLevel) }
    var currentIterations by remember(maxIterations) { mutableIntStateOf(maxIterations) }
    var currentTemp by remember(temperature) { mutableFloatStateOf(temperature) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AragonObsidianBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .testTag("settings_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Settings & Infrastructure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Configure NVIDIA NIM inference, local Ubuntu computer, and autonomy guardrails.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AI Provider: NVIDIA NIM Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AragonSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Provider: NVIDIA NIM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Endpoint Field
                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    label = { Text("NVIDIA NIM Endpoint URL") },
                    placeholder = { Text("https://integrate.api.nvidia.com/v1") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { endpointInput = PreferencesManager.DEFAULT_ENDPOINT }) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_endpoint_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // API Key Field
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("NVIDIA API Key") },
                    placeholder = { Text("nvapi-...") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val trimmedKey = apiKeyInput.trim()
                            val trimmedEndpoint = endpointInput.trim()
                            onSaveApiKey(trimmedKey)
                            onSaveEndpoint(trimmedEndpoint)
                            connectionStatus = "Configuration saved successfully."
                            isConnectionError = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_key_btn")
                    ) {
                        Text("Save Config")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            val trimmedKey = apiKeyInput.trim()
                            val trimmedEndpoint = endpointInput.trim()
                            isTestingConnection = true
                            connectionStatus = "Testing connection to endpoint..."
                            isConnectionError = false

                            coroutineScope.launch {
                                val result = onTestConnection(trimmedKey, trimmedEndpoint)
                                isTestingConnection = false
                                result.onSuccess { msg ->
                                    connectionStatus = msg
                                    isConnectionError = false
                                }.onFailure { error ->
                                    connectionStatus = error.message ?: "Connection failed with unknown error"
                                    isConnectionError = true
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("test_connection_btn")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test Connection")
                    }
                }

                if (connectionStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = connectionStatus!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isConnectionError) MaterialTheme.colorScheme.error else AragonSuccess
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // Model Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Selected Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(selectedModel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                    }
                    Button(onClick = onSelectModelClick, shape = RoundedCornerShape(8.dp)) {
                        Text("Change Model")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Agent Autonomy & Safety Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AragonSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agent Autonomy Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AutonomyLevel.SAFE to "Safe (Read-only)", AutonomyLevel.NORMAL to "Normal", AutonomyLevel.FULL to "Full Autonomy").forEach { (lvl, title) ->
                        FilterChip(
                            selected = currentAutonomy == lvl,
                            onClick = {
                                currentAutonomy = lvl
                                onSaveAutonomyLevel(lvl)
                            },
                            label = { Text(title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Max Iterations per Task: $currentIterations", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = currentIterations.toFloat(),
                    onValueChange = {
                        currentIterations = it.toInt()
                        onSaveMaxIterations(currentIterations)
                    },
                    valueRange = 5f..50f,
                    steps = 9
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(String.format("Temperature: %.2f", currentTemp), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = currentTemp,
                    onValueChange = {
                        currentTemp = it
                        onSaveTemperature(currentTemp)
                    },
                    valueRange = 0.0f..1.0f,
                    steps = 10
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Local Linux Computer & Benchmark Suite Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AragonSurface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = AragonSuccess)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Local Linux Computer & Tests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Runs directly on Android device in userspace. Filesystem root at /workspace. No cloud server required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRunHealthCheck,
                        modifier = Modifier.weight(1f).testTag("settings_health_check_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Health Check")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onRunBenchmark,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1.3f).testTag("settings_run_benchmark_btn")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Benchmarks")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
