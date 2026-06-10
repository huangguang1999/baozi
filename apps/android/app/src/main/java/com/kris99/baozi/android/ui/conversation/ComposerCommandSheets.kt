package com.kris99.baozi.android.ui.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.common.reportsEffectiveThreadPermissions
import com.kris99.baozi.android.ui.common.supportsThreadPermissionOverrides
import com.kris99.baozi.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AbsolutePath
import uniffi.codex_mobile_client.AppAskForApproval
import uniffi.codex_mobile_client.AppListExperimentalFeaturesRequest
import uniffi.codex_mobile_client.AppListSkillsRequest
import uniffi.codex_mobile_client.AppWriteConfigValueRequest
import uniffi.codex_mobile_client.ExperimentalFeature
import uniffi.codex_mobile_client.AppMergeStrategy
import uniffi.codex_mobile_client.AppSandboxPolicy
import uniffi.codex_mobile_client.SkillMetadata
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.threadPermissionsAreAuthoritative

private data class ComposerSelectionOption(
    val title: String,
    val description: String,
    val wireValue: String,
)

private val composerApprovalOptions = listOf(
    ComposerSelectionOption(
        title = "默认",
        description = "使用会话或服务器默认设置",
        wireValue = "inherit",
    ),
    ComposerSelectionOption(
        title = "不信任",
        description = "执行操作前始终询问",
        wireValue = "untrusted",
    ),
    ComposerSelectionOption(
        title = "失败时",
        description = "仅在命令失败时询问",
        wireValue = "on-failure",
    ),
    ComposerSelectionOption(
        title = "请求时",
        description = "请求提权时询问",
        wireValue = "on-request",
    ),
    ComposerSelectionOption(
        title = "从不",
        description = "无需批准直接运行",
        wireValue = "never",
    ),
)

private val composerSandboxOptions = listOf(
    ComposerSelectionOption(
        title = "默认",
        description = "使用会话或服务器默认设置",
        wireValue = "inherit",
    ),
    ComposerSelectionOption(
        title = "只读",
        description = "可读取文件，但不能编辑",
        wireValue = "read-only",
    ),
    ComposerSelectionOption(
        title = "工作区写入",
        description = "可编辑文件，但仅限本工作区",
        wireValue = "workspace-write",
    ),
    ComposerSelectionOption(
        title = "完全访问",
        description = "可编辑本工作区以外的文件",
        wireValue = "danger-full-access",
    ),
)

private fun selectedApprovalLabel(approvalPolicy: String): String =
    composerApprovalOptions.firstOrNull { it.wireValue == approvalPolicy }?.title ?: "自定义"

private fun selectedSandboxLabel(sandboxMode: String): String =
    composerSandboxOptions.firstOrNull { it.wireValue == sandboxMode }?.title ?: "自定义"

private fun AppAskForApproval.displayTitle(): String =
    when (this) {
        AppAskForApproval.UnlessTrusted -> "不信任"
        AppAskForApproval.OnFailure -> "失败时"
        AppAskForApproval.OnRequest -> "请求时"
        is AppAskForApproval.Granular -> "细粒度"
        AppAskForApproval.Never -> "从不"
    }

private fun AppSandboxPolicy.displayTitle(): String =
    when (this) {
        AppSandboxPolicy.DangerFullAccess -> "完全访问"
        is AppSandboxPolicy.ReadOnly -> "只读"
        is AppSandboxPolicy.WorkspaceWrite -> "工作区写入"
        is AppSandboxPolicy.ExternalSandbox -> "外部沙箱"
    }

