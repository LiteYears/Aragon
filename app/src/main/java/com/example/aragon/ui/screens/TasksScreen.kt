package com.example.aragon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.ui.theme.AmoledAccent
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledError
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary
import com.example.ui.theme.AmoledWarning

@Composable
fun TasksScreen(
    tasks: List<Task>,
    onSelectTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onNewTask: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredTasks = when (selectedFilter) {
        "ACTIVE" -> tasks.filter { it.status.isActive }
        "COMPLETED" -> tasks.filter { it.status == TaskStatus.COMPLETED }
        "FAILED" -> tasks.filter { it.status == TaskStatus.FAILED }
        else -> tasks
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewTask,
                containerColor = AmoledTextPrimary,
                contentColor = AmoledBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("new_task_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Task")
            }
        },
        containerColor = AmoledBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("tasks_screen")
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Autonomous Sessions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = AmoledTextPrimary
            )
            Text(
                text = "Complete history of tasks, tool execution steps, and verification checkpoints.",
                style = MaterialTheme.typography.bodySmall,
                color = AmoledTextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All (${tasks.size})",
                    "ACTIVE" to "Active (${tasks.count { it.status.isActive }})",
                    "COMPLETED" to "Completed (${tasks.count { it.status == TaskStatus.COMPLETED }})",
                    "FAILED" to "Failed (${tasks.count { it.status == TaskStatus.FAILED }})"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoledInteractive,
                            selectedLabelColor = AmoledTextPrimary,
                            containerColor = AmoledCard,
                            labelColor = AmoledTextSecondary
                        ),
                        border = BorderStroke(1.dp, if (selectedFilter == key) AmoledTextPrimary else AmoledBorder),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = AmoledCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(
                            text = "No sessions match the selected filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmoledTextSecondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredTasks) { task ->
                        TaskListItem(
                            task = task,
                            onClick = { onSelectTask(task.id) },
                            onDelete = { onDeleteTask(task.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskListItem(
    task: Task,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("task_row_${task.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusColor = when (task.status) {
                TaskStatus.COMPLETED -> AmoledSuccess
                TaskStatus.EXECUTING, TaskStatus.PLANNING, TaskStatus.OBSERVING -> AmoledAccent
                TaskStatus.FAILED -> AmoledError
                TaskStatus.WAITING_FOR_USER -> AmoledWarning
                else -> AmoledTextMuted
            }

            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
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

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = AmoledTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = AmoledTextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
