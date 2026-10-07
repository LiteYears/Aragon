package com.example.aragon.artifacts

import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Artifact
import com.example.aragon.domain.model.ArtifactStage
import java.io.File
import java.util.UUID

class ArtifactDetector {

    fun scan(taskId: String, resolver: WorkspacePathResolver): List<Artifact> {
        val discovered = mutableListOf<Artifact>()
        val baseDir = resolver.workspaceDir
        if (!baseDir.exists()) return emptyList()

        baseDir.walkTopDown()
            .filter { it.isFile && !it.path.contains(".aragon") }
            .forEach { file ->
                val logicalPath = resolver.toLogicalPath(file)
                val stage = classifyStage(file, logicalPath)
                val report = ArtifactValidator.validate(file)

                discovered.add(
                    Artifact(
                        id = UUID.randomUUID().toString(),
                        taskId = taskId,
                        logicalPath = logicalPath,
                        filename = file.name,
                        mimeType = report.mimeType,
                        size = file.length(),
                        createdAt = file.lastModified(),
                        modifiedAt = file.lastModified(),
                        stage = stage,
                        valid = report.isValid,
                        verified = report.isValid,
                        previewable = isPreviewable(report.mimeType),
                        shareable = stage == ArtifactStage.PRODUCT && report.isValid,
                        downloadable = true,
                        validationDetails = report.details
                    )
                )
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
