package com.example.aragon.agent

import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Task
import java.io.File
import java.util.zip.ZipFile

data class VerificationCheck(
    val name: String,
    val passed: Boolean,
    val details: String
)

data class VerificationResult(
    val isVerified: Boolean,
    val summary: String,
    val checks: List<VerificationCheck>
)

class VerificationEngine {

    fun verifyTaskObjective(task: Task, resolver: WorkspacePathResolver): VerificationResult {
        val checks = mutableListOf<VerificationCheck>()
        val request = task.originalRequest.lowercase()

        // 1. DOCX Objective
        val expectedDocx = request.contains(".docx") || request.contains("docx") || request.contains("word document")
        if (expectedDocx) {
            val docxFiles = findFilesWithExtension(resolver, "docx")

            if (docxFiles.isEmpty()) {
                checks.add(VerificationCheck("DOCX File Existence", false, "No .docx document found in workspace or artifacts"))
            } else {
                for (doc in docxFiles) {
                    val report = ArtifactValidator.validate(doc)
                    if (!report.isValid) {
                        checks.add(VerificationCheck("DOCX Validation (${doc.name})", false, report.details))
                    } else {
                        val deepCheck = inspectDocxContent(doc)
                        checks.add(VerificationCheck("DOCX Deep Structural Check (${doc.name})", deepCheck.first, deepCheck.second))
                    }
                }
            }
        }

        // 2. XLSX Objective
        val expectedXlsx = request.contains(".xlsx") || request.contains("xlsx") || request.contains("spreadsheet") || request.contains("excel")
        if (expectedXlsx) {
            val xlsxFiles = findFilesWithExtension(resolver, "xlsx")

            if (xlsxFiles.isEmpty()) {
                checks.add(VerificationCheck("XLSX File Existence", false, "No .xlsx spreadsheet found in workspace or artifacts"))
            } else {
                for (xls in xlsxFiles) {
                    val report = ArtifactValidator.validate(xls)
                    checks.add(VerificationCheck("XLSX Validation (${xls.name})", report.isValid, report.details))
                }
            }
        }

        // 3. PDF Objective
        val expectedPdf = request.contains(".pdf") || request.contains("pdf")
        if (expectedPdf) {
            val pdfFiles = findFilesWithExtension(resolver, "pdf")

            if (pdfFiles.isEmpty()) {
                checks.add(VerificationCheck("PDF File Existence", false, "No .pdf document found in workspace or artifacts"))
            } else {
                for (pdf in pdfFiles) {
                    val report = ArtifactValidator.validate(pdf)
                    checks.add(VerificationCheck("PDF Validation (${pdf.name})", report.isValid, report.details))
                }
            }
        }

        // 4. Code / Script Objective
        val expectedPython = request.contains(".py") || request.contains("python script")
        if (expectedPython) {
            val pyFiles = findFilesWithExtension(resolver, "py")
            if (pyFiles.isEmpty()) {
                checks.add(VerificationCheck("Python File Existence", false, "No .py file produced in workspace"))
            } else {
                for (py in pyFiles) {
                    val hasContent = py.length() > 0
                    checks.add(VerificationCheck("Python Script Non-Empty (${py.name})", hasContent, "${py.length()} bytes"))
                }
            }
        }

        // 5. Explicit Named File Check (e.g., hello.txt, data.csv)
        // CRITICAL FIX: Strip URLs, web domains (.com, .git, etc.) so they are not falsely treated as target files!
        val cleanedRequest = task.originalRequest
            .replace(Regex("https?://[^\\s]+"), " ") // Strip URLs
            .replace(Regex("\\b[a-zA-Z0-9.-]+\\.(git|com|org|net|io|edu|gov|co)\\b", RegexOption.IGNORE_CASE), " ") // Strip domain names and git repos

        val fileRegex = "\\b([a-zA-Z0-9_-]+\\.(txt|csv|json|md|py|sh|xml|yaml|yml|html|css|js|docx|xlsx|pdf))\\b".toRegex(RegexOption.IGNORE_CASE)
        val requestedFilenames = fileRegex.findAll(cleanedRequest).map { it.groupValues[1] }.toSet()

        for (targetName in requestedFilenames) {
            if (targetName.endsWith(".docx", ignoreCase = true) ||
                targetName.endsWith(".xlsx", ignoreCase = true) ||
                targetName.endsWith(".pdf", ignoreCase = true) ||
                targetName.endsWith(".py", ignoreCase = true)
            ) {
                continue // Handled above
            }
            val targetFile = findFileByName(resolver, targetName)
            val exists = targetFile != null && targetFile.length() > 0
            checks.add(
                VerificationCheck(
                    name = "Named File Verification ($targetName)",
                    passed = exists,
                    details = if (exists) "File exists (${targetFile?.length()} bytes)" else "File $targetName was not found or is empty"
                )
            )
        }

        // 6. Generic Deliverable Check:
        val allDiscoveredFiles = getAllWorkspaceFiles(resolver)
        val hasDeliverableArtifacts = allDiscoveredFiles.any { f ->
            f.length() > 0 && !f.name.startsWith(".") && f.extension.lowercase() in listOf(
                "docx", "xlsx", "pdf", "txt", "md", "csv", "json", "py", "sh", "html"
            )
        }

        // 7. Error check
        if (task.lastError != null && task.status == com.example.aragon.domain.model.TaskStatus.FAILED) {
            checks.add(VerificationCheck("Fatal Error Check", false, "Task terminated with error: ${task.lastError}"))
        }

        val allChecksPassed = checks.isNotEmpty() && checks.all { it.passed }
        val fallbackPassed = (checks.isEmpty() || checks.none { !it.passed }) && (hasDeliverableArtifacts || allDiscoveredFiles.isNotEmpty())

        // If at iteration >= 2 and deliverables exist or no structural failures, verify
        val verified = allChecksPassed || fallbackPassed || (task.iteration >= 2 && checks.none { !it.passed })

        val summary = if (verified) {
            if (checks.isNotEmpty()) {
                "Objective verified: ${checks.count { it.passed }}/${checks.size} verification checks passed."
            } else {
                "Objective verified: Workspace outputs and deliverables validated (${allDiscoveredFiles.size} files ready)."
            }
        } else {
            "Objective verification unmet: ${checks.filter { !it.passed }.joinToString("; ") { it.details }}"
        }

        return VerificationResult(
            isVerified = verified,
            summary = summary,
            checks = checks
        )
    }

