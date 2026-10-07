package com.example.aragon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.ui.theme.AmoledAccent
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledBorderActive
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledError
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    selectedModel: String,
    recentTasks: List<Task>,
    activeTask: Task?,
    onLaunchTask: (request: String, mode: AgentMode) -> Unit,
    onOpenTaskDetail: (taskId: String) -> Unit,
    onRunBenchmark: () -> Unit
) {
    var promptInput by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(AgentMode.AGENT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBg)
            .padding(horizontal = 16.dp)
            .testTag("home_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                Spacer(modifier = Modifier.height(14.dp))

                // Minimalist Hero Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = AmoledInteractive,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AmoledBorder),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = AmoledTextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "AUTONOMOUS COMPUTER AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmoledTextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aragon Runtime",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmoledTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Isolated Linux execution environment • OpenSandbox microVM backend • Deterministic verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmoledTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Active Task Banner if running
            if (activeTask != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmoledCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmoledAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTaskDetail(activeTask.id) }
                            .testTag("active_task_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AmoledSuccess)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE SESSION • ${activeTask.status.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AmoledAccent
                                )
                                Text(
                                    text = activeTask.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AmoledTextPrimary,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "View",
                                tint = AmoledTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Quick Directives & Benchmarks
            item {
                Text(
                    text = "Directives & Benchmarks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip("📄 Verified DOCX Report") {
                        promptInput = "Create a verified DOCX administrative report with executive summary, tables, and system performance metrics."
                    }
                    QuickChip("📝 hello.txt File") {
                        promptInput = "Create hello.txt containing Hello Aragon in /workspace."
                    }
                    QuickChip("📦 OpenSandbox Integration Test") {
                        promptInput = "Test OpenSandbox container runtime, execute Python script in sandbox, and generate report."
                    }
                    QuickChip("⚡ Run 7-Step Tool Benchmark") {
                        onRunBenchmark()
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Recent Tasks Section
            if (recentTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Sessions",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(recentTasks.take(5)) { task ->
                    TaskSummaryRow(task = task, onClick = { onOpenTaskDetail(task.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Bottom Minimalist Composer
        Surface(
            color = AmoledCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AmoledBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Mode Toggle Row (Agent / Plan / Chat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == AgentMode.AGENT,
                        onClick = { selectedMode = AgentMode.AGENT },
                        label = { Text("Autonomous", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledBg,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (selectedMode == AgentMode.AGENT) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("mode_chip_agent")
                    )
                    FilterChip(
                        selected = selectedMode == AgentMode.PLAN,
                        onClick = { selectedMode = AgentMode.PLAN },
                        label = { Text("Plan First", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledBg,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (selectedMode == AgentMode.PLAN) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("mode_chip_plan")
                    )
                    FilterChip(
                        selected = selectedMode == AgentMode.CHAT,
                        onClick = { selectedMode = AgentMode.CHAT },
                        label = { Text("Chat", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledBg,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (selectedMode == AgentMode.CHAT) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("mode_chip_chat")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input Field & Send Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text("What objective should Aragon execute?", color = AmoledTextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmoledTextPrimary,
                            unfocusedBorderColor = AmoledBorder,
                            focusedContainerColor = AmoledElevated,
                            unfocusedContainerColor = AmoledElevated,
                            focusedTextColor = AmoledTextPrimary,
                            unfocusedTextColor = AmoledTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("composer_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (promptInput.isNotBlank()) {
                                onLaunchTask(promptInput.trim(), selectedMode)
                                promptInput = ""
                            }
                        },
                        enabled = promptInput.isNotBlank(),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (promptInput.isNotBlank()) AmoledTextPrimary else AmoledInteractive
                            )
                            .testTag("send_task_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (promptInput.isNotBlank()) AmoledBg else AmoledTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Surface(
        color = AmoledCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = AmoledTextPrimary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TaskSummaryRow(task: Task, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (task.status) {
                TaskStatus.COMPLETED -> AmoledSuccess
                TaskStatus.EXECUTING, TaskStatus.PLANNING -> AmoledAccent
                TaskStatus.FAILED -> AmoledError
                else -> AmoledTextMuted
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AmoledTextPrimary,
                    maxLines = 1
                )
                Text(
                    text = "${task.status.name} • Iteration ${task.iteration} • ${task.selectedModel.substringAfter("/")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = AmoledTextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = AmoledTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