@Composable
fun ComposerPermissionsSheet(threadKey: ThreadKey? = null, onDismiss: () -> Unit) {
    val appModel = LocalAppModel.current
    val launchState by appModel.launchState.snapshot.collectAsState()
    val selectedApproval = appModel.launchState.selectedApprovalPolicy(threadKey)
    val selectedSandbox = appModel.launchState.selectedSandboxMode(threadKey)
    val effectiveThread = appModel.snapshot.value?.threads?.firstOrNull { it.key == threadKey }
    val selectedRuntime = effectiveThread?.agentRuntimeKind ?: launchState.selectedAgentRuntimeKind
    val currentRuntimeSupportsPermissionOverrides =
        selectedRuntime?.supportsThreadPermissionOverrides ?: true
    val hasAuthoritativeThreadPermissions = if (
        (selectedRuntime?.reportsEffectiveThreadPermissions ?: true) &&
        threadPermissionsAreAuthoritative(
            approvalPolicy = effectiveThread?.effectiveApprovalPolicy,
            sandboxPolicy = effectiveThread?.effectiveSandboxPolicy,
        )
    ) true else false
    val currentApprovalLabel =
        if (hasAuthoritativeThreadPermissions) effectiveThread?.effectiveApprovalPolicy?.displayTitle() ?: "同步中..."
        else "同步中..."
    val currentSandboxLabel =
        if (hasAuthoritativeThreadPermissions) effectiveThread?.effectiveSandboxPolicy?.displayTitle() ?: "同步中..."
        else "同步中..."
    val usesThreadDefaults = selectedApproval == "inherit" && selectedSandbox == "inherit"
    val scrollState = rememberScrollState()

    LaunchedEffect(threadKey) {
        threadKey ?: return@LaunchedEffect
        appModel.hydrateThreadPermissions(threadKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.92f)
            .verticalScroll(scrollState)
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader(title = "权限", onDismiss = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BaoziTheme.surface.copy(alpha = 0.82f), RoundedCornerShape(20.dp))
                .border(1.dp, BaoziTheme.border.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "会话(线程)权限",
                        color = BaoziTheme.textPrimary,
                        fontSize = 18f.scaled,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (currentRuntimeSupportsPermissionOverrides) {
                            "更改在你下一轮及之后的对话中生效。"
                        } else {
                            "该运行时自行管理其权限。"
                        },
                        color = BaoziTheme.textMuted,
                        fontSize = BaoziTextStyle.caption2.scaled,
                    )
                }
                Text(
                    text = if (!currentRuntimeSupportsPermissionOverrides) {
                        "运行时管理"
                    } else if (usesThreadDefaults) {
                        "使用默认"
                    } else {
                        "自定义覆盖"
                    },
                    color = if (!currentRuntimeSupportsPermissionOverrides || usesThreadDefaults) {
                        BaoziTheme.textSecondary
                    } else {
                        BaoziTheme.accentStrong
                    },
                    fontSize = BaoziTextStyle.caption2.scaled,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            color = (
                                if (!currentRuntimeSupportsPermissionOverrides || usesThreadDefaults) {
                                    BaoziTheme.surfaceLight
                                } else {
                                    BaoziTheme.accentStrong
                                }
                            ).copy(alpha = 0.16f),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PermissionSummaryTile(
                    title = "下一轮",
                    approval = selectedApprovalLabel(selectedApproval),
                    sandbox = selectedSandboxLabel(selectedSandbox),
                    accent = BaoziTheme.accentStrong,
                    modifier = Modifier.weight(1f),
                )
                if (threadKey != null) {
                    PermissionSummaryTile(
                        title = "当前会话",
                        approval = currentApprovalLabel,
                        sandbox = currentSandboxLabel,
                        accent = if (hasAuthoritativeThreadPermissions) BaoziTheme.textSecondary else BaoziTheme.warning,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (currentRuntimeSupportsPermissionOverrides) {
            PermissionSettingsSection(
                title = "批准策略",
                subtitle = "选择 Codex 何时请求批准",
            ) {
                PermissionDropdownField(
                    options = composerApprovalOptions,
                    selectedValue = selectedApproval,
                    onSelect = { value ->
                        appModel.launchState.updateThreadPermissions(threadKey, value, selectedSandbox)
                    },
                )
            }

            PermissionSettingsSection(
                title = "沙箱设置",
                subtitle = "选择 Codex 运行命令时的权限范围",
            ) {
                PermissionDropdownField(
                    options = composerSandboxOptions,
                    selectedValue = selectedSandbox,
                    onSelect = { value ->
                        appModel.launchState.updateThreadPermissions(threadKey, selectedApproval, value)
                    },
                )
            }
        } else {
            PermissionSettingsSection(
                title = "运行时管理",
                subtitle = "该运行时不接受客户端侧的权限覆盖。",
            ) {
                Text(
                    text = "请使用该运行时自带的控制项来设置批准和沙箱行为。",
                    color = BaoziTheme.textSecondary,
                    fontSize = BaoziTextStyle.caption.scaled,
                )
            }
        }
    }
}

@Composable
private fun PermissionSummaryTile(
    title: String,
    approval: String,
    sandbox: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(BaoziTheme.surfaceLight.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .border(1.dp, BaoziTheme.border.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = BaoziTheme.textSecondary,
            fontSize = BaoziTextStyle.caption2.scaled,
            fontWeight = FontWeight.SemiBold,
        )
        PermissionSummaryRow(label = "批准", value = approval, accent = accent)
        PermissionSummaryRow(label = "沙箱", value = sandbox, accent = accent)
    }
}

@Composable
private fun PermissionSummaryRow(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            color = BaoziTheme.textMuted,
            fontSize = 10f.scaled,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = accent,
            fontSize = BaoziTextStyle.code.scaled,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PermissionSettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.74f), RoundedCornerShape(20.dp))
            .border(1.dp, BaoziTheme.border.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = BaoziTheme.textPrimary,
                fontSize = 18f.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.caption.scaled,
            )
        }
        content()
    }
}

