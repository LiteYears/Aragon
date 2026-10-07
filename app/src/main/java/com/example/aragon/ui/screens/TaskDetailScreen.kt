package com.example.aragon.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.TimelineEvent
import com.example.aragon.domain.model.TimelineEventType
import com.example.aragon.ui.components.ArtifactCard
import com.example.aragon.ui.components.ArtifactViewerDialog
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
import com.example.ui.theme.AmoledWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    task: Task?,
    planSteps: List<PlanStep>,
    timeline: List<TimelineEvent>,
    artifacts: List<Artifact>,
    onBack: () -> Unit,
    onApprovePlan: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    var viewingArtifact by remember { mutableStateOf<Artifact?>(null) }

    if (task == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBg),
            contentAlignment = Alignment.Center
        ) {
            Text("Task not found", color = AmoledTextSecondary)
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AmoledBg,
                    titleContentColor = AmoledTextPrimary,
                    navigationIconContentColor = AmoledTextPrimary
                ),
                title = {
                    Column {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
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
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("task_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (task.status.isActive) {
                        IconButton(onClick = onPause, modifier = Modifier.testTag("task_pause_btn")) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = AmoledTextPrimary)
                        }
                    } else if (task.status == TaskStatus.PAUSED) {
                        IconButton(onClick = onResume, modifier = Modifier.testTag("task_resume_btn")) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = AmoledSuccess)
                        }
                    }
                    if (!task.status.isTerminal) {
                        IconButton(onClick = onCancel, modifier = Modifier.testTag("task_cancel_btn")) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = AmoledError)
                        }
                    }
                }
            )
        },
        containerColor = AmoledBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("task_detail_content")
        ) {
            // Task Objective & Status Overview
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TASK OBJECTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmoledTextMuted,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = when (task.status) {
                                    TaskStatus.COMPLETED -> AmoledSuccess.copy(alpha = 0.15f)
                                    TaskStatus.FAILED -> AmoledError.copy(alpha = 0.15f)
                                    TaskStatus.EXECUTING, TaskStatus.OBSERVING -> AmoledAccent.copy(alpha = 0.15f)
                                    else -> AmoledInteractive
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    1.dp,
                                    when (task.status) {
                                        TaskStatus.COMPLETED -> AmoledSuccess.copy(alpha = 0.4f)
                                        TaskStatus.FAILED -> AmoledError.copy(alpha = 0.4f)
                                        TaskStatus.EXECUTING, TaskStatus.OBSERVING -> AmoledAccent.copy(alpha = 0.4f)
                                        else -> AmoledBorder
                                    }
                                )
                            ) {
                                Text(
                                    text = task.status.name,
                                    color = when (task.status) {
                                        TaskStatus.COMPLETED -> AmoledSuccess
                                        TaskStatus.FAILED -> AmoledError
                                        TaskStatus.EXECUTING, TaskStatus.OBSERVING -> AmoledAccent
                                        else -> AmoledTextSecondary
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = task.originalRequest,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AmoledTextPrimary,
                            lineHeight = 20.sp
                        )

                        if (task.finalSummary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = AmoledBorder)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Executive Summary",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmoledSuccess
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = task.finalSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = AmoledTextSecondary
                            )
                        }

                        if (task.lastError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = AmoledError.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, AmoledError.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Error: ${task.lastError}",
                                    color = AmoledError,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // PROMINENT DELIVERABLES & GENERATED FILES SECTION
            if (artifacts.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = AmoledTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Generated Deliverables (${artifacts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmoledTextPrimary
                            )
                        }
                        Text(
                            text = "Tap to view or download",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmoledTextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(artifacts) { artifact ->
                    ArtifactCard(
                        artifact = artifact,
                        onView = { viewingArtifact = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Plan / Approval Card
            if (task.status == TaskStatus.WAITING_FOR_USER || task.status == TaskStatus.AWAITING_PLAN_APPROVAL || task.status == TaskStatus.AWAITING_APPROVAL) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmoledCard),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmoledBorderActive),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("plan_approval_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (task.status == TaskStatus.AWAITING_APPROVAL) "Dangerous Action Approval" else "Review Execution Plan",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = AmoledTextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "The agent is paused waiting for your authorization to proceed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmoledTextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = onApprovePlan,
                                    colors = ButtonDefaults.buttonColors(containerColor = AmoledTextPrimary, contentColor = AmoledBg),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("approve_plan_btn")
                                ) {
                                    Text("Approve & Continue", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onCancel,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AmoledError),
                                    border = BorderStroke(1.dp, AmoledBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("reject_plan_btn")
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }

            // Execution Plan Steps
            if (planSteps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Hierarchical Plan (${planSteps.count { it.status == StepStatus.COMPLETED }}/${planSteps.size} Completed)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(planSteps) { step ->
                    PlanStepRow(step = step)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Live Timeline Events Section
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Execution Feed (${timeline.size} Events)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (timeline.isEmpty()) {
                item {
                    Surface(
                        color = AmoledCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Initializing agent harness and environment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmoledTextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(timeline.reversed()) { event ->
                    TimelineEventCard(event = event)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    // Full in-app preview and download modal dialog
    viewingArtifact?.let { art ->
        ArtifactViewerDialog(
            artifact = art,
            onDismiss = { viewingArtifact = null }
        )
    }
}

@Composable
private fun PlanStepRow(step: PlanStep) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (iconColor, iconVector) = when (step.status) {
                StepStatus.COMPLETED -> Pair(AmoledSuccess, Icons.Default.CheckCircle)
                StepStatus.RUNNING, StepStatus.IN_PROGRESS -> Pair(AmoledAccent, Icons.Default.PlayArrow)
                StepStatus.FAILED -> Pair(AmoledError, Icons.Default.Error)
                else -> Pair(AmoledTextMuted, Icons.Default.Check)
            }

            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${step.stepNumber}: ${step.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AmoledTextPrimary
                )
                if (step.description.isNotBlank()) {
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmoledTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelineEvent) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotColor = when (event.type) {
                    TimelineEventType.PLANNING -> AmoledWarning
                    TimelineEventType.TOOL_EXECUTION -> AmoledAccent
                    TimelineEventType.OBSERVATION -> AmoledSuccess
                    TimelineEventType.VERIFICATION -> AmoledSuccess
                    TimelineEventType.ERROR -> AmoledError
                    TimelineEventType.ARTIFACT_GENERATION -> AmoledTextPrimary
                    else -> AmoledTextSecondary
                }

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                    Text(
                        text = event.type.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = AmoledTextMuted
                    )
                }

                if (event.details.isNotBlank()) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = AmoledTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded && event.details.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = AmoledBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AmoledElevated,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = event.details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = AmoledTextSecondary,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
