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

data class GoalCriterion(
    val id: String,
    val description: String,
    val targetType: String,
    val isSatisfied: Boolean = false,
    val evidence: String = ""
)

data class VerificationResult(
    val isVerified: Boolean,
    val summary: String,
    val checks: List<VerificationCheck>,
    val criteria: List<GoalCriterion> = emptyList()
)

class VerificationEngine {

    fun deriveGoalCriteria(task: Task): List<GoalCriterion> {
        val list = mutableListOf<GoalCriterion>()
        val req = task.originalRequest.lowercase()

        if (req.contains(".docx") || req.contains("docx") || req.contains("word document")) {
            list.add(
                GoalCriterion(
                    id = "crit_docx",
                    description = "Generate verified OpenXML DOCX administrative report with valid body and paragraph structures",
                    targetType = "DOCX"
                )
            )
        }
        if (req.contains(".xlsx") || req.contains("xlsx") || req.contains("spreadsheet") || req.contains("excel")) {
            list.add(
                GoalCriterion(
                    id = "crit_xlsx",
                    description = "Generate verified XLSX spreadsheet data file",
                    targetType = "XLSX"
                )
            )
        }
        if (req.contains(".pdf") || req.contains("pdf")) {
            list.add(
                GoalCriterion(
                    id = "crit_pdf",
                    description = "Generate non-empty PDF document deliverable",
                    targetType = "PDF"
                )
            )
        }
        if (req.contains(".py") || req.contains("python") || req.contains("script")) {
            list.add(
                GoalCriterion(
                    id = "crit_python",
                    description = "Generate and execute functional Python script (.py)",
                    targetType = "SCRIPT"
                )
            )
        }

        // Cleaned request for named files (e.g. hello.txt, data.csv)
        val cleanedRequest = task.originalRequest
            .replace(Regex("https?://[^\\s]+"), " ")
            .replace(Regex("\\b[a-zA-Z0-9.-]+\\.(git|com|org|net|io|edu|gov|co)\\b", RegexOption.IGNORE_CASE), " ")

        val fileRegex = "\\b([a-zA-Z0-9_-]+\\.(txt|csv|json|md|py|sh|xml|yaml|yml|html|css|js))\\b".toRegex(RegexOption.IGNORE_CASE)
        val requestedFilenames = fileRegex.findAll(cleanedRequest).map { it.groupValues[1] }.toSet()

        for (filename in requestedFilenames) {
            list.add(
                GoalCriterion(
                    id = "crit_file_$filename",
                    description = "Create non-empty target deliverable: $filename",
                    targetType = "NAMED_FILE"
                )
            )
        }

        if (list.isEmpty()) {
            list.add(
                GoalCriterion(
                    id = "crit_general",
                    description = "Generate verified product deliverable files in /artifacts",
                    targetType = "WORKSPACE_DELIVERABLE"
                )
            )
        }

        return list
    }

    fun verifyTaskObjective(task: Task, resolver: WorkspacePathResolver): VerificationResult {
        val checks = mutableListOf<VerificationCheck>()
        val request = task.originalRequest.lowercase()
        val criteria = deriveGoalCriteria(task).toMutableList()

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
        val expectedPython = request.contains(".py") || request.contains("python") || request.contains("script")
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
        val cleanedRequest = task.originalRequest
            .replace(Regex("https?://[^\\s]+"), " ")
            .replace(Regex("\\b[a-zA-Z0-9.-]+\\.(git|com|org|net|io|edu|gov|co)\\b", RegexOption.IGNORE_CASE), " ")

        val fileRegex = "\\b([a-zA-Z0-9_-]+\\.(txt|csv|json|md|py|sh|xml|yaml|yml|html|css|js|docx|xlsx|pdf))\\b".toRegex(RegexOption.IGNORE_CASE)
        val requestedFilenames = fileRegex.findAll(cleanedRequest).map { it.groupValues[1] }.toSet()

        for (targetName in requestedFilenames) {
            if (targetName.endsWith(".docx", ignoreCase = true) ||
                targetName.endsWith(".xlsx", ignoreCase = true) ||
                targetName.endsWith(".pdf", ignoreCase = true) ||
                targetName.endsWith(".py", ignoreCase = true)
            ) {
                continue
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

        // Evaluate criteria list satisfaction
        val evaluatedCriteria = criteria.map { crit ->
            val passed = when (crit.targetType) {
                "DOCX" -> checks.filter { it.name.startsWith("DOCX") }.let { it.isNotEmpty() && it.all { c -> c.passed } }
                "XLSX" -> checks.filter { it.name.startsWith("XLSX") }.let { it.isNotEmpty() && it.all { c -> c.passed } }
                "PDF" -> checks.filter { it.name.startsWith("PDF") }.let { it.isNotEmpty() && it.all { c -> c.passed } }
                "SCRIPT" -> checks.filter { it.name.startsWith("Python") }.let { it.isNotEmpty() && it.all { c -> c.passed } }
                "NAMED_FILE" -> {
                    val fname = crit.id.removePrefix("crit_file_")
                    checks.any { it.name.contains(fname, ignoreCase = true) && it.passed }
                }
                else -> hasDeliverableArtifacts
            }
            crit.copy(
                isSatisfied = passed,
                evidence = if (passed) "Satisfied by workspace artifact" else "Pending creation"
            )
        }

        val hasFailingChecks = checks.any { !it.passed }
        val allChecksPassed = checks.isNotEmpty() && !hasFailingChecks
        val fallbackPassed = checks.isEmpty() && hasDeliverableArtifacts

        val verified = !hasFailingChecks && (allChecksPassed || fallbackPassed)

        val summary = if (verified) {
            if (checks.isNotEmpty()) {
                "Objective verified: ${checks.count { it.passed }}/${checks.size} verification checks passed."
            } else {
                "Objective verified: Workspace outputs and deliverables validated (${allDiscoveredFiles.size} files ready)."
            }
        } else {
            if (hasFailingChecks) {
                "Objective verification unmet: ${checks.filter { !it.passed }.joinToString("; ") { it.details }}"
            } else {
                "Objective verification unmet: No valid deliverable artifacts found on disk."
            }
        }

        return VerificationResult(
            isVerified = verified,
            summary = summary,
            checks = checks,
            criteria = evaluatedCriteria
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
