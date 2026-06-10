package com.kris99.baozi.android.ui.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppSnapshotRecord
import uniffi.codex_mobile_client.AppSubagentStatus
import uniffi.codex_mobile_client.HydratedMultiAgentActionData
import uniffi.codex_mobile_client.HydratedMultiAgentStateData
import uniffi.codex_mobile_client.ThreadKey

/**
 * Expandable card for multi-agent actions.
 * Uses Rust-provided [HydratedMultiAgentActionData] directly — no type duplication.
 */
@Composable
fun SubagentCard(
    data: HydratedMultiAgentActionData,
    serverId: String,
    onOpenThread: ((ThreadKey) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val agentRows = remember(data.targets, data.receiverThreadIds, data.agentStates) {
        buildAgentRows(data)
    }
    val agentCount = maxOf(data.targets.size, data.agentStates.size)
    val agentCountLabel = if (agentCount == 1) "1 个智能体" else "$agentCount 个智能体"

    val actionLabel = when (data.tool.lowercase()) {
        "spawn", "spawnagent", "spawn_agent" -> "正在创建智能体"
        "sendinput", "send_input" -> "正在发送输入"
        "resume", "resumeagent", "resume_agent" -> "正在恢复智能体"
        "wait" -> "正在等待智能体"
        "close", "closeagent", "close_agent" -> "正在关闭智能体"
        else -> data.tool
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(8.dp))
            .animateContentSize()
            .padding(8.dp),
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            StatusIcon(data.status)
            Spacer(Modifier.width(6.dp))
            Text(
                text = actionLabel,
                color = BaoziTheme.toolCallCollaboration,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = agentCountLabel,
                color = BaoziTheme.textMuted,
                fontSize = BaoziTextStyle.caption2.scaled,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = BaoziTheme.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }

        // Expanded agent list
        if (expanded) {
            // Show prompt if present
            data.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                Text(
                    text = prompt,
                    color = BaoziTheme.textMuted,
                    fontSize = BaoziTextStyle.caption2.scaled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, start = 20.dp),
                )
            }

            for (row in agentRows) {
                val threadKey = row.threadId?.let { threadId ->
                    snapshot?.resolvedThreadKey(threadId, serverId)
                        ?: AgentLabelFormatter.sanitized(threadId)?.let { normalized ->
                            ThreadKey(serverId = serverId, threadId = normalized)
                        }
                }
                val displayLabel = resolvedLabel(snapshot, row, serverId)
                val liveStatus = liveStatus(snapshot, row, serverId)
                val statusText = readableStatus(liveStatus)
                val statusColor = statusColor(liveStatus)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 20.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayLabel,
                            color = BaoziTheme.textPrimary,
                            fontSize = BaoziTextStyle.caption.scaled,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = BaoziTextStyle.caption2.scaled,
                            )
                        }
                    }

                    if (row.threadId != null && threadKey != null) {
                        IconButton(
                            onClick = {
                                if (onOpenThread != null) {
                                    onOpenThread(threadKey)
                                } else {
                                    scope.launch {
                                        appModel.store.setActiveThread(threadKey)
                                        appModel.refreshThreadSnapshot(threadKey)
                                    }
                                }
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "打开",
                                tint = BaoziTheme.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class AgentRowData(
    val label: String,
    val threadId: String?,
    val status: AppSubagentStatus?,
)

private fun buildAgentRows(data: HydratedMultiAgentActionData): List<AgentRowData> {
    val statesByTarget = data.agentStates.associateBy(HydratedMultiAgentStateData::targetId)
    val rows = mutableListOf<AgentRowData>()

    data.targets.forEachIndexed { index, target ->
        val threadId = data.receiverThreadIds.getOrNull(index)
        val state = threadId?.let(statesByTarget::get) ?: statesByTarget[target]
        rows += AgentRowData(
            label = target,
            threadId = threadId,
            status = state?.status,
        )
    }

    data.agentStates.forEach { state ->
        val alreadyPresent = rows.any { it.threadId == state.targetId || it.label == state.targetId }
        if (!alreadyPresent) {
            rows += AgentRowData(
                label = state.targetId,
                threadId = state.targetId,
                status = state.status,
            )
        }
    }

    return rows
}

private fun resolvedLabel(
    snapshot: AppSnapshotRecord?,
    row: AgentRowData,
    serverId: String,
): String {
    if (row.label.isNotBlank() && !looksLikeRawId(row.label)) {
        return row.label
    }
    return snapshot?.resolvedAgentTargetLabel(row.label, serverId)
        ?: row.threadId?.let { snapshot?.resolvedAgentTargetLabel(it, serverId) }
        ?: row.label
}

private fun liveStatus(
    snapshot: AppSnapshotRecord?,
    row: AgentRowData,
    serverId: String,
): AppSubagentStatus? {
    val key = row.threadId?.let { snapshot?.resolvedThreadKey(it, serverId) }
    val summary = key?.let { threadKey -> snapshot?.sessionSummary(threadKey) }
    return when {
        summary?.hasActiveTurn == true -> AppSubagentStatus.RUNNING
        summary != null && summary.agentStatus != AppSubagentStatus.UNKNOWN -> summary.agentStatus
        else -> row.status
    }
}

private fun readableStatus(status: AppSubagentStatus?): String {
    return when (status ?: AppSubagentStatus.UNKNOWN) {
        AppSubagentStatus.RUNNING -> "正在思考"
        AppSubagentStatus.PENDING_INIT -> "正在等待指令"
        AppSubagentStatus.COMPLETED -> "已完成"
        AppSubagentStatus.ERRORED -> "出错了"
        AppSubagentStatus.INTERRUPTED -> "已中断"
        AppSubagentStatus.SHUTDOWN -> "已关闭"
        AppSubagentStatus.UNKNOWN -> ""
    }
}

private fun statusColor(status: AppSubagentStatus?): Color {
    return when (status ?: AppSubagentStatus.UNKNOWN) {
        AppSubagentStatus.RUNNING -> BaoziTheme.accent
        AppSubagentStatus.COMPLETED -> BaoziTheme.success
        AppSubagentStatus.ERRORED -> BaoziTheme.danger
        else -> BaoziTheme.textMuted
    }
}

private fun AppSnapshotRecord.sessionSummary(key: ThreadKey) =
    sessionSummaries.firstOrNull { it.key == key }

private fun AppSnapshotRecord.resolvedThreadKey(receiverId: String, serverId: String): ThreadKey? {
    val normalized = AgentLabelFormatter.sanitized(receiverId) ?: return null
    return sessionSummaries.firstOrNull {
        it.key.serverId == serverId && it.key.threadId == normalized
    }?.key ?: ThreadKey(serverId = serverId, threadId = normalized)
}

private fun AppSnapshotRecord.resolvedAgentTargetLabel(target: String, serverId: String): String? {
    if (AgentLabelFormatter.looksLikeDisplayLabel(target)) {
        return AgentLabelFormatter.sanitized(target)
    }
    val normalized = AgentLabelFormatter.sanitized(target) ?: return null
    val summary = sessionSummaries.firstOrNull {
        it.key.serverId == serverId && it.key.threadId == normalized
    }
    return if (summary != null) {
        summary.agentDisplayLabel ?: AgentLabelFormatter.sanitized(target)
    } else {
        null
    }
}

private fun looksLikeRawId(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.length >= 16 && RAW_ID_REGEX.matches(trimmed)
}

private object AgentLabelFormatter {
    fun sanitized(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        return trimmed.ifEmpty { null }
    }

    fun looksLikeDisplayLabel(raw: String?): Boolean {
        val value = sanitized(raw) ?: return false
        if (!value.endsWith("]")) return false
        val openBracket = value.lastIndexOf('[')
        if (openBracket < 0) return false
        val nickname = value.substring(0, openBracket).trim()
        val role = value.substring(openBracket + 1, value.length - 1).trim()
        return nickname.isNotEmpty() && role.isNotEmpty()
    }
}

private val RAW_ID_REGEX = Regex("^[0-9a-fA-F-]+$")
