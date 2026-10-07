package com.example

import com.example.aragon.agent.LoopDetector
import com.example.aragon.agent.LoopType
import com.example.aragon.agent.ReplanDecisionType
import com.example.aragon.agent.Replanner
import com.example.aragon.agent.VerificationEngine
import com.example.aragon.computer.WorkspacePathResolver
import com.example.aragon.domain.model.PlanStep
import com.example.aragon.domain.model.StepStatus
import com.example.aragon.domain.model.Task
import com.example.aragon.domain.model.TaskStatus
import com.example.aragon.domain.model.ToolResult
import com.example.aragon.tools.DocxGenerator
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AragonIterationLoopTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var resolver: WorkspacePathResolver
    private lateinit var verificationEngine: VerificationEngine
    private lateinit var loopDetector: LoopDetector
    private lateinit var replanner: Replanner

    @Before
    fun setUp() {
        val rootDir = tempFolder.newFolder("aragon_loop_test")
        resolver = WorkspacePathResolver(rootDir)
        verificationEngine = VerificationEngine()
        loopDetector = LoopDetector(maxRepeatedFailures = 3, maxSameActions = 2, maxNoProgressSteps = 3)
        replanner = Replanner()
    }

    @Test
    fun `Explicit Goal Criteria Derived and Satisfied Immediately Stops Loop`() {
        val task = Task(
            id = "task_docx_goal",
            title = "Executive Report",
            originalRequest = "Compile verified executive audit report.docx with performance metrics"
        )

        // 1. Derive criteria
        val criteria = verificationEngine.deriveGoalCriteria(task)
        assertTrue("Must derive DOCX criterion", criteria.any { it.targetType == "DOCX" })

        // 2. Before deliverable exists -> goal NOT satisfied
        val preResult = verificationEngine.verifyTaskObjective(task, resolver)
        assertFalse("Goal must not be satisfied before artifact generation", preResult.isVerified)

        // 3. Generate target DOCX
        val docxFile = File(resolver.artifactsDir, "report.docx")
        DocxGenerator.createDocument(
            outputFile = docxFile,
            content = DocxGenerator.DocxContent(
                title = "Executive Report",
                paragraphs = listOf("System verified operational."),
                tableHeaders = listOf("Metric", "Value"),
                tableRows = listOf(DocxGenerator.TableRow(listOf("Latency", "12ms")))
            )
        )

        // 4. Post check -> goal satisfied immediately
        val postResult = verificationEngine.verifyTaskObjective(task, resolver)
        assertTrue("Goal must be verified immediately upon deliverable generation", postResult.isVerified)
        assertTrue("Summary must confirm verification", postResult.summary.contains("Objective verified"))

        // Replanner must immediately recommend COMPLETE rather than looping
        val decision = replanner.analyzeAndReplan(
            task = task,
            currentPlan = listOf(PlanStep(id = "s1", taskId = task.id, stepNumber = 1, title = "Phase", status = StepStatus.IN_PROGRESS)),
            failedApproaches = emptyList(),
            recentResults = emptyList(),
            artifacts = emptyList(),
            verificationResult = postResult,
            resolver = resolver
        )
        assertEquals(ReplanDecisionType.COMPLETE, decision.type)
    }

    @Test
    fun `Stagnation Detection Halts Repeated Read Operations without Progress`() {
        val dummyReadOnly = ToolResult(
            callId = "call_ro",
            taskId = "t_stagnant",
            success = true,
            exitCode = 0,
            stdout = "file1.txt\nfile2.txt",
            stderr = "",
            durationMs = 25L,
            workingDirectory = "/workspace"
        )

        // Simulate agent repeatedly listing files without doing anything
        val a1 = loopDetector.record("file_list", "{}", dummyReadOnly)
        assertFalse(a1.isLooping)

        val a2 = loopDetector.record("file_list", "{}", dummyReadOnly)
        // Exact repetition on 2nd attempt with maxSameActions = 2
        assertTrue("Must detect repetition or stagnation", a2.isLooping)

        val a3 = loopDetector.record("inspect_file", "{\"path\":\"/workspace/file1.txt\"}", dummyReadOnly)
        assertTrue("Consecutive loop detections must escalate to critical termination", a3.shouldTerminateBlocked || a3.isCritical)
        assertEquals("TERMINATE_BLOCKED", a3.recommendedAction)
    }

    @Test
    fun `Exhausted Strategies Trigger ABORT in Replanner to Prevent Infinite Loop`() {
        val task = Task(
            id = "t_exhausted",
            title = "Failing task",
            originalRequest = "Execute complex script"
        )

        val failedApproaches = (1..5).map {
            com.example.aragon.domain.model.FailedApproach(
                id = "fail_$it",
                strategy = "strategy_$it",
                error = "Fatal error $it",
                context = "Execution context"
            )
        }

        val decision = replanner.analyzeAndReplan(
            task = task,
            currentPlan = emptyList(),
            failedApproaches = failedApproaches,
            recentResults = emptyList(),
            artifacts = emptyList(),
            verificationResult = null,
            resolver = resolver
        )

        assertEquals("Replanner must ABORT when strategies are exhausted", ReplanDecisionType.ABORT, decision.type)
        assertTrue("Explanation must state exhausted strategies", decision.explanation.contains("Exhausted"))
    }

    @Test
    fun `Authoritative Terminal States Are Respected`() {
        assertTrue(TaskStatus.COMPLETED.isTerminal)
        assertTrue(TaskStatus.FAILED.isTerminal)
        assertTrue(TaskStatus.BLOCKED.isTerminal)
        assertTrue(TaskStatus.CANCELLED.isTerminal)
        assertFalse(TaskStatus.EXECUTING.isTerminal)
        assertFalse(TaskStatus.PLANNING.isTerminal)
    }

    @Test
    fun `Hierarchical Plan Phase and Subtask Model Integrity`() {
        val step = PlanStep(
            id = "s_hier",
            taskId = "t_test",
            stepNumber = 1,
            phase = "Phase 1: Ingestion & Environment",
            title = "Provision Workspace",
            description = "Setup directories",
            status = StepStatus.IN_PROGRESS,
            subtasks = listOf("Subtask A", "Subtask B", "Subtask C"),
            activeSubtaskIndex = 1,
            nextIntent = "Executing Subtask B."
        )

        assertEquals("Phase 1: Ingestion & Environment", step.phase)
        assertEquals(3, step.subtasks.size)
        assertEquals(1, step.activeSubtaskIndex)
        assertEquals("Executing Subtask B.", step.nextIntent)
    }
}
