package com.example.aragon.agent

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskMetrics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CheckpointData(
    val checkpointId: String,
    val taskId: String,
    val timestamp: Long,
    val taskStatus: String,
    val iteration: Int,
    val currentObjective: String,
    val planStepsCount: Int,
    val artifactsCount: Int,
    val metrics: TaskMetrics
)

class CheckpointManager {

    fun saveCheckpoint(
        task: Task,
        planSteps: List<PlanStep>,
        artifacts: List<Artifact>,
        resolver: WorkspacePathResolver
    ): String {
        val checkpointId = "chk_${System.currentTimeMillis()}"
        val checkpointsDir = resolver.checkpointsDir
        checkpointsDir.mkdirs()

        val json = JSONObject()
        json.put("checkpointId", checkpointId)
        json.put("taskId", task.id)
        json.put("timestamp", System.currentTimeMillis())
        json.put("taskStatus", task.status.name)
        json.put("iteration", task.iteration)
        json.put("currentObjective", task.currentObjective)
        json.put("originalRequest", task.originalRequest)

        // Plan steps
        val stepsArr = JSONArray()
        for (s in planSteps) {
            val stepObj = JSONObject()
            stepObj.put("id", s.id)
            stepObj.put("stepNumber", s.stepNumber)
            stepObj.put("title", s.title)
            stepObj.put("status", s.status.name)
            stepObj.put("verified", s.verified)
            stepsArr.put(stepObj)
        }
        json.put("planSteps", stepsArr)

        // Artifacts
        val artArr = JSONArray()
        for (a in artifacts) {
            val artObj = JSONObject()
            artObj.put("id", a.id)
            artObj.put("filename", a.filename)
            artObj.put("logicalPath", a.logicalPath)
            artObj.put("size", a.size)
            artObj.put("valid", a.valid)
            artArr.put(artObj)
        }
        json.put("artifacts", artArr)

        // Metrics
        val metObj = JSONObject()
        metObj.put("toolCallsCount", task.metrics.toolCallsCount)
        metObj.put("successfulToolCalls", task.metrics.successfulToolCalls)
        metObj.put("filesCreated", task.metrics.filesCreated)
        metObj.put("artifactsProduced", task.metrics.artifactsProduced)
        json.put("metrics", metObj)

        val targetFile = File(checkpointsDir, "$checkpointId.json")
        targetFile.writeText(json.toString(2))

        val latestFile = File(checkpointsDir, "latest.json")
        latestFile.writeText(json.toString(2))

        return checkpointId
    }

    fun loadLatestCheckpoint(resolver: WorkspacePathResolver): CheckpointData? {
        val latestFile = File(resolver.checkpointsDir, "latest.json")
        if (!latestFile.exists() || latestFile.length() == 0L) return null

        return try {
            val json = JSONObject(latestFile.readText())
            val metObj = json.optJSONObject("metrics") ?: JSONObject()

            CheckpointData(
                checkpointId = json.optString("checkpointId"),
                taskId = json.optString("taskId"),
                timestamp = json.optLong("timestamp"),
                taskStatus = json.optString("taskStatus"),
                iteration = json.optInt("iteration"),
                currentObjective = json.optString("currentObjective"),
                planStepsCount = json.optJSONArray("planSteps")?.length() ?: 0,
                artifactsCount = json.optJSONArray("artifacts")?.length() ?: 0,
                metrics = TaskMetrics(
                    toolCallsCount = metObj.optInt("toolCallsCount"),
                    successfulToolCalls = metObj.optInt("successfulToolCalls"),
                    filesCreated = metObj.optInt("filesCreated"),
                    artifactsProduced = metObj.optInt("artifactsProduced")
                )
            )
        } catch (e: Exception) {
            null
        }
    }

    fun listCheckpoints(resolver: WorkspacePathResolver): List<File> {
        return resolver.checkpointsDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("chk_") && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
