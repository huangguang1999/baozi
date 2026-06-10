package com.kris99.baozi.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedTextField
import com.kris99.baozi.android.ui.BerkeleyMono
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.scaled
import com.kris99.baozi.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppStore
import uniffi.codex_mobile_client.ApprovalDecisionValue
import uniffi.codex_mobile_client.ApprovalKind
import uniffi.codex_mobile_client.PendingApproval
import uniffi.codex_mobile_client.PendingUserInputAnswer
import uniffi.codex_mobile_client.PendingUserInputRequest

/**
 * Full-screen overlay for pending approvals and user input requests.
 * Reads typed [PendingApproval] from Rust snapshot — no parsing needed.
 */
@Composable
fun ApprovalOverlay(
    approvals: List<PendingApproval>,
    userInputs: List<PendingUserInputRequest>,
    appStore: AppStore,
    onDismissUserInput: ((String) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var submittingRequestId by remember { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }

    fun submitResponse(requestId: String, kind: String, action: suspend () -> Unit) {
        scope.launch {
            submittingRequestId = requestId
            submitError = null
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                LLog.e(
                    TAG,
                    "$kind response failed",
                    error,
                    fields = mapOf("requestId" to requestId),
                )
                submitError = responseSubmissionErrorMessage(error)
            } finally {
                if (submittingRequestId == requestId) {
                    submittingRequestId = null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) { /* block interaction */ },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            submitError?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFF6B6B),
                    fontSize = BaoziTextStyle.caption.scaled,
                )
            }

            for (approval in approvals) {
                ApprovalCard(
                    approval = approval,
                    isSubmitting = submittingRequestId == approval.id,
                    onDecision = { decision ->
                        submitResponse(approval.id, "approval") {
                            appStore.respondToApproval(approval.id, decision)
                        }
                    },
                )
            }

            for (input in userInputs) {
                UserInputCard(
                    request = input,
                    isSubmitting = submittingRequestId == input.id,
                    onSubmit = { answers ->
                        submitResponse(input.id, "user input") {
                            appStore.respondToUserInput(input.id, answers)
                        }
                    },
                    onDismiss = { onDismissUserInput?.invoke(input.id) },
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingApproval,
    isSubmitting: Boolean,
    onDecision: (ApprovalDecisionValue) -> Unit,
) {
    val appModel = com.kris99.baozi.android.ui.LocalAppModel.current
    val snap = appModel.snapshot.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLocal = snap.value?.servers?.firstOrNull { it.serverId == approval.serverId }?.isLocal == true
    val title = when (approval.kind) {
        ApprovalKind.COMMAND -> "运行命令？"
        ApprovalKind.FILE_CHANGE -> "修改文件？"
        ApprovalKind.PERMISSIONS -> "授予权限？"
        ApprovalKind.MCP_ELICITATION -> "工具请求"
    }

    // Bare layout (no card background) to match iOS ConversationView prompt.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = BaoziTheme.textPrimary,
            fontSize = 16f.scaled,
        )

        // Command text — capped + scrollable so a long command can't push the
        // action buttons off-screen (issue #92).
        approval.command?.let { cmd ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(BaoziTheme.codeBackground, RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
            ) {
                Text(
                    text = cmd,
                    color = BaoziTheme.accent,
                    fontFamily = BaoziTheme.monoFont,
                    fontSize = BaoziTextStyle.code.scaled,
                )
            }
        }

        // CWD
        approval.cwd?.let { cwd ->
            Text(
                text = "位于 " + com.kris99.baozi.android.state.PathDisplay.display(cwd, isLocal, context),
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.caption.scaled,
            )
        }

        // Path (for file changes)
        approval.path?.let { path ->
            Text(
                text = com.kris99.baozi.android.state.PathDisplay.display(path, isLocal, context),
                color = BaoziTheme.textSecondary,
                fontFamily = BaoziTheme.monoFont,
                fontSize = BaoziTextStyle.caption.scaled,
            )
        }

        // Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { onDecision(ApprovalDecisionValue.DECLINE) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("拒绝")
            }
            OutlinedButton(
                onClick = { onDecision(ApprovalDecisionValue.ACCEPT_FOR_SESSION) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("允许此会话")
            }
            Button(
                onClick = { onDecision(ApprovalDecisionValue.ACCEPT) },
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BaoziTheme.accent,
                    contentColor = Color.Black,
                ),
            ) {
                Text("允许")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserInputCard(
    request: PendingUserInputRequest,
    isSubmitting: Boolean,
    onSubmit: (List<PendingUserInputAnswer>) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    val answers = remember { mutableMapOf<String, String>() }

    // Bare layout (no card background) to match iOS ConversationView prompt.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Header with close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Requester badge
            val requester = buildString {
                request.requesterAgentNickname?.let { append(it) }
                request.requesterAgentRole?.let {
                    if (isNotEmpty()) append(" ")
                    append("[$it]")
                }
            }
            if (requester.isNotBlank()) {
                Text(
                    text = requester,
                    color = BaoziTheme.accent,
                    fontSize = BaoziTextStyle.caption2.scaled,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (onDismiss != null) {
                Text(
                    text = "✕",
                    color = BaoziTheme.textMuted,
                    fontSize = BaoziTextStyle.body.scaled,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
                        .semantics { contentDescription = "关闭输入请求" },
                )
            }
        }

        for (question in request.questions) {
            Text(
                text = question.question,
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.body.scaled,
            )

            if (question.options.isNotEmpty()) {
                // FlowRow so long option labels wrap to a new line instead of
                // crushing a short option into a narrow column with character-
                // by-character text wrapping.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (option in question.options) {
                        val isSelected = answers[question.id] == option.label
                        Text(
                            text = option.label,
                            color = if (isSelected) Color.Black else BaoziTheme.textPrimary,
                            fontSize = BaoziTextStyle.caption.scaled,
                            modifier = Modifier
                                .background(
                                    if (isSelected) BaoziTheme.accent else BaoziTheme.codeBackground,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { answers[question.id] = option.label }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            } else {
                // Free text input
                var text by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        answers[question.id] = it
                    },
                    label = { Text(question.header ?: "回答") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Button(
            onClick = {
                val answerList = request.questions.map { q ->
                    PendingUserInputAnswer(
                        questionId = q.id,
                        answers = listOfNotNull(answers[q.id]),
                    )
                }
                onSubmit(answerList)
            },
            enabled = !isSubmitting,
            colors = ButtonDefaults.buttonColors(
                containerColor = BaoziTheme.accent,
                contentColor = Color.Black,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("提交")
        }
    }
}

private const val TAG = "ApprovalOverlay"