@Composable
private fun PermissionDropdownField(
    options: List<ComposerSelectionOption>,
    selectedValue: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.firstOrNull { it.wireValue == selectedValue }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BaoziTheme.surfaceLight.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                .border(1.dp, BaoziTheme.border.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = selectedOption?.title ?: "自定义",
                    color = BaoziTheme.textPrimary,
                    fontSize = BaoziTextStyle.body.scaled,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = selectedOption?.description ?: "此设置由服务器管理。",
                    color = BaoziTheme.textMuted,
                    fontSize = BaoziTextStyle.caption2.scaled,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = BaoziTheme.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = option.title,
                                color = BaoziTheme.textPrimary,
                                fontSize = BaoziTextStyle.body.scaled,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = option.description,
                                color = BaoziTheme.textMuted,
                                fontSize = BaoziTextStyle.caption2.scaled,
                            )
                        }
                    },
                    trailingIcon = {
                        if (selectedValue == option.wireValue) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = BaoziTheme.accentStrong,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option.wireValue)
                    },
                )
            }
        }
    }
}

@Composable
fun ComposerExperimentalSheet(
    serverId: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()
    var features by remember(serverId) { mutableStateOf<List<ExperimentalFeature>>(emptyList()) }
    var isLoading by remember(serverId) { mutableStateOf(true) }
    var reloadToken by remember(serverId) { mutableIntStateOf(0) }

    LaunchedEffect(serverId, reloadToken) {
        isLoading = true
        runCatching {
            appModel.client.listExperimentalFeatures(
                serverId,
                AppListExperimentalFeaturesRequest(cursor = null, limit = 200u),
            )
        }.onSuccess { featuresResult ->
            features = featuresResult.sortedBy { (it.displayName ?: it.name).lowercase() }
        }.onFailure { error ->
            features = emptyList()
            onError(error.message ?: "加载实验性功能失败")
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.9f)
            .imePadding()
            .padding(16.dp),
    ) {
        SheetHeader(
            title = "实验性",
            leadingActionLabel = "重新加载",
            onLeadingAction = { reloadToken += 1 },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BaoziTheme.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            features.isEmpty() -> {
                Text("没有可用的实验性功能", color = BaoziTheme.textMuted, fontSize = BaoziTextStyle.code.scaled)
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(features, key = { it.name }) { feature ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(feature.displayName ?: feature.name, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.body.scaled)
                                feature.description?.takeIf { it.isNotBlank() }?.let { description ->
                                    Text(description, color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled)
                                }
                            }
                            Switch(
                                checked = feature.enabled,
                                onCheckedChange = { enabled ->
                                    val previous = feature.enabled
                                    features = features.map {
                                        if (it.name == feature.name) {
                                            ExperimentalFeature(
                                                name = it.name,
                                                stage = it.stage,
                                                displayName = it.displayName,
                                                description = it.description,
                                                announcement = it.announcement,
                                                enabled = enabled,
                                                defaultEnabled = it.defaultEnabled,
                                            )
                                        } else {
                                            it
                                        }
                                    }
                                    scope.launch {
                                        runCatching {
                                            appModel.client.writeConfigValue(
                                                serverId,
                                                AppWriteConfigValueRequest(
                                                    keyPath = "features.${feature.name}",
                                                    valueJson = if (enabled) "true" else "false",
                                                    mergeStrategy = AppMergeStrategy.UPSERT,
                                                    filePath = null,
                                                    expectedVersion = null,
                                                ),
                                            )
                                        }.onFailure { error ->
                                            features = features.map {
                                                if (it.name == feature.name) {
                                                    ExperimentalFeature(
                                                        name = it.name,
                                                        stage = it.stage,
                                                        displayName = it.displayName,
                                                        description = it.description,
                                                        announcement = it.announcement,
                                                        enabled = previous,
                                                        defaultEnabled = it.defaultEnabled,
                                                    )
                                                } else {
                                                    it
                                                }
                                            }
                                            onError(error.message ?: "更新实验性功能失败")
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposerSkillsSheet(
    serverId: String,
    cwd: String,
    onDismiss: () -> Unit,
    onError: (String) -> Unit,
) {
    val appModel = LocalAppModel.current
    var skills by remember(serverId, cwd) { mutableStateOf<List<SkillMetadata>>(emptyList()) }
    var isLoading by remember(serverId, cwd) { mutableStateOf(true) }
    var reloadToken by remember(serverId, cwd) { mutableIntStateOf(0) }

    LaunchedEffect(serverId, cwd, reloadToken) {
        isLoading = true
        runCatching {
            appModel.client.listSkills(
                serverId,
                AppListSkillsRequest(
                    cwds = listOf(cwd),
                    forceReload = reloadToken > 0,
                ),
            )
        }.onSuccess { skillResults ->
            skills = skillResults.sortedBy { it.name.lowercase() }
        }.onFailure { error ->
            skills = emptyList()
            onError(error.message ?: "加载技能失败")
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(fraction = 0.9f)
            .imePadding()
            .padding(16.dp),
    ) {
        SheetHeader(
            title = "技能",
            leadingActionLabel = "重新加载",
            onLeadingAction = { reloadToken += 1 },
            onDismiss = onDismiss,
        )
        Spacer(Modifier.height(12.dp))
        when {
            isLoading -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BaoziTheme.accent, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            skills.isEmpty() -> {
                Text("此工作区没有可用的技能", color = BaoziTheme.textMuted, fontSize = BaoziTextStyle.code.scaled)
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(skills, key = { "${it.path.value}#${it.name}" }) { skill ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(skill.name, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.body.scaled, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.weight(1f))
                                if (skill.enabled) {
                                    Text(
                                        "已启用",
                                        color = BaoziTheme.accent,
                                        fontSize = BaoziTextStyle.caption2.scaled,
                                        modifier = Modifier
                                            .background(BaoziTheme.accent.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(skill.description, color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled)
                            Text(skill.path.value, color = BaoziTheme.textMuted, fontSize = BaoziTextStyle.caption2.scaled)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    leadingActionLabel: String? = null,
    onLeadingAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingActionLabel != null && onLeadingAction != null) {
            TextButton(onClick = onLeadingAction) {
                Text(leadingActionLabel, color = BaoziTheme.accent)
            }
        } else {
            Spacer(Modifier.width(64.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = BaoziTheme.textPrimary, fontSize = BaoziTextStyle.headline.scaled, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text("完成", color = BaoziTheme.accent)
        }
    }
    HorizontalDivider(color = BaoziTheme.divider, modifier = Modifier.padding(top = 8.dp))
}
