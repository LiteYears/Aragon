package com.example.aragon.data.repository

import com.example.aragon.data.local.ProjectDao
import com.example.aragon.data.local.ProjectEntity
import com.example.aragon.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao
) {

    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects().map { list ->
        list.map { it.toDomain() }
    }

    fun getProjectFlow(id: String): Flow<Project?> = projectDao.getProjectByIdFlow(id).map { it?.toDomain() }

    suspend fun createProject(name: String, description: String, instructions: String): Project {
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            instructions = instructions
        )
        projectDao.insertProject(ProjectEntity.fromDomain(project))
        return project
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProject(id)
    }
}
