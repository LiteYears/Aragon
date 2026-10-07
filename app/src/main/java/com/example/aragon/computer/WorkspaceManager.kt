package com.example.aragon.computer

import android.content.Context
import java.io.File

class WorkspaceManager(private val context: Context) {
    val aragonRoot: File = File(context.filesDir, "Aragon").apply { mkdirs() }
    val tasksRoot: File = File(aragonRoot, "tasks").apply { mkdirs() }
    val projectsRoot: File = File(aragonRoot, "projects").apply { mkdirs() }

    fun getTaskRootDir(taskId: String): File {
        val dir = File(tasksRoot, taskId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectRootDir(projectId: String): File {
        val dir = File(projectsRoot, projectId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPathResolver(taskId: String): WorkspacePathResolver {
        return WorkspacePathResolver(getTaskRootDir(taskId))
    }

    fun initializeTaskWorkspace(taskId: String, projectId: String? = null): WorkspacePathResolver {
        val resolver = getPathResolver(taskId)
        // If tied to a project, initialize task workspace with project files if present
        if (!projectId.isNullOrBlank()) {
            val projectDir = File(projectsRoot, projectId)
            val projectWs = File(projectDir, "workspace")
            if (projectWs.exists() && projectWs.isDirectory) {
                projectWs.listFiles()?.forEach { file ->
                    val target = File(resolver.workspaceDir, file.name)
                    if (!target.exists()) {
                        file.copyRecursively(target, overwrite = false)
                    }
                }
            }
        }
        return resolver
    }

    fun deleteTaskWorkspace(taskId: String): Boolean {
        val dir = File(tasksRoot, taskId)
        return if (dir.exists()) dir.deleteRecursively() else true
    }
}
