package com.example.aragon.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.theme.AmoledBorderActive
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledError
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledSurface
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary
import com.example.ui.theme.AmoledWarning
import kotlinx.coroutines.launch

private enum class DetailWorkspaceSection {
    FEED,
    PLAN,
    DELIVERABLES,
    AUDIT
}

private enum class FeedFilter {
    ALL,
    ACTIONS,
    OBSERVATIONS,
    DECISIONS
}

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
    var activeSection by remember { mutableStateOf(DetailWorkspaceSection.FEED) }
    var feedFilter by remember { mutableStateOf(FeedFilter.ALL) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

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

    // Calculate plan progress
    val totalSteps = planSteps.size.coerceAtLeast(1)
    val completedSteps = planSteps.count { it.status == StepStatus.COMPLETED }
    val planProgress = (completedSteps.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
    val activePhase = planSteps.find { it.status == StepStatus.IN_PROGRESS } ?: planSteps.find { it.status == StepStatus.PENDING }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ActiveStatusBadge(status = task.status)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Iter ${task.iteration} • ${task.selectedModel.substringAfter("/")}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = AmoledTextSecondary
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Task Objective Header Strip with Goal Progress
            TaskGoalBanner(
                task = task,
                planProgress = planProgress,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                activePhase = activePhase
            )

            // High-Level Connected Workspace Segment Selector
            WorkspaceSegmentSelector(
                activeSection = activeSection,
                onSectionSelected = { activeSection = it },
                feedCount = timeline.size,
                planCount = planSteps.size,
                artifactsCount = artifacts.size
            )

            // Content Area depending on active workspace section
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (activeSection) {
                    DetailWorkspaceSection.FEED -> {
                        ExecutionFeedView(
                            task = task,
                            timeline = timeline,
                            filter = feedFilter,
                            onFilterChange = { feedFilter = it },
                            onApprovePlan = onApprovePlan,
                            onCancel = onCancel,
                            listState = listState
                        )
                    }
                    DetailWorkspaceSection.PLAN -> {
                        HierarchicalPlanView(
                            task = task,
                            planSteps = planSteps,
                            onApprovePlan = onApprovePlan,
                            onCancel = onCancel
                        )
                    }
                    DetailWorkspaceSection.DELIVERABLES -> {
                        DeliverablesView(
                            artifacts = artifacts,
                            onViewArtifact = { viewingArtifact = it }
                        )
                    }
                    DetailWorkspaceSection.AUDIT -> {
                        TaskAuditView(task = task, planSteps = planSteps, artifacts = artifacts)
                    }
                }
            }
        }
    }

    // Modal artifact viewer
    viewingArtifact?.let { art ->
        ArtifactViewerDialog(
            artifact = art,
            onDismiss = { viewingArtifact = null }
        )
    }
}

@Composable
private fun ActiveStatusBadge(status: TaskStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val (bgColor, textColor, label) = when (status) {
        TaskStatus.COMPLETED -> Triple(AmoledSuccess.copy(alpha = 0.15f), AmoledSuccess, "SUCCESS ✓")
        TaskStatus.FAILED -> Triple(AmoledError.copy(alpha = 0.15f), AmoledError, "FAILED")
        TaskStatus.BLOCKED -> Triple(AmoledWarning.copy(alpha = 0.15f), AmoledWarning, "BLOCKED")
        TaskStatus.CANCELLED -> Triple(AmoledBorder, AmoledTextMuted, "CANCELLED")
        TaskStatus.EXECUTING -> Triple(AmoledAccent.copy(alpha = 0.2f), AmoledAccent, "EXECUTING")
        TaskStatus.OBSERVING -> Triple(AmoledSuccess.copy(alpha = 0.2f), AmoledSuccess, "OBSERVING")
        TaskStatus.PLANNING -> Triple(AmoledWarning.copy(alpha = 0.2f), AmoledWarning, "PLANNING")
        TaskStatus.WAITING_FOR_USER, TaskStatus.AWAITING_PLAN_APPROVAL, TaskStatus.AWAITING_APPROVAL ->
            Triple(AmoledWarning.copy(alpha = 0.25f), AmoledWarning, "APPROVAL REQUIRED")
        TaskStatus.PAUSED -> Triple(AmoledInteractive, AmoledTextSecondary, "PAUSED")
        else -> Triple(AmoledInteractive, AmoledTextSecondary, status.name)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = if (status.isActive) alphaPulse else 0.4f))
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TaskGoalBanner(
    task: Task,
    planProgress: Float,
    completedSteps: Int,
    totalSteps: Int,
    activePhase: PlanStep?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.dp, Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TARGET GOAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${(planProgress * 100).toInt()}% • $completedSteps/$totalSteps Phases",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (planProgress >= 1f) AmoledSuccess else AmoledAccent
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.originalRequest,
                style = MaterialTheme.typography.bodyMedium,
                color = AmoledTextPrimary,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { planProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (planProgress >= 1f) AmoledSuccess else AmoledAccent,
                trackColor = AmoledInteractive
            )

            if (activePhase != null && activePhase.nextIntent.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = AmoledWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Intent: ${activePhase.nextIntent}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmoledTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
        HorizontalDivider(color = AmoledBorder, thickness = 0.5.dp)
    }
}

