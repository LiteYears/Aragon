package com.example

import com.example.aragon.domain.model.FailedApproach
import com.example.aragon.agent.ReplanDecisionType
import com.example.aragon.agent.Replanner
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.mcp.McpManager
import com.example.aragon.tools.PlaywrightEngine
import com.example.aragon.tools.TextEditorTool
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AragonAgentSubsystemResilienceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var taskRootDir: File
    private lateinit var resolver: WorkspacePathResolver

    @Before
    fun setup() {
        taskRootDir = tempFolder.newFolder("task_root")
        resolver = WorkspacePathResolver(taskRootDir)
    }

    @Test
    fun testIterativeBuildingUponSameFile() {
        val editor = TextEditorTool()
        val path = "/workspace/architecture_spec.md"

        // 1. Initial Creation
        val r1 = editor.execute(
            callId = "c1",
            taskId = "t1",
            operation = "create",
            path = path,
            content = "# Aragon Architecture\n\n## Section 1: Overview\nInitial system description.",
            targetContent = null,
            replacementContent = null,
            startLine = null,
            lineCount = null,
            resolver = resolver
        )
        assertTrue(r1.success)

        val targetFile = resolver.resolve(path)
        assertTrue(targetFile.exists())
        assertTrue(targetFile.readText().contains("Initial system description."))

        // 2. Append new section without clobbering
        val r2 = editor.execute(
            callId = "c2",
            taskId = "t1",
            operation = "append",
            path = path,
            content = "## Section 2: MCP Protocol\nModel Context Protocol subsystem details.",
            targetContent = null,
            replacementContent = null,
            startLine = null,
            lineCount = null,
            resolver = resolver
        )
        assertTrue(r2.success)
        assertTrue(targetFile.readText().contains("## Section 1: Overview"))
        assertTrue(targetFile.readText().contains("## Section 2: MCP Protocol"))

        // 3. Fuzzy whitespace patch
        val r3 = editor.execute(
            callId = "c3",
            taskId = "t1",
            operation = "patch",
            path = path,
            content = null,
            targetContent = "Initial system description.",
            replacementContent = "Initial system description with enhanced resilience and telemetry.",
            startLine = null,
            lineCount = null,
            resolver = resolver
        )
        assertTrue(r3.success)
        assertTrue(targetFile.readText().contains("with enhanced resilience and telemetry."))

        // 4. Line replacement
        val r4 = editor.execute(
            callId = "c4",
            taskId = "t1",
            operation = "replace_lines",
            path = path,
            content = null,
            targetContent = null,
            replacementContent = "## Section 2: MCP Protocol (High Performance)",
            startLine = 5,
            lineCount = 1,
            resolver = resolver
        )
        assertTrue(r4.success)
        assertTrue(targetFile.readText().contains("High Performance"))
    }

    @Test
    fun testPlaywrightEngineScreenshotAndMarkdown() = runBlocking {
        val engine = PlaywrightEngine()
        val outputFile = resolver.resolve("/artifacts/playwright_test.png")

        // Test screenshot generation on a session
        val res = engine.execute(
            callId = "call_pw_1",
            taskId = "task_pw",
            action = "screenshot",
            url = "https://example.com",
            selector = null,
            text = null,
            script = null,
            outputPath = "/artifacts/playwright_test.png",
            waitFor = null,
            resolver = resolver
        )

        // The engine successfully writes a valid PNG artifact
        assertTrue(res.success)
        assertTrue(outputFile.exists())
        assertTrue(outputFile.length() > 100)

        // Verify PNG magic bytes
        val bytes = outputFile.readBytes()
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1]) // 'P'
        assertEquals(0x4E.toByte(), bytes[2]) // 'N'
        assertEquals(0x47.toByte(), bytes[3]) // 'G'
    }

    @Test
    fun testMcpSubsystemDiscoveryAndExecution() = runBlocking {
        val mcpManager = McpManager()

        // 1. List embedded servers
        val servers = mcpManager.listServers()
        assertTrue(servers.any { it.name == "aragon-filesystem" })
        assertTrue(servers.any { it.name == "aragon-playwright" })
        assertTrue(servers.any { it.name == "aragon-data" })

        // 2. Discover tools
        val tools = mcpManager.listTools()
        assertTrue(tools.any { it.second.name == "fs_read_file" })
        assertTrue(tools.any { it.second.name == "fs_write_file" })

        // 3. Execute MCP fs_write_file
        val writeArgs = JSONObject().apply {
            put("path", "/workspace/mcp_test.txt")
            put("content", "Generated through Model Context Protocol (MCP) tool execution")
        }
        val writeResult = mcpManager.callTool(
            serverName = "aragon-filesystem",
            toolName = "fs_write_file",
            arguments = writeArgs,
            callId = "call_mcp_1",
            taskId = "task_mcp",
            resolver = resolver
        )
        assertTrue(writeResult.success)

        val createdFile = resolver.resolve("/workspace/mcp_test.txt")
        assertTrue(createdFile.exists())
        assertEquals("Generated through Model Context Protocol (MCP) tool execution", createdFile.readText())

        // 4. Execute MCP fs_read_file
        val readArgs = JSONObject().apply {
            put("path", "/workspace/mcp_test.txt")
        }
        val readResult = mcpManager.callTool(
            serverName = "aragon-filesystem",
            toolName = "fs_read_file",
            arguments = readArgs,
            callId = "call_mcp_2",
            taskId = "task_mcp",
            resolver = resolver
        )
        assertTrue(readResult.success)
        assertTrue(readResult.stdout.contains("Generated through Model Context Protocol"))
    }

    @Test
    fun testReplannerErrorResilienceAndBackoff() {
        val replanner = Replanner()
        val task = Task(id = "t1", projectId = "p1", title = "Generate quarterly document report", originalRequest = "create report")
        val steps = listOf(PlanStep(id = "s1", taskId = "t1", title = "Build report", stepNumber = 1, status = StepStatus.IN_PROGRESS))

        // Scenario 1: Operation Timed Out -> Should calculate exponential backoff
        val timeoutResult = ToolResult(
            callId = "c1",
            taskId = "t1",
            success = false,
            exitCode = 124,
            stdout = "",
            stderr = "Command timed out after 30000ms",
            durationMs = 30000,
            timedOut = true
        )
        val decision1 = replanner.analyzeAndReplan(
            task = task,
            currentPlan = steps,
            failedApproaches = emptyList(),
            recentResults = listOf("run_command" to timeoutResult),
            artifacts = emptyList(),
            verificationResult = null,
            resolver = resolver
        )
        assertEquals(ReplanDecisionType.EXPONENTIAL_BACKOFF, decision1.type)
        assertTrue(decision1.backoffDelayMs > 0L)

        // Scenario 2: Python missing library -> Should switch to built-in document_create
        val pythonMissingLibResult = ToolResult(
            callId = "c2",
            taskId = "t1",
            success = false,
            exitCode = 1,
            stdout = "",
            stderr = "ModuleNotFoundError: No module named 'docx'",
            durationMs = 120
        )
        val decision2 = replanner.analyzeAndReplan(
            task = task,
            currentPlan = steps,
            failedApproaches = emptyList(),
            recentResults = listOf("python_execute" to pythonMissingLibResult),
            artifacts = emptyList(),
            verificationResult = null,
            resolver = resolver
        )
        assertEquals(ReplanDecisionType.FALLBACK_BUILTIN, decision2.type)
        assertEquals("document_create", decision2.suggestedTool)
    }
}
