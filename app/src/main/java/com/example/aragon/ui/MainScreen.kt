package com.example.aragon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.ui.components.AragonTopAppBar
import com.example.aragon.ui.components.ComputerHealthSheet
import com.example.aragon.ui.components.ModelSelectorDialog
import com.example.aragon.ui.screens.ArtifactsScreen
import com.example.aragon.ui.screens.HomeScreen
import com.example.aragon.ui.screens.ProjectsScreen
import com.example.aragon.ui.screens.SettingsScreen
import com.example.aragon.ui.screens.TaskDetailScreen
import com.example.aragon.ui.screens.TasksScreen
import com.example.ui.theme.AmoledAccent
import com.example.ui.theme.AmoledBg
import com.example.ui.theme.AmoledBorder
import com.example.ui.theme.AmoledBorderActive
import com.example.ui.theme.AmoledCard
import com.example.ui.theme.AmoledElevated
import com.example.ui.theme.AmoledInteractive
import com.example.ui.theme.AmoledSuccess
import com.example.ui.theme.AmoledTextMuted
import com.example.ui.theme.AmoledTextPrimary
import com.example.ui.theme.AmoledTextSecondary

enum class AragonTab {
    HOME,
    TASKS,
    PROJECTS,
    ARTIFACTS,
    SETTINGS
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var currentTab by remember { mutableStateOf(AragonTab.HOME) }
    var showHealthSheet by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val allArtifacts by viewModel.allArtifacts.collectAsStateWithLifecycle()
    val allProjects by viewModel.allProjects.collectAsStateWithLifecycle()
    val selectedTaskId by viewModel.selectedTaskId.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val planSteps by viewModel.planSteps.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val taskArtifacts by viewModel.taskArtifacts.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val endpoint by viewModel.endpoint.collectAsStateWithLifecycle()
    val autonomyLevel by viewModel.autonomyLevel.collectAsStateWithLifecycle()
    val maxIterations by viewModel.maxIterations.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val ubuntuReport by viewModel.ubuntuReport.collectAsStateWithLifecycle()
    val executionBackend by viewModel.executionBackend.collectAsStateWithLifecycle()
    val openSandboxServerUrl by viewModel.openSandboxServerUrl.collectAsStateWithLifecycle()
    val openSandboxApiKey by viewModel.openSandboxApiKey.collectAsStateWithLifecycle()
    val openSandboxImage by viewModel.openSandboxImage.collectAsStateWithLifecycle()
    val openSandboxActiveId by viewModel.openSandboxActiveId.collectAsStateWithLifecycle()

    val activeTask = allTasks.find { it.status.isActive }

    // If viewing a specific task detail:
    if (selectedTaskId != null) {
        BackHandler {
            viewModel.selectTask(null)
        }
        TaskDetailScreen(
            task = selectedTask,
            planSteps = planSteps,
            timeline = timeline,
            artifacts = taskArtifacts,
            onBack = { viewModel.selectTask(null) },
            onApprovePlan = { selectedTaskId?.let { viewModel.approvePlan(it) } },
            onPause = { selectedTaskId?.let { viewModel.pauseTask(it) } },
            onResume = { selectedTaskId?.let { viewModel.resumeTask(it) } },
            onCancel = { selectedTaskId?.let { viewModel.cancelTask(it) } }
        )
        return
    }