@Composable
private fun WorkspaceSegmentSelector(
    activeSection: DetailWorkspaceSection,
    onSectionSelected: (DetailWorkspaceSection) -> Unit,
    feedCount: Int,
    planCount: Int,
    artifactsCount: Int
) {
    Surface(
        color = AmoledBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WorkspaceSegmentTab(
                title = "Execution Feed",
                badge = feedCount.toString(),
                isSelected = activeSection == DetailWorkspaceSection.FEED,
                onClick = { onSectionSelected(DetailWorkspaceSection.FEED) },
                modifier = Modifier.weight(1f).testTag("tab_execution_feed")
            )
            WorkspaceSegmentTab(
                title = "Execution Plan",
                badge = planCount.toString(),
                isSelected = activeSection == DetailWorkspaceSection.PLAN,
                onClick = { onSectionSelected(DetailWorkspaceSection.PLAN) },
                modifier = Modifier.weight(1f).testTag("tab_hierarchical_plan")
            )
            WorkspaceSegmentTab(
                title = "Deliverables",
                badge = artifactsCount.toString(),
                isSelected = activeSection == DetailWorkspaceSection.DELIVERABLES,
                onClick = { onSectionSelected(DetailWorkspaceSection.DELIVERABLES) },
                modifier = Modifier.weight(1f).testTag("tab_deliverables")
            )
            WorkspaceSegmentTab(
                title = "Audit",
                badge = null,
                isSelected = activeSection == DetailWorkspaceSection.AUDIT,
                onClick = { onSectionSelected(DetailWorkspaceSection.AUDIT) },
                modifier = Modifier.weight(0.8f).testTag("tab_audit")
            )
        }
    }
}

@Composable
private fun WorkspaceSegmentTab(
    title: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animBg by animateColorAsState(
        targetValue = if (isSelected) AmoledElevated else AmoledSurface,
        animationSpec = tween(180),
        label = "tab_bg"
    )
    val animBorder by animateColorAsState(
        targetValue = if (isSelected) AmoledBorderActive else AmoledBorder,
        animationSpec = tween(180),
        label = "tab_border"
    )

    Surface(
        color = animBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, animBorder),
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AmoledTextPrimary else AmoledTextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
            if (!badge.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) AmoledAccent else AmoledTextMuted
                )
            }
        }
    }
}

@Composable
private fun FeedFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) AmoledElevated else AmoledSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) AmoledBorderActive else AmoledBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) AmoledTextPrimary else AmoledTextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (selected) AmoledAccent else AmoledTextMuted
            )
        }
    }
}

