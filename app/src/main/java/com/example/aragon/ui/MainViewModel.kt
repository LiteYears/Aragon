package com.example.aragon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aragon.AragonApplication
import com.example.aragon.computer.UbuntuHealthReport
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.ModelInfo
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Project
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel : ViewModel() {

    private val app = AragonApplication.instance
    private val taskRepo = app.taskRepository
    private val projectRepo = app.projectRepository
    private val artifactRepo = app.artifactManager
    private val agentHarness = app.agentHarness
    private val preferences = app.preferencesManager
    private val ubuntuManager = app.ubuntuManager
    private val modelRegistry = app.modelRegistry
    private val nimProvider = app.nimProvider

    val allTasks: StateFlow<List<Task>> = taskRepo.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArtifacts: StateFlow<List<Artifact>> = artifactRepo.getAllArtifacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjects: StateFlow<List<Project>> = projectRepo.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTaskId = MutableStateFlow<String?>(null)
    val selectedTaskId: StateFlow<String?> = _selectedTaskId.asStateFlow()

    val selectedTask: StateFlow<Task?> = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(null) else taskRepo.getTaskFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val planSteps: StateFlow<List<PlanStep>> = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else taskRepo.getPlanStepsFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val timeline: StateFlow<List<TimelineEvent>> = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else taskRepo.getTimelineFlow(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskArtifacts: StateFlow<List<Artifact>> = _selectedTaskId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else artifactRepo.getArtifactsForTask(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingApprovals: StateFlow<List<com.example.aragon.domain.model.ApprovalRequest>> =
        agentHarness.approvalManager.pendingRequests

    private val _models = MutableStateFlow(modelRegistry.getAllModels())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    val selectedModel: StateFlow<String> = preferences.selectedModel
    val apiKey: StateFlow<String> = preferences.nvidiaApiKey
    val endpoint: StateFlow<String> = preferences.endpoint
    val autonomyLevel: StateFlow<AutonomyLevel> = preferences.autonomyLevel
    val maxIterations: StateFlow<Int> = preferences.maxIterations
    val temperature: StateFlow<Float> = preferences.temperature

    private val _ubuntuReport = MutableStateFlow<UbuntuHealthReport?>(null)
    val ubuntuReport: StateFlow<UbuntuHealthReport?> = _ubuntuReport.asStateFlow()

    init {
        refreshHealthReport()
        refreshModels()
    }

    fun refreshHealthReport() {
        viewModelScope.launch(Dispatchers.IO) {
            _ubuntuReport.value = ubuntuManager.getHealthReport(forceRefresh = true)
        }
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = nimProvider.listModels()
            _models.value = list
        }
    }

    fun selectTask(taskId: String?) {
        _selectedTaskId.value = taskId
    }

    fun launchTask(request: String, mode: AgentMode, projectId: String? = null): String {
        var createdId = ""
        viewModelScope.launch {
            val task = taskRepo.createTask(
                request = request,
                title = request.take(45),
                selectedModel = selectedModel.value,
                mode = mode,
                projectId = projectId
            )
            createdId = task.id
            _selectedTaskId.value = task.id
            agentHarness.startTask(task.id)
        }
        return createdId
    }

    fun approvePlan(taskId: String) {
        agentHarness.approvePlanAndExecute(taskId)
    }

    fun pauseTask(taskId: String) {
        agentHarness.pauseTask(taskId)
    }

    fun resumeTask(taskId: String) {
        agentHarness.startTask(taskId)
    }

    fun cancelTask(taskId: String) {
        agentHarness.cancelTask(taskId)
    }

    fun respondToApproval(requestId: String, approved: Boolean) {
        agentHarness.respondToApproval(requestId, approved)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepo.deleteTask(taskId)
            if (_selectedTaskId.value == taskId) {
                _selectedTaskId.value = null
            }
        }
    }

    fun createProject(name: String, desc: String, instructions: String) {
        viewModelScope.launch {
            projectRepo.createProject(name, desc, instructions)
        }
    }

    fun saveApiKey(key: String) {
        preferences.setNvidiaApiKey(key)
        refreshModels()
    }

    fun saveEndpoint(url: String) {
        preferences.setEndpoint(url)
        refreshModels()
    }

    suspend fun testConnection(apiKey: String? = null, endpointUrl: String? = null): Result<String> {
        val res = nimProvider.testConnection(apiKey, endpointUrl)
        if (res.isSuccess) {
            refreshModels()
        }
        return res
    }

    fun selectModel(modelId: String) {
        preferences.setSelectedModel(modelId)
    }

    fun saveAutonomyLevel(level: AutonomyLevel) {
        preferences.setAutonomyLevel(level)
    }

    fun saveMaxIterations(iterations: Int) {
        preferences.setMaxIterations(iterations)
    }

    fun saveTemperature(temp: Float) {
        preferences.setTemperature(temp)
    }

    fun runBenchmarkSuite() {
        viewModelScope.launch {
            // Launch Test 3: Verified DOCX administrative report
            launchTask(
                request = "Create a verified DOCX administrative report with executive summary, tables, and system performance metrics.",
                mode = AgentMode.AGENT
            )
        }
    }
}
