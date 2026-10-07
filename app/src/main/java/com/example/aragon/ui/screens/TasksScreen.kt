package com.example.aragon.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.ui.theme.AragonObsidianBg
import com.example.ui.theme.AragonSuccess
import com.example.ui.theme.AragonSurface
import com.example.ui.theme.AragonSurfaceVariant

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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("new_task_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Task")
            }
        },
        containerColor = AragonObsidianBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("tasks_screen")
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All", "ACTIVE" to "Active", "COMPLETED" to "Completed", "FAILED" to "Failed").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No tasks found in this view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        colors = CardDefaults.cardColors(containerColor = AragonSurface),
        shape = RoundedCornerShape(12.dp),
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
                TaskStatus.COMPLETED -> AragonSuccess
                TaskStatus.EXECUTING, TaskStatus.PLANNING -> MaterialTheme.colorScheme.primary
                TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                TaskStatus.WAITING_FOR_USER -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${task.status} • Iteration ${task.iteration} • ${task.selectedModel.substringAfter("/")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Task",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