@Composable
private fun ExecutionFeedView(
    task: Task,
    timeline: List<TimelineEvent>,
    filter: FeedFilter,
    onFilterChange: (FeedFilter) -> Unit,
    onApprovePlan: () -> Unit,
    onCancel: () -> Unit,
    listState: LazyListState
) {
    val filteredTimeline = remember(timeline, filter) {
        when (filter) {
            FeedFilter.ALL -> timeline
            FeedFilter.ACTIONS -> timeline.filter { it.type == TimelineEventType.ACTION || it.type == TimelineEventType.TOOL_EXECUTION }
            FeedFilter.OBSERVATIONS -> timeline.filter { it.type == TimelineEventType.OBSERVATION || it.type == TimelineEventType.VERIFICATION }
            FeedFilter.DECISIONS -> timeline.filter { it.type == TimelineEventType.REASONING || it.type == TimelineEventType.DECISION || it.type == TimelineEventType.PLANNING || it.type == TimelineEventType.REPLAN }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("execution_feed_list")
    ) {
        // Feed Filter Chips
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FeedFilterChip(label = "All", count = timeline.size, selected = filter == FeedFilter.ALL, onClick = { onFilterChange(FeedFilter.ALL) })
                FeedFilterChip(label = "Actions", count = timeline.count { it.type == TimelineEventType.ACTION || it.type == TimelineEventType.TOOL_EXECUTION }, selected = filter == FeedFilter.ACTIONS, onClick = { onFilterChange(FeedFilter.ACTIONS) })
                FeedFilterChip(label = "Observations", count = timeline.count { it.type == TimelineEventType.OBSERVATION || it.type == TimelineEventType.VERIFICATION }, selected = filter == FeedFilter.OBSERVATIONS, onClick = { onFilterChange(FeedFilter.OBSERVATIONS) })
                FeedFilterChip(label = "Decisions", count = timeline.count { it.type == TimelineEventType.REASONING || it.type == TimelineEventType.DECISION || it.type == TimelineEventType.PLANNING }, selected = filter == FeedFilter.DECISIONS, onClick = { onFilterChange(FeedFilter.DECISIONS) })
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Active Agent Working Pod (Stacked Head Unit)
        if (task.status == TaskStatus.EXECUTING || task.status == TaskStatus.PLANNING || task.status == TaskStatus.REPLANNING || task.status == TaskStatus.VERIFYING) {
            item {
                ActiveAgentWorkingPod(task = task, latestEvent = timeline.lastOrNull())
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Approval Gate Card (if waiting)
        if (task.status == TaskStatus.WAITING_FOR_USER || task.status == TaskStatus.AWAITING_PLAN_APPROVAL || task.status == TaskStatus.AWAITING_APPROVAL) {
            item {
                PlanApprovalCard(
                    isDangerousAction = task.status == TaskStatus.AWAITING_APPROVAL,
                    onApprove = onApprovePlan,
                    onCancel = onCancel
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (filteredTimeline.isEmpty()) {
            item {
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = AmoledAccent.copy(alpha = 0.7f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (timeline.isEmpty()) "Initializing Autonomous Tool Execution Pipeline..." else "No events matching current filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmoledTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            // Render stacked boxes from newest to oldest with connected conduits
            val reversedItems = filteredTimeline.reversed()
            items(reversedItems, key = { it.id }) { event ->
                val indexInList = filteredTimeline.indexOf(event)
                val isLatest = event.id == timeline.lastOrNull()?.id
                StackedExecutionBox(
                    event = event,
                    boxIndex = indexInList + 1,
                    totalBoxes = filteredTimeline.size,
                    isLatest = isLatest
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ActiveAgentWorkingPod(task: Task, latestEvent: TimelineEvent?) {
    val infiniteTransition = rememberInfiniteTransition(label = "agent_working_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Surface(
        color = Color(0xFF0D141C),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, AmoledAccent.copy(alpha = glowAlpha)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsating radar dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(AmoledAccent.copy(alpha = glowAlpha), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AGENT COMPUTE ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = AmoledAccent,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    color = AmoledElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, AmoledBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = AmoledTextMuted,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = task.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = AmoledTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active subtask / tool banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AmoledAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = latestEvent?.title ?: "Executing autonomous strategy...",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AmoledTextPrimary,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Animated scanning progress strip
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AmoledAccent,
                trackColor = AmoledSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = AmoledTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "OpenSandbox Substrate: Active",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = AmoledTextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lan,
                        contentDescription = null,
                        tint = AmoledTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "MCP & Playwright: Ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = AmoledTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun StackedExecutionBox(
    event: TimelineEvent,
    boxIndex: Int,
    totalBoxes: Int,
    isLatest: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Semantic status styling
    val isGoalCompleted = event.type == TimelineEventType.GOAL_COMPLETED || event.type == TimelineEventType.TASK_COMPLETED
    val isObservation = event.type == TimelineEventType.OBSERVATION
    val isDecision = event.type == TimelineEventType.REASONING || event.type == TimelineEventType.DECISION
    val isAction = event.type == TimelineEventType.ACTION || event.type == TimelineEventType.TOOL_EXECUTION
    val isError = event.type == TimelineEventType.ERROR
    val isReplan = event.type == TimelineEventType.REPLAN

    val accentColor = when {
        isGoalCompleted -> AmoledSuccess
        isObservation -> Color(0xFF10B981) // emerald
        isDecision -> Color(0xFFA855F7) // purple
        isAction -> Color(0xFF38BDF8) // cyan/accent
        isError -> AmoledError
        isReplan -> AmoledWarning
        else -> AmoledTextMuted
    }

    val boxBg = when {
        isGoalCompleted -> Color(0xFF091C12)
        isDecision -> Color(0xFF181024)
        isError -> Color(0xFF200D11)
        isLatest -> Color(0xFF0C131B)
        else -> AmoledCard
    }

    val borderStrokeColor = if (isLatest) {
        accentColor.copy(alpha = 0.85f)
    } else {
        accentColor.copy(alpha = 0.25f)
    }

    // Stacked Container with Left Pipeline Conduit
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Left Conduit Track with Connecting Dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(24.dp)
                .padding(top = 10.dp)
        ) {
            // Node pip
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accentColor, CircleShape)
            )
            // Vertical connecting line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(48.dp)
                    .background(accentColor.copy(alpha = 0.2f))
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Stacked Box Pod
        Surface(
            color = boxBg,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.2.dp, borderStrokeColor),
            modifier = Modifier
                .weight(1f)
                .animateContentSize(animationSpec = tween(200, easing = FastOutSlowInEasing))
                .clickable { if (event.details.isNotBlank()) isExpanded = !isExpanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Top Header Strip of the Box
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Box Index Pill (e.g. BOX #04)
                    Surface(
                        color = AmoledSurface,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, AmoledBorder)
                    ) {
                        Text(
                            text = "BOX #${boxIndex.toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            color = AmoledTextSecondary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Semantic Stage Badge
                    val (stageIcon, stageLabel) = when {
                        isGoalCompleted -> Pair(Icons.Default.CheckCircle, "VERIFIED")
                        isObservation -> Pair(Icons.Default.Visibility, "OBSERVATION")
                        isDecision -> Pair(Icons.Default.Lightbulb, "DECISION")
                        isAction -> Pair(Icons.Default.Code, "ACTION")
                        isError -> Pair(Icons.Default.Error, "ERROR")
                        isReplan -> Pair(Icons.Default.Refresh, "REPLAN")
                        else -> Pair(Icons.Default.Terminal, event.type.name)
                    }

                    Surface(
                        color = accentColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = stageIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stageLabel,
                                color = accentColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Duration badge
                    if (event.durationMs != null) {
                        Text(
                            text = "${event.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = AmoledTextMuted
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Expand indicator
                    if (event.details.isNotBlank()) {
                        val rotation by animateFloatAsState(
                            targetValue = if (isExpanded) 180f else 0f,
                            animationSpec = tween(180),
                            label = "box_expand_rot"
                        )
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Toggle Details",
                            tint = AmoledTextMuted,
                            modifier = Modifier
                                .size(15.dp)
                                .rotate(rotation)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Box Title
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextPrimary,
                    fontFamily = if (isAction || isObservation) FontFamily.Monospace else FontFamily.Default,
                    maxLines = if (isExpanded) 4 else 2
                )

                // Compact Single-Line Preview if not expanded
                if (!isExpanded && event.details.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val preview = event.details.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: ""
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = AmoledTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }

                // Expanded Terminal / Output Compartment
                AnimatedVisibility(
                    visible = isExpanded && event.details.isNotBlank(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        HorizontalDivider(color = AmoledBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Terminal Box with Copy Button
                        Surface(
                            color = AmoledElevated,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "EXECUTION TRACE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        color = AmoledTextMuted,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(event.details))
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy Trace",
                                            tint = AmoledTextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = event.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = AmoledTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HierarchicalPlanView(
    task: Task,
    planSteps: List<PlanStep>,
    onApprovePlan: () -> Unit,
    onCancel: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("hierarchical_plan_list")
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hierarchical Execution Plan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AmoledTextPrimary
            )
            Text(
                text = "Phased milestone breakdown with active state transitions and subtasks",
                style = MaterialTheme.typography.bodySmall,
                color = AmoledTextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (task.status == TaskStatus.AWAITING_PLAN_APPROVAL) {
            item {
                PlanApprovalCard(
                    isDangerousAction = false,
                    onApprove = onApprovePlan,
                    onCancel = onCancel
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (planSteps.isEmpty()) {
            item {
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Formulating hierarchical plan...",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmoledTextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(planSteps, key = { it.id }) { step ->
                HierarchicalPhaseCard(step = step)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HierarchicalPhaseCard(step: PlanStep) {
    var isExpanded by remember { mutableStateOf(step.status == StepStatus.IN_PROGRESS || step.status == StepStatus.FAILED) }

    val isActive = step.status == StepStatus.IN_PROGRESS
    val isCompleted = step.status == StepStatus.COMPLETED
    val isFailed = step.status == StepStatus.FAILED

    val infiniteTransition = rememberInfiniteTransition(label = "step_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    val borderStrokeColor = when {
        isCompleted -> AmoledSuccess.copy(alpha = 0.5f)
        isActive -> AmoledAccent.copy(alpha = borderAlpha)
        isFailed -> AmoledError
        else -> AmoledBorder
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledCard),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, borderStrokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200))
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                val (statusColor, statusIcon) = when {
                    isCompleted -> Pair(AmoledSuccess, Icons.Default.CheckCircle)
                    isActive -> Pair(AmoledAccent, Icons.Default.PlayArrow)
                    isFailed -> Pair(AmoledError, Icons.Default.Error)
                    else -> Pair(AmoledTextMuted, Icons.Default.Check)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (step.phase.isNotBlank()) step.phase else "Phase ${step.stepNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActive) AmoledAccent else AmoledTextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AmoledTextPrimary
                    )
                }

                // Subtasks counter badge
                if (step.subtasks.isNotEmpty()) {
                    val doneCount = if (isCompleted) step.subtasks.size else step.activeSubtaskIndex.coerceAtMost(step.subtasks.size)
                    Surface(
                        color = AmoledInteractive,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$doneCount/${step.subtasks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = AmoledTextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AmoledTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Description
            if (step.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmoledTextSecondary
                )
            }

            // Next Intent Pill if active
            if (isActive && step.nextIntent.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AmoledAccent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AmoledAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = AmoledAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Next Intent: ${step.nextIntent}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmoledAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Subtasks expansion
            AnimatedVisibility(
                visible = isExpanded && step.subtasks.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = AmoledBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    step.subtasks.forEachIndexed { idx, subtask ->
                        val isSubtaskDone = isCompleted || idx < step.activeSubtaskIndex
                        val isSubtaskActive = isActive && idx == step.activeSubtaskIndex

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isSubtaskDone -> Icons.Default.Check
                                    isSubtaskActive -> Icons.Default.PlayArrow
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = when {
                                    isSubtaskDone -> AmoledSuccess
                                    isSubtaskActive -> AmoledAccent
                                    else -> AmoledTextMuted.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = subtask,
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    isSubtaskActive -> AmoledTextPrimary
                                    isSubtaskDone -> AmoledTextSecondary
                                    else -> AmoledTextMuted
                                },
                                fontWeight = if (isSubtaskActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (step.dependencies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Requires: ${step.dependencies.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = AmoledTextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliverablesView(
    artifacts: List<Artifact>,
    onViewArtifact: (Artifact) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("deliverables_list")
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generated Deliverables (${artifacts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AmoledTextPrimary
                )
                Text(
                    text = "Tap to view / download",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmoledTextMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (artifacts.isEmpty()) {
            item {
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = AmoledTextMuted,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No artifacts generated yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmoledTextSecondary
                        )
                        Text(
                            text = "Outputs will appear here as the agent produces files.",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmoledTextMuted
                        )
                    }
                }
            }
        } else {
            items(artifacts, key = { it.id }) { artifact ->
                ArtifactCard(artifact = artifact, onView = { onViewArtifact(artifact) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TaskAuditView(
    task: Task,
    planSteps: List<PlanStep>,
    artifacts: List<Artifact>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("task_audit_list")
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Execution Audit & Telemetry",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AmoledTextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Executive Summary Card
            val summary = task.finalSummary
            if (!summary.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledCard),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AmoledSuccess.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmoledSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Executive Delivery Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AmoledSuccess)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = summary, style = MaterialTheme.typography.bodySmall, color = AmoledTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Metrics Telemetry Card
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledCard),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmoledBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Session Telemetry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = AmoledTextPrimary)
                    Spacer(modifier = Modifier.height(10.dp))
                    AuditMetricRow("Selected Model", task.selectedModel)
                    AuditMetricRow("Agent Mode", task.mode.name)
                    AuditMetricRow("Completed Iterations", "${task.iteration}")
                    AuditMetricRow("Tool Invocations", "${task.metrics.toolCallsCount} (${task.metrics.successfulToolCalls} successful)")
                    AuditMetricRow("Delivered Products", "${artifacts.count { it.stage == com.example.aragon.domain.model.ArtifactStage.PRODUCT }}")
                    AuditMetricRow("Duration", "${task.metrics.durationMs / 1000}s")
                    if (task.lastError != null) {
                        AuditMetricRow("Last Error / Block", task.lastError, isError = true)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuditMetricRow(label: String, value: String, isError: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AmoledTextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isError) AmoledError else AmoledTextPrimary
        )
    }
}

@Composable
private fun PlanApprovalCard(
    isDangerousAction: Boolean,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
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
                text = if (isDangerousAction) "Dangerous Action Authorization" else "Review Execution Plan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AmoledTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The agent is paused waiting for your authorization to proceed with the planned actions.",
                style = MaterialTheme.typography.bodySmall,
                color = AmoledTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
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
