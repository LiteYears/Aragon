package com.example.aragon.data.repository

import com.example.aragon.data.local.PlanStepDao
import com.example.aragon.data.local.TaskDao
import com.example.aragon.data.local.TaskEntity
import com.example.aragon.data.local.TimelineEventDao
import com.example.aragon.domain.model.AgentMode
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.TimelineEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TaskRepository(
    private val taskDao: TaskDao,
    private val planStepDao: PlanStepDao,
    private val timelineEventDao: TimelineEventDao
) {

    fun getAllTasks(): Flow<List<Task>> = taskDao.getAllTasks().map { list ->
        list.map { it.toDomain() }
    }

    fun getActiveTasks(): Flow<List<Task>> = taskDao.getActiveTasks().map { list ->
        list.map { it.toDomain() }
    }

    fun getTaskFlow(taskId: String): Flow<Task?> = taskDao.getTaskByIdFlow(taskId).map { it?.toDomain() }

    suspend fun getTask(taskId: String): Task? = taskDao.getTaskById(taskId)?.toDomain()

    fun getPlanStepsFlow(taskId: String): Flow<List<PlanStep>> =
        planStepDao.getStepsForTaskFlow(taskId).map { list -> list.map { it.toDomain() } }

    fun getTimelineFlow(taskId: String): Flow<List<TimelineEvent>> =
        timelineEventDao.getTimelineForTaskFlow(taskId).map { list -> list.map { it.toDomain() } }

    suspend fun createTask(
        request: String,
        title: String = request.take(40),
        selectedModel: String,
        mode: AgentMode,
        projectId: String? = null
    ): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            originalRequest = request,
            status = TaskStatus.CREATED,
            selectedModel = selectedModel,
            mode = mode
        )
        taskDao.insertTask(TaskEntity.fromDomain(task))
        return task
    }

    suspend fun deleteTask(taskId: String) {
        taskDao.deleteTask(taskId)
    }
}
