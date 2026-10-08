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
            list.filter { it.stage == ArtifactStage.PRODUCT && it.existsOnDisk && it.valid }.map { it.toDomain() }
        }
    }

    fun getAllArtifacts(): Flow<List<Artifact>> {
        return artifactDao.getAllArtifactsFlow().map { list ->
            list.filter { it.existsOnDisk && it.valid }.map { it.toDomain() }
        }
    }

    suspend fun registerArtifactFromTool(
        taskId: String,
        logicalPath: String,
        sourceToolInvocationId: String,
        resolver: WorkspacePathResolver
    ): Artifact? = withContext(Dispatchers.IO) {
        val file = resolver.resolve(logicalPath)
        if (!file.exists() || !file.isFile || !file.canRead() || file.length() == 0L) {
            return@withContext null
        }

        val report = ArtifactValidator.validate(file)
        val stage = if (logicalPath.startsWith("/artifacts") ||
            file.extension.lowercase() in listOf("docx", "xlsx", "pdf", "apk", "zip", "pptx", "py", "txt", "md")
        ) ArtifactStage.PRODUCT else ArtifactStage.PROCESS

        val stableId = ArtifactDetector.generateStableId(taskId, logicalPath)
        val existing = artifactDao.getArtifactByLogicalPath(taskId, logicalPath)

        val entity = ArtifactEntity(
            id = existing?.id ?: stableId,
            taskId = taskId,
            logicalPath = logicalPath,
            filename = file.name,
            mimeType = report.mimeType,
            size = file.length(),
            createdAt = existing?.createdAt ?: file.lastModified(),
            modifiedAt = file.lastModified(),
            valid = report.isValid,
            verified = report.isValid,
            previewable = true,
            shareable = stage == ArtifactStage.PRODUCT && report.isValid,
            downloadable = report.isValid && file.exists(),
            validationDetails = report.details,
            stage = stage,
            sourceToolInvocationId = sourceToolInvocationId,
            existsOnDisk = true
        )

        artifactDao.insertArtifact(entity)
        entity.toDomain()
    }

    suspend fun discoverArtifacts(
        taskId: String,
        resolver: WorkspacePathResolver,
        activeToolInvocationId: String? = null
    ): List<Artifact> = withContext(Dispatchers.IO) {
        // Automatically sync any files created in OpenSandbox microVM into local workspace
        runCatching {
            com.example.aragon.AragonApplication.instance.openSandboxManager.syncSandboxToLocal(resolver)
        }

        val detected = detector.scan(taskId, resolver)
        val existingEntities = artifactDao.getArtifactsForTask(taskId)
        val existingMap = existingEntities.associateBy { it.logicalPath }
        val detectedPaths = detected.map { it.logicalPath }.toSet()

        // 1. Reconcile missing or deleted files in database
        for (existing in existingEntities) {
            val realFile = resolver.resolve(existing.logicalPath)
            if (!realFile.exists() || !realFile.isFile) {
                if (existing.existsOnDisk || existing.valid) {
                    val updatedMissing = existing.copy(
                        existsOnDisk = false,
                        valid = false,
                        verified = false,
                        downloadable = false,
                        validationDetails = "File missing or deleted from disk"
                    )
                    artifactDao.updateArtifact(updatedMissing)
                }
            }
        }

        // 2. Insert or update existing detected artifacts
        val result = mutableListOf<Artifact>()
        for (art in detected) {
            val existing = existingMap[art.logicalPath]

            val entity = if (existing != null) {
                existing.copy(
                    size = art.size,
                    modifiedAt = art.modifiedAt,
                    valid = art.valid,
                    verified = art.verified,
                    mimeType = art.mimeType,
                    stage = art.stage,
                    downloadable = art.existsOnDisk && art.valid,
                    existsOnDisk = art.existsOnDisk,
                    validationDetails = art.validationDetails,
                    sourceToolInvocationId = existing.sourceToolInvocationId ?: activeToolInvocationId
                )
            } else {
                ArtifactEntity.fromDomain(
                    art.copy(
                        sourceToolInvocationId = activeToolInvocationId
                    )
                )
            }

            artifactDao.insertArtifact(entity)
            result.add(entity.toDomain())
        }
        result
    }
}
