package com.example.aragon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.example.ui.theme.AragonObsidianBg
import com.example.ui.theme.AragonSurface

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
    val autonomyLevel by viewModel.autonomyLevel.collectAsStateWithLifecycle()
    val maxIterations by viewModel.maxIterations.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val ubuntuReport by viewModel.ubuntuReport.collectAsStateWithLifecycle()

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
            NavigationBar(
                containerColor = AragonSurface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == AragonTab.HOME,
                    onClick = { currentTab = AragonTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_home")
                )
                NavigationBarItem(
                    selected = currentTab == AragonTab.TASKS,
                    onClick = { currentTab = AragonTab.TASKS },
                    icon = { Icon(Icons.Default.List, contentDescription = "Tasks") },
                    label = { Text("Tasks") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_tasks")
                )
                NavigationBarItem(
                    selected = currentTab == AragonTab.PROJECTS,
                    onClick = { currentTab = AragonTab.PROJECTS },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Projects") },
                    label = { Text("Projects") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_projects")
                )
                NavigationBarItem(
                    selected = currentTab == AragonTab.ARTIFACTS,
                    onClick = { currentTab = AragonTab.ARTIFACTS },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Artifacts") },
                    label = { Text("Artifacts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_artifacts")
                )
                NavigationBarItem(
                    selected = currentTab == AragonTab.SETTINGS,
                    onClick = { currentTab = AragonTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("nav_item_settings")
                )
            }
        },
        containerColor = AragonObsidianBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
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
                    selectedModel = selectedModel,
                    autonomyLevel = autonomyLevel,
                    maxIterations = maxIterations,
                    temperature = temperature,
                    onSaveApiKey = { viewModel.saveApiKey(it) },
                    onTestConnection = {
                        viewModel.refreshModels()
                    },
                    onSelectModelClick = { showModelDialog = true },
                    onSaveAutonomyLevel = { viewModel.saveAutonomyLevel(it) },
                    onSaveMaxIterations = { viewModel.saveMaxIterations(it) },
                    onSaveTemperature = { viewModel.saveTemperature(it) },
                    onRunBenchmark = { viewModel.runBenchmarkSuite() },
                    onRunHealthCheck = { viewModel.refreshHealthReport() }
                )
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
