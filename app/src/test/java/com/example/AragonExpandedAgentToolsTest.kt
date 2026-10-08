package com.example

import com.example.aragon.agent.ApprovalManager
import com.example.aragon.artifacts.ArtifactValidator
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.opensandbox.OpenSandboxClient
import com.example.aragon.opensandbox.OpenSandboxManager
import com.example.aragon.tools.ToolDispatcher
import com.example.aragon.tools.ToolExecutor
import com.example.aragon.tools.ToolRegistry
import com.example.aragon.tools.XlsxGenerator
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AragonExpandedAgentToolsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: WorkspacePathResolver
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var approvalManager: ApprovalManager
    private lateinit var toolDispatcher: ToolDispatcher
    private lateinit var openSandboxClient: OpenSandboxClient

    @Before
    fun setup() {
        val rootDir = tempFolder.newFolder("aragon_expanded_tools_test")
        resolver = WorkspacePathResolver(rootDir)
        toolRegistry = ToolRegistry()
        approvalManager = ApprovalManager()
        openSandboxClient = OpenSandboxClient()

        val processManager = ProcessManager()
        toolExecutor = ToolExecutor(processManager = processManager)
        toolDispatcher = ToolDispatcher(toolExecutor, approvalManager, toolRegistry)
    }

    @Test
    fun `test spreadsheet_create generates valid OpenXML XLSX`() = runBlocking {
        val headers = JSONArray().apply {
            put("Server")
            put("Region")
            put("LatencyMs")
            put("Status")
        }
        val rows = JSONArray().apply {
            put(JSONArray().apply { put("srv-01"); put("us-east"); put("12"); put("ONLINE") })
            put(JSONArray().apply { put("srv-02"); put("eu-west"); put("28"); put("ONLINE") })
            put(JSONArray().apply { put("srv-03"); put("ap-south"); put("45"); put("DEGRADED") })
        }

        val call = ToolCall(
            callId = "call_xlsx_1",
            taskId = "task_expanded",
            toolName = "spreadsheet_create",
            argumentsJson = JSONObject().apply {
                put("filename", "Infrastructure_Audit.xlsx")
                put("sheetName", "Telemetry")
                put("headers", headers.toString())
                put("rows", rows.toString())
            }.toString()
        )

        val result = toolDispatcher.dispatch(call, resolver)
        assertTrue("Spreadsheet creation must succeed", result.success)
        assertEquals(0, result.exitCode)

        val generatedFile = File(resolver.artifactsDir, "Infrastructure_Audit.xlsx")
        assertTrue("XLSX file must physically exist", generatedFile.exists())
        assertTrue("XLSX file must have non-zero size", generatedFile.length() > 0)

        val validation = ArtifactValidator.validate(generatedFile)
        assertTrue("Must be validated as valid OpenXML XLSX: ${validation.details}", validation.isValid)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", validation.mimeType)
    }

    @Test
    fun `test csv_analyze computes accurate statistics`() = runBlocking {
        val csvFile = resolver.resolve("/workspace/sample_metrics.csv")
        csvFile.parentFile?.mkdirs()
        csvFile.writeText(
            """
            service,p99_latency,error_rate
            auth_service,12.5,0.01
            billing_service,35.0,0.05
            gateway,8.5,0.00
            search_service,24.0,0.02
            """.trimIndent()
        )

        val call = ToolCall(
            callId = "call_csv_1",
            taskId = "task_expanded",
            toolName = "csv_analyze",
            argumentsJson = JSONObject().apply {
                put("path", "/workspace/sample_metrics.csv")
                put("column", "p99_latency")
                put("limit", 5)
            }.toString()
        )

        val result = toolDispatcher.dispatch(call, resolver)
        assertTrue(result.success)
        assertTrue(result.stdout.contains("Columns (3): service, p99_latency, error_rate"))
        assertTrue(result.stdout.contains("Count: 4"))
        assertTrue(result.stdout.contains("Min: 8.5"))
        assertTrue(result.stdout.contains("Max: 35.0"))
    }

    @Test
    fun `test json_query extracts targeted properties`() = runBlocking {
        val jsonFile = resolver.resolve("/workspace/cluster_config.json")
        jsonFile.parentFile?.mkdirs()
        jsonFile.writeText(
            """
            {
                "cluster": "prod-us-east",
                "nodes": [
                    {"id": "node-1", "ip": "10.0.0.1", "role": "master"},
                    {"id": "node-2", "ip": "10.0.0.2", "role": "worker"}
                ],
                "telemetry": {
                    "health": "GREEN",
                    "uptime": 99.98
                }
            }
            """.trimIndent()
        )

        // Query nested object
        val call1 = ToolCall(
            callId = "call_json_1",
            taskId = "task_expanded",
            toolName = "json_query",
            argumentsJson = JSONObject().apply {
                put("path", "/workspace/cluster_config.json")
                put("query", "telemetry.health")
            }.toString()
        )
        val res1 = toolDispatcher.dispatch(call1, resolver)
        assertTrue(res1.success)
        assertEquals("GREEN", res1.stdout.trim())

        // Query indexed array object
        val call2 = ToolCall(
            callId = "call_json_2",
            taskId = "task_expanded",
            toolName = "json_query",
            argumentsJson = JSONObject().apply {
                put("path", "/workspace/cluster_config.json")
                put("query", "nodes[1].role")
            }.toString()
        )
        val res2 = toolDispatcher.dispatch(call2, resolver)
        assertTrue(res2.success)
        assertEquals("worker", res2.stdout.trim())
    }

    @Test
    fun `test archive_manage creates and extracts ZIP archives`() = runBlocking {
        // Create files to zip
        val file1 = resolver.resolve("/workspace/file1.txt")
        val file2 = resolver.resolve("/workspace/file2.txt")
        file1.writeText("Content for file 1")
        file2.writeText("Content for file 2")

        // 1. Create ZIP
        val zipCall = ToolCall(
            callId = "call_zip_create",
            taskId = "task_expanded",
            toolName = "archive_manage",
            argumentsJson = JSONObject().apply {
                put("operation", "create_zip")
                put("archivePath", "/artifacts/test_bundle.zip")
                put("sourcePaths", JSONArray().apply {
                    put("/workspace/file1.txt")
                    put("/workspace/file2.txt")
                }.toString())
            }.toString()
        )
        val zipRes = toolDispatcher.dispatch(zipCall, resolver)
        assertTrue(zipRes.success)

        val zipFile = File(resolver.artifactsDir, "test_bundle.zip")
        assertTrue(zipFile.exists())
        val validation = ArtifactValidator.validate(zipFile)
        assertTrue(validation.isValid)
        assertEquals("application/zip", validation.mimeType)

        // 2. Extract ZIP
        val extractCall = ToolCall(
            callId = "call_zip_extract",
            taskId = "task_expanded",
            toolName = "archive_manage",
            argumentsJson = JSONObject().apply {
                put("operation", "extract_zip")
                put("archivePath", "/artifacts/test_bundle.zip")
                put("destinationDir", "/workspace/extracted")
            }.toString()
        )
        val extractRes = toolDispatcher.dispatch(extractCall, resolver)
        assertTrue(extractRes.success)

        val extractedFile1 = resolver.resolve("/workspace/extracted/file1.txt")
        val extractedFile2 = resolver.resolve("/workspace/extracted/file2.txt")
        assertTrue(extractedFile1.exists())
        assertEquals("Content for file 1", extractedFile1.readText())
        assertTrue(extractedFile2.exists())
        assertEquals("Content for file 2", extractedFile2.readText())
    }

    @Test
    fun `test process manager enhanced command execution with head, tail, and wc`() = runBlocking {
        val testFile = resolver.resolve("/workspace/lines.txt")
        testFile.writeText((1..20).joinToString("\n") { "Line $it" })

        val processManager = ProcessManager()

        // Test head
        val headResult = processManager.execute("head -n 5 lines.txt", resolver.workspaceDir)
        assertEquals(0, headResult.exitCode)
        val headLines = headResult.stdout.lines()
        assertEquals(5, headLines.size)
        assertEquals("Line 1", headLines.first())
        assertEquals("Line 5", headLines.last())

        // Test tail
        val tailResult = processManager.execute("tail -n 3 lines.txt", resolver.workspaceDir)
        assertEquals(0, tailResult.exitCode)
        val tailLines = tailResult.stdout.lines()
        assertEquals(3, tailLines.size)
        assertEquals("Line 18", tailLines.first())
        assertEquals("Line 20", tailLines.last())

        // Test wc
        val wcResult = processManager.execute("wc lines.txt", resolver.workspaceDir)
        assertEquals(0, wcResult.exitCode)
        assertTrue(wcResult.stdout.contains("20"))
    }
}
