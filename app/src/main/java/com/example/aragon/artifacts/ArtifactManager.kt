package com.example.aragon.artifacts

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.data.local.ArtifactDao
import com.example.aragon.data.local.ArtifactEntity
import com.example.aragon.domain.model.Artifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ArtifactManager(
    private val artifactDao: ArtifactDao
) {

    fun getArtifactsForTask(taskId: String): Flow<List<Artifact>> {
        return artifactDao.getArtifactsForTaskFlow(taskId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getAllArtifacts(): Flow<List<Artifact>> {
        return artifactDao.getAllArtifactsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun discoverArtifacts(taskId: String, resolver: WorkspacePathResolver): List<Artifact> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<Artifact>()
        val targets = listOf(resolver.workspaceDir, resolver.artifactsDir)

        for (dir in targets) {
            if (!dir.exists()) continue
            dir.walkTopDown()
                .filter { it.isFile && !it.name.startsWith(".") && !it.path.contains(".aragon") }
                .forEach { file ->
                    val logicalPath = resolver.toLogicalPath(file)
                    val report = ArtifactValidator.validate(file)

                    val existing = artifactDao.getArtifactsForTask(taskId)
                        .find { it.logicalPath == logicalPath }

                    val artifact = if (existing != null) {
                        existing.copy(
                            size = file.length(),
                            modifiedAt = file.lastModified(),
                            valid = report.isValid,
                            verified = report.isValid,
                            mimeType = report.mimeType,
                            validationDetails = report.details
                        )
                    } else {
                        ArtifactEntity(
                            id = UUID.randomUUID().toString(),
                            taskId = taskId,
                            logicalPath = logicalPath,
                            filename = file.name,
                            mimeType = report.mimeType,
                            size = file.length(),
                            createdAt = System.currentTimeMillis(),
                            modifiedAt = file.lastModified(),
                            valid = report.isValid,
                            verified = report.isValid,
                            previewable = isPreviewable(report.mimeType),
                            shareable = true,
                            downloadable = true,
                            validationDetails = report.details
                        )
                    }

                    artifactDao.insertArtifact(artifact)
                    discovered.add(artifact.toDomain())
                }
        }
        discovered
    }

    private fun isPreviewable(mime: String): Boolean {
        return mime.startsWith("text/") || mime.startsWith("image/") ||
                mime.contains("json") || mime.contains("document")
    }
}
