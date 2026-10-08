package com.example.aragon.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.aragon.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status IN ('PLANNING', 'READY', 'EXECUTING', 'OBSERVING', 'VERIFYING', 'REPLANNING') ORDER BY updatedAt DESC")
    fun getActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskByIdFlow(taskId: String): Flow<TaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: TaskStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun getProjectByIdFlow(projectId: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProject(projectId: String)
}

@Dao
interface PlanStepDao {
    @Query("SELECT * FROM plan_steps WHERE taskId = :taskId ORDER BY stepNumber ASC")
    fun getStepsForTaskFlow(taskId: String): Flow<List<PlanStepEntity>>

    @Query("SELECT * FROM plan_steps WHERE taskId = :taskId ORDER BY stepNumber ASC")
    suspend fun getStepsForTask(taskId: String): List<PlanStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<PlanStepEntity>)

    @Update
    suspend fun updateStep(step: PlanStepEntity)

    @Query("DELETE FROM plan_steps WHERE taskId = :taskId")
    suspend fun deleteStepsForTask(taskId: String)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts ORDER BY createdAt DESC")
    fun getAllArtifactsFlow(): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE taskId = :taskId ORDER BY createdAt DESC")
    fun getArtifactsForTaskFlow(taskId: String): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE taskId = :taskId")
    suspend fun getArtifactsForTask(taskId: String): List<ArtifactEntity>

    @Query("SELECT * FROM artifacts WHERE id = :artifactId")
    suspend fun getArtifactById(artifactId: String): ArtifactEntity?

    @Query("SELECT * FROM artifacts WHERE taskId = :taskId AND logicalPath = :logicalPath LIMIT 1")
    suspend fun getArtifactByLogicalPath(taskId: String, logicalPath: String): ArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: ArtifactEntity)

    @Update
    suspend fun updateArtifact(artifact: ArtifactEntity)

    @Query("DELETE FROM artifacts WHERE id = :artifactId")
    suspend fun deleteArtifact(artifactId: String)

    @Query("DELETE FROM artifacts WHERE taskId = :taskId")
    suspend fun deleteArtifactsForTask(taskId: String)
}

@Dao
interface ToolExecutionDao {
    @Query("SELECT * FROM tool_executions WHERE taskId = :taskId ORDER BY timestamp DESC, requestedAt DESC, startedAt DESC, rowid DESC")
    fun getExecutionsForTaskFlow(taskId: String): Flow<List<ToolExecutionEntity>>

    @Query("SELECT * FROM tool_executions WHERE taskId = :taskId ORDER BY timestamp DESC, requestedAt DESC, startedAt DESC, rowid DESC LIMIT :limit")
    suspend fun getRecentExecutions(taskId: String, limit: Int = 10): List<ToolExecutionEntity>

    @Query("SELECT * FROM (SELECT * FROM tool_executions WHERE taskId = :taskId ORDER BY timestamp DESC, requestedAt DESC, startedAt DESC, rowid DESC LIMIT :limit) ORDER BY timestamp ASC, requestedAt ASC, startedAt ASC, rowid ASC")
    suspend fun getRecentExecutionsChronological(taskId: String, limit: Int = 20): List<ToolExecutionEntity>

    @Query("SELECT * FROM tool_executions WHERE taskId = :taskId ORDER BY timestamp ASC, requestedAt ASC, startedAt ASC, rowid ASC")
    suspend fun getAllExecutionsForTask(taskId: String): List<ToolExecutionEntity>

    @Query("SELECT * FROM tool_executions WHERE callId = :callId LIMIT 1")
    suspend fun getExecutionByCallId(callId: String): ToolExecutionEntity?

    @Query("SELECT * FROM tool_executions WHERE status = :status ORDER BY timestamp ASC, requestedAt ASC, rowid ASC")
    suspend fun getExecutionsByStatus(status: String): List<ToolExecutionEntity>

    @Query("SELECT * FROM tool_executions WHERE taskId = :taskId AND status = :status ORDER BY timestamp ASC, requestedAt ASC, rowid ASC")
    suspend fun getExecutionsForTaskByStatus(taskId: String, status: String): List<ToolExecutionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(execution: ToolExecutionEntity)

    @Update
    suspend fun updateExecution(execution: ToolExecutionEntity)
}

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM timeline_events WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getTimelineForTaskFlow(taskId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE taskId = :taskId ORDER BY timestamp ASC")
    suspend fun getTimelineForTask(taskId: String): List<TimelineEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TimelineEventEntity)

    @Query("DELETE FROM timeline_events WHERE taskId = :taskId")
    suspend fun deleteTimelineForTask(taskId: String)
}
