package com.example.aragon.artifacts

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.data.local.ArtifactDao
import com.example.aragon.data.local.ArtifactEntity
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.ArtifactStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ArtifactManager(
    private val artifactDao: ArtifactDao,
    private val detector: ArtifactDetector = ArtifactDetector()
) {

    fun getArtifactsForTask(taskId: String): Flow<List<Artifact>> {
        return artifactDao.getArtifactsForTaskFlow(taskId).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getProductArtifactsForTask(taskId: String): Flow<List<Artifact>> {
        return artifactDao.getArtifactsForTaskFlow(taskId).map { list ->
            list.filter { it.stage == ArtifactStage.PRODUCT }.map { it.toDomain() }
        }
    }

    fun getAllArtifacts(): Flow<List<Artifact>> {
        return artifactDao.getAllArtifactsFlow().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun discoverArtifacts(taskId: String, resolver: WorkspacePathResolver): List<Artifact> = withContext(Dispatchers.IO) {
        // Automatically sync any files created in OpenSandbox microVM into local workspace
        runCatching {
            com.example.aragon.AragonApplication.instance.openSandboxManager.syncSandboxToLocal(resolver)
        }

        val detected = detector.scan(taskId, resolver)
        val result = mutableListOf<Artifact>()

        for (art in detected) {
            val existing = artifactDao.getArtifactsForTask(taskId)
                .find { it.logicalPath == art.logicalPath }

            val entity = if (existing != null) {
                existing.copy(
                    size = art.size,
                    modifiedAt = art.modifiedAt,
                    valid = art.valid,
                    verified = art.verified,
                    mimeType = art.mimeType,
                    stage = art.stage,
                    validationDetails = art.validationDetails
                )
            } else {
                ArtifactEntity.fromDomain(art)
            }

            artifactDao.insertArtifact(entity)
            result.add(entity.toDomain())
        }
        result
    }
}
