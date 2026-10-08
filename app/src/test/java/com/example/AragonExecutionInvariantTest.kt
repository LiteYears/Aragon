package com.example

import com.example.aragon.agent.ApprovalManager
import com.example.aragon.artifacts.ArtifactDetector
import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.AutonomyLevel
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.domain.model.ToolExecutionStatus
import com.example.aragon.llm.ToolCallParser
import com.example.aragon.tools.DocxGenerator
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

/**
 * Forensic Verification Test Suite for Aragon Agent Execution Invariant:
 *
 * Agent intends to execute action
 *         ↓
 * Agent produces a real structured tool call
 *         ↓
 * Tool dispatcher receives it
 *         ↓
 * Correct tool is actually invoked
 *         ↓
 * Tool executes in the environment
 *         ↓
 * Real result is returned
 *         ↓
 * Result is captured by agent state
 *         ↓
 * Artifacts are detected/registered when applicable
 *         ↓
 * Artifact is persisted
 *         ↓
 * Artifact is exposed to the user
 *         ↓
 * Artifact can be inspected/downloaded
 *
 * Invariant: The system NEVER reports execution or artifact creation when no real tool was invoked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AragonExecutionInvariantTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: WorkspacePathResolver
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var approvalManager: ApprovalManager
    private lateinit var toolDispatcher: ToolDispatcher
    private lateinit var artifactDetector: ArtifactDetector

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("aragon_forensic_invariant_test")
        resolver = WorkspacePathResolver(rootDir)
        toolRegistry = ToolRegistry()
        approvalManager = ApprovalManager()
        toolExecutor = ToolExecutor(ProcessManager())
        toolDispatcher = ToolDispatcher(toolExecutor, approvalManager, toolRegistry)
        artifactDetector = ArtifactDetector()
    }

    @Test
    fun `Test 1 - Reject conversational claim without tool call (Case A and O)`() {
        val conversationalText = "I will now create the administrative report at /workspace/report.docx and execute the analysis."
        val toolCalls = ToolCallParser.parseFromContent(conversationalText)
        assertTrue("Parser must NOT extract tool calls from conversational text", toolCalls.isEmpty())

        val isClaiming = ToolCallParser.isClaimingExecutionWithoutToolCall(conversationalText)
        assertTrue("System must detect unverified prose claim", isClaiming)
    }

    @Test
    fun `Test 2 - Parse structured tool call correctly from XML, Markdown, and JSON (Case B)`() {
        // XML format
        val xmlText = "<tool_call>{\"name\":\"file_write\",\"arguments\":{\"path\":\"/workspace/test.txt\",\"content\":\"hello\"}}</tool_call>"
        val parsedXml = ToolCallParser.parseFromContent(xmlText)
        assertEquals(1, parsedXml.size)
        assertEquals("file_write", parsedXml[0].name)

        // Markdown format
        val mdText = "```tool_call\n{\"name\":\"run_command\",\"arguments\":{\"command\":\"echo 42\"}}\n```"
        val parsedMd = ToolCallParser.parseFromContent(mdText)
        assertEquals(1, parsedMd.size)
        assertEquals("run_command", parsedMd[0].name)

        // Raw JSON format
        val jsonText = "{\"name\":\"directory_create\",\"arguments\":{\"path\":\"/workspace/output\"}}"
        val parsedJson = ToolCallParser.parseFromContent(jsonText)
        assertEquals(1, parsedJson.size)
        assertEquals("directory_create", parsedJson[0].name)
    }

    @Test
    fun `Test 3 - Real tool invocation executes on disk and updates status DISPATCHED to SUCCEEDED`() = runBlocking {
        val statusList = mutableListOf<ToolExecutionStatus>()
        val call = ToolCall(
            callId = "call_write_verified",
            taskId = "task_forensic",
            toolName = "file_write",
            argumentsJson = """{"path":"/workspace/verified_file.txt","content":"Verified physical write on disk"}"""
        )

        val result = toolDispatcher.dispatch(
            toolCall = call,
            resolver = resolver,
            autonomyLevel = AutonomyLevel.FULL,
            onStatusChange = { statusList.add(it) }
        )

        // Verify status lifecycle
        assertTrue("Must transition through DISPATCHED", statusList.contains(ToolExecutionStatus.DISPATCHED))
        assertTrue("Must transition through RUNNING", statusList.contains(ToolExecutionStatus.RUNNING))
        assertEquals(ToolExecutionStatus.SUCCEEDED, result.status)
        assertTrue(result.success)

        // Verify disk reality
        val physicalFile = resolver.resolve("/workspace/verified_file.txt")
        assertTrue("File must physically exist on disk", physicalFile.exists())
        assertEquals("Verified physical write on disk", physicalFile.readText())
    }

    @Test
    fun `Test 4 - Reject malformed JSON arguments gracefully (Case E)`() = runBlocking {
        val statusList = mutableListOf<ToolExecutionStatus>()
        val call = ToolCall(
            callId = "call_malformed",
            taskId = "task_forensic",
            toolName = "file_write",
            argumentsJson = "{ malformed json "
        )

        val result = toolDispatcher.dispatch(
            toolCall = call,
            resolver = resolver,
            onStatusChange = { statusList.add(it) }
        )

        assertFalse(result.success)
        assertEquals("INVALID_JSON_ARGUMENTS", result.errorType)
        assertEquals(ToolExecutionStatus.FAILED, result.status)
    }

    @Test
    fun `Test 5 - Reject unregistered tool gracefully (Case D)`() = runBlocking {
        val call = ToolCall(
            callId = "call_unknown",
            taskId = "task_forensic",
            toolName = "magic_wand_tool",
            argumentsJson = "{}"
        )

        val result = toolDispatcher.dispatch(call, resolver)
        assertFalse(result.success)
        assertEquals("UNKNOWN_TOOL", result.errorType)
        assertEquals(ToolExecutionStatus.FAILED, result.status)
    }

    @Test
    fun `Test 6 - Reject missing required arguments gracefully (Case E)`() = runBlocking {
        // file_write requires 'path' and 'content'
        val call = ToolCall(
            callId = "call_missing_arg",
            taskId = "task_forensic",
            toolName = "file_write",
            argumentsJson = """{"path":"/workspace/file.txt"}""" // missing 'content'
        )

        val result = toolDispatcher.dispatch(call, resolver)
        assertFalse(result.success)
        assertEquals("MISSING_REQUIRED_ARGUMENT", result.errorType)
        assertEquals(ToolExecutionStatus.FAILED, result.status)
    }

    @Test
    fun `Test 7 - Artifact detection discovers real files on disk with valid validation (Case H, I, J)`() = runBlocking {
        // 1. Create a document via document_create tool
        val docCall = ToolCall(
            callId = "call_doc_1",
            taskId = "task_forensic_doc",
            toolName = "document_create",
            argumentsJson = JSONObject().apply {
                put("path", "/workspace/artifacts/Forensic_Summary.docx")
                put("title", "Forensic Investigation Report")
                put("sections", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("heading", "Audit Findings")
                        put("content", "All tool executions correspond to physical state.")
                    })
                })
            }.toString()
        )

        val toolResult = toolDispatcher.dispatch(docCall, resolver)
        assertTrue(toolResult.success)

        // 2. Artifact detector scans the workspace
        val artifacts = artifactDetector.detectArtifacts(
            taskId = "task_forensic_doc",
            resolver = resolver,
            toolResults = listOf(toolResult)
        )

        assertEquals(1, artifacts.size)
        val docArtifact = artifacts[0]
        assertEquals("Forensic_Summary.docx", docArtifact.name)
        assertTrue("Artifact must exist on disk", docArtifact.existsOnDisk)
        assertTrue("Artifact must be validated as valid deliverable", docArtifact.valid)
        assertEquals("call_doc_1", docArtifact.sourceToolInvocationId)
        assertTrue("Artifact must be inspectable/downloadable", docArtifact.downloadable)

        // 3. Physical file inspection
        val realFile = resolver.resolve(docArtifact.logicalPath)
        assertTrue(realFile.exists())
        assertTrue(realFile.length() > 0)
    }

    @Test
    fun `Test 8 - ArtifactValidator rejects phantom non-existent file (Case K)`() {
        val nonExistentFile = File(resolver.workspaceDir, "phantom_file.docx")
        assertFalse(nonExistentFile.exists())

        val validation = ArtifactValidator.validate(nonExistentFile)
        assertFalse("Phantom file must not be validated", validation.isValid)
        assertTrue(validation.details.contains("does not exist") || validation.details.contains("Missing"))
    }
}
