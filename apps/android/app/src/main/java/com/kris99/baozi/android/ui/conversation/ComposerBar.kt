package com.kris99.baozi.android.ui.conversation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.state.AppModel
import com.kris99.baozi.android.state.ComposerImageAttachment
import com.kris99.baozi.android.state.ComposerFileAttachment
import com.kris99.baozi.android.state.AppComposerPayload
import com.kris99.baozi.android.state.VoiceTranscriptionManager
import com.kris99.baozi.android.state.ampReasoningEffortLocked
import com.kris99.baozi.android.util.LLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import uniffi.codex_mobile_client.AuthStatusRequest
import uniffi.codex_mobile_client.AppSearchFilesRequest
import uniffi.codex_mobile_client.PendingUserInputAnswer
import uniffi.codex_mobile_client.PendingUserInputRequest
import uniffi.codex_mobile_client.ReasoningEffort
import uniffi.codex_mobile_client.ServiceTier
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BerkeleyMono
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.scaled
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppInterruptTurnRequest
import uniffi.codex_mobile_client.AppQueuedFollowUpKind
import uniffi.codex_mobile_client.AppQueuedFollowUpPreview
import uniffi.codex_mobile_client.AppThreadGoal
import uniffi.codex_mobile_client.AppThreadGoalClearRequest
import uniffi.codex_mobile_client.AppThreadGoalGetRequest
import uniffi.codex_mobile_client.AppThreadGoalSetRequest
import uniffi.codex_mobile_client.AppThreadGoalStatus

/** Slash command definitions matching iOS. */
internal data class SlashCommand(val name: String, val description: String)
internal data class SlashInvocation(val command: SlashCommand, val args: String?)
data class ActiveTaskSummary(val progress: String, val label: String)

private val SLASH_COMMANDS = listOf(
    SlashCommand("计划", "切换协作模式"),
    SlashCommand("model", "更改模型或推理强度"),
    SlashCommand("new", "开始新会话"),
    SlashCommand("分叉", "分叉此对话"),
    SlashCommand("rename", "重命名此会话"),
    SlashCommand("review", "开始代码评审"),
    SlashCommand("goal", "设置或管理线程目标"),
    SlashCommand("resume", "浏览会话"),
    SlashCommand("skills", "列出可用技能"),
    SlashCommand("permissions", "更改权限"),
    SlashCommand("experimental", "切换实验性功能"),
)

private val SUPPORTED_IMAGE_FILE_MIME_TYPES = arrayOf(
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
)

private val ALL_FILE_MIME_TYPES = arrayOf("*/*")

