package com.example.aragon.agent

import com.example.aragon.domain.model.ApprovalRequest
import com.example.aragon.domain.model.ApprovalStatus
import com.example.aragon.domain.model.ApprovalType
import com.example.aragon.domain.model.AutonomyLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class ApprovalManager {

    private val _pendingRequests = MutableStateFlow<List<ApprovalRequest>>(emptyList())
    val pendingRequests: StateFlow<List<ApprovalRequest>> = _pendingRequests.asStateFlow()

    private val completedRequests = mutableMapOf<String, ApprovalRequest>()

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
        proposedAction: String
    ): ApprovalRequest {
        val request = ApprovalRequest(
            id = UUID.randomUUID().toString(),
            taskId = taskId,
            type = type,
            description = description,
            risk = risk,
            proposedAction = proposedAction,
            status = ApprovalStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 600000L // 10 minutes
        )

        val current = _pendingRequests.value.toMutableList()
        current.add(request)
        _pendingRequests.value = current
        return request
    }

    fun grantApproval(requestId: String) {
        updateRequestStatus(requestId, ApprovalStatus.APPROVED)
    }

    fun denyApproval(requestId: String) {
        updateRequestStatus(requestId, ApprovalStatus.DENIED)
    }

    fun getRequest(requestId: String): ApprovalRequest? {
        return _pendingRequests.value.find { it.id == requestId } ?: completedRequests[requestId]
    }

    fun getPendingForTask(taskId: String): List<ApprovalRequest> {
        return _pendingRequests.value.filter { it.taskId == taskId }
    }

    private fun updateRequestStatus(requestId: String, status: ApprovalStatus) {
        val current = _pendingRequests.value.toMutableList()
        val index = current.indexOfFirst { it.id == requestId }
        if (index != -1) {
            val updated = current.removeAt(index).copy(status = status)
            completedRequests[requestId] = updated
            _pendingRequests.value = current
        }
    }
}
