package com.example.aragon.ui.screens

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.ui.theme.AragonObsidianBg
import com.example.ui.theme.AragonSuccess
import com.example.ui.theme.AragonSurface
import com.example.ui.theme.AragonSurfaceVariant

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
            .background(AragonObsidianBg)
            .padding(horizontal = 16.dp)
            .testTag("home_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))

                // Hero Greeting Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = AragonSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Autonomous Computer Agent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Aragon is ready.",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connected to local Linux container • Persistent workspace at /workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Active Task Banner if running
            if (activeTask != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTaskDetail(activeTask.id) }
                            .testTag("active_task_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(AragonSuccess)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE TASK • ${activeTask.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = activeTask.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "View",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Quick Prompt / Benchmark Chips
            item {
                Text(
                    text = "Quick Directives & Benchmarks",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickChip("📄 Create Verified DOCX Report") {
                        promptInput = "Create a verified DOCX administrative report with executive summary, tables, and system performance metrics."
                    }
                    QuickChip("📝 Create hello.txt") {
                        promptInput = "Create hello.txt containing Hello Aragon in /workspace."
                    }
                    QuickChip("🌐 Generate Local Web App") {
                        promptInput = "Build a local responsive web application project inside /workspace/webapp with HTML, CSS, and JavaScript."
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
                        text = "Recent Task History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(recentTasks.take(5)) { task ->
                    TaskSummaryRow(task = task, onClick = { onOpenTaskDetail(task.id) })
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Bottom Large Composer
        Surface(
            color = AragonSurface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Mode Toggle Row (Agent / Plan / Chat)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == AgentMode.AGENT,
                        onClick = { selectedMode = AgentMode.AGENT },
                        label = { Text("Agent (Autonomous)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("mode_chip_agent")
                    )
                    FilterChip(
                        selected = selectedMode == AgentMode.PLAN,
                        onClick = { selectedMode = AgentMode.PLAN },
                        label = { Text("Plan (Review First)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("mode_chip_plan")
                    )
                    FilterChip(
                        selected = selectedMode == AgentMode.CHAT,
                        onClick = { selectedMode = AgentMode.CHAT },
                        label = { Text("Chat") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
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
                        placeholder = { Text("What can I do for you?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = AragonSurfaceVariant,
                            unfocusedContainerColor = AragonSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
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
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (promptInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("send_task_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (promptInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
        color = AragonSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TaskSummaryRow(task: Task, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AragonSurfaceVariant),
        shape = RoundedCornerShape(10.dp),
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
                TaskStatus.COMPLETED -> AragonSuccess
                TaskStatus.EXECUTING, TaskStatus.PLANNING -> MaterialTheme.colorScheme.primary
                TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${task.status} • Iteration ${task.iteration}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
