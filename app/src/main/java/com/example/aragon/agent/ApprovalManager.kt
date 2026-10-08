package com.example.aragon.agent

import com.example.aragon.domain.model.ApprovalRequest
import com.example.aragon.domain.model.ApprovalStatus
import com.example.aragon.domain.model.ApprovalType
import com.example.aragon.domain.model.AutonomyLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ApprovalManager {

    private val _pendingRequests = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingRequests: StateFlow<List<ApprovalRequest>> = _pendingRequests.asStateFlow()

    private val requests = ConcurrentHashMap<String, ApprovalRequest>()
    private val consumedApprovals = ConcurrentHashMap.newKeySet<String>()

    enum class CommandRiskLevel {
        SAFE,
        CONTROLLED,
        SENSITIVE,
        DESTRUCTIVE
    }

    fun classifyCommand(command: String, workingDir: String): Pair<CommandRiskLevel, String> {
        val trimmed = command.trim().lowercase()

        // 1. Destructive commands
        if (trimmed.contains("rm -rf /") || trimmed.contains("mkfs") || trimmed.contains("dd if=") ||
            trimmed.contains("> /dev/sd") || trimmed.contains(":(){ :|:& };:") ||
            (trimmed.contains("rm -rf") && !trimmed.contains("/workspace/process") && !trimmed.contains("/workspace/.aragon/runtime"))
        ) {
            return Pair(CommandRiskLevel.DESTRUCTIVE, "Potentially destructive deletion or disk modification command")
        }

        // 2. Sensitive commands
        if (trimmed.startsWith("chmod 777") || trimmed.contains("ssh-keygen") ||
            trimmed.contains("cat ~/.ssh") || trimmed.contains("/etc/shadow") ||
            trimmed.contains("curl") && (trimmed.contains("http://") || trimmed.contains("pastebin") || trimmed.contains("ngrok")) ||
            trimmed.contains("env |") || trimmed.contains("printenv")
        ) {
            return Pair(CommandRiskLevel.SENSITIVE, "Command handles sensitive credentials or external networking")
        }

        // 3. Controlled commands (package manager, service start, git push)
        if (trimmed.startsWith("apt") || trimmed.startsWith("apt-get") ||
            trimmed.startsWith("pip install") || trimmed.startsWith("npm install") ||
            trimmed.contains("git push") || trimmed.contains("systemctl")
        ) {
            return Pair(CommandRiskLevel.CONTROLLED, "Command installs packages or alters system configuration")
        }

        return Pair(CommandRiskLevel.SAFE, "Read-only or local development command")
    }

    fun requiresApproval(
        command: String,
        workingDir: String,
        autonomyLevel: AutonomyLevel
    ): ApprovalType? {
        val (risk, _) = classifyCommand(command, workingDir)

        return when (autonomyLevel) {
            AutonomyLevel.SAFE -> {
                // In SAFE autonomy, any controlled or higher command requires approval
                if (risk != CommandRiskLevel.SAFE) ApprovalType.DANGEROUS_COMMAND else null
            }
            AutonomyLevel.NORMAL -> {
                // In NORMAL autonomy, sensitive and destructive commands require approval
                if (risk == CommandRiskLevel.DESTRUCTIVE) ApprovalType.DESTRUCTIVE_FILE_OPERATION
                else if (risk == CommandRiskLevel.SENSITIVE) ApprovalType.SECRET_INJECTION
                else null
            }
            AutonomyLevel.FULL -> {
                // In FULL autonomy, only destructive commands require approval
                if (risk == CommandRiskLevel.DESTRUCTIVE) ApprovalType.DESTRUCTIVE_FILE_OPERATION else null
            }
        }
    }

    fun createRequest(
        taskId: String,
        type: ApprovalType,
        description: String,
        risk: String,
        proposedAction: String,
        toolCallId: String? = null,
        toolName: String? = null,
        argumentsJson: String? = null
    ): ApprovalRequest {
        // If a request already exists for this specific toolCallId, return it idempotently
        if (!toolCallId.isNullOrBlank()) {
            val existing = requests.values.find { it.toolCallId == toolCallId }
            if (existing != null) {
                return existing
            }
        }

        val stableId = if (!toolCallId.isNullOrBlank()) "appr_$toolCallId" else UUID.randomUUID().toString()
        val request = ApprovalRequest(
            id = stableId,
            taskId = taskId,
            type = type,
            description = description,
            risk = risk,
            proposedAction = proposedAction,
            status = ApprovalStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 600000L, // 10 minutes
            toolCallId = toolCallId,
            toolName = toolName,
            argumentsJson = argumentsJson
        )

        requests[request.id] = request
        updatePendingFlow()
        return request
    }

    fun grantApproval(requestId: String) {
        updateRequestStatus(requestId, ApprovalStatus.APPROVED)
    }

    fun denyApproval(requestId: String) {
        updateRequestStatus(requestId, ApprovalStatus.DENIED)
        requests[requestId]?.toolCallId?.let { consumedApprovals.add(it) }
    }

    fun restoreRequest(request: ApprovalRequest) {
        requests[request.id] = request
        updatePendingFlow()
    }

    fun clearRequestsForTask(taskId: String) {
        requests.values.filter { it.taskId == taskId }.forEach { req ->
            requests.remove(req.id)
            req.toolCallId?.let { consumedApprovals.remove(it) }
        }
        updatePendingFlow()
    }

    fun getRequest(requestId: String): ApprovalRequest? {
        return requests[requestId]
    }

    fun getApprovalForCall(toolCallId: String): ApprovalRequest? {
        return requests.values.find { it.toolCallId == toolCallId }
    }

    fun isApprovedForCall(toolCallId: String): Boolean {
        if (consumedApprovals.contains(toolCallId)) return false
        val req = requests.values.find { it.toolCallId == toolCallId } ?: return false
        return req.status == ApprovalStatus.APPROVED
    }

    fun isDeniedForCall(toolCallId: String): Boolean {
        val req = requests.values.find { it.toolCallId == toolCallId } ?: return false
        return req.status == ApprovalStatus.DENIED
    }

    @Synchronized
    fun consumeApprovalForCall(toolCallId: String): Boolean {
        if (isApprovedForCall(toolCallId)) {
            consumedApprovals.add(toolCallId)
            return true
        }
        return false
    }

    /**
     * Atomically consumes and returns an approved request for the given task.
     * Guarantees the approved call can execute exactly once without repeated prompts.
     */
    @Synchronized
    fun consumeApprovedRequestForTask(taskId: String): ApprovalRequest? {
        val approved = requests.values.firstOrNull { req ->
            req.taskId == taskId &&
                req.status == ApprovalStatus.APPROVED &&
                !req.toolCallId.isNullOrBlank() &&
                !consumedApprovals.contains(req.toolCallId)
        } ?: return null

        approved.toolCallId?.let { consumedApprovals.add(it) }
        return approved
    }

    fun getPendingForTask(taskId: String): List<ApprovalRequest> {
        return requests.values.filter { it.taskId == taskId && it.status == ApprovalStatus.PENDING }
    }

    private fun updateRequestStatus(requestId: String, status: ApprovalStatus) {
        requests.computeIfPresent(requestId) { _, existing ->
            existing.copy(status = status)
        }
        updatePendingFlow()
    }

    private fun updatePendingFlow() {
        _pendingRequests.value = requests.values
            .filter { it.status == ApprovalStatus.PENDING }
            .sortedBy { it.createdAt }
    }
}
