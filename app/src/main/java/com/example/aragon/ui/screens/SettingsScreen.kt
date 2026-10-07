package com.example.aragon.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.ExecutionBackend
import com.example.ui.theme.AmoledAccent
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledError
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    currentApiKey: String,
    currentEndpoint: String,
    selectedModel: String,
    autonomyLevel: AutonomyLevel,
    maxIterations: Int,
    temperature: Float,
    executionBackend: ExecutionBackend = ExecutionBackend.LOCAL_COMPUTER,
    openSandboxServerUrl: String = "http://10.0.2.2:8080/v1",
    openSandboxApiKey: String = "",
    openSandboxImage: String = "opensandbox/python:3.12",
    openSandboxActiveId: String? = null,
    onSaveApiKey: (String) -> Unit,
    onSaveEndpoint: (String) -> Unit,
    onTestConnection: suspend (apiKey: String, endpoint: String) -> Result<String>,
    onSelectModelClick: () -> Unit,
    onSaveAutonomyLevel: (AutonomyLevel) -> Unit,
    onSaveMaxIterations: (Int) -> Unit,
    onSaveTemperature: (Float) -> Unit,
    onSetExecutionBackend: (ExecutionBackend) -> Unit = {},
    onSaveOpenSandboxSettings: (url: String, apiKey: String, image: String) -> Unit = { _, _, _ -> },
    onTestOpenSandboxConnection: suspend (url: String, apiKey: String) -> Result<String> = { _, _ -> Result.success("OK") },
    onSpawnOpenSandbox: suspend (customImage: String?) -> Result<Any> = { Result.success(Unit) },
    onTerminateOpenSandbox: suspend () -> Result<Boolean> = { Result.success(true) },
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

    // OpenSandbox UI States
    var currentBackend by remember(executionBackend) { mutableStateOf(executionBackend) }
    var sandboxUrlInput by remember(openSandboxServerUrl) { mutableStateOf(openSandboxServerUrl) }
    var sandboxApiKeyInput by remember(openSandboxApiKey) { mutableStateOf(openSandboxApiKey) }
    var sandboxImageInput by remember(openSandboxImage) { mutableStateOf(openSandboxImage) }
    var showSandboxApiKey by remember { mutableStateOf(false) }
    var sandboxTestStatus by remember { mutableStateOf<String?>(null) }
    var isSandboxTestError by remember { mutableStateOf(false) }
    var isTestingSandbox by remember { mutableStateOf(false) }
    var isSpawningSandbox by remember { mutableStateOf(false) }
    var isTerminatingSandbox by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBg)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
            .testTag("settings_screen")
    ) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Settings & Infrastructure",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = AmoledTextPrimary
        )
        Text(
            text = "Configure NVIDIA NIM inference, OpenSandbox microVM backend, and autonomous loop limits.",
            style = MaterialTheme.typography.bodySmall,
            color = AmoledTextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // AI Provider: NVIDIA NIM Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = AmoledTextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Provider: NVIDIA NIM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("NVIDIA API Key", color = AmoledTextSecondary) },
                    placeholder = { Text("nvapi-...", color = AmoledTextMuted) },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide API key" else "Show API key",
                                tint = AmoledTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = endpointInput,
                    onValueChange = { endpointInput = it },
                    label = { Text("API Endpoint URL", color = AmoledTextSecondary) },
                    placeholder = { Text("https://integrate.api.nvidia.com/v1", color = AmoledTextMuted) },
                    modifier = Modifier.fillMaxWidth().testTag("endpoint_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val trimmedKey = apiKeyInput.trim()
                            val trimmedEndpoint = endpointInput.trim()
                            onSaveApiKey(trimmedKey)
                            onSaveEndpoint(trimmedEndpoint)
                            connectionStatus = "Settings saved successfully."
                            isConnectionError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("save_api_key_btn")
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val trimmedKey = apiKeyInput.trim()
                            val trimmedEndpoint = endpointInput.trim()
                            isTestingConnection = true
                            connectionStatus = "Testing connection..."
                            isConnectionError = false

                            coroutineScope.launch {
                                val result = onTestConnection(trimmedKey, trimmedEndpoint)
                                isTestingConnection = false
                                result.onSuccess { msg ->
                                    connectionStatus = msg
                                    isConnectionError = false
                                }.onFailure { error ->
                                    connectionStatus = error.message ?: "Connection failed"
                                    isConnectionError = true
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f).testTag("test_connection_btn")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmoledTextPrimary, strokeWidth = 2.dp)
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
                        color = if (isConnectionError) AmoledError else AmoledSuccess
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = AmoledBorder, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Model Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Selected Model", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
                        Text(selectedModel, style = MaterialTheme.typography.labelSmall, color = AmoledAccent, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = onSelectModelClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledInteractive, contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Change Model")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OpenSandbox Runtime Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = AmoledTextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sandbox Runtime: OpenSandbox",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seamless integration with OpenSandbox microVM container clusters. Two-way workspace file sync and isolated code execution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmoledTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Chips
                Text("Active Execution Backend", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentBackend == ExecutionBackend.LOCAL_COMPUTER,
                        onClick = {
                            currentBackend = ExecutionBackend.LOCAL_COMPUTER
                            onSetExecutionBackend(ExecutionBackend.LOCAL_COMPUTER)
                        },
                        label = { Text("Local Userspace", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledBg,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (currentBackend == ExecutionBackend.LOCAL_COMPUTER) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = currentBackend == ExecutionBackend.OPEN_SANDBOX,
                        onClick = {
                            currentBackend = ExecutionBackend.OPEN_SANDBOX
                            onSetExecutionBackend(ExecutionBackend.OPEN_SANDBOX)
                        },
                        label = { Text("OpenSandbox MicroVM", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledBg,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (currentBackend == ExecutionBackend.OPEN_SANDBOX) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Server URL
                OutlinedTextField(
                    value = sandboxUrlInput,
                    onValueChange = { sandboxUrlInput = it },
                    label = { Text("OpenSandbox Server URL", color = AmoledTextSecondary) },
                    placeholder = { Text("http://10.0.2.2:8080/v1", color = AmoledTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { sandboxUrlInput = "http://10.0.2.2:8080/v1" }) {
                        Text("Emulator (10.0.2.2)", style = MaterialTheme.typography.labelSmall, color = AmoledTextSecondary)
                    }
                    TextButton(onClick = { sandboxUrlInput = "http://localhost:8080/v1" }) {
                        Text("Localhost (8080)", style = MaterialTheme.typography.labelSmall, color = AmoledTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // API Key
                OutlinedTextField(
                    value = sandboxApiKeyInput,
                    onValueChange = { sandboxApiKeyInput = it },
                    label = { Text("OpenSandbox API Key (Optional)", color = AmoledTextSecondary) },
                    placeholder = { Text("OPEN-SANDBOX-API-KEY", color = AmoledTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showSandboxApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSandboxApiKey = !showSandboxApiKey }) {
                            Icon(
                                if (showSandboxApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = AmoledTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Container Image
                OutlinedTextField(
                    value = sandboxImageInput,
                    onValueChange = { sandboxImageInput = it },
                    label = { Text("Container Image / Template", color = AmoledTextSecondary) },
                    placeholder = { Text("opensandbox/python:3.12", color = AmoledTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AmoledTextPrimary,
                        unfocusedTextColor = AmoledTextPrimary,
                        focusedBorderColor = AmoledTextPrimary,
                        unfocusedBorderColor = AmoledBorder,
                        focusedContainerColor = AmoledElevated,
                        unfocusedContainerColor = AmoledElevated
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Save & Test Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val url = sandboxUrlInput.trim()
                            val key = sandboxApiKeyInput.trim()
                            val img = sandboxImageInput.trim()
                            onSaveOpenSandboxSettings(url, key, img)
                            sandboxTestStatus = "OpenSandbox configuration saved."
                            isSandboxTestError = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val url = sandboxUrlInput.trim()
                            val key = sandboxApiKeyInput.trim()
                            isTestingSandbox = true
                            sandboxTestStatus = "Testing OpenSandbox cluster..."
                            isSandboxTestError = false

                            coroutineScope.launch {
                                val result = onTestOpenSandboxConnection(url, key)
                                isTestingSandbox = false
                                result.onSuccess { msg ->
                                    sandboxTestStatus = msg
                                    isSandboxTestError = false
                                }.onFailure { error ->
                                    sandboxTestStatus = error.message ?: "OpenSandbox ping failed"
                                    isSandboxTestError = true
                                }
                            }
                        },
                        enabled = !isTestingSandbox,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        if (isTestingSandbox) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmoledTextPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Test Cluster")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Spawn / Terminate Controls
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            isSpawningSandbox = true
                            coroutineScope.launch {
                                val res = onSpawnOpenSandbox(sandboxImageInput.trim())
                                isSpawningSandbox = false
                                if (res.isSuccess) {
                                    sandboxTestStatus = "Spawned fresh sandbox instance with image ${sandboxImageInput.trim()}"
                                    isSandboxTestError = false
                                } else {
                                    sandboxTestStatus = "Failed to spawn: ${res.exceptionOrNull()?.message}"
                                    isSandboxTestError = true
                                }
                            }
                        },
                        enabled = !isSpawningSandbox,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSpawningSandbox) "Spawning..." else "Spawn MicroVM")
                    }

                    OutlinedButton(
                        onClick = {
                            isTerminatingSandbox = true
                            coroutineScope.launch {
                                onTerminateOpenSandbox()
                                isTerminatingSandbox = false
                                sandboxTestStatus = "Sandbox instance terminated."
                                isSandboxTestError = false
                            }
                        },
                        enabled = !isTerminatingSandbox,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledError),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Terminate")
                    }
                }

                if (openSandboxActiveId != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = AmoledElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmoledSuccess, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Active Sandbox Container", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
                                Text(openSandboxActiveId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = AmoledAccent)
                            }
                        }
                    }
                }

                if (sandboxTestStatus != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = sandboxTestStatus!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSandboxTestError) AmoledError else AmoledSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Agent Autonomy & Safety Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AmoledTextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Agent Autonomy Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
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
                            label = { Text(title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmoledInteractive,
                                selectedLabelColor = AmoledTextPrimary,
                                containerColor = AmoledBg,
                                labelColor = AmoledTextSecondary
                            ),
                            border = BorderStroke(1.dp, if (currentAutonomy == lvl) AmoledTextPrimary else AmoledBorder),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Max Iterations per Task: $currentIterations", style = MaterialTheme.typography.bodyMedium, color = AmoledTextPrimary)
                Slider(
                    value = currentIterations.toFloat(),
                    onValueChange = {
                        currentIterations = it.toInt()
                        onSaveMaxIterations(currentIterations)
                    },
                    valueRange = 5f..50f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = AmoledTextPrimary,
                        activeTrackColor = AmoledTextPrimary,
                        inactiveTrackColor = AmoledBorder
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(String.format("Temperature: %.2f", currentTemp), style = MaterialTheme.typography.bodyMedium, color = AmoledTextPrimary)
                Slider(
                    value = currentTemp,
                    onValueChange = {
                        currentTemp = it
                        onSaveTemperature(currentTemp)
                    },
                    valueRange = 0.0f..1.0f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = AmoledTextPrimary,
                        activeTrackColor = AmoledTextPrimary,
                        inactiveTrackColor = AmoledBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Local Linux Computer & Benchmark Suite Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AmoledCard),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = AmoledTextPrimary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Computer Health & Benchmarks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Inspect execution telemetry across Local Android Container and OpenSandbox microVMs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AmoledTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onRunHealthCheck,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledTextPrimary),
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("settings_health_check_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Health Check")
                    }

                    Button(
                        onClick = onRunBenchmark,
                        colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.3f).testTag("settings_run_benchmark_btn")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Benchmarks", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