/**
 * Bottom composer bar with text input, send, voice, slash commands,
 * @file search, and inline pending user input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerBar(
    threadKey: ThreadKey,
    collaborationMode: uniffi.codex_mobile_client.AppModeKind,
    activePlanProgress: uniffi.codex_mobile_client.AppPlanProgressSnapshot? = null,
    activeTurnId: String?,
    contextPercent: Int?,
    isThinking: Boolean,
    activeTaskSummary: ActiveTaskSummary? = null,
    queuedFollowUps: List<uniffi.codex_mobile_client.AppQueuedFollowUpPreview> = emptyList(),
    goal: AppThreadGoal? = null,
    rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot? = null,
    showCollaborationModeChip: Boolean = true,
    onOpenCollaborationModePicker: (() -> Unit)? = null,
    onToggleModelSelector: (() -> Unit)? = null,
    onNavigateToSessions: (() -> Unit)? = null,
    onShowDirectoryPicker: (() -> Unit)? = null,
    onShowRenameDialog: ((String?) -> Unit)? = null,
    onShowPermissionsSheet: (() -> Unit)? = null,
    onShowExperimentalSheet: (() -> Unit)? = null,
    onShowSkillsSheet: (() -> Unit)? = null,
    onSlashError: ((String) -> Unit)? = null,
    pendingUserInput: PendingUserInputRequest? = null,
    onDismissPendingUserInput: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val composerPrefillRequest by appModel.composerPrefillRequest.collectAsState()
    // Hydrate the live composer state from `AppModel`'s per-thread draft so
    // it survives ComposerBar recomposition / view-tree teardown when the
    // user backgrounds the app. `remember(threadKey)` re-initializes when
    // navigating to a different thread; subsequent edits write back.
    var textFieldValue by remember(threadKey) {
        val saved = appModel.composerDraft(threadKey).text
        mutableStateOf(TextFieldValue(saved, selection = TextRange(saved.length)))
    }
    val text = textFieldValue.text
    var attachedImage by remember(threadKey) {
        mutableStateOf(appModel.composerDraft(threadKey).attachment)
    }
    var attachedFiles by remember(threadKey) {
        mutableStateOf(appModel.composerDraft(threadKey).fileAttachments)
    }
    LaunchedEffect(threadKey, text, attachedImage, attachedFiles) {
        appModel.setComposerDraft(
            threadKey,
            AppModel.ComposerDraft(
                text = text,
                attachment = attachedImage,
                fileAttachments = attachedFiles,
            ),
        )
    }
    var showAttachMenu by remember { mutableStateOf(false) }
    var showExpanded by remember { mutableStateOf(false) }
    val inlineFocusRequester = remember { FocusRequester() }
    val transcriptionManager = remember { VoiceTranscriptionManager() }
    val isRecording by transcriptionManager.isRecording.collectAsState()
    val isTranscribing by transcriptionManager.isTranscribing.collectAsState()
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) transcriptionManager.startRecording(context)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        attachedImage = readAttachmentFromUri(context, uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        when (val picked = readPickedComposerAttachment(context, uri)) {
            is PickedComposerAttachment.Image -> attachedImage = picked.attachment
            is PickedComposerAttachment.File -> {
                if (picked.attachment !in attachedFiles) {
                    attachedFiles = attachedFiles + picked.attachment
                }
            }
            null -> Unit
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        attachedImage = prepareBitmapAttachment(bitmap)
    }

    // Slash command state
    val slashQuery by remember {
        derivedStateOf {
            if (text.startsWith("/")) text.removePrefix("/").lowercase() else null
        }
    }
    val filteredCommands by remember {
        derivedStateOf {
            val q = slashQuery ?: return@derivedStateOf emptyList()
            SLASH_COMMANDS.filter { it.name.startsWith(q) || q.isEmpty() }
        }
    }
    var showSlashMenu by remember { mutableStateOf(false) }
    LaunchedEffect(slashQuery) { showSlashMenu = slashQuery != null && filteredCommands.isNotEmpty() }

    // @file search state
    var fileSearchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    var showFileMenu by remember { mutableStateOf(false) }
    var fileSearchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(text) {
        val atIdx = text.lastIndexOf('@')
        if (atIdx >= 0 && atIdx < text.length - 1 && !text.substring(atIdx).contains(' ')) {
            val query = text.substring(atIdx + 1)
            fileSearchJob?.cancel()
            fileSearchJob = scope.launch {
                delay(140) // debounce
                try {
                    val cwd = appModel.snapshot.value?.threads?.find { it.key == threadKey }?.info?.cwd ?: "~"
                    val results = appModel.client.searchFiles(
                        threadKey.serverId,
                        AppSearchFilesRequest(query = query, roots = listOf(cwd), cancellationToken = null),
                    )
                    fileSearchResults = results.map { it.path }.take(8)
                    showFileMenu = fileSearchResults.isNotEmpty()
                } catch (_: Exception) {
                    showFileMenu = false
                }
            }
        } else {
            showFileMenu = false
        }
    }

    // Pending user input answers
    var userInputAnswers by remember { mutableStateOf(mapOf<String, String>()) }
    var pendingUserInputSubmitError by remember(pendingUserInput?.id) { mutableStateOf<String?>(null) }
    var isSubmittingPendingUserInput by remember(pendingUserInput?.id) { mutableStateOf(false) }

    suspend fun handleGoalCommand(args: String?) {
        val raw = args?.trim().orEmpty()
        when (raw.lowercase()) {
            "" -> {
                val current = appModel.client.getThreadGoal(
                    threadKey.serverId,
                    AppThreadGoalGetRequest(threadId = threadKey.threadId),
                )
                onSlashError?.invoke(current?.let(::goalSummary) ?: "此线程未设置目标。")
            }
            "pause" -> {
                appModel.client.setThreadGoal(
                    threadKey.serverId,
                    AppThreadGoalSetRequest(
                        threadId = threadKey.threadId,
                        objective = null,
                        status = AppThreadGoalStatus.PAUSED,
                        tokenBudget = null,
                    ),
                )
            }
            "resume" -> {
                appModel.client.setThreadGoal(
                    threadKey.serverId,
                    AppThreadGoalSetRequest(
                        threadId = threadKey.threadId,
                        objective = null,
                        status = AppThreadGoalStatus.ACTIVE,
                        tokenBudget = null,
                    ),
                )
            }
            "clear" -> {
                appModel.client.clearThreadGoal(
                    threadKey.serverId,
                    AppThreadGoalClearRequest(threadId = threadKey.threadId),
                )
            }
            else -> {
                appModel.client.setThreadGoal(
                    threadKey.serverId,
                    AppThreadGoalSetRequest(
                        threadId = threadKey.threadId,
                        objective = raw,
                        status = AppThreadGoalStatus.ACTIVE,
                        tokenBudget = null,
                    ),
                )
            }
        }
    }

    // Only consume edit-message prefill for the intended thread.
    LaunchedEffect(composerPrefillRequest?.requestId, threadKey) {
        val prefill = composerPrefillRequest ?: return@LaunchedEffect
        if (prefill.threadKey != threadKey) return@LaunchedEffect
        textFieldValue = TextFieldValue(
            text = prefill.text,
            selection = TextRange(prefill.text.length),
        )
        attachedImage = null
        attachedFiles = emptyList()
        appModel.clearComposerPrefill(prefill.requestId)
    }

    fun dispatchSlashCommand(commandName: String, args: String?): Boolean {
        when (commandName) {
            "计划" -> onOpenCollaborationModePicker?.invoke()
            "model" -> onToggleModelSelector?.invoke()
            "new" -> onShowDirectoryPicker?.invoke()
            "resume" -> onNavigateToSessions?.invoke()
            "rename" -> onShowRenameDialog?.invoke(args)
            "skills" -> onShowSkillsSheet?.invoke()
            "permissions" -> onShowPermissionsSheet?.invoke()
            "experimental" -> onShowExperimentalSheet?.invoke()
            "goal" -> scope.launch {
                try {
                    handleGoalCommand(args)
                } catch (e: Exception) {
                    onSlashError?.invoke(e.message ?: "更新目标失败")
                }
            }
            "分叉" -> scope.launch {
                try {
                    val cwd = appModel.snapshot.value?.threads?.find { it.key == threadKey }?.info?.cwd
                    val newKey = appModel.client.forkThread(
                        threadKey.serverId,
                        appModel.launchState.threadForkRequest(
                            sourceThreadId = threadKey.threadId,
                            cwdOverride = cwd,
                            modelOverride = appModel.launchState.snapshot.value.selectedModel.trim().ifEmpty { null },
                            threadKey = threadKey,
                        ),
                    )
                    appModel.store.setActiveThread(newKey)
                    appModel.refreshThreadSnapshot(newKey)
                } catch (e: Exception) {
                    onSlashError?.invoke(e.message ?: "分叉对话失败")
                }
            }
            "review" -> scope.launch {
                try {
                    appModel.client.startReview(
                        threadKey.serverId,
                        uniffi.codex_mobile_client.AppStartReviewRequest(
                            threadId = threadKey.threadId,
                            target = uniffi.codex_mobile_client.AppReviewTarget.UncommittedChanges,
                            delivery = null,
                        ),
                    )
                } catch (e: Exception) {
                    onSlashError?.invoke(e.message ?: "启动评审失败")
                }
            }
            else -> return false
        }
        return true
    }

    // Single send path used by both the inline send button and the expanded
    // dialog. Keep this in sync if you change slash-command dispatch or
    // payload shape.
    val sendCurrent: () -> Unit = {
        if (pendingUserInput != null) {
            onDismissPendingUserInput?.invoke()
        }
        val handledAsSlash = parseSlashCommandInvocation(text)?.let { invocation ->
            if (dispatchSlashCommand(invocation.command.name, invocation.args)) {
                textFieldValue = TextFieldValue("")
                attachedImage = null
                attachedFiles = emptyList()
                true
            } else false
        } ?: false
        if (!handledAsSlash && (text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty())) {
            val launchState = appModel.launchState.snapshot.value
            val pendingModel = launchState.selectedModel.trim().ifEmpty { null }
            val thread = appModel.snapshot.value?.threads?.find { it.key == threadKey }
            val effort = if (thread?.ampReasoningEffortLocked == true) {
                null
            } else {
                launchState.reasoningEffort.trim().ifEmpty { null }
                    ?.let(::reasoningEffortFromServerValue)
            }
            val tier = if (HeaderOverrides.pendingFastMode) ServiceTier.FAST else null
            val attachmentToSend = attachedImage
            val filesToSend = attachedFiles
            val payload = AppComposerPayload(
                text = text.trim(),
                additionalInputs = listOfNotNull(attachmentToSend?.toUserInput()),
                fileAttachments = filesToSend,
                approvalPolicy = appModel.launchState.approvalPolicyValue(threadKey),
                sandboxPolicy = appModel.launchState.turnSandboxPolicy(threadKey),
                model = pendingModel,
                reasoningEffort = effort,
                serviceTier = tier,
            )
            textFieldValue = TextFieldValue("")
            attachedImage = null
            attachedFiles = emptyList()
            scope.launch {
                try {
                    appModel.startTurn(threadKey, payload)
                } catch (e: Exception) {
                    textFieldValue = TextFieldValue(
                        text = payload.text,
                        selection = TextRange(payload.text.length),
                    )
                    attachedImage = attachmentToSend
                    attachedFiles = filesToSend
                }
            }
        }
    }
    val canSend = text.isNotBlank() || attachedImage != null || attachedFiles.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface)
            .imePadding(),
    ) {
        if (attachedImage != null) {
            val previewBitmap = remember(attachedImage?.data) {
                attachedImage?.data?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                Box {
                    previewBitmap?.let { bitmap ->
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "已附加图片",
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                    IconButton(
                        onClick = { attachedImage = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除附件",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
        }

        if (attachedFiles.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                attachedFiles.forEach { file ->
                    ComposerFileAttachmentRow(
                        attachment = file,
                        onRemove = {
                            attachedFiles = attachedFiles.filterNot { it == file }
                        },
                    )
                }
            }
        }

        goal?.let { current ->
            val goalActions = remember(current.threadId, current.status) {
                GoalCardActions(
                    togglePause = {
                        scope.launch {
                            val next = when (current.status) {
                                AppThreadGoalStatus.ACTIVE -> AppThreadGoalStatus.PAUSED
                                AppThreadGoalStatus.PAUSED,
                                AppThreadGoalStatus.BLOCKED,
                                AppThreadGoalStatus.USAGE_LIMITED,
                                AppThreadGoalStatus.BUDGET_LIMITED -> AppThreadGoalStatus.ACTIVE
                                AppThreadGoalStatus.COMPLETE -> return@launch
                            }
                            runCatching {
                                appModel.client.setThreadGoal(
                                    threadKey.serverId,
                                    AppThreadGoalSetRequest(
                                        threadId = threadKey.threadId,
                                        objective = null,
                                        status = next,
                                        tokenBudget = null,
                                    ),
                                )
                            }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
                        }
                    },
                    markComplete = {
                        scope.launch {
                            runCatching {
                                appModel.client.setThreadGoal(
                                    threadKey.serverId,
                                    AppThreadGoalSetRequest(
                                        threadId = threadKey.threadId,
                                        objective = null,
                                        status = AppThreadGoalStatus.COMPLETE,
                                        tokenBudget = null,
                                    ),
                                )
                            }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
                        }
                    },
                    setObjective = { objective ->
                        scope.launch {
                            runCatching {
                                appModel.client.setThreadGoal(
                                    threadKey.serverId,
                                    AppThreadGoalSetRequest(
                                        threadId = threadKey.threadId,
                                        objective = objective,
                                        status = null,
                                        tokenBudget = null,
                                    ),
                                )
                            }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
                        }
                    },
                    setBudget = { budget ->
                        scope.launch {
                            val resumeFromLimit = current.status == AppThreadGoalStatus.BUDGET_LIMITED
                                && budget != null
                                && budget > current.tokensUsed
                            runCatching {
                                appModel.client.setThreadGoal(
                                    threadKey.serverId,
                                    AppThreadGoalSetRequest(
                                        threadId = threadKey.threadId,
                                        objective = null,
                                        status = if (resumeFromLimit) AppThreadGoalStatus.ACTIVE else null,
                                        tokenBudget = budget,
                                    ),
                                )
                            }.onFailure { onSlashError?.invoke(it.message ?: "更新目标失败") }
                        }
                    },
                    clear = {
                        scope.launch {
                            runCatching {
                                appModel.client.clearThreadGoal(
                                    threadKey.serverId,
                                    AppThreadGoalClearRequest(threadId = threadKey.threadId),
                                )
                            }.onFailure { onSlashError?.invoke(it.message ?: "清除目标失败") }
                        }
                    },
                )
            }
            GoalPanel(current, goalActions)
        }

        activePlanProgress?.let { progress ->
            PlanProgressPanel(progress = progress)
        }

        activeTaskSummary?.let { summary ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BaoziTheme.codeBackground.copy(alpha = 0.72f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u2610",
                    color = BaoziTheme.accent,
                    fontSize = BaoziTextStyle.caption.scaled,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "进行中的任务",
                            color = BaoziTheme.textPrimary,
                            fontSize = BaoziTextStyle.caption.scaled,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = summary.progress,
                            color = BaoziTheme.accent,
                            fontSize = BaoziTextStyle.caption.scaled,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BerkeleyMono,
                        )
                    }
                    Text(
                        text = summary.label,
                        color = BaoziTheme.textSecondary,
                        fontSize = BaoziTextStyle.caption.scaled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Inline pending user input prompt (above composer)
        if (pendingUserInput != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BaoziTheme.codeBackground)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "需要输入",
                        color = BaoziTheme.textPrimary,
                        fontSize = BaoziTextStyle.caption.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (onDismissPendingUserInput != null) {
                        Text(
                            text = "✕",
                            color = BaoziTheme.textMuted,
                            fontSize = BaoziTextStyle.body.scaled,
                            modifier = Modifier
                                .clickable { onDismissPendingUserInput() }
                                .padding(4.dp)
                                .semantics { contentDescription = "关闭输入请求" },
                        )
                    }
                }
                for (question in pendingUserInput.questions) {
                    Text(question.question, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.footnote.scaled)
                    if (question.options.isNotEmpty()) {
                        // FlowRow so long option labels wrap to a new line
                        // instead of crushing a short option into a narrow
                        // column with character-by-character text wrapping.
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (option in question.options) {
                                val selected = userInputAnswers[question.id] == option.label
                                Text(
                                    text = option.label,
                                    color = if (selected) Color.Black else BaoziTheme.textPrimary,
                                    fontSize = BaoziTextStyle.caption.scaled,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .background(
                                            if (selected) BaoziTheme.accent else BaoziTheme.surface,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { userInputAnswers = userInputAnswers + (question.id to option.label) }
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    } else {
                        var answer by remember { mutableStateOf("") }
                        BasicTextField(
                            value = answer,
                            onValueChange = {
                                answer = it
                                userInputAnswers = userInputAnswers + (question.id to it)
                            },
                            textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.footnote.scaled),
                            cursorBrush = SolidColor(BaoziTheme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                        )
                    }
                }
                pendingUserInputSubmitError?.let { message ->
                    Text(
                        text = message,
                        color = Color(0xFFFF6B6B),
                        fontSize = BaoziTextStyle.caption.scaled,
                    )
                }
                Text(
                    text = "提交",
                    color = if (isSubmittingPendingUserInput) BaoziTheme.textMuted else Color.Black,
                    fontSize = BaoziTextStyle.code.scaled,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            if (isSubmittingPendingUserInput) BaoziTheme.surface else BaoziTheme.accent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = !isSubmittingPendingUserInput) {
                            scope.launch {
                                isSubmittingPendingUserInput = true
                                pendingUserInputSubmitError = null
                                try {
                                    val answers = pendingUserInput.questions.map { q ->
                                        PendingUserInputAnswer(
                                            questionId = q.id,
                                            answers = listOfNotNull(userInputAnswers[q.id]),
                                        )
                                    }
                                    appModel.store.respondToUserInput(pendingUserInput.id, answers)
                                    userInputAnswers = emptyMap()
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    LLog.e(
                                        "ComposerBar",
                                        "user input response failed",
                                        error,
                                        fields = mapOf("requestId" to pendingUserInput.id),
                                    )
                                    pendingUserInputSubmitError = responseSubmissionErrorMessage(error)
                                } finally {
                                    isSubmittingPendingUserInput = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        if (queuedFollowUps.isNotEmpty()) {
            QueuedFollowUpsPreviewPanel(
                previews = queuedFollowUps,
                onSteer = { preview ->
                    scope.launch {
                        runCatching {
                            appModel.store.steerQueuedFollowUp(threadKey, preview.id)
                        }
                    }
                },
                onDelete = { preview ->
                    scope.launch {
                        runCatching {
                            appModel.store.deleteQueuedFollowUp(threadKey, preview.id)
                        }
                    }
                },
            )
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isRecording && !isTranscribing && !isThinking) {
                IconButton(
                    onClick = { showAttachMenu = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "附加",
                        tint = BaoziTheme.textPrimary,
                    )
                }
            }

            // Text field
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp, max = 120.dp)
                    .background(BaoziTheme.codeBackground, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            text = "\u6d88\u606f\u2026",
                            color = BaoziTheme.textMuted,
                            fontSize = BaoziTextStyle.body.scaled,
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        textStyle = TextStyle(
                            color = BaoziTheme.textPrimary,
                            fontSize = BaoziTextStyle.body.scaled,
                            fontFamily = BaoziTheme.monoFont,
                        ),
                        cursorBrush = SolidColor(BaoziTheme.accent),
                        // Always reserve trailing space for the expand icon so
                        // wrapped lines don't slide under it when the icon
                        // appears (and it doesn't cause a layout jump when it
                        // toggles on/off at the 60-char threshold).
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 24.dp)
                            .focusRequester(inlineFocusRequester),
                    )

                    val shouldShowExpand = (text.contains('\n') || text.length > 60) &&
                        !isRecording && !isTranscribing
                    if (shouldShowExpand) {
                        IconButton(
                            onClick = { showExpanded = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "展开输入框",
                                tint = BaoziTheme.textSecondary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    // Slash command popup
                    DropdownMenu(
                        expanded = showSlashMenu,
                        onDismissRequest = { showSlashMenu = false },
                    ) {
                        for (cmd in filteredCommands) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("/${cmd.name}", color = BaoziTheme.accent, fontSize = BaoziTextStyle.footnote.scaled, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.width(8.dp))
                                        Text(cmd.description, color = BaoziTheme.textMuted, fontSize = BaoziTextStyle.caption2.scaled)
                                    }
                                },
                                onClick = {
                                    showSlashMenu = false
                                    if (dispatchSlashCommand(cmd.name, args = null)) {
                                        textFieldValue = TextFieldValue("")
                                        attachedImage = null
                                        attachedFiles = emptyList()
                                    }
                                },
                            )
                        }
                    }

                    // @file search popup
                    DropdownMenu(
                        expanded = showFileMenu,
                        onDismissRequest = { showFileMenu = false },
                    ) {
                        for (path in fileSearchResults) {
                            DropdownMenuItem(
                                text = { Text(path, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.caption.scaled, fontFamily = BaoziTheme.monoFont) },
                                onClick = {
                                    showFileMenu = false
                                    val atIdx = text.lastIndexOf('@')
                                    if (atIdx >= 0) {
                                        val updated = text.substring(0, atIdx) + "@$path "
                                        textFieldValue = TextFieldValue(
                                            text = updated,
                                            selection = TextRange(updated.length),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                when {
                    isRecording -> {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val auth = runCatching {
                                        appModel.client.authStatus(
                                            threadKey.serverId,
                                            AuthStatusRequest(
                                                includeToken = true,
                                                refreshToken = false,
                                            ),
                                        )
                                    }.getOrNull()
                                    val transcript = transcriptionManager.stopAndTranscribe(
                                        authMethod = auth?.authMethod,
                                        authToken = auth?.authToken,
                                    )
                                    transcript?.let {
                                        textFieldValue = insertComposerTranscript(textFieldValue, it)
                                    }
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "停止录音",
                                tint = BaoziTheme.accentStrong,
                            )
                        }
                    }

                    isTranscribing -> {
                        Spacer(Modifier.width(8.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.width(24.dp),
                            color = BaoziTheme.accent,
                            trackColor = Color.Transparent,
                        )
                    }

                    else -> {
                        val realtimeAvailable = remember {
                            com.kris99.baozi.android.ui.ExperimentalFeatures.isEnabled(
                                com.kris99.baozi.android.ui.BaoziFeature.REALTIME_VOICE,
                            )
                        }
                        val voiceController = remember { com.kris99.baozi.android.state.VoiceRuntimeController.shared }
                        val voiceSession by voiceController.activeVoiceSession.collectAsState()
                        val voiceSnapshot by appModel.snapshot.collectAsState()
                        val voicePhase = voiceSnapshot?.voiceSession?.phase
                        val voiceInputLevel = voiceSession?.inputLevel ?: 0f

                        if (realtimeAvailable && text.isEmpty() && attachedImage == null && attachedFiles.isEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            com.kris99.baozi.android.ui.voice.InlineVoiceButton(
                                phase = voicePhase,
                                inputLevel = voiceInputLevel,
                                isAvailable = true,
                                onStart = {
                                    scope.launch {
                                        voiceController.startVoiceOnThread(appModel, threadKey)
                                    }
                                },
                                onStop = {
                                    scope.launch {
                                        voiceController.stopActiveVoiceSession(appModel)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (transcriptionManager.hasMicPermission(context)) {
                                        transcriptionManager.startRecording(context)
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "语音",
                                    tint = BaoziTheme.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            if (canSend) {
                IconButton(
                    onClick = sendCurrent,
                    enabled = !isRecording && !isTranscribing,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isRecording && !isTranscribing) {
                                BaoziTheme.accent
                            } else {
                                BaoziTheme.accent.copy(alpha = 0.45f)
                            },
                            CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.Black,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            if (isThinking && !canSend) {
                Text(
                    text = "取消",
                    color = BaoziTheme.textPrimary,
                    fontSize = BaoziTextStyle.caption.scaled,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(BaoziTheme.surface)
                        .clickable {
                            val turnId = activeTurnId ?: return@clickable
                            scope.launch {
                                try {
                                    appModel.client.interruptTurn(
                                        threadKey.serverId,
                                        AppInterruptTurnRequest(threadId = threadKey.threadId, turnId = turnId),
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        if (showExpanded) {
            ComposerExpandedDialog(
                text = text,
                onTextChange = {
                    textFieldValue = TextFieldValue(
                        text = it,
                        selection = TextRange(it.length),
                    )
                },
                onSend = sendCurrent,
                onDismiss = {
                    showExpanded = false
                    // Restore inline focus after the dialog animates away, so
                    // the user can keep typing without tapping again.
                    scope.launch {
                        kotlinx.coroutines.delay(80)
                        runCatching { inlineFocusRequester.requestFocus() }
                    }
                },
                canSend = canSend,
            )
        }

        val hasIndicators = contextPercent != null || rateLimits?.primary != null || rateLimits?.secondary != null
        if (hasIndicators) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 52.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rateLimits?.primary?.let { window ->
                    RateLimitBadge(window)
                }
                rateLimits?.secondary?.let { window ->
                    RateLimitBadge(window)
                }
                contextPercent?.let {
                    ContextBadge(it)
                }
            }
        }
    }

    if (showAttachMenu) {
        val clipboardManager = remember { context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }
        val clipboardHasImage = remember(showAttachMenu) {
            val clip = clipboardManager.primaryClip ?: return@remember false
            if (clip.itemCount == 0) return@remember false
            val desc = clip.description
            for (i in 0 until desc.mimeTypeCount) {
                if (desc.getMimeType(i).startsWith("image/")) return@remember true
            }
            clip.getItemAt(0)?.uri?.let { uri ->
                context.contentResolver.getType(uri)?.startsWith("image/") == true
            } ?: false
        }

        ModalBottomSheet(
            onDismissRequest = { showAttachMenu = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BaoziTheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "附加",
                    color = BaoziTheme.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (clipboardHasImage) {
                    AttachmentActionRow(
                        title = "粘贴图片",
                        onClick = {
                            showAttachMenu = false
                            val clip = clipboardManager.primaryClip
                            val uri = clip?.getItemAt(0)?.uri
                            if (uri != null) {
                                attachedImage = readAttachmentFromUri(context, uri)
                            }
                        },
                    )
                }

                AttachmentActionRow(
                    title = "相册",
                    onClick = {
                        showAttachMenu = false
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )

                AttachmentActionRow(
                    title = "选择文件",
                    onClick = {
                        showAttachMenu = false
                        filePicker.launch(ALL_FILE_MIME_TYPES)
                    },
                )

                AttachmentActionRow(
                    title = "拍照",
                    onClick = {
                        showAttachMenu = false
                        cameraLauncher.launch(null)
                    },
                )
            }
        }
    }
}

private data class QueuedFollowUpUiStyle(
    val title: String,
    val tint: Color,
    val background: Color,
    val border: Color,
)

@Composable
private fun QueuedFollowUpsPreviewPanel(
    previews: List<AppQueuedFollowUpPreview>,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(BaoziTheme.codeBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = BaoziTheme.accent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "下一项排队中",
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = previews.size.toString(),
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.caption2.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(BaoziTheme.surface.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        previews.forEach { preview ->
            QueuedFollowUpCard(
                preview = preview,
                onSteer = onSteer,
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun QueuedFollowUpCard(
    preview: AppQueuedFollowUpPreview,
    onSteer: (AppQueuedFollowUpPreview) -> Unit,
    onDelete: (AppQueuedFollowUpPreview) -> Unit,
) {
    val style = queuedFollowUpUiStyle(preview.kind)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, style.border, RoundedCornerShape(12.dp))
            .background(style.background, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(style.tint.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(style.tint, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = style.title,
                    color = style.tint,
                    fontSize = BaoziTextStyle.caption2.scaled,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = preview.text,
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.caption.scaled,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (preview.kind == AppQueuedFollowUpKind.MESSAGE) {
            Text(
                text = "\u21b3 \u5f15\u5bfc",
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(BaoziTheme.surface.copy(alpha = 0.96f), RoundedCornerShape(999.dp))
                    .clickable { onSteer(preview) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        IconButton(
            onClick = { onDelete(preview) },
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除排队的后续项",
                tint = BaoziTheme.textSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun queuedFollowUpUiStyle(kind: AppQueuedFollowUpKind): QueuedFollowUpUiStyle =
    when (kind) {
        AppQueuedFollowUpKind.MESSAGE ->
            QueuedFollowUpUiStyle(
                title = "排队的消息",
                tint = BaoziTheme.accent,
                background = BaoziTheme.accent.copy(alpha = 0.08f),
                border = BaoziTheme.accent.copy(alpha = 0.24f),
            )

        AppQueuedFollowUpKind.PENDING_STEER ->
            QueuedFollowUpUiStyle(
                title = "引导排队中",
                tint = BaoziTheme.accentStrong,
                background = BaoziTheme.accentStrong.copy(alpha = 0.10f),
                border = BaoziTheme.accentStrong.copy(alpha = 0.28f),
            )

        AppQueuedFollowUpKind.RETRYING_STEER ->
            QueuedFollowUpUiStyle(
                title = "重试引导中",
                tint = BaoziTheme.warning,
                background = BaoziTheme.warning.copy(alpha = 0.10f),
                border = BaoziTheme.warning.copy(alpha = 0.28f),
            )
    }

private fun reasoningEffortFromServerValue(value: String): ReasoningEffort? =
    when (value.trim().lowercase()) {
        "none" -> ReasoningEffort.NONE
        "minimal" -> ReasoningEffort.MINIMAL
        "low" -> ReasoningEffort.LOW
        "medium" -> ReasoningEffort.MEDIUM
        "high" -> ReasoningEffort.HIGH
        "xhigh" -> ReasoningEffort.X_HIGH
        "max" -> ReasoningEffort.MAX
        else -> null
    }

@Composable
internal fun CollaborationModeChip(
    mode: uniffi.codex_mobile_client.AppModeKind,
    onClick: () -> Unit,
) {
    val label = when (mode) {
        uniffi.codex_mobile_client.AppModeKind.PLAN -> "计划"
        uniffi.codex_mobile_client.AppModeKind.DEFAULT -> "默认"
    }
    val container = if (mode == uniffi.codex_mobile_client.AppModeKind.PLAN) {
        BaoziTheme.accent
    } else {
        BaoziTheme.surfaceLight
    }
    val contentColor = if (mode == uniffi.codex_mobile_client.AppModeKind.PLAN) {
        Color.Black
    } else {
        BaoziTheme.textPrimary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = BaoziTextStyle.caption.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "打开协作模式选择器",
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun PlanProgressPanel(
    progress: uniffi.codex_mobile_client.AppPlanProgressSnapshot,
) {
    var expanded by remember(progress.turnId) { mutableStateOf(true) }
    val completed = remember(progress.plan) {
        progress.plan.count { it.status == uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED }
    }
    val currentStepLabel = remember(progress.plan) {
        val currentStep = progress.plan.firstOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS
        } ?: progress.plan.firstOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.PENDING
        } ?: progress.plan.lastOrNull {
            it.status == uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED
        }

        currentStep?.step?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (progress.plan.isEmpty()) "暂无计划任务" else "计划完成"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BaoziTheme.codeBackground.copy(alpha = 0.82f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
        ) {
            Text(
                text = if (expanded) "计划进度" else "计划",
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$completed/${progress.plan.size}",
                color = BaoziTheme.accent,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                fontFamily = BerkeleyMono,
            )
            if (!expanded) {
                Text(
                    text = currentStepLabel,
                    color = BaoziTheme.textPrimary,
                    fontSize = BaoziTextStyle.caption.scaled,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "收起计划进度" else "展开计划进度",
                tint = BaoziTheme.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            progress.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                Text(
                    text = explanation,
                    color = BaoziTheme.textSecondary,
                    fontSize = BaoziTextStyle.caption.scaled,
                )
            }
            progress.plan.forEachIndexed { index, step ->
                val icon = when (step.status) {
                    uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED -> "✓"
                    uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS -> "●"
                    uniffi.codex_mobile_client.AppPlanStepStatus.PENDING -> "○"
                }
                val tint = when (step.status) {
                    uniffi.codex_mobile_client.AppPlanStepStatus.COMPLETED -> BaoziTheme.success
                    uniffi.codex_mobile_client.AppPlanStepStatus.IN_PROGRESS -> BaoziTheme.warning
                    uniffi.codex_mobile_client.AppPlanStepStatus.PENDING -> BaoziTheme.textMuted
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = icon,
                        color = tint,
                        fontSize = BaoziTextStyle.caption.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${index + 1}.",
                        color = BaoziTheme.textMuted,
                        fontSize = BaoziTextStyle.caption.scaled,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BerkeleyMono,
                    )
                    Text(
                        text = step.step,
                        color = BaoziTheme.textPrimary,
                        fontSize = BaoziTextStyle.caption.scaled,
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentActionRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.body.scaled, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ComposerFileAttachmentRow(
    attachment: ComposerFileAttachment,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BaoziTheme.codeBackground.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "FILE",
            color = BaoziTheme.accent,
            fontSize = BaoziTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BerkeleyMono,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.label,
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.caption.scaled,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.path,
                color = BaoziTheme.textMuted,
                fontSize = BaoziTextStyle.caption2.scaled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "移除文件",
                tint = BaoziTheme.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun insertComposerTranscript(current: TextFieldValue, transcript: String): TextFieldValue {
    val insertion = transcript.trim()
    if (insertion.isEmpty()) return current

    val text = current.text
    val start = current.selection.min.coerceIn(0, text.length)
    val end = current.selection.max.coerceIn(0, text.length)
    val replacement = composerInsertionText(insertion, text, start, end)
    val updated = text.replaceRange(start, end, replacement)
    val cursor = start + replacement.length
    return TextFieldValue(
        text = updated,
        selection = TextRange(cursor),
    )
}

private fun composerInsertionText(insertion: String, text: String, start: Int, end: Int): String {
    var replacement = insertion
    if (start > 0 && !text[start - 1].isWhitespace()) {
        replacement = " $replacement"
    }
    if (end < text.length && !text[end].isWhitespace()) {
        replacement += " "
    }
    return replacement
}

private sealed interface PickedComposerAttachment {
    data class Image(val attachment: ComposerImageAttachment) : PickedComposerAttachment
    data class File(val attachment: ComposerFileAttachment) : PickedComposerAttachment
}

private fun readPickedComposerAttachment(
    context: android.content.Context,
    uri: Uri,
): PickedComposerAttachment? {
    val resolver = context.contentResolver
    val displayName = resolver.displayName(uri) ?: uri.lastPathSegment ?: "selected-file"
    val mimeType = resolver.getType(uri).orEmpty()
    if (isSupportedImageFile(displayName, mimeType)) {
        readAttachmentFromUri(context, uri)?.let { return PickedComposerAttachment.Image(it) }
    }
    return PickedComposerAttachment.File(
        ComposerFileAttachment(
            label = displayName.substringBeforeLast('.', displayName),
            path = uri.toString(),
        ),
    )
}

private fun android.content.ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index < 0) null else cursor.getString(index)
    }

private fun isSupportedImageFile(displayName: String, mimeType: String): Boolean {
    val normalizedMimeType = mimeType.lowercase()
    if (SUPPORTED_IMAGE_FILE_MIME_TYPES.any { it == normalizedMimeType }) {
        return true
    }
    val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in setOf("png", "jpg", "jpeg", "gif", "webp")
}

private fun readAttachmentFromUri(context: android.content.Context, uri: Uri): ComposerImageAttachment? {
    val resolver = context.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val mimeType = resolver.getType(uri).orEmpty()
    return prepareImageAttachment(bytes, mimeType)
}

private fun prepareBitmapAttachment(bitmap: Bitmap): ComposerImageAttachment? {
    val output = ByteArrayOutputStream()
    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val mimeType = if (bitmap.hasAlpha()) "image/png" else "image/jpeg"
    val quality = if (bitmap.hasAlpha()) 100 else 85
    if (!bitmap.compress(format, quality, output)) return null
    return ComposerImageAttachment(output.toByteArray(), mimeType)
}

private fun prepareImageAttachment(bytes: ByteArray, mimeTypeHint: String): ComposerImageAttachment? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val inferredMime = mimeTypeHint.lowercase()
    if (inferredMime == "image/png" && bitmap.hasAlpha()) {
        return ComposerImageAttachment(bytes, "image/png")
    }
    return prepareBitmapAttachment(bitmap)
}

internal fun parseSlashCommandInvocation(text: String): SlashInvocation? {
    val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
    if (!firstLine.startsWith("/")) return null
    val commandText = firstLine.drop(1).trim()
    if (commandText.isEmpty()) return null
    val parts = commandText.split(Regex("\\s+"), limit = 2)
    val command = SLASH_COMMANDS.firstOrNull { it.name == parts.first().lowercase() } ?: return null
    val args = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    return SlashInvocation(command = command, args = args)
}

private fun goalSummary(goal: AppThreadGoal): String {
    return buildString {
        append("目标：")
        append(goal.objective)
        append("\n状态：")
        append(goalStatusLabel(goal.status))
        append("\n已用 token：")
        append(goal.tokensUsed)
        goal.tokenBudget?.let {
            append("\ntoken 预算：")
            append(it)
        }
    }
}

private fun goalStatusLabel(status: AppThreadGoalStatus): String =
    when (status) {
        AppThreadGoalStatus.ACTIVE -> "进行中"
        AppThreadGoalStatus.PAUSED -> "已暂停"
        AppThreadGoalStatus.BLOCKED -> "已阻塞"
        AppThreadGoalStatus.USAGE_LIMITED -> "用量受限"
        AppThreadGoalStatus.BUDGET_LIMITED -> "预算受限"
        AppThreadGoalStatus.COMPLETE -> "已完成"
    }

data class GoalCardActions(
    val togglePause: () -> Unit,
    val markComplete: () -> Unit,
    val setObjective: (String) -> Unit,
    val setBudget: (Long?) -> Unit,
    val clear: () -> Unit,
) {
    companion object {
        val Noop = GoalCardActions(
            togglePause = {},
            markComplete = {},
            setObjective = {},
            setBudget = {},
            clear = {},
        )
    }
}

@Composable
private fun GoalPanel(goal: AppThreadGoal, actions: GoalCardActions) {
    val tint = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> BaoziTheme.accent
        AppThreadGoalStatus.PAUSED -> BaoziTheme.textMuted
        AppThreadGoalStatus.BLOCKED,
        AppThreadGoalStatus.USAGE_LIMITED,
        AppThreadGoalStatus.BUDGET_LIMITED -> BaoziTheme.warning
        AppThreadGoalStatus.COMPLETE -> BaoziTheme.success
    }
    val statusLabel = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> "进行中"
        AppThreadGoalStatus.PAUSED -> "已暂停"
        AppThreadGoalStatus.BLOCKED -> "已阻塞"
        AppThreadGoalStatus.USAGE_LIMITED -> "用量受限"
        AppThreadGoalStatus.BUDGET_LIMITED -> "已受限"
        AppThreadGoalStatus.COMPLETE -> "已完成"
    }
    val budgetProgress: Float? = goal.tokenBudget?.takeIf { it > 0 }?.let { budget ->
        (goal.tokensUsed.toFloat() / budget.toFloat()).coerceIn(0f, 1f)
    }
    val budgetLabel = goal.tokenBudget?.takeIf { it > 0 }?.let { budget ->
        "${formatGoalTokens(goal.tokensUsed)} / ${formatGoalTokens(budget)}"
    }
    val progressTint = when {
        budgetProgress == null -> tint
        budgetProgress >= 1f -> BaoziTheme.danger
        budgetProgress >= 0.85f -> BaoziTheme.warning
        else -> tint
    }
    val progressTextTint = when {
        budgetProgress == null -> BaoziTheme.textSecondary
        budgetProgress >= 1f -> BaoziTheme.danger
        budgetProgress >= 0.85f -> BaoziTheme.warning
        else -> BaoziTheme.textSecondary
    }
    val canTogglePause = goal.status != AppThreadGoalStatus.COMPLETE
    val pauseResumeLabel: String? = when (goal.status) {
        AppThreadGoalStatus.ACTIVE -> "暂停目标"
        AppThreadGoalStatus.PAUSED -> "恢复目标"
        AppThreadGoalStatus.BLOCKED -> "恢复目标（忽略阻塞）"
        AppThreadGoalStatus.USAGE_LIMITED -> "恢复目标（忽略用量上限）"
        AppThreadGoalStatus.BUDGET_LIMITED -> "恢复目标（忽略上限）"
        AppThreadGoalStatus.COMPLETE -> null
    }

    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Pulsing status dot — only animates while the goal is active. Mirrors
    // the iOS pill's 0.35 ↔ 1.0 ease-in-out at 1.1s autoreverse.
    val pulse = rememberInfiniteTransition(label = "goalPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "goalPulseAlpha",
    )
    val statusDotAlpha = if (goal.status == AppThreadGoalStatus.ACTIVE) pulseAlpha else 1f
    val animatedProgress by animateFloatAsState(
        targetValue = budgetProgress ?: 0f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "goalProgress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BaoziTheme.codeBackground.copy(alpha = 0.92f))
            .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status pill — tappable to pause/resume (or override cap when
            // BUDGET_LIMITED). Disabled once the goal is COMPLETE.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(tint.copy(alpha = 0.14f))
                    .border(0.5.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .clickable(enabled = canTogglePause) { actions.togglePause() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tint.copy(alpha = statusDotAlpha), CircleShape),
                )
                Text(
                    text = statusLabel.uppercase(),
                    color = tint,
                    fontSize = 10f.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BerkeleyMono,
                )
            }

            Text(
                text = goal.objective,
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.caption.scaled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showEditDialog = true },
            )

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "目标操作",
                        tint = BaoziTheme.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (pauseResumeLabel != null) {
                        DropdownMenuItem(
                            text = { Text(pauseResumeLabel, color = BaoziTheme.textPrimary) },
                            onClick = {
                                showMenu = false
                                actions.togglePause()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("编辑目标", color = BaoziTheme.textPrimary) },
                        onClick = {
                            showMenu = false
                            showEditDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("设置 token 预算", color = BaoziTheme.textPrimary) },
                        onClick = {
                            showMenu = false
                            showBudgetDialog = true
                        },
                    )
                    if (goal.status != AppThreadGoalStatus.COMPLETE) {
                        DropdownMenuItem(
                            text = { Text("标记完成", color = BaoziTheme.textPrimary) },
                            onClick = {
                                showMenu = false
                                actions.markComplete()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("清除目标", color = BaoziTheme.danger) },
                        onClick = {
                            showMenu = false
                            showClearConfirm = true
                        },
                    )
                }
            }
        }

        if (budgetProgress != null) {
            val percent = (budgetProgress * 100).toInt()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tint.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = animatedProgress.coerceIn(0f, 1f))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(progressTint.copy(alpha = 0.85f), progressTint),
                                ),
                                RoundedCornerShape(999.dp),
                            ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (budgetLabel != null) {
                        Text(
                            text = budgetLabel,
                            color = BaoziTheme.textSecondary,
                            fontSize = 10f.scaled,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BerkeleyMono,
                        )
                    }
                    Text(
                        text = "$percent%",
                        color = progressTextTint,
                        fontSize = 10f.scaled,
                        fontWeight = FontWeight.Bold,
                        fontFamily = BerkeleyMono,
                    )
                }
            }
        }

        // Usage chips (tokens used + elapsed time). Visible whenever the goal
        // has any usage — including when no budget is set, so the user can
        // still see what the goal has consumed. Mirrors iOS
        // `ConversationComposerContentView.usageMetricsRow`.
        val hasUsage = goal.tokensUsed > 0 || goal.timeUsedSeconds > 0
        if (hasUsage) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (goal.tokensUsed > 0) {
                    Text(
                        text = "T ${formatGoalTokens(goal.tokensUsed)}",
                        color = BaoziTheme.textSecondary,
                        fontSize = 10f.scaled,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = BerkeleyMono,
                    )
                }
                if (goal.tokensUsed > 0 && goal.timeUsedSeconds > 0) {
                    Text(
                        text = "·",
                        color = BaoziTheme.textMuted.copy(alpha = 0.6f),
                        fontSize = 10f.scaled,
                        fontFamily = BerkeleyMono,
                    )
                }
                if (goal.timeUsedSeconds > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = BaoziTheme.textSecondary,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatGoalSeconds(goal.timeUsedSeconds),
                            color = BaoziTheme.textSecondary,
                            fontSize = 10f.scaled,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = BerkeleyMono,
                        )
                    }
                }
                Spacer(Modifier.weight(1f, fill = false))
            }
        }
    }

    if (showEditDialog) {
        GoalTextInputDialog(
            title = "编辑目标",
            initial = goal.objective,
            placeholder = "你想完成什么？",
            singleLine = false,
            confirmLabel = "保存",
            onConfirm = { value ->
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) actions.setObjective(trimmed)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showBudgetDialog) {
        GoalTextInputDialog(
            title = "Token 预算",
            initial = goal.tokenBudget?.toString().orEmpty(),
            placeholder = "例如 50000",
            singleLine = true,
            keyboardNumeric = true,
            confirmLabel = "保存",
            helper = "达到上限时智能体将暂停。",
            onConfirm = { value ->
                val parsed = value.trim().toLongOrNull()
                if (parsed != null && parsed > 0) actions.setBudget(parsed)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false },
        )
    }

    if (showClearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            confirmButton = {
                Text(
                    text = "清除目标",
                    color = BaoziTheme.danger,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            showClearConfirm = false
                            actions.clear()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    color = BaoziTheme.textPrimary,
                    modifier = Modifier
                        .clickable { showClearConfirm = false }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            },
            title = {
                Text("清除这个目标？", color = BaoziTheme.textPrimary, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "从此线程移除目标。智能体将停止跟踪目标进度。",
                    color = BaoziTheme.textSecondary,
                )
            },
            containerColor = BaoziTheme.surface,
        )
    }
}

@Composable
private fun GoalTextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    helper: String? = null,
    singleLine: Boolean = true,
    keyboardNumeric: Boolean = false,
) {
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Text(
                text = confirmLabel,
                color = BaoziTheme.accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onConfirm(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        },
        dismissButton = {
            Text(
                text = "取消",
                color = BaoziTheme.textPrimary,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        },
        title = {
            Text(title, color = BaoziTheme.textPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = BaoziTheme.textPrimary,
                        fontSize = BaoziTextStyle.body.scaled,
                        fontFamily = BaoziTheme.monoFont,
                    ),
                    cursorBrush = SolidColor(BaoziTheme.accent),
                    keyboardOptions = if (keyboardNumeric) {
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        )
                    } else {
                        androidx.compose.foundation.text.KeyboardOptions.Default
                    },
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.codeBackground, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = BaoziTheme.textMuted,
                                    fontSize = BaoziTextStyle.body.scaled,
                                )
                            }
                            inner()
                        }
                    },
                )
                if (helper != null) {
                    Text(
                        text = helper,
                        color = BaoziTheme.textSecondary,
                        fontSize = BaoziTextStyle.caption.scaled,
                    )
                }
            }
        },
        containerColor = BaoziTheme.surface,
    )
}

private fun formatGoalTokens(value: Long): String =
    when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fk".format(value / 1_000.0)
        else -> value.toString()
    }

private fun formatGoalSeconds(seconds: Long): String {
    if (seconds < 60) return "${seconds}s"
    val total = seconds.toInt()
    val minutes = total / 60
    val remainSecs = total % 60
    if (total < 3600) {
        return if (remainSecs == 0) "${minutes}m" else "${minutes}m ${remainSecs}s"
    }
    val hours = total / 3600
    val remainMins = (total % 3600) / 60
    return if (remainMins == 0) "${hours}h" else "${hours}h ${remainMins}m"
}

// ── Rate Limit Badge (matching iOS RateLimitBadgeView) ───────────────────────

@Composable
private fun RateLimitBadge(window: uniffi.codex_mobile_client.RateLimitWindow) {
    val remaining = (100 - window.usedPercent.toInt()).coerceIn(0, 100)
    val label = window.windowDurationMins?.let { mins ->
        when {
            mins >= 1440 -> "${mins / 1440}d"
            mins >= 60 -> "${mins / 60}h"
            else -> "${mins}m"
        }
    } ?: "?"
    val tint = when {
        remaining <= 10 -> BaoziTheme.danger
        remaining <= 30 -> BaoziTheme.warning
        else -> BaoziTheme.textMuted
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = BaoziTheme.textSecondary,
            fontSize = 10f.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BaoziTheme.monoFont,
        )
        ContextBadge(percent = remaining, tint = tint)
    }
}

// ── Context Badge (matching iOS ContextBadgeView) ────────────────────────────

@Composable
private fun ContextBadge(
    percent: Int,
    tint: Color = when {
        percent <= 15 -> BaoziTheme.danger
        percent <= 35 -> BaoziTheme.warning
        else -> BaoziTheme.success
    },
) {
    val normalizedPercent = percent.coerceIn(0, 100)

    Box(
        modifier = Modifier
            .size(width = 35.dp, height = 16.dp)
            .background(Color.Transparent, RoundedCornerShape(4.dp))
            .border(1.2.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Fill bar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = normalizedPercent / 100f)
                .background(tint.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
        )
        // Number overlay
        Text(
            text = "$normalizedPercent",
            color = tint,
            fontSize = 9f.scaled,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = BaoziTheme.monoFont,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