    Scaffold(
        topBar = {
            AragonTopAppBar(
                selectedModel = selectedModel,
                onOpenModelSelector = { showModelDialog = true },
                onOpenComputerStatus = { showHealthSheet = true }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AmoledBg)
            ) {
                // Persistent Active Session Floating Indicator Pill
                AnimatedVisibility(
                    visible = activeTask != null,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                ) {
                    if (activeTask != null) {
                        Surface(
                            color = AmoledElevated,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, AmoledAccent.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { viewModel.selectTask(activeTask.id) }
                                .testTag("active_session_pill")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val pulse = rememberInfiniteTransition(label = "green_pulse")
                                    val alpha by pulse.animateFloat(
                                        initialValue = 0.4f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                                        label = "a"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AmoledSuccess.copy(alpha = alpha))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "ACTIVE SESSION • Iter ${activeTask.iteration}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = AmoledAccent,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = activeTask.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AmoledTextPrimary,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "LIVE FEED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmoledSuccess,
                                        fontSize = 10.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = AmoledSuccess,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Premium Floating AMOLED Workspace Dock
                Surface(
                    color = AmoledCard,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bottom_nav_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WorkspaceDockItem(
                            icon = Icons.Default.Home,
                            label = "Workspace",
                            badge = null,
                            isSelected = currentTab == AragonTab.HOME,
                            onClick = { currentTab = AragonTab.HOME },
                            testTag = "nav_item_home"
                        )
                        WorkspaceDockItem(
                            icon = Icons.AutoMirrored.Filled.List,
                            label = "Tasks",
                            badge = if (allTasks.isNotEmpty()) "${allTasks.size}" else null,
                            isSelected = currentTab == AragonTab.TASKS,
                            onClick = { currentTab = AragonTab.TASKS },
                            testTag = "nav_item_tasks"
                        )
                        WorkspaceDockItem(
                            icon = Icons.Default.Folder,
                            label = "Projects",
                            badge = if (allProjects.isNotEmpty()) "${allProjects.size}" else null,
                            isSelected = currentTab == AragonTab.PROJECTS,
                            onClick = { currentTab = AragonTab.PROJECTS },
                            testTag = "nav_item_projects"
                        )
                        WorkspaceDockItem(
                            icon = Icons.Default.Description,
                            label = "Artifacts",
                            badge = if (allArtifacts.isNotEmpty()) "${allArtifacts.size}" else null,
                            isSelected = currentTab == AragonTab.ARTIFACTS,
                            onClick = { currentTab = AragonTab.ARTIFACTS },
                            testTag = "nav_item_artifacts"
                        )
                        WorkspaceDockItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            badge = null,
                            isSelected = currentTab == AragonTab.SETTINGS,
                            onClick = { currentTab = AragonTab.SETTINGS },
                            testTag = "nav_item_settings"
                        )
                    }
                }
            }
        },
        containerColor = AmoledBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
                },
                label = "workspace_screen_transition"
            ) { targetTab ->
                when (targetTab) {
                    AragonTab.HOME -> HomeScreen(
                        selectedModel = selectedModel,
                        recentTasks = allTasks,
                        activeTask = activeTask,
                        onLaunchTask = { request, mode ->
                            val id = viewModel.launchTask(request, mode)
                        },
                        onOpenTaskDetail = { id -> viewModel.selectTask(id) },
                        onRunBenchmark = { viewModel.runBenchmarkSuite() }
                    )
                    AragonTab.TASKS -> TasksScreen(
                        tasks = allTasks,
                        onSelectTask = { id -> viewModel.selectTask(id) },
                        onDeleteTask = { id -> viewModel.deleteTask(id) },
                        onNewTask = { currentTab = AragonTab.HOME }
                    )
                    AragonTab.PROJECTS -> ProjectsScreen(
                        projects = allProjects,
                        onCreateProject = { name, desc, instructions ->
                            viewModel.createProject(name, desc, instructions)
                        },
                        onStartTaskInProject = { proj ->
                            viewModel.launchTask(
                                request = "Initialize and verify project environment for ${proj.name}",
                                mode = AgentMode.AGENT,
                                projectId = proj.id
                            )
                        }
                    )
                    AragonTab.ARTIFACTS -> ArtifactsScreen(
                        artifacts = allArtifacts
                    )
                    AragonTab.SETTINGS -> SettingsScreen(
                        currentApiKey = apiKey,
                        currentEndpoint = endpoint,
                        selectedModel = selectedModel,
                        autonomyLevel = autonomyLevel,
                        maxIterations = maxIterations,
                        temperature = temperature,
                        executionBackend = executionBackend,
                        openSandboxServerUrl = openSandboxServerUrl,
                        openSandboxApiKey = openSandboxApiKey,
                        openSandboxImage = openSandboxImage,
                        openSandboxActiveId = openSandboxActiveId,
                        onSaveApiKey = { viewModel.saveApiKey(it) },
                        onSaveEndpoint = { viewModel.saveEndpoint(it) },
                        onTestConnection = { testKey, testEndpoint ->
                            viewModel.testConnection(testKey, testEndpoint)
                        },
                        onSelectModelClick = { showModelDialog = true },
                        onSaveAutonomyLevel = { viewModel.saveAutonomyLevel(it) },
                        onSaveMaxIterations = { viewModel.saveMaxIterations(it) },
                        onSaveTemperature = { viewModel.saveTemperature(it) },
                        onSetExecutionBackend = { viewModel.setExecutionBackend(it) },
                        onSaveOpenSandboxSettings = { url, key, img -> viewModel.saveOpenSandboxSettings(url, key, img) },
                        onTestOpenSandboxConnection = { url, key -> viewModel.testOpenSandboxConnection(url, key) },
                        onSpawnOpenSandbox = { img -> viewModel.spawnOpenSandbox(img) },
                        onTerminateOpenSandbox = { viewModel.terminateOpenSandbox() },
                        onRunBenchmark = { viewModel.runBenchmarkSuite() },
                        onRunHealthCheck = { viewModel.refreshHealthReport() }
                    )
                }
            }
        }
    }

    if (showHealthSheet) {
        ComputerHealthSheet(
            report = ubuntuReport,
            onDismiss = { showHealthSheet = false },
            onRunHealthCheck = { viewModel.refreshHealthReport() },
            onRunBenchmarkSuite = { viewModel.runBenchmarkSuite() }
        )
    }

    if (showModelDialog) {
        ModelSelectorDialog(
            models = models,
            selectedModelId = selectedModel,
            onSelectModel = {
                viewModel.selectModel(it)
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false }
        )
    }
}

@Composable
private fun WorkspaceDockItem(
    icon: ImageVector,
    label: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (isSelected) AmoledElevated else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSelected) AmoledBorderActive else Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) AmoledTextPrimary else AmoledTextMuted,
                    modifier = Modifier.size(20.dp)
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AmoledAccent)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) AmoledTextPrimary else AmoledTextMuted
            )
        }
    }
}
