package com.example.aragon.artifacts

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.ArtifactStage
import java.io.File
import java.util.UUID

class ArtifactDetector {

    companion object {
        fun generateStableId(taskId: String, logicalPath: String): String {
            val sanitized = logicalPath.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
            return "art_${taskId}_$sanitized"
        }
    }

    fun scan(taskId: String, resolver: WorkspacePathResolver): List<Artifact> {
        val discovered = mutableListOf<Artifact>()
        val seenPaths = mutableSetOf<String>()

        fun processFile(file: File) {
            if (!file.isFile || file.path.contains(".aragon")) return
            val canonical = file.canonicalPath
            if (!seenPaths.add(canonical)) return

            val logicalPath = resolver.toLogicalPath(file)
            val stage = classifyStage(file, logicalPath)
            val report = ArtifactValidator.validate(file)
            val exists = file.exists() && file.isFile && file.canRead()

            discovered.add(
                Artifact(
                    id = generateStableId(taskId, logicalPath),
                    taskId = taskId,
                    logicalPath = logicalPath,
                    filename = file.name,
                    mimeType = report.mimeType,
                    size = file.length(),
                    createdAt = file.lastModified(),
                    modifiedAt = file.lastModified(),
                    stage = stage,
                    valid = report.isValid && exists,
                    verified = report.isValid && exists,
                    previewable = isPreviewable(report.mimeType),
                    shareable = stage == ArtifactStage.PRODUCT && report.isValid && exists,
                    downloadable = exists,
                    validationDetails = if (exists) report.details else "File missing or inaccessible",
                    existsOnDisk = exists
                )
            )
        }

        if (resolver.workspaceDir.exists()) {
            resolver.workspaceDir.walkTopDown().forEach { processFile(it) }
        }
        if (resolver.artifactsDir.exists()) {
            resolver.artifactsDir.walkTopDown().forEach { processFile(it) }
        }

        return discovered
    }

    private fun classifyStage(file: File, logicalPath: String): ArtifactStage {
        val ext = file.extension.lowercase()
        val path = logicalPath.lowercase()

        // Explicit process folder or scratch patterns
        if (path.startsWith("/process") || path.contains("/process/") ||
            file.name.startsWith("scratch") || file.name.startsWith("temp_") ||
            ext == "tmp" || ext == "log" || ext == "cache"
        ) {
            return ArtifactStage.PROCESS
        }

        // Product artifacts: reports, office documents, PDFs, apps, deliverables
        if (path.startsWith("/artifacts") ||
            ext == "docx" || ext == "xlsx" || ext == "pdf" || ext == "apk" ||
            ext == "pptx" || ext == "zip" || file.name.contains("report") ||
            file.name.contains("summary") || file.name.contains("final")
        ) {
            return ArtifactStage.PRODUCT
        }

        // Default: If in workspace root/subdirectories and valid code/document/data, treat as product deliverable
        return if (ext in listOf("py", "txt", "md", "csv", "json", "png", "jpg", "jpeg", "sh", "html", "css", "js", "ts", "sql", "xml", "yaml", "yml")) {
            ArtifactStage.PRODUCT
        } else {
            ArtifactStage.PROCESS
        }
    }

    private fun isPreviewable(mime: String): Boolean {
        return mime.startsWith("text/") || mime.startsWith("image/") ||
                mime.contains("json") || mime.contains("document") || mime.contains("pdf") ||
                mime.contains("python") || mime.contains("javascript") || mime.contains("markdown")
    }
}
