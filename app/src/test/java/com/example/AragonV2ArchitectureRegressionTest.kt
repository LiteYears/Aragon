package com.example

import com.example.aragon.agent.ApprovalManager
import com.example.aragon.agent.CheckpointManager
import com.example.aragon.agent.ContextManager
import com.example.aragon.agent.CoordinatorAgent
import com.example.aragon.agent.LoopDetector
import com.example.aragon.agent.LoopType
import com.example.aragon.agent.ReplanDecisionType
import com.example.aragon.agent.Replanner
import com.example.aragon.agent.VerificationEngine
import com.example.aragon.artifacts.ArtifactDetector
import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ApprovalStatus
import com.example.aragon.domain.model.ApprovalType
import com.example.aragon.domain.model.ArtifactStage
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.tools.DocxGenerator
import com.example.aragon.tools.TextEditorTool
import com.example.aragon.tools.ToolDispatcher
import com.example.aragon.tools.ToolExecutor
import com.example.aragon.tools.ToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AragonV2ArchitectureRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: WorkspacePathResolver
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var toolDispatcher: ToolDispatcher
    private lateinit var approvalManager: ApprovalManager
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var verificationEngine: VerificationEngine
    private lateinit var contextManager: ContextManager
    private lateinit var loopDetector: LoopDetector
    private lateinit var replanner: Replanner
    private lateinit var checkpointManager: CheckpointManager
    private lateinit var artifactDetector: ArtifactDetector

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("aragon_v2_regression")
        resolver = WorkspacePathResolver(rootDir)
        approvalManager = ApprovalManager()
        toolRegistry = ToolRegistry()
        toolExecutor = ToolExecutor(ProcessManager())
        toolDispatcher = ToolDispatcher(toolExecutor, approvalManager, toolRegistry)
        verificationEngine = VerificationEngine()
        contextManager = ContextManager()
        loopDetector = LoopDetector()
        replanner = Replanner()
        checkpointManager = CheckpointManager()
        artifactDetector = ArtifactDetector()
    }

    @Test
    fun `Test 1 - Valid DOCX compilation, table structure and OpenXML integrity`() {
        val docxFile = File(resolver.artifactsDir, "Executive_Admin_Audit.docx")
        DocxGenerator.createDocument(
            outputFile = docxFile,
            content = DocxGenerator.DocxContent(
                title = "Administrative Performance Audit",
                subtitle = "Autonomous Systems Inspection",
                paragraphs = listOf(
                    "All subsystems operating under verified conditions.",
                    "Memory substrate confirmed persistent at /workspace/.aragon."
                ),
                bulletPoints = listOf("Runtime: Verified", "Storage: OK", "Integrity: High"),
                tableHeaders = listOf("Module", "Status", "Code"),
                tableRows = listOf(
                    DocxGenerator.TableRow(listOf("Agent Harness", "Active", "MOD-01")),
                    DocxGenerator.TableRow(listOf("Local Computer", "Verified", "MOD-02"))
                )
            )
        )

        assertTrue("DOCX file should exist", docxFile.exists())
        assertTrue("DOCX file size should be greater than 0", docxFile.length() > 0)

        // 1. Structural Zip & OpenXML Check
        val report = ArtifactValidator.validate(docxFile)
        assertTrue("ArtifactValidator report must be valid: ${report.details}", report.isValid)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", report.mimeType)

        // 2. OpenXML entries inspection
        ZipFile(docxFile).use { zip ->
            assertNotNull("Must contain [Content_Types].xml", zip.getEntry("[Content_Types].xml"))
            assertNotNull("Must contain word/document.xml", zip.getEntry("word/document.xml"))
            val docXml = zip.getInputStream(zip.getEntry("word/document.xml")!!).bufferedReader().readText()
            assertTrue("Must contain paragraph XML tags", docXml.contains("<w:p"))
            assertTrue("Must contain table XML tags", docXml.contains("<w:tbl"))
            assertTrue("Must contain text from payload", docXml.contains("Administrative Performance Audit"))
        }

        // 3. Verifier pass
        val task = Task(
            id = "t_1",
            title = "Create DOCX",
            originalRequest = "Create an executive .docx report with administrative data and tables"
        )
        val verification = verificationEngine.verifyTaskObjective(task, resolver)
        assertTrue("Deterministic verification must succeed: ${verification.summary}", verification.isVerified)
    }

    @Test
    fun `Test 2 - Broken Python Recovery via Failure Memory and Replanner`() = runBlocking {
        val task = Task(
            id = "t_2",
            title = "Execute complex script",
            originalRequest = "Run statistical transformation script in Python"
        )

        // Simulate Python failure (e.g. missing external module)
        val brokenResult = ToolResult(
            callId = "call_py_fail",
            taskId = task.id,
            success = false,
            exitCode = 1,
            stdout = "",
            stderr = "ModuleNotFoundError: No module named 'scipy_unsupported'",
            durationMs = 120L,
            workingDirectory = "/workspace"
        )

        contextManager.recordFailedApproach(
            strategy = "python_scipy_external",
            error = brokenResult.stderr,
            context = "Importing external scipy library"
        )

        val failedApproaches = contextManager.getFailedApproaches()
        assertEquals(1, failedApproaches.size)

        // Replanner receives failed approach and observations
        val decision = replanner.analyzeAndReplan(
            task = task,
            currentPlan = listOf(PlanStep(id = "s1", taskId = task.id, stepNumber = 1, title = "Execute script", status = StepStatus.IN_PROGRESS)),
            failedApproaches = failedApproaches,
            recentResults = listOf(Pair("python_execute", brokenResult)),
            artifacts = emptyList(),
            verificationResult = null,
            resolver = resolver
        )

        assertEquals("Replanner must switch strategy when external module fails", ReplanDecisionType.CHANGE_STRATEGY, decision.type)
        assertTrue("Replanner explanation must address failure", decision.explanation.contains("Switching strategy") || decision.explanation.contains("dependency"))
    }

    @Test
    fun `Test 3 - False Success where command exits 0 but produces no required artifact`() = runBlocking {
        val task = Task(
            id = "t_3",
            title = "Create administrative report",
            originalRequest = "Create report.docx with quarterly administrative metrics"
        )

        // Tool executes echo command with exitCode = 0
        val echoCall = ToolCall(
            callId = "call_echo_1",
            taskId = task.id,
            toolName = "run_command",
            argumentsJson = """{"command":"echo 'Finished writing report.docx successfully'"}"""
        )
        val result = toolDispatcher.dispatch(echoCall, resolver)
        assertEquals(0, result.exitCode)
        assertTrue(result.success)

        // Verifier must NOT trust exit code 0 or model claims when required artifact is missing
        val verification = verificationEngine.verifyTaskObjective(task, resolver)
        assertFalse("Verification must fail because report.docx does not actually exist", verification.isVerified)
        assertTrue(verification.summary.contains("unmet") || verification.summary.contains("No .docx"))
    }

    @Test
    fun `Test 4 - Unified Logical Workspace across multiple tools`() = runBlocking {
        // Tool 1: Create file via file_write at /workspace/sub/config.json
        val writeCall = ToolCall(
            callId = "call_w4",
            taskId = "t_4",
            toolName = "file_write",
            argumentsJson = """{"path":"/workspace/sub/config.json","content":"{\"agent\":\"aragon\"}"}"""
        )
        val writeResult = toolDispatcher.dispatch(writeCall, resolver)
        assertTrue(writeResult.success)

        // Tool 2: Read file via text_editor tool at same logical path
        val textEditor = TextEditorTool()
        val readResult = textEditor.execute(
            callId = "call_ed4",
            taskId = "t_4",
            operation = "view",
            path = "/workspace/sub/config.json",
            content = null,
            targetContent = null,
            replacementContent = null,
            startLine = 1,
            lineCount = 10,
            resolver = resolver
        )

        assertTrue("text_editor must find the file created by file_write", readResult.success)
        assertTrue("text_editor content must match", readResult.stdout.contains("\"agent\":\"aragon\""))
    }

    @Test
    fun `Test 5 - Automatic Artifact Discovery of externally created files`() {
        // Create file directly on filesystem inside workspace without agent awareness
        val externalFile = File(resolver.workspaceDir, "discovered_dataset.csv")
        externalFile.writeText("id,name,value\n1,alpha,100\n2,beta,200\n")

        val discovered = artifactDetector.scan("t_5", resolver)
        assertTrue("ArtifactDetector must discover external files", discovered.any { it.filename == "discovered_dataset.csv" })
        val match = discovered.first { it.filename == "discovered_dataset.csv" }
        assertEquals("/workspace/discovered_dataset.csv", match.logicalPath)
        assertTrue(match.valid)
        assertEquals("text/csv", match.mimeType)
    }

    @Test
    fun `Test 6 - Process vs Product Artifact Stage Separation`() {
        // Create scratch.py in /workspace/process/
        val scratchFile = File(resolver.processDir, "scratch.py")
        scratchFile.parentFile?.mkdirs()
        scratchFile.writeText("print('intermediate step')")

        // Create raw.json in /workspace/process/
        val rawJson = File(resolver.processDir, "raw.json")
        rawJson.writeText("{\"status\":\"processing\"}")

        // Create final_report.docx in /workspace/artifacts/
        val finalDoc = File(resolver.artifactsDir, "final_report.docx")
        finalDoc.parentFile?.mkdirs()
        DocxGenerator.createDocument(
            finalDoc,
            DocxGenerator.DocxContent(title = "Final Product", paragraphs = listOf("Completed"))
        )

        val scanned = artifactDetector.scan("t_6", resolver)
        val scratchArtifact = scanned.find { it.filename == "scratch.py" }
        val rawArtifact = scanned.find { it.filename == "raw.json" }
        val finalDocArtifact = scanned.find { it.filename == "final_report.docx" }

        assertNotNull("scratch.py must be discovered", scratchArtifact)
        assertNotNull("raw.json must be discovered", rawArtifact)
        assertNotNull("final_report.docx must be discovered", finalDocArtifact)

        assertEquals("scratch.py must be PROCESS stage", ArtifactStage.PROCESS, scratchArtifact!!.stage)
        assertEquals("raw.json must be PROCESS stage", ArtifactStage.PROCESS, rawArtifact!!.stage)
        assertEquals("final_report.docx must be PRODUCT stage", ArtifactStage.PRODUCT, finalDocArtifact!!.stage)
    }

    @Test
    fun `Test 7 - Loop Detection on repeated failures and oscillation`() {
        val loopDet = LoopDetector(maxRepeatedFailures = 3, maxSameActions = 3)
        val dummyFail = ToolResult(
            callId = "call_f",
            taskId = "t_7",
            success = false,
            exitCode = 1,
            stdout = "",
            stderr = "Connection refused to remote endpoint",
            durationMs = 50L,
            workingDirectory = "/workspace"
        )

        // Repeat identical failing tool call 3 times
        val r1 = loopDet.record("web_fetch", """{"url":"http://down.local"}""", dummyFail)
        assertFalse(r1.isLooping)
        val r2 = loopDet.record("web_fetch", """{"url":"http://down.local"}""", dummyFail)
        assertFalse(r2.isLooping)
        val r3 = loopDet.record("web_fetch", """{"url":"http://down.local"}""", dummyFail)

        assertTrue("LoopDetector must detect exact repeated failure on 3rd attempt", r3.isLooping)
        assertEquals(LoopType.EXACT_REPETITION, r3.loopType)
    }

    @Test
    fun `Test 8 - Context Compression and transfer snapshot`() {
        val task = Task(id = "t_8", title = "Long Running Task", originalRequest = "Generate multi-chapter analysis")
        val messages = mutableListOf<com.example.aragon.llm.LlmMessage>()
        for (i in 1..20) {
            messages.add(
                com.example.aragon.llm.LlmMessage(
                    role = com.example.aragon.llm.LlmRole.TOOL,
                    content = "Detailed command output from step $i with lots of lines...".repeat(50)
                )
            )
        }

        contextManager.recordFailedApproach("approach_alpha", "Timeout error", "step 3")
        val snapshotLogicalPath = contextManager.compressContext(resolver, task, messages)

        assertEquals("/workspace/.aragon/memory/context_summary.md", snapshotLogicalPath)
        val memoryText = contextManager.retrieveRelevantMemory(resolver)
        assertTrue("Memory must retain compressed snapshot", memoryText.contains("Compressed Context Transfer Snapshot"))
        assertTrue("Memory must retain recorded failures", memoryText.contains("approach_alpha"))
    }

    @Test
    fun `Test 9 - App Restart and Checkpoint Restore`() {
        val task = Task(
            id = "t_9",
            title = "Task with checkpoint",
            originalRequest = "Prepare market analysis",
            status = TaskStatus.EXECUTING,
            iteration = 4,
            currentObjective = "Compile financial tables"
        )
        val steps = listOf(
            PlanStep(id = "s1", taskId = task.id, stepNumber = 1, title = "Step 1", status = StepStatus.COMPLETED),
            PlanStep(id = "s2", taskId = task.id, stepNumber = 2, title = "Step 2", status = StepStatus.IN_PROGRESS)
        )

        val chkId = checkpointManager.saveCheckpoint(task, steps, emptyList(), resolver)
        assertNotNull(chkId)

        // Simulate app restart: reload from fresh CheckpointManager instance
        val freshManager = CheckpointManager()
        val restored = freshManager.loadLatestCheckpoint(resolver)

        assertNotNull("Checkpoint must be restored successfully", restored)
        assertEquals(task.id, restored!!.taskId)
        assertEquals("EXECUTING", restored.taskStatus)
        assertEquals(4, restored.iteration)
        assertEquals("Compile financial tables", restored.currentObjective)
        assertEquals(2, restored.planStepsCount)
    }

    @Test
    fun `Test 10 - Human Approval Gate on Destructive Operations`() = runBlocking {
        // Attempt sensitive destructive command with NORMAL autonomy level
        val dangerousCall = ToolCall(
            callId = "call_danger_1",
            taskId = "t_10",
            toolName = "run_command",
            argumentsJson = """{"command":"rm -rf /workspace/project_data"}"""
        )

        val result = toolDispatcher.dispatch(dangerousCall, resolver, AutonomyLevel.NORMAL)
        assertTrue("Operation must be cancelled pending approval", result.cancelled)
        assertEquals("AWAITING_APPROVAL", result.terminationReason)

        val pending = approvalManager.getPendingForTask("t_10")
        assertEquals(1, pending.size)
        val req = pending.first()
        assertEquals(ApprovalType.DESTRUCTIVE_FILE_OPERATION, req.type)
        assertEquals(ApprovalStatus.PENDING, req.status)

        // User approves in UI
        approvalManager.grantApproval(req.id)
        val updatedReq = approvalManager.getRequest(req.id)
        assertEquals(ApprovalStatus.APPROVED, updatedReq?.status)
    }
}
