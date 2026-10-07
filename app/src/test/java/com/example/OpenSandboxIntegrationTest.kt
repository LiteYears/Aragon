package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.aragon.computer.ProcessManager
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.data.preferences.PreferencesManager
import com.example.aragon.domain.model.ExecutionBackend
import com.example.aragon.domain.model.ToolCall
import com.example.aragon.opensandbox.OpenSandboxClient
import com.example.aragon.opensandbox.OpenSandboxManager
import com.example.aragon.tools.ToolExecutor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class OpenSandboxIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var openSandboxClient: OpenSandboxClient
    private lateinit var openSandboxManager: OpenSandboxManager
    private lateinit var resolver: WorkspacePathResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        preferencesManager = PreferencesManager(context)
        openSandboxClient = OpenSandboxClient()
        openSandboxManager = OpenSandboxManager(preferencesManager, openSandboxClient)
        val rootDir = tempFolder.newFolder("opensandbox_test_workspace")
        resolver = WorkspacePathResolver(rootDir)
    }

    @Test
    fun `test OpenSandbox client standalone lifecycle`() = runBlocking {
        // 1. Create sandbox
        val createResult = openSandboxClient.createSandbox(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            imageUri = "opensandbox/python:3.12"
        )
        assertTrue(createResult.isSuccess)
        val sandbox = createResult.getOrThrow()
        assertNotNull(sandbox.id)
        assertEquals("opensandbox/python:3.12", sandbox.image)
        assertEquals("running", sandbox.status)

        // 2. Write file into sandbox
        val writeRes = openSandboxClient.writeFile(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            sandboxId = sandbox.id,
            path = "/workspace/greeting.txt",
            content = "Hello from OpenSandbox!"
        )
        assertTrue(writeRes.isSuccess)

        // 3. Read file back
        val readRes = openSandboxClient.readFile(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            sandboxId = sandbox.id,
            path = "/workspace/greeting.txt"
        )
        assertTrue(readRes.isSuccess)
        assertEquals("Hello from OpenSandbox!", readRes.getOrThrow())

        // 4. Execute command inside sandbox
        val execResult = openSandboxClient.executeCommand(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            sandboxId = sandbox.id,
            command = "echo 'OpenSandbox microVM active'",
            workingDir = "/workspace"
        )
        assertTrue(execResult.isSuccess)
        val output = execResult.getOrThrow()
        assertEquals(0, output.exitCode)
        assertTrue(output.stdout.contains("OpenSandbox microVM active"))

        // 5. Execute python code inside sandbox
        val codeResult = openSandboxClient.executePythonCode(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            sandboxId = sandbox.id,
            code = "print('OpenSandbox Code Execution')"
        )
        assertTrue(codeResult.isSuccess)
        assertEquals(0, codeResult.getOrThrow().exitCode)

        // 6. Delete sandbox
        val deleteRes = openSandboxClient.deleteSandbox(
            serverUrl = "http://10.0.2.2:8080/v1",
            apiKey = "",
            sandboxId = sandbox.id
        )
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun `test OpenSandboxManager orchestration and health check`() = runBlocking {
        preferencesManager.setExecutionBackend(ExecutionBackend.OPEN_SANDBOX)
        preferencesManager.setOpenSandboxServerUrl("http://10.0.2.2:8080/v1")
        preferencesManager.setOpenSandboxImage("opensandbox/python:3.12")

        val health = openSandboxManager.checkHealth()
        assertTrue(health.isAvailable)
        assertEquals(ExecutionBackend.OPEN_SANDBOX, health.backendType)

        // Spawn sandbox
        val spawnResult = openSandboxManager.spawnSandbox()
        assertTrue(spawnResult.isSuccess)
        val active = openSandboxManager.activeSandbox.value
        assertNotNull(active)
        assertEquals(active!!.id, preferencesManager.openSandboxActiveId.value)

        // Execute command through manager
        val cmdRes = openSandboxManager.executeCommand("pwd", "/workspace")
        assertEquals(0, cmdRes.exitCode)
        assertEquals("/workspace", cmdRes.stdout.trim())

        // Terminate
        val termRes = openSandboxManager.terminateSandbox()
        assertTrue(termRes.isSuccess)
    }

    @Test
    fun `test ProcessManager routes through OpenSandbox when backend enabled`() = runBlocking {
        preferencesManager.setExecutionBackend(ExecutionBackend.OPEN_SANDBOX)

        val processManager = ProcessManager(
            openSandboxManagerProvider = { openSandboxManager },
            executionBackendProvider = { preferencesManager.executionBackend.value }
        )

        val result = processManager.execute(
            command = "echo 'Sandboxed Execution'",
            workingDir = resolver.workspaceDir
        )

        assertEquals(0, result.exitCode)
        assertTrue(result.isSandbox)
        assertTrue(result.stdout.contains("Sandboxed Execution"))
    }

    @Test
    fun `test ToolExecutor sandbox_manage tool execution`() = runBlocking {
        val processManager = ProcessManager(
            openSandboxManagerProvider = { openSandboxManager },
            executionBackendProvider = { preferencesManager.executionBackend.value }
        )

        val toolExecutor = ToolExecutor(
            processManager = processManager,
            openSandboxManager = openSandboxManager,
            preferencesManager = preferencesManager
        )

        // Call sandbox_manage with action "status"
        val statusResult = toolExecutor.executeTool(
            callId = "call_sb_status",
            taskId = "task_test_sb",
            toolName = "sandbox_manage",
            argumentsJson = """{"action":"status"}""",
            resolver = resolver
        )

        assertTrue(statusResult.success)
        assertEquals(0, statusResult.exitCode)
        assertTrue(statusResult.stdout.contains("OpenSandbox Runtime Status"))

        // Call sandbox_manage with action "spawn"
        val spawnResult = toolExecutor.executeTool(
            callId = "call_sb_spawn",
            taskId = "task_test_sb",
            toolName = "sandbox_manage",
            argumentsJson = """{"action":"spawn","image":"opensandbox/python:3.12"}""",
            resolver = resolver
        )

        assertTrue(spawnResult.success)
        assertEquals(0, spawnResult.exitCode)
        assertTrue(spawnResult.stdout.contains("Successfully spawned OpenSandbox instance"))
    }
}
