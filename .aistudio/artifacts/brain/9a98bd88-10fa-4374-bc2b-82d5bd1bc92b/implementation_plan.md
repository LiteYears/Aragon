# Aragon V2 Architecture Integration & Validation Pass

Transform the Aragon V2 agent codebase from individual isolated subsystems into a single, cohesive, verified autonomous runtime pipeline where every execution flows through SessionOrchestrator, ModelRouter, ToolDispatcher, real workspace storage, VerificationEngine, and Replanner.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed with the user during Phase 1:
> - **Test Suite Structure**: Dedicated comprehensive integration test suite covering all 12 regression and validation scenarios (`AragonV2ArchitectureIntegrationTest.kt`).
> - **Office Deliverables Strategy**: Python script execution in sandbox with automatic Kotlin `DocxGenerator` fallback to guarantee valid OpenXML output.
> - **Verification Failure Policy**: Automatic replanning up to the task's maximum iteration limit before pausing or escalating.

---

## 1. Overview & Core Concept

- **What It Delivers**: A unified, deterministic agent execution loop adhering to the reverse-engineered Manus execution pipeline:
  `UI → SessionOrchestrator → AgentHarness → ModelRouter → ToolDispatcher → Computer (Workspace) → Observation → Verifier → Replanner → Artifacts → TaskState / EventStore → UI Projection`.
- **Primary Benefit**: Eliminates architectural disconnects, bypass paths, and unvalidated claims. Completion cannot occur through language model output alone; it strictly requires cryptographic/structural evidence validated by `VerificationEngine`.

---

## 2. Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                   │
│   (MainScreen, TaskDetailScreen, ArtifactCard, Dialogs) │
└────────────────────────────┬────────────────────────────┘
                             │ StateFlow / Events
                             ▼
┌─────────────────────────────────────────────────────────┐
│              MainViewModel / TaskRepository             │
│        (Observes DB flows, triggers session actions)    │
└────────────────────────────┬────────────────────────────┘
                             │ startSession / pause / cancel
                             ▼
┌─────────────────────────────────────────────────────────┐
│                  SessionOrchestrator                    │
│     (Authoritative lifecycle, ReAct loop, state engine) │
└─────────────┬─────────────────────────────┬─────────────┘
              │                             │
              ▼                             ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│       AgentHarness       │  │      ContextManager      │
│  (Session lifecycle,     │  │  (System prompt, durable  │
│   coordination adapter)  │  │   memory, log snapshots)  │
└─────────────┬────────────┘  └─────────────┬────────────┘
              │                             │
              ▼                             ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│       ModelRouter        │  │     CoordinatorAgent     │
│  (Model capabilities,    │  │  (Parallel workers for    │
│   fallback, NIM provider)│  │   multi-source research)  │
└─────────────┬────────────┘  └─────────────┬────────────┘
              │ ToolCalls                   │ Sub-tasks
              ▼                             ▼
┌─────────────────────────────────────────────────────────┐
│                     ToolDispatcher                      │
│   (Validates tool existence, checks human approval)     │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│             ToolExecutor & Computer Layer               │
│  (ProcessManager, TextEditorTool, DocxGenerator, HTTP)  │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│               Unified Logical Workspace                 │
│   (/workspace, /process, /artifacts, /tmp, .aragon)     │
│   Managed by WorkspacePathResolver                      │
└────────────────────────────┬────────────────────────────┘
                             │ Real Files & Output
                             ▼
┌─────────────────────────────────────────────────────────┐
│                       Observation                       │
│    (Structured ToolResult, disk logs, loop detection)   │
└────────────────────────────┬────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────┐
│                   VerificationEngine                    │
│  (Structural integrity, OpenXML zip check, file proofs) │
└─────────────┬─────────────────────────────┬─────────────┘
              │ Verified                    │ Unmet
              ▼                             ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│       Task Complete      │  │        Replanner         │
│  (Product artifacts to   │  │  (Strategy shift, error   │
│   /artifacts, DB update) │  │   diagnosis, step repair) │
└──────────────────────────┘  └──────────────────────────┘
```

---

## 3. Key Product Decisions & Refactoring Tasks

### 1. Introduce `ModelRouter`
- **Current State**: `SessionOrchestrator` communicates with `LlmProvider` directly; `ModelRegistry` exists but there is no routing or capability-matching intermediary.
- **Refactoring**: Create `ModelRouter` in `com.example.aragon.llm`. It wraps `NvidiaNimProvider`, consults `ModelRegistry` for model capabilities (vision, reasoning, tool calling, context window), and implements automatic graceful fallback if a model is deprecated, throttled, or fails.

### 2. Connect SessionOrchestrator as the Single Engine
- **Current State**: `AragonApplication` initializes `AgentHarness` which creates its own inner `SessionOrchestrator`, while `MainViewModel` interacts with disparate dependencies.
- **Refactoring**: Centralize `SessionOrchestrator` as an authoritative singleton in `AragonApplication`, injected into `AgentHarness` and `MainViewModel`. Ensure all user actions (`launchTask`, `pauseTask`, `cancelTask`, `approvePlan`, `respondToApproval`) route through `SessionOrchestrator`.

### 3. State Transition Validation
- **Current State**: Task status transitions can occur directly via DAO calls.
- **Refactoring**: Enforce a strict state transition machine inside `SessionOrchestrator` (`CREATED → INITIALIZING → PROVISIONING → PLANNING → READY → EXECUTING → OBSERVING → VERIFYING → COMPLETING → COMPLETED`, or `REPLANNING`, `AWAITING_APPROVAL`, `PAUSED`, `CANCELLED`, `FAILED`). Disallow unauthorized or illegal transitions.

### 4. Shared Logical Workspace Integrity
- Ensure `WorkspacePathResolver` translates logical paths (`/workspace`, `/process`, `/artifacts`, `/tmp`) consistently across all tools (`file_write`, `file_read`, `run_command`, `python_execute`, `text_editor`, `inspect_file`) and `ArtifactDetector`. No tool writes to private paths outside the workspace boundary.

### 5. Verification & Anti-False-Success Gate
- `VerificationEngine` must be the strict gatekeeper of completion. If the model claims "Done" or an echo command exits with code 0 without creating the required verified file, `VerificationEngine` returns `isVerified = false`, forcing `Replanner` to engage.

### 6. Comprehensive 12-Scenario Integration Test Suite
- Create `AragonV2ArchitectureIntegrationTest.kt` covering:
  1. Valid DOCX compilation, table structure, and OpenXML integrity
  2. Broken Python script recovery via failure memory and Replanner
  3. False success detection (exit 0 but missing file)
  4. Loop detection on repeated failures and oscillation
  5. Unified workspace consistency across multiple tools
  6. Artifact classification (PROCESS vs PRODUCT)
  7. App restart simulation and state restoration
  8. Checkpoint persistence and reload
  9. Human approval gate on dangerous actions
  10. Session cancellation and task shutdown
  11. Model provider error / fallback handling
  12. End-to-end autonomous task execution pipeline
