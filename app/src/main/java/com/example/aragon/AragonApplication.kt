package com.example.aragon

import android.app.Application
import com.example.aragon.agent.AgentHarness
import com.example.aragon.artifacts.ArtifactManager
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.UbuntuManager
import com.example.aragon.computer.WorkspaceManager
import com.example.aragon.data.local.AragonDatabase
import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.data.repository.ProjectRepository
import com.example.aragon.data.repository.TaskRepository
import com.example.aragon.llm.ModelRegistry
import com.example.aragon.llm.NvidiaNimProvider
import com.example.aragon.tools.ToolExecutor
import com.example.aragon.tools.ToolRegistry

class AragonApplication : Application() {

    lateinit var database: AragonDatabase
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var workspaceManager: WorkspaceManager
        private set

    lateinit var processManager: ProcessManager
        private set

    lateinit var ubuntuManager: UbuntuManager
        private set

    lateinit var toolRegistry: ToolRegistry
        private set

    lateinit var toolExecutor: ToolExecutor
        private set

    lateinit var modelRegistry: ModelRegistry
        private set

    lateinit var nimProvider: NvidiaNimProvider
        private set

    lateinit var artifactManager: ArtifactManager
        private set

    lateinit var taskRepository: TaskRepository
        private set

    lateinit var projectRepository: ProjectRepository
        private set

    lateinit var agentHarness: AgentHarness
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AragonDatabase.getInstance(this)
        preferencesManager = PreferencesManager(this)
        workspaceManager = WorkspaceManager(this)
        processManager = ProcessManager()
        ubuntuManager = UbuntuManager(this)
        toolRegistry = ToolRegistry()
        toolExecutor = ToolExecutor(processManager)
        modelRegistry = ModelRegistry()
        nimProvider = NvidiaNimProvider(preferencesManager, modelRegistry)
        artifactManager = ArtifactManager(database.artifactDao())
        taskRepository = TaskRepository(database.taskDao(), database.planStepDao(), database.timelineEventDao())
        projectRepository = ProjectRepository(database.projectDao())

        agentHarness = AgentHarness(
            taskDao = database.taskDao(),
            projectDao = database.projectDao(),
            planStepDao = database.planStepDao(),
            toolExecutionDao = database.toolExecutionDao(),
            timelineEventDao = database.timelineEventDao(),
            artifactManager = artifactManager,
            workspaceManager = workspaceManager,
            ubuntuManager = ubuntuManager,
            toolRegistry = toolRegistry,
            toolExecutor = toolExecutor,
            llmProvider = nimProvider,
            preferencesManager = preferencesManager
        )
    }

    companion object {
        lateinit var instance: AragonApplication
            private set
    }
}