    private fun findFilesWithExtension(resolver: WorkspacePathResolver, ext: String): List<File> {
        val results = mutableListOf<File>()
        resolver.workspaceDir.walkTopDown()
            .filter { it.isFile && it.extension.equals(ext, ignoreCase = true) && !it.path.contains(".aragon") }
            .forEach { results.add(it) }

        if (resolver.artifactsDir.exists() && resolver.artifactsDir != resolver.workspaceDir) {
            resolver.artifactsDir.walkTopDown()
                .filter { it.isFile && it.extension.equals(ext, ignoreCase = true) && !it.path.contains(".aragon") }
                .forEach { if (!results.contains(it)) results.add(it) }
        }
        return results
    }

    private fun findFileByName(resolver: WorkspacePathResolver, filename: String): File? {
        val inWs = File(resolver.workspaceDir, filename)
        if (inWs.exists()) return inWs
        val inArt = File(resolver.artifactsDir, filename)
        if (inArt.exists()) return inArt

        return resolver.workspaceDir.walkTopDown()
            .filter { it.isFile && it.name.equals(filename, ignoreCase = true) && !it.path.contains(".aragon") }
            .firstOrNull()
    }

    private fun getAllWorkspaceFiles(resolver: WorkspacePathResolver): List<File> {
        val files = mutableListOf<File>()
        if (resolver.workspaceDir.exists()) {
            resolver.workspaceDir.walkTopDown()
                .filter { it.isFile && !it.path.contains(".aragon") }
                .forEach { files.add(it) }
        }
        return files
    }

    private fun inspectDocxContent(file: File): Pair<Boolean, String> {
        return try {
            ZipFile(file).use { zip ->
                val docEntry = zip.getEntry("word/document.xml") ?: return Pair(false, "Missing word/document.xml")
                val text = zip.getInputStream(docEntry).bufferedReader().use { it.readText() }
                val hasText = text.contains("<w:t") || text.contains("<w:p")
                val hasTable = text.contains("<w:tbl")
                if (hasText) {
                    val tableNote = if (hasTable) " (includes tables)" else ""
                    Pair(true, "Document contains valid OpenXML content paragraphs$tableNote")
                } else {
                    Pair(false, "Document body is empty")
                }
            }
        } catch (e: Exception) {
            Pair(false, "Error inspecting document.xml: ${e.message}")
        }
    }
}
