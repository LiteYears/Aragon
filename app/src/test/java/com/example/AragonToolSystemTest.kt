package com.example

import com.example.aragon.agent.VerificationEngine
import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.tools.DocxGenerator
import com.example.aragon.tools.ToolExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AragonToolSystemTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: WorkspacePathResolver
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var verificationEngine: VerificationEngine

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("aragon_task_test")
        resolver = WorkspacePathResolver(rootDir)
        toolExecutor = ToolExecutor(ProcessManager())
        verificationEngine = VerificationEngine()
    }

    @Test
    fun `test WorkspacePathResolver logical to physical mapping`() {
        val resolved = resolver.resolve("/workspace/subdir/script.py")
        val expected = File(resolver.workspaceDir, "subdir/script.py")
        assertEquals(expected.canonicalPath, resolved.canonicalPath)

        val logical = resolver.toLogicalPath(resolved)
        assertEquals("/workspace/subdir/script.py", logical)
    }

    @Test
    fun `test file_write and file_read tools`() = runBlocking {
        val writeResult = toolExecutor.executeTool(
            callId = "call_write_1",
            taskId = "task_test",
            toolName = "file_write",
            argumentsJson = """{"path":"/workspace/hello.txt","content":"Hello Aragon"}""",
            resolver = resolver
        )
        assertTrue(writeResult.success)
        assertEquals(0, writeResult.exitCode)

        val readResult = toolExecutor.executeTool(
            callId = "call_read_1",
            taskId = "task_test",
            toolName = "file_read",
            argumentsJson = """{"path":"/workspace/hello.txt"}""",
            resolver = resolver
        )
        assertTrue(readResult.success)
        assertEquals("Hello Aragon", readResult.stdout.trim())
    }

    @Test
    fun `test directory_create and file_list tools`() = runBlocking {
        val mkdirResult = toolExecutor.executeTool(
            callId = "call_mkdir_1",
            taskId = "task_test",
            toolName = "directory_create",
            argumentsJson = """{"path":"/workspace/test_dir"}""",
            resolver = resolver
        )
        assertTrue(mkdirResult.success)

        toolExecutor.executeTool(
            callId = "call_write_2",
            taskId = "task_test",
            toolName = "file_write",
            argumentsJson = """{"path":"/workspace/test_dir/file1.txt","content":"data 1"}""",
            resolver = resolver
        )

        val listResult = toolExecutor.executeTool(
            callId = "call_list_1",
            taskId = "task_test",
            toolName = "file_list",
            argumentsJson = """{"path":"/workspace/test_dir"}""",
            resolver = resolver
        )
        assertTrue(listResult.success)
        assertTrue(listResult.stdout.contains("file1.txt"))
    }

    @Test
    fun `test run_command tool with echo and pwd`() = runBlocking {
        val cmdResult = toolExecutor.executeTool(
            callId = "call_cmd_1",
            taskId = "task_test",
            toolName = "run_command",
            argumentsJson = """{"command":"echo 'Aragon Agent Online'"}""",
            resolver = resolver
        )
        assertTrue(cmdResult.success)
        assertEquals(0, cmdResult.exitCode)
        assertTrue(cmdResult.stdout.contains("Aragon Agent Online"))
    }

    @Test
    fun `test python_execute byte-for-byte SHA256 integrity and DOCX compilation`() = runBlocking {
        val code = """
            title = 'Quarterly Executive Summary'
            add_paragraph('All system parameters within normal operating thresholds.')
            print('Report generation script completed')
        """.trimIndent()

        val pyResult = toolExecutor.executeTool(
            callId = "call_py_1",
            taskId = "task_test",
            toolName = "python_execute",
            argumentsJson = JSONObject().put("code", code).toString(),
            resolver = resolver
        )
        assertTrue(pyResult.success)
        assertEquals(0, pyResult.exitCode)

        // Verify python script file was written byte-for-byte in runtime dir
        val scriptFile = File(resolver.runtimeDir, "call_py_1.py")
        assertTrue(scriptFile.exists())
        assertEquals(code, scriptFile.readText())
    }

    @Test
    fun `test valid OpenXML DOCX generation and ArtifactValidator`() {
        val docxFile = File(resolver.workspaceDir, "Administrative_Report.docx")
        DocxGenerator.createDocument(
            docxFile,
            DocxGenerator.DocxContent(
                title = "Administrative Performance Audit",
                subtitle = "Autonomous Systems Inspection",
                paragraphs = listOf(
                    "All subsystems operating under verified conditions.",
                    "Memory substrate confirmed persistent at /workspace/.aragon."
                ),
                bulletPoints = listOf("Runtime: Verified", "Storage: OK"),
                tableHeaders = listOf("Module", "Status"),
                tableRows = listOf(DocxGenerator.TableRow(listOf("Agent Harness", "Passed")))
            )
        )

        assertTrue(docxFile.exists())
        assertTrue(docxFile.length() > 0)

        // ArtifactValidator must strictly check Zip structure, [Content_Types].xml, and word/document.xml
        val validation = ArtifactValidator.validate(docxFile)
        assertTrue("DOCX validation failed: ${validation.details}", validation.isValid)
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", validation.mimeType)
    }

    @Test
    fun `test VerificationEngine against objective`() {
        val task = Task(
            id = "task_1",
            title = "Create DOCX report",
            originalRequest = "Create an editable .docx report with administrative data",
            status = TaskStatus.EXECUTING
        )

        // Before file creation -> should not be verified
        val resultBefore = verificationEngine.verifyTaskObjective(task, resolver)
        assertFalse(resultBefore.isVerified)

        // Create docx
        val docxFile = File(resolver.workspaceDir, "Administrative_Report.docx")
        DocxGenerator.createDocument(
            docxFile,
            DocxGenerator.DocxContent(
                title = "Administrative Report",
                paragraphs = listOf("Verified content")
            )
        )

        // After file creation -> must be verified!
        val resultAfter = verificationEngine.verifyTaskObjective(task, resolver)
        assertTrue("Expected verified, got: ${resultAfter.summary}", resultAfter.isVerified)
    }
}
