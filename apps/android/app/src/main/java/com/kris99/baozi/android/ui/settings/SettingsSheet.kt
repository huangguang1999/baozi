package com.kris99.baozi.android.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.auth.ChatGPTOAuthActivity
import com.kris99.baozi.android.state.ChatGPTOAuth
import com.kris99.baozi.android.state.ChatGPTOAuthTokenStore
import com.kris99.baozi.android.state.DebugSettings
import com.kris99.baozi.android.state.MessageRecorder
import com.kris99.baozi.android.state.OpenAIApiKeyStore
import com.kris99.baozi.android.state.PetOverlayController
import com.kris99.baozi.android.state.SavedServer
import com.kris99.baozi.android.state.SavedServerStore
import com.kris99.baozi.android.state.SshAuthMethod
import com.kris99.baozi.android.state.SshCredentialStore
import com.kris99.baozi.android.state.connectionModeLabel
import com.kris99.baozi.android.state.isConnected
import com.kris99.baozi.android.state.statusColor
import com.kris99.baozi.android.state.statusLabel
import com.kris99.baozi.android.state.toRecord
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BaoziAppearanceMode
import com.kris99.baozi.android.ui.BaoziColorThemeType
import com.kris99.baozi.android.ui.BerkeleyMono
import com.kris99.baozi.android.ui.ConversationPrefs
import com.kris99.baozi.android.ui.ExperimentalFeatures
import com.kris99.baozi.android.ui.BaoziFeature
import com.kris99.baozi.android.ui.WallpaperBackdrop
import com.kris99.baozi.android.ui.WallpaperManager
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.BaoziThemeIndexEntry
import com.kris99.baozi.android.ui.BaoziThemeManager
import com.kris99.baozi.android.ui.discovery.SSHLoginDialog
import com.kris99.baozi.android.util.LLog
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppLoginAccountRequest
import uniffi.codex_mobile_client.AppPetSummary

/**
 * Settings — hierarchical navigation matching iOS:
 * Top level: Appearance → | Font | Conversation | Experimental → | Account | Servers
 * Appearance pushes to sub-screen with theme pickers.
 * Experimental pushes to sub-screen with feature toggles.
 */

// ═══════════════════════════════════════════════════════════════════════════════
// Top-level Settings
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    onDismiss: () -> Unit,
    onOpenAccount: (serverId: String) -> Unit,
    initialSubScreen: SettingsStartDestination = SettingsStartDestination.TopLevel,
    onOpenApps: (() -> Unit)? = null,
) {
    // Sub-screen navigation
    var subScreen by remember(initialSubScreen) {
        mutableStateOf(
            when (initialSubScreen) {
                SettingsStartDestination.TopLevel -> null
                SettingsStartDestination.Pets -> SettingsSubScreen.Pets
            },
        )
    }

    when (subScreen) {
        SettingsSubScreen.Appearance -> AppearanceScreen(onBack = { subScreen = null })
        SettingsSubScreen.Experimental -> ExperimentalScreen(onBack = { subScreen = null })
        SettingsSubScreen.Pets -> PetsScreen(onBack = { subScreen = null })
        SettingsSubScreen.Debug -> DebugScreen(onBack = { subScreen = null })
        null -> SettingsTopLevel(
            onDismiss = onDismiss,
            onOpenAppearance = { subScreen = SettingsSubScreen.Appearance },
            onOpenExperimental = { subScreen = SettingsSubScreen.Experimental },
            onOpenPets = { subScreen = SettingsSubScreen.Pets },
            onOpenDebug = { subScreen = SettingsSubScreen.Debug },
            onOpenAccount = onOpenAccount,
            onOpenApps = onOpenApps,
        )
    }
}

enum class SettingsStartDestination { TopLevel, Pets }

private enum class SettingsSubScreen { Appearance, Experimental, Pets, Debug }

@Composable
private fun SettingsTopLevel(
    onDismiss: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenPets: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAccount: (serverId: String) -> Unit,
    onOpenApps: (() -> Unit)?,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val collapseTurns = ConversationPrefs.areTurnsCollapsed
    var renameTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var renameText by remember { mutableStateOf("") }

    val currentServer = remember(snapshot) {
        val activeServerId = snapshot?.activeThread?.serverId
        snapshot?.servers?.firstOrNull { it.serverId == activeServerId }
            ?: snapshot?.servers?.firstOrNull { it.isLocal }
            ?: snapshot?.servers?.firstOrNull()
    }

    var editTarget by remember { mutableStateOf<AppServerSnapshot?>(null) }
    var sshReconnectTarget by remember { mutableStateOf<SavedServer?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Title
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("设置", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("完成", color = BaoziTheme.accent)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Support ──

        // ── Theme ──
        item { SectionHeader("主题") }
        item {
            NavRow(icon = Icons.Default.Palette, label = "外观", onClick = onOpenAppearance)
        }

        // ── Font ──
        item { SectionHeader("字体") }
        item {
            Column(
                Modifier.fillMaxWidth().background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
            ) {
                FontRow("Berkeley Mono", BerkeleyMono, BaoziThemeManager.monoFontEnabled) { BaoziThemeManager.applyFont(true) }
                HorizontalDivider(color = BaoziTheme.divider)
                FontRow("System Default", FontFamily.Default, !BaoziThemeManager.monoFontEnabled) { BaoziThemeManager.applyFont(false) }
            }
        }

        // ── Conversation ──
        item { SectionHeader("对话") }
        item {
            SettingsRow(
                icon = { Text("⊟", color = BaoziTheme.accent, fontSize = 16.sp) },
                label = "折叠回合", subtitle = "将之前的回合折叠为卡片",
                trailing = {
                    Switch(
                        checked = collapseTurns,
                        onCheckedChange = { ConversationPrefs.setCollapseTurns(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accent),
                    )
                },
            )
        }

        // ── Pets ──
        item { SectionHeader("宠物") }
        item {
            SettingsRow(
                icon = { Icon(Icons.Default.Pets, null, tint = BaoziTheme.accent, modifier = Modifier.size(18.dp)) },
                label = "唤醒宠物",
                subtitle = PetOverlayController.selectedPet?.displayName ?: "选择一个 Codex 宠物",
                trailing = {
                    Switch(
                        checked = PetOverlayController.visible,
                        onCheckedChange = { PetOverlayController.setVisible(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accent),
                    )
                },
                onClick = onOpenPets,
            )
        }

        // ── Apps ──
        if (onOpenApps != null) {
            item { SectionHeader("应用") }
            item {
                NavRow(
                    icon = Icons.Default.Widgets,
                    label = "已保存的 App",
                    onClick = {
                        onDismiss()
                        onOpenApps()
                    },
                )
            }
        }

        // ── Experimental ──
        item { SectionHeader("实验性") }
        item {
            NavRow(icon = Icons.Default.Science, label = "实验性功能", onClick = onOpenExperimental)
        }

        // ── Debug ──
        if (DebugSettings.enabled) {
            item { SectionHeader("调试") }
            item {
                NavRow(icon = Icons.Default.Science, label = "调试设置", onClick = onOpenDebug)
            }
        }

        // ── Account ──
        item { SectionHeader("账户") }
        item {
            if (currentServer != null) {
                val accountStatus = when (val account = currentServer!!.account) {
                    is Account.Chatgpt -> account.email.ifEmpty { "ChatGPT 账户" }
                    is Account.ApiKey -> "OpenAI API 密钥"
                    null -> "未登录"
                }
                SettingsRow(
                    icon = { Text("@", color = BaoziTheme.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                    label = currentServer!!.displayName,
                    subtitle = accountStatus,
                    trailing = {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = BaoziTheme.textMuted,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    onClick = { onOpenAccount(currentServer!!.serverId) },
                )
            } else {
                SettingsRow(label = "请先连接到服务器")
            }
        }

        // ── Servers ──
        item { SectionHeader("服务器") }
        val servers = snapshot?.servers ?: emptyList()
        if (servers.isEmpty()) {
            item { SettingsRow(label = "未连接服务器") }
        } else {
            items(servers, key = { it.serverId }) { server ->
                ServerSettingsRow(
                    server = server,
                    onRename = {
                        renameText = server.displayName
                        renameTarget = server
                    },
                    onEdit = {
                        editTarget = server
                    },
                    onRemove = {
                        scope.launch {
                            SavedServerStore.remove(context, server.serverId)
                            appModel.sshSessionStore.close(server.serverId)
                            appModel.serverBridge.disconnectServer(server.serverId)
                            appModel.refreshSnapshot()
                        }
                    },
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    renameTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名服务器") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isEmpty()) return@TextButton
                    scope.launch {
                        SavedServerStore.rename(context, server.serverId, trimmed)
                        appModel.refreshSnapshot()
                    }
                    renameTarget = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    editTarget?.let { server ->
        ServerEditSheet(
            server = server,
            onDismiss = { editTarget = null },
            onSave = { editTarget = null },
            onTriggerSshReconnect = { saved ->
                editTarget = null
                sshReconnectTarget = saved
            },
        )
    }

    sshReconnectTarget?.let { saved ->
        val sshCredentialStore = remember(context) { SshCredentialStore(context.applicationContext) }
        val sshPort = saved.resolvedSshPort
        SSHLoginDialog(
            server = saved,
            initialCredential = sshCredentialStore.load(saved.hostname, sshPort),
            onDismiss = { sshReconnectTarget = null },
            onConnect = { credential, rememberCredentials ->
                try {
                    if (rememberCredentials) {
                        sshCredentialStore.save(saved.hostname, sshPort, credential)
                    } else {
                        sshCredentialStore.delete(saved.hostname, sshPort)
                    }

                    appModel.serverBridge.disconnectServer(saved.id)

                    when (credential.method) {
                        SshAuthMethod.PASSWORD -> appModel.serverBridge.startRemoteOverSshConnect(
                            serverId = saved.id,
                            displayName = saved.name,
                            host = saved.hostname,
                            port = sshPort.toUShort(),
                            username = credential.username,
                            password = credential.password,
                            privateKeyPem = null,
                            passphrase = null,
                            unlockMacosKeychain = credential.unlockMacosKeychain,
                            acceptUnknownHost = true,
                            workingDir = null,
                        )
                        SshAuthMethod.KEY -> appModel.serverBridge.startRemoteOverSshConnect(
                            serverId = saved.id,
                            displayName = saved.name,
                            host = saved.hostname,
                            port = sshPort.toUShort(),
                            username = credential.username,
                            password = null,
                            privateKeyPem = credential.privateKey,
                            passphrase = credential.passphrase,
                            unlockMacosKeychain = false,
                            acceptUnknownHost = true,
                            workingDir = null,
                        )
                    }
                    appModel.refreshSnapshot()
                    sshReconnectTarget = null
                    null
                } catch (e: Exception) {
                    LLog.e("SettingsSheet", "SSH reconnect failed: ${e.message}", e)
                    e.message ?: "SSH reconnect failed"
                }
            },
        )
    }

}

@Composable
private fun ServerSettingsRow(
    server: AppServerSnapshot,
    onRename: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(if (server.isLocal) "📱" else "🖥", fontSize = 16.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(server.displayName, color = BaoziTheme.textPrimary, fontSize = 13.sp)
                Text(
                    "${server.statusLabel} · ${server.connectionModeLabel}",
                    color = server.statusColor,
                    fontSize = 11.sp,
                )
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "服务器操作",
                    tint = BaoziTheme.textSecondary,
                )
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                )
            }
            if (onRename != null) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("移除") },
                onClick = {
                    showMenu = false
                    onRemove()
                },
            )
        }
    }
}

private enum class ServerConnectionMode(val label: String, val formHeader: String) {
    LOCAL("本地", "本地运行时"),
    SSH("SSH", "SSH 主机"),
    DIRECT_CODEX("Codex", "Codex 服务器"),
    WEBSOCKET("WebSocket", "Codex URL"),
    SLINGSHOT("Slingshot", "Slingshot"),
}

private fun isSettingsSlingshotUrl(rawUrl: String): Boolean =
    runCatching { Uri.parse(rawUrl).scheme?.equals("slingshot", ignoreCase = true) == true }
        .getOrDefault(false)

private suspend fun loadSettingsSlingshotTokens(context: Context) =
    ChatGPTOAuth.requireStoredOrRefreshedTokens(
        context,
        "使用 Slingshot 连接前，请先用 ChatGPT 登录。",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerEditSheet(
    server: AppServerSnapshot,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onTriggerSshReconnect: (SavedServer) -> Unit,
) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val scope = rememberCoroutineScope()

    val savedServers = remember { SavedServerStore.load(context) }
    val originalSaved = remember(savedServers, server.serverId) {
        savedServers.firstOrNull { it.id == server.serverId }
    }

    val resolvedMode = remember(originalSaved, server.isLocal) {
        when {
            server.isLocal -> ServerConnectionMode.LOCAL
            originalSaved?.websocketURL?.let(::isSettingsSlingshotUrl) == true -> ServerConnectionMode.SLINGSHOT
            originalSaved?.websocketURL != null -> ServerConnectionMode.WEBSOCKET
            originalSaved?.preferredConnectionMode == "ssh" || (originalSaved?.sshPort != null && originalSaved?.hasCodexServer == false) -> ServerConnectionMode.SSH
            else -> ServerConnectionMode.DIRECT_CODEX
        }
    }
    var displayName by remember { mutableStateOf(originalSaved?.name?.trim()?.takeIf { it.isNotEmpty() } ?: server.displayName) }
    var connectionMode by remember { mutableStateOf(resolvedMode) }
    var host by remember { mutableStateOf(originalSaved?.hostname?.trim()?.takeIf { it.isNotEmpty() } ?: server.host) }
    var codexPort by remember { mutableStateOf(originalSaved?.preferredCodexPort?.toString() ?: originalSaved?.port?.takeIf { it > 0 }?.toString() ?: "8390") }
    var websocketURL by remember { mutableStateOf(originalSaved?.websocketURL ?: "") }
    var sshPort by remember { mutableStateOf(originalSaved?.sshPort?.toString() ?: "22") }
    var wakeMAC by remember { mutableStateOf(originalSaved?.wakeMAC ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var isReconnecting by remember { mutableStateOf(false) }
    var pendingSlingshotReconnect by remember { mutableStateOf<SavedServer?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun validateAndBuild(): SavedServer? {
        val name = displayName.trim()
        if (name.isEmpty()) {
            validationError = "服务器名称不能为空。"
            return null
        }

        if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
            // Paired server — only name is editable
            return originalSaved.copy(name = name)
        }

        return when (connectionMode) {
            ServerConnectionMode.LOCAL -> {
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = "127.0.0.1",
                    port = 0,
                    codexPorts = emptyList(),
                    sshPort = null,
                    source = "local",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = null,
                    preferredCodexPort = null,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.SSH -> {
                val resolvedHost = host.trim()
                if (resolvedHost.isEmpty()) {
                    validationError = "主机不能为空。"
                    return null
                }
                val resolvedSSHPort = sshPort.trim().toIntOrNull()
                if (resolvedSSHPort == null || resolvedSSHPort !in 1..65535) {
                    validationError = "SSH 端口必须是有效数字。"
                    return null
                }
                val wakeInput = wakeMAC.trim()
                val resolvedWakeMAC = SavedServer.normalizeWakeMac(wakeInput)
                if (wakeInput.isNotEmpty() && resolvedWakeMAC == null) {
                    validationError = "唤醒 MAC 地址格式应为 aa:bb:cc:dd:ee:ff。"
                    return null
                }
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = resolvedHost,
                    port = 0,
                    codexPorts = emptyList(),
                    sshPort = resolvedSSHPort,
                    source = "manual",
                    hasCodexServer = false,
                    wakeMAC = resolvedWakeMAC,
                    preferredConnectionMode = "ssh",
                    preferredCodexPort = null,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.DIRECT_CODEX -> {
                val resolvedHost = host.trim()
                if (resolvedHost.isEmpty()) {
                    validationError = "主机不能为空。"
                    return null
                }
                val resolvedCodexPort = codexPort.trim().toIntOrNull()
                if (resolvedCodexPort == null || resolvedCodexPort !in 1..65535) {
                    validationError = "Codex 端口必须是有效数字。"
                    return null
                }
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = resolvedHost,
                    port = resolvedCodexPort,
                    codexPorts = listOf(resolvedCodexPort),
                    sshPort = null,
                    source = "manual",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = "directCodex",
                    preferredCodexPort = resolvedCodexPort,
                    sshPortForwardingEnabled = null,
                    websocketURL = null,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.WEBSOCKET -> {
                val rawURL = websocketURL.trim()
                if (!rawURL.startsWith("ws://", ignoreCase = true) && !rawURL.startsWith("wss://", ignoreCase = true)) {
                    validationError = "请输入有效的 ws:// 或 wss:// URL。"
                    return null
                }
                val uri = runCatching { java.net.URI(rawURL) }.getOrNull()
                if (uri == null || uri.host.isNullOrEmpty()) {
                    validationError = "请输入有效的 ws:// 或 wss:// URL。"
                    return null
                }
                val resolvedPort = if (uri.port != -1) uri.port else null
                SavedServer(
                    id = server.serverId,
                    name = name,
                    hostname = uri.host,
                    port = resolvedPort ?: 0,
                    codexPorts = if (resolvedPort != null) listOf(resolvedPort) else emptyList(),
                    sshPort = null,
                    source = "manual",
                    hasCodexServer = true,
                    wakeMAC = null,
                    preferredConnectionMode = "directCodex",
                    preferredCodexPort = resolvedPort,
                    sshPortForwardingEnabled = null,
                    websocketURL = rawURL,
                    rememberedByUser = true,
                )
            }
            ServerConnectionMode.SLINGSHOT -> {
                val saved = originalSaved ?: run {
                    validationError = "请先移除并重新添加这台已连接的电脑。"
                    return null
                }
                saved.copy(
                    name = name,
                    rememberedByUser = true,
                )
            }
        }
    }

    fun persist(saved: SavedServer) {
        val existing = SavedServerStore.load(context).toMutableList()
        existing.removeAll { it.id == saved.id }
        existing.add(saved)
        SavedServerStore.save(context, existing)
        appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
        appModel.reconnectController.syncSavedServers(
            existing.filter { it.rememberedByUser }.map { it.toRecord(context) }
        )
        appModel.store.renameServer(saved.id, saved.name)
    }

    suspend fun reconnect(serverId: String) {
        val servers = SavedServerStore.load(context).map { it.toRecord(context) }
        appModel.reconnectController.setMultiClankerAndQuicEnabled(true)
        appModel.reconnectController.syncSavedServers(servers)
        val result = appModel.reconnectController.reconnectServer(serverId)
        if (result.needsLocalAuthRestore) {
            appModel.restoreStoredLocalAuthState(result.serverId)
            runCatching { appModel.refreshSessions(listOf(result.serverId)) }
        }
        appModel.refreshSnapshot()
    }

    suspend fun connectSlingshotSaved(saved: SavedServer, stepUpToken: String) {
        val websocketURL = saved.websocketURL?.takeIf(::isSettingsSlingshotUrl)
            ?: throw IllegalStateException("Saved server is not a Slingshot connection.")

        val tokens = loadSettingsSlingshotTokens(context)
        appModel.serverBridge.connectRemoteSlingshotUrlServer(
            saved.id,
            saved.name,
            websocketURL,
            tokens.accessToken,
            tokens.accountId,
            stepUpToken,
        )
        appModel.refreshSnapshot()
    }

    suspend fun reconnectSaved(saved: SavedServer) {
        if (saved.websocketURL?.let(::isSettingsSlingshotUrl) != true) {
            reconnect(saved.id)
            return
        }

        connectSlingshotSaved(saved, "")
    }

    val slingshotStepUpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val saved = pendingSlingshotReconnect
        pendingSlingshotReconnect = null
        if (saved == null) {
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            validationError = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
                ?: "远程控制授权已取消。"
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }
        val stepUpToken = ChatGPTOAuthActivity.parseRemoteControlStepUpToken(result.data)
        if (stepUpToken == null) {
            validationError = "远程控制授权返回的凭据不完整。"
            isReconnecting = false
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            isReconnecting = true
            try {
                connectSlingshotSaved(saved, stepUpToken)
                onSave()
            } catch (e: Exception) {
                validationError = e.message
            } finally {
                isReconnecting = false
            }
        }
    }

    fun launchSlingshotStepUp(saved: SavedServer) {
        try {
            pendingSlingshotReconnect = saved
            slingshotStepUpLauncher.launch(
                ChatGPTOAuthActivity.createIntent(
                    context,
                    ChatGPTOAuth.createRemoteControlEnrollmentAttempt(),
                ),
            )
        } catch (e: Exception) {
            pendingSlingshotReconnect = null
            validationError = e.localizedMessage ?: e.message ?: "无法授权远程控制。"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BaoziTheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text("编辑服务器", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("完成", color = BaoziTheme.accent) }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    SectionHeader("名称")
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("服务器名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                    )
                }

                item {
                    SectionHeader(connectionMode.formHeader)

                    if (originalSaved?.alleycatNodeId != null || originalSaved?.alleycatAgentWire == "ssh-bridge") {
                        Text(
                            "此已配对服务器使用已保存的配对元数据。可在此处编辑其显示名称，或移除后重新添加以更改配对。",
                            color = BaoziTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (server.isLocal) {
                        Text(
                            "本设备的本地运行时由系统自动管理。",
                            color = BaoziTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else if (connectionMode == ServerConnectionMode.SLINGSHOT) {
                        Text(
                            "这台已连接的电脑来自 ChatGPT，使用你登录的账户。可在此处编辑其显示名称，或移除后重新添加以更换电脑。",
                            color = BaoziTheme.textSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        // Mode selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val modes = listOf(
                                ServerConnectionMode.SSH,
                                ServerConnectionMode.DIRECT_CODEX,
                                ServerConnectionMode.WEBSOCKET,
                            )
                            modes.forEach { mode ->
                                val selected = mode == connectionMode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) BaoziTheme.accent else Color.Transparent)
                                        .clickable { connectionMode = mode }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        mode.label,
                                        color = if (selected) BaoziTheme.onAccentStrong else BaoziTheme.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when (connectionMode) {
                            ServerConnectionMode.SSH -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = sshPort,
                                    onValueChange = { sshPort = it },
                                    label = { Text("SSH 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = wakeMAC,
                                    onValueChange = { wakeMAC = it },
                                    label = { Text("唤醒 MAC（可选）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.DIRECT_CODEX -> {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("主机名或 IP") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                                OutlinedTextField(
                                    value = codexPort,
                                    onValueChange = { codexPort = it },
                                    label = { Text("Codex 端口") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            ServerConnectionMode.WEBSOCKET -> {
                                OutlinedTextField(
                                    value = websocketURL,
                                    onValueChange = { websocketURL = it },
                                    label = { Text("ws://主机:端口 或 wss://...") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                                )
                            }
                            else -> Unit
                        }

                        if (connectionMode == ServerConnectionMode.WEBSOCKET) {
                            Text(
                                "尽量优先使用 SSH。如果你手动运行 codex，请绑定 loopback 并自行建立隧道；除非你清楚自己在做什么，否则不要将其直接暴露到互联网。",
                                color = BaoziTheme.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                item {
                    if (isReconnecting) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = BaoziTheme.accent, strokeWidth = 2.dp)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    validationError = null
                                    val saved = validateAndBuild()
                                    if (saved != null) {
                                        persist(saved)
                                        onSave()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BaoziTheme.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("保存", color = BaoziTheme.onAccentStrong)
                            }
                            if (server.isLocal || (originalSaved?.alleycatNodeId == null && originalSaved?.alleycatAgentWire != "ssh-bridge")) {
                                Button(
                                    onClick = {
                                        validationError = null
                                        val saved = validateAndBuild()
                                        if (saved != null) {
                                            persist(saved)
                                            // SSH mode requires interactive credentials, mirroring iOS:
                                            // hand off to the parent which will open SSHLoginDialog.
                                            if (connectionMode == ServerConnectionMode.SSH && !server.isLocal) {
                                                onTriggerSshReconnect(saved)
                                                return@Button
                                            }
                                            scope.launch {
                                                isReconnecting = true
                                                try {
                                                    reconnectSaved(saved)
                                                    onSave()
                                                } catch (e: Exception) {
                                                    isReconnecting = false
                                                    if (
                                                        connectionMode == ServerConnectionMode.SLINGSHOT &&
                                                        ChatGPTOAuth.isRemoteControlAuthorizationRequired(e)
                                                    ) {
                                                        launchSlingshotStepUp(saved)
                                                    } else {
                                                        validationError = e.message
                                                    }
                                                } finally {
                                                    if (pendingSlingshotReconnect == null) {
                                                        isReconnecting = false
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BaoziTheme.accentStrong),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        if (server.isLocal) "保存并重启" else "保存并重连",
                                        color = BaoziTheme.background,
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }

    validationError?.let { error ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("无效的服务器") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { validationError = null }) {
                    Text("确定")
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Appearance Sub-Screen (matches iOS AppearanceSettingsView)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textSizeStep by remember { mutableFloatStateOf(com.kris99.baozi.android.ui.TextSizePrefs.currentStep.toFloat()) }
    var showThemePicker by remember { mutableStateOf<BaoziColorThemeType?>(null) }
    var wallpaperError by remember { mutableStateOf<String?>(null) }
    val appearanceMode = BaoziThemeManager.appearanceMode
    val wallpaperPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                wallpaperError =
                    if (WallpaperManager.setCustomFromUri(uri)) {
                        null
                    } else {
                        "无法从所选图片保存壁纸。"
                    }
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = BaoziTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("外观", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Appearance mode
            item { SectionHeader("模式") }
            item {
                AppearanceModePicker(
                    selectedMode = appearanceMode,
                    onSelect = BaoziThemeManager::applyAppearanceMode,
                )
            }
            item {
                Text(
                    "跟随设备设置，或将 包子 固定为浅色或深色模式。",
                    color = BaoziTheme.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            // Font size slider
            item { SectionHeader("字号") }
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("字号", color = BaoziTheme.textPrimary, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        val label = com.kris99.baozi.android.ui.ConversationTextSize.fromStep(textSizeStep.toInt()).label
                        Text(label, color = BaoziTheme.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("A", color = BaoziTheme.textMuted, fontSize = 11.sp)
                        Slider(
                            value = textSizeStep,
                            onValueChange = {
                                textSizeStep = it
                                com.kris99.baozi.android.ui.TextSizePrefs.setStep(context, it.toInt())
                            },
                            valueRange = 0f..6f, steps = 5,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = BaoziTheme.accent, activeTrackColor = BaoziTheme.accent),
                        )
                        Text("A", color = BaoziTheme.textMuted, fontSize = 18.sp)
                    }
                }
            }
            item {
                Text("在对话中捏合调整，或用此滑块。", color = BaoziTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
            }

            // Wallpaper picker
            item { SectionHeader("聊天壁纸") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, BaoziTheme.border.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                    ) {
                        WallpaperBackdrop(modifier = Modifier.fillMaxSize())
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TextButton(
                            onClick = { wallpaperPicker.launch("image/*") },
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Text("从相册选择", color = BaoziTheme.accent)
                        }
                        if (WallpaperManager.isWallpaperSet) {
                            TextButton(
                                onClick = {
                                    WallpaperManager.clear()
                                    wallpaperError = null
                                },
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) {
                                Text("移除壁纸", color = BaoziTheme.danger)
                            }
                        }
                        if (!wallpaperError.isNullOrBlank()) {
                            Text(
                                wallpaperError!!,
                                color = BaoziTheme.danger,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            // Conversation preview
            item { SectionHeader("预览") }
            item {
                val scale = com.kris99.baozi.android.ui.ConversationTextSize.fromStep(textSizeStep.toInt()).scale
                val previewFontSize = (14f * scale).sp
                val previewCodeFontSize = (13f * scale).sp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    WallpaperBackdrop(modifier = Modifier.fillMaxSize())
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // User bubble
                        Text(
                            "嘿，生产环境怎么炸了",
                            color = BaoziTheme.textPrimary,
                            fontSize = previewFontSize,
                            lineHeight = (previewFontSize.value * 1.3f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        )
                        // Tool call card
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("✓", color = BaoziTheme.success, fontSize = 12.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("rg 'TODO: fix later' --count", color = BaoziTheme.toolCallCommand, fontFamily = BerkeleyMono, fontSize = (previewFontSize.value - 2).sp)
                            Spacer(Modifier.weight(1f))
                            Text("0.3s", color = BaoziTheme.textMuted, fontSize = 10.sp)
                        }
                        // Assistant bubble
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "找到问题了。有人部署了这个：",
                                color = BaoziTheme.textBody,
                                fontSize = previewFontSize,
                                lineHeight = (previewFontSize.value * 1.3f).sp,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "PYTHON",
                                    color = BaoziTheme.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(BaoziTheme.codeBackground, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                ) {
                                    Text(
                                        "if is_friday():\n    yolo_deploy(skip_tests=True)",
                                        color = BaoziTheme.textBody,
                                        fontFamily = BaoziTheme.monoFont,
                                        fontSize = previewCodeFontSize,
                                        lineHeight = (previewCodeFontSize.value * 1.35f).sp,
                                    )
                                }
                            }
                            Text(
                                "我不是生气，只是有点失望。",
                                color = BaoziTheme.textBody,
                                fontSize = previewFontSize,
                                lineHeight = (previewFontSize.value * 1.3f).sp,
                            )
                        }
                        // User reply
                        Text(
                            "那就是你",
                            color = BaoziTheme.textPrimary,
                            fontSize = previewFontSize,
                            lineHeight = (previewFontSize.value * 1.3f).sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BaoziTheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        )
                    }
                }
            }

            // Light theme picker
            item { SectionHeader("浅色主题") }
            item {
                val selectedLight = BaoziThemeManager.lightThemes.firstOrNull {
                    it.slug == BaoziThemeManager.lightTheme.slug
                } ?: BaoziThemeManager.lightThemes.firstOrNull()
                ThemePickerButton(entry = selectedLight, onClick = { showThemePicker = BaoziColorThemeType.LIGHT })
            }

            // Dark theme picker
            item { SectionHeader("深色主题") }
            item {
                val selectedDark = BaoziThemeManager.darkThemes.firstOrNull {
                    it.slug == BaoziThemeManager.darkTheme.slug
                } ?: BaoziThemeManager.darkThemes.firstOrNull()
                ThemePickerButton(entry = selectedDark, onClick = { showThemePicker = BaoziColorThemeType.DARK })
            }
        }
    }

    // Theme picker sheet
    showThemePicker?.let { type ->
        ModalBottomSheet(
            onDismissRequest = { showThemePicker = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BaoziTheme.background,
        ) {
            val themes = if (type == BaoziColorThemeType.DARK) BaoziThemeManager.darkThemes else BaoziThemeManager.lightThemes
            val selectedSlug = if (type == BaoziColorThemeType.DARK) BaoziThemeManager.darkTheme.slug else BaoziThemeManager.lightTheme.slug
            ThemePickerContent(
                title = if (type == BaoziColorThemeType.DARK) "深色主题" else "浅色主题",
                themes = themes,
                selectedSlug = selectedSlug,
                onSelect = { slug ->
                    if (type == BaoziColorThemeType.DARK) {
                        BaoziThemeManager.selectDarkTheme(slug)
                    } else {
                        BaoziThemeManager.selectLightTheme(slug)
                    }
                    showThemePicker = null
                },
                onDismiss = { showThemePicker = null },
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Theme Picker Sheet (matches iOS ThemePickerSheet)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThemePickerContent(
    title: String,
    themes: List<BaoziThemeIndexEntry>,
    selectedSlug: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(themes, searchQuery) {
        if (searchQuery.isBlank()) themes
        else themes.filter { it.name.contains(searchQuery, ignoreCase = true) || it.slug.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
    ) {
        // Title + Done
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Text(title, color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("完成", color = BaoziTheme.accent) }
        }

        Spacer(Modifier.height(8.dp))

        // Search
        Row(
            Modifier.fillMaxWidth()
                .background(BaoziTheme.surface.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .border(1.dp, BaoziTheme.border.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = BaoziTheme.textMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(BaoziTheme.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) Text("搜索主题", color = BaoziTheme.textMuted, fontSize = 14.sp)
                    inner()
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        // Theme list
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, tint = BaoziTheme.textMuted, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(8.dp))
                Text("没有匹配的主题", color = BaoziTheme.textPrimary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.slug }) { entry ->
                    val isSelected = entry.slug == selectedSlug
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .background(BaoziTheme.surface.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isSelected) BaoziTheme.accent.copy(alpha = 0.6f) else BaoziTheme.border.copy(alpha = 0.85f),
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onSelect(entry.slug) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        ThemePreviewBadge(entry)
                        Spacer(Modifier.width(10.dp))
                        Text(entry.name, color = BaoziTheme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.Check, null, tint = BaoziTheme.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "Aa" badge with background/foreground/accent dot — matches iOS ThemePreviewBadge */
@Composable
private fun ThemePreviewBadge(entry: BaoziThemeIndexEntry) {
    val bg = try { Color(android.graphics.Color.parseColor(entry.backgroundHex)) } catch (_: Exception) { BaoziTheme.surface }
    val fg = try { Color(android.graphics.Color.parseColor(entry.foregroundHex)) } catch (_: Exception) { BaoziTheme.textPrimary }
    val accent = try { Color(android.graphics.Color.parseColor(entry.accentHex)) } catch (_: Exception) { BaoziTheme.accent }

    Box {
        Box(
            Modifier.size(width = 28.dp, height = 22.dp)
                .background(bg, RoundedCornerShape(5.dp))
                .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = BerkeleyMono)
        }
        Spacer(
            Modifier.size(6.dp).clip(CircleShape).background(accent)
                .align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun ThemePickerButton(entry: BaoziThemeIndexEntry?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        if (entry != null) {
            ThemePreviewBadge(entry)
            Spacer(Modifier.width(10.dp))
            Text(entry.name, color = BaoziTheme.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        } else {
            Text("没有主题", color = BaoziTheme.textMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        Text("⇅", color = BaoziTheme.textMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AppearanceModePicker(
    selectedMode: BaoziAppearanceMode,
    onSelect: (BaoziAppearanceMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BaoziAppearanceMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) BaoziTheme.accent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.displayName,
                    color = if (isSelected) BaoziTheme.onAccentStrong else BaoziTheme.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Pets Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val connectedServers = remember(snapshot) {
        snapshot?.servers.orEmpty().filter { it.isConnected }
    }
    var selectedServerId by remember(connectedServers) {
        mutableStateOf(
            PetOverlayController.selectedPet?.serverId?.takeIf { id ->
                connectedServers.any { it.serverId == id }
            }
                ?: snapshot?.activeThread?.serverId?.takeIf { id ->
                    connectedServers.any { it.serverId == id }
                }
                ?: connectedServers.firstOrNull()?.serverId
                ?: "",
        )
    }
    var pets by remember(selectedServerId) { mutableStateOf<List<AppPetSummary>>(emptyList()) }
    var loading by remember(selectedServerId) { mutableStateOf(false) }
    var error by remember(selectedServerId) { mutableStateOf<String?>(null) }
    val overlayPermissionGranted = PetOverlayController.canDrawOverlays(context)

    fun refresh() {
        if (selectedServerId.isBlank()) return
        scope.launch {
            loading = true
            error = null
            runCatching { appModel.client.listPets(selectedServerId) }
                .onSuccess { pets = it }
                .onFailure {
                    pets = emptyList()
                    error = it.message ?: "无法加载宠物。"
                }
            loading = false
        }
    }

    LaunchedEffect(selectedServerId) {
        refresh()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = BaoziTheme.accent)
                }
                Spacer(Modifier.weight(1f))
                Text("宠物", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { refresh() }, enabled = selectedServerId.isNotBlank() && !loading) {
                    Icon(Icons.Default.Refresh, "刷新", tint = BaoziTheme.accent)
                }
            }
        }

        item { SectionHeader("唤醒") }
        item {
            SettingsRow(
                label = "显示宠物",
                subtitle = PetOverlayController.selectedPet?.displayName ?: "未选择宠物",
                icon = { Icon(Icons.Default.Pets, null, tint = BaoziTheme.accent, modifier = Modifier.size(18.dp)) },
                trailing = {
                    Switch(
                        checked = PetOverlayController.visible,
                        onCheckedChange = { PetOverlayController.setVisible(context, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accent),
                    )
                },
            )
        }
        item {
            SettingsRow(
                label = "悬浮在其他应用之上",
                subtitle = if (overlayPermissionGranted) {
                    "已授予悬浮窗权限"
                } else {
                    "需要「显示在其他应用上层」权限"
                },
                icon = { Icon(Icons.Default.Widgets, null, tint = BaoziTheme.accent, modifier = Modifier.size(18.dp)) },
                trailing = {
                    Switch(
                        checked = PetOverlayController.overlayEnabled,
                        onCheckedChange = { enabled ->
                            PetOverlayController.setOverlayEnabled(context, enabled)
                            if (enabled && !overlayPermissionGranted) {
                                PetOverlayController.requestOverlayPermission(context)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accent),
                    )
                },
                onClick = if (!overlayPermissionGranted) {
                    { PetOverlayController.requestOverlayPermission(context) }
                } else {
                    null
                },
            )
        }

        item { SectionHeader("服务器") }
        if (connectedServers.isEmpty()) {
            item { SettingsRow(label = "请先连接到服务器") }
        } else {
            items(connectedServers, key = { it.serverId }) { server ->
                SettingsRow(
                    label = server.displayName,
                    subtitle = server.connectionModeLabel,
                    trailing = {
                        if (server.serverId == selectedServerId) {
                            Icon(Icons.Default.Check, null, tint = BaoziTheme.accentStrong, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { selectedServerId = server.serverId },
                )
            }
        }

        item { SectionHeader("宠物") }
        when {
            selectedServerId.isBlank() -> {
                item { SettingsRow(label = "未选择服务器") }
            }
            loading -> {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BaoziTheme.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("正在加载宠物", color = BaoziTheme.textSecondary, fontSize = 13.sp)
                    }
                }
            }
            error != null -> {
                item { SettingsRow(label = "无法加载宠物", subtitle = error) }
            }
            pets.isEmpty() -> {
                item { SettingsRow(label = "未找到宠物", subtitle = "~/.codex/pets 中没有 hatch-pet 包") }
            }
            else -> {
                items(pets, key = { it.id }) { pet ->
                    val selected = PetOverlayController.selectedPet?.serverId == selectedServerId &&
                        PetOverlayController.selectedPet?.id == pet.id
                    SettingsRow(
                        label = pet.displayName,
                        subtitle = pet.validationError ?: pet.description ?: pet.sourcePath,
                        trailing = {
                            if (PetOverlayController.isLoading && selected) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BaoziTheme.accent, strokeWidth = 2.dp)
                            } else if (selected) {
                                Icon(Icons.Default.Check, null, tint = BaoziTheme.accentStrong, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = if (pet.hasValidSpritesheet) {
                            {
                                scope.launch {
                                    PetOverlayController.selectPet(context, appModel, selectedServerId, pet)
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        PetOverlayController.errorMessage?.let { message ->
            item { SettingsRow(label = "宠物加载失败", subtitle = message) }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Experimental Sub-Screen (matches iOS ExperimentalFeaturesView)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExperimentalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val features = remember { BaoziFeature.entries }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = BaoziTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("实验性", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader("功能")
        Column(
            Modifier.fillMaxWidth().background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        ) {
            features.forEachIndexed { idx, feature ->
                val enabled = ExperimentalFeatures.isEnabled(feature)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(feature.displayName, color = BaoziTheme.textPrimary, fontSize = 14.sp)
                        Text(feature.description, color = BaoziTheme.textSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { ExperimentalFeatures.setEnabled(context, feature, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accentStrong),
                    )
                }
                if (idx < features.lastIndex) HorizontalDivider(color = BaoziTheme.divider)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("实验性功能可能不稳定，或在不另行通知的情况下变更。", color = BaoziTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Debug Sub-Screen
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
    ) {
        // Nav bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = BaoziTheme.accent)
            }
            Spacer(Modifier.weight(1f))
            Text("调试", color = BaoziTheme.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(16.dp))

        SectionHeader("渲染")
        Column(
            Modifier.fillMaxWidth().background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("禁用 Markdown", color = BaoziTheme.textPrimary, fontSize = 14.sp)
                    Text("显示原始等宽文本而非渲染后的 markdown", color = BaoziTheme.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = DebugSettings.disableMarkdown,
                    onCheckedChange = { DebugSettings.setDisableMarkdown(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accentStrong),
                )
            }
            HorizontalDivider(color = BaoziTheme.divider)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示回合指标", color = BaoziTheme.textPrimary, fontSize = 14.sp)
                    Text("在回合项上显示耗时和 token 数", color = BaoziTheme.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = DebugSettings.showTurnMetrics,
                    onCheckedChange = { DebugSettings.setShowTurnMetrics(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = BaoziTheme.accentStrong),
                )
            }
        }

        // ── Recording ──
        Spacer(Modifier.height(12.dp))
        SectionHeader("录音中")

        val appModel = LocalAppModel.current
        val scope = rememberCoroutineScope()
        var isRecording by remember { mutableStateOf(MessageRecorder.isRecording(appModel.store)) }
        var recordings by remember { mutableStateOf(MessageRecorder.listRecordings(context)) }

        Column(
            Modifier.fillMaxWidth().background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isRecording) "正在录制..." else "消息录制",
                        color = if (isRecording) BaoziTheme.danger else BaoziTheme.textPrimary,
                        fontSize = 14.sp,
                    )
                    Text("记录服务器消息以便回放", color = BaoziTheme.textSecondary, fontSize = 11.sp)
                }
                TextButton(onClick = {
                    if (isRecording) {
                        MessageRecorder.stopRecording(context, appModel.store)
                        isRecording = false
                        recordings = MessageRecorder.listRecordings(context)
                    } else {
                        MessageRecorder.startRecording(appModel.store)
                        isRecording = true
                    }
                }) {
                    Text(
                        if (isRecording) "停止" else "开始",
                        color = if (isRecording) BaoziTheme.danger else BaoziTheme.accent,
                    )
                }
            }

            if (recordings.isNotEmpty()) {
                HorizontalDivider(color = BaoziTheme.divider)
                Text("已保存的录制", color = BaoziTheme.textSecondary, fontSize = 11.sp)
                recordings.forEach { file ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Text(
                            file.name,
                            color = BaoziTheme.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        val sizeKb = file.length() / 1024
                        Text("${sizeKb}KB", color = BaoziTheme.textMuted, fontSize = 10.sp)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            MessageRecorder.deleteRecording(file)
                            recordings = MessageRecorder.listRecordings(context)
                        }) {
                            Text("删除", color = BaoziTheme.danger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("调试功能仅用于开发和测试。", color = BaoziTheme.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Account Section (inline in top-level, matches iOS SettingsConnectionAccountSection)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AccountSection(server: uniffi.codex_mobile_client.AppServerSnapshot) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKeyStore = remember(context) { OpenAIApiKeyStore(context.applicationContext) }
    var apiKey by remember { mutableStateOf("") }
    var openAIBaseUrl by remember { mutableStateOf("") }
    var isAuthWorking by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var hasStoredApiKey by remember { mutableStateOf(apiKeyStore.hasStoredKey()) }
    var hasStoredBaseUrl by remember { mutableStateOf(apiKeyStore.hasStoredBaseUrl()) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isAuthWorking = false
        LLog.d("ChatGPTOAuth", "settings auth result", fields = mapOf("resultCode" to result.resultCode))
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val tokens = ChatGPTOAuthActivity.parseResult(result.data)
            if (tokens == null) {
                authError = "ChatGPT 登录返回的凭据不完整。"
                LLog.w("ChatGPTOAuth", "settings auth result missing tokens")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                isAuthWorking = true
                try {
                    LLog.d("ChatGPTOAuth", "settings loginAccount starting")
                    appModel.client.loginAccount(
                        server.serverId,
                        AppLoginAccountRequest.ChatgptAuthTokens(
                            accessToken = tokens.accessToken,
                            chatgptAccountId = tokens.accountId,
                            chatgptPlanType = tokens.planType,
                        ),
                    )
                    appModel.refreshSnapshot()
                    authError = null
                    LLog.i("ChatGPTOAuth", "settings loginAccount succeeded")
                } catch (e: Exception) {
                    authError = e.localizedMessage ?: e.message
                    LLog.e("ChatGPTOAuth", "settings loginAccount failed", e)
                }
                isAuthWorking = false
            }
        } else {
            authError = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
            authError?.let { LLog.w("ChatGPTOAuth", "settings auth canceled", fields = mapOf("error" to it)) }
        }
    }

    val authColor = when (server.account) {
        is Account.Chatgpt -> BaoziTheme.accent
        is Account.ApiKey -> Color(0xFF00AAFF)
        else -> BaoziTheme.textMuted
    }
    val authTitle = when (val acct = server.account) {
        is Account.Chatgpt -> acct.email.ifEmpty { "ChatGPT" }
        is Account.ApiKey -> "API 密钥"
        else -> "未登录"
    }
    val authSubtitle = when (server.account) {
        is Account.Chatgpt -> "ChatGPT 账户"
        is Account.ApiKey -> "OpenAI API 密钥"
        else -> null
    }
    val allowsLocalEnvApiKey = server.isLocal
    val isChatGPTAccount = server.account is Account.Chatgpt

    androidx.compose.runtime.LaunchedEffect(server.serverId, server.account) {
        hasStoredApiKey = apiKeyStore.hasStoredKey()
        hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
    }

    Column(
        Modifier.fillMaxWidth().background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(10.dp).clip(CircleShape).background(authColor))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(authTitle, color = BaoziTheme.textPrimary, fontSize = 14.sp)
                authSubtitle?.let { Text(it, color = BaoziTheme.textSecondary, fontSize = 11.sp) }
            }
            if (server.isLocal && server.account != null) {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ChatGPTOAuthTokenStore(context).clear()
                            apiKeyStore.clear()
                            appModel.client.logoutAccount(server.serverId)
                            appModel.restartLocalServer()
                        } catch (_: Exception) {}
                    }
                }) { Text("退出登录", color = BaoziTheme.danger, fontSize = 12.sp) }
            }
        }

        if (server.isLocal && hasStoredApiKey) {
            Text(
                "本地 OpenAI API 密钥已保存。",
                color = BaoziTheme.accent,
                fontSize = 11.sp,
            )
        }

        if (server.isLocal && hasStoredBaseUrl) {
            Text(
                "OpenAI 兼容的基础 URL 已保存。",
                color = BaoziTheme.accent,
                fontSize = 11.sp,
            )
        }

        // Login button
        if (server.isLocal && !isChatGPTAccount) {
            Button(
                onClick = {
                    try {
                        authError = null
                        isAuthWorking = true
                        authLauncher.launch(
                            ChatGPTOAuthActivity.createIntent(
                                context,
                                ChatGPTOAuth.createLoginAttempt(),
                            ),
                        )
                    } catch (e: Exception) {
                        isAuthWorking = false
                        authError = e.localizedMessage ?: e.message
                    }
                },
                enabled = !isAuthWorking,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            ) {
                if (isAuthWorking) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = BaoziTheme.textPrimary); Spacer(Modifier.width(6.dp)) }
                Text("使用 ChatGPT 登录", color = BaoziTheme.accent, fontSize = 14.sp)
            }
        }

        if (allowsLocalEnvApiKey) {
            if (hasStoredApiKey) {
                Text(
                    "OpenAI API 密钥已保存在本地环境中。",
                    color = BaoziTheme.textSecondary,
                    fontSize = 11.sp,
                )
            } else if (isChatGPTAccount) {
                Text(
                    "在本地 Codex 环境中保存 API 密钥。",
                    color = BaoziTheme.textSecondary,
                    fontSize = 11.sp,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = apiKey, onValueChange = { apiKey = it },
                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(BaoziTheme.accent),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f).background(BaoziTheme.codeBackground, RoundedCornerShape(6.dp)).padding(8.dp),
                    decorationBox = { inner -> if (apiKey.isEmpty()) Text("sk-...", color = BaoziTheme.textMuted, fontSize = 13.sp); inner() },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val key = apiKey.trim(); if (key.isEmpty()) return@TextButton
                        scope.launch {
                            isAuthWorking = true
                            try {
                                apiKeyStore.save(key)
                                if (server.account is Account.ApiKey) {
                                    appModel.client.logoutAccount(server.serverId)
                                }
                                appModel.restartLocalServer()
                                hasStoredApiKey = apiKeyStore.hasStoredKey()
                                if (hasStoredApiKey) {
                                    apiKey = ""
                                } else {
                                    authError = "API 密钥未能本地保存。"
                                    return@launch
                                }
                                authError = null
                            } catch (e: Exception) {
                                authError = e.message
                            }
                            isAuthWorking = false
                        }
                    },
                    enabled = apiKey.trim().isNotEmpty() && !isAuthWorking,
                ) {
                    Text(
                        if (hasStoredApiKey) "更新 API 密钥" else "保存 API 密钥",
                        color = BaoziTheme.accent,
                        fontSize = 12.sp,
                    )
                }
            }

            Text(
                if (hasStoredBaseUrl) {
                    "已为本地 Codex 服务器保存自定义的 OpenAI 兼容端点。"
                } else {
                    "用于本地模型的可选 OpenAI 兼容端点。"
                },
                color = BaoziTheme.textSecondary,
                fontSize = 11.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = openAIBaseUrl,
                    onValueChange = { openAIBaseUrl = it },
                    textStyle = TextStyle(color = BaoziTheme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(BaoziTheme.accent),
                    modifier = Modifier.weight(1f).background(BaoziTheme.codeBackground, RoundedCornerShape(6.dp)).padding(8.dp),
                    decorationBox = { inner -> if (openAIBaseUrl.isEmpty()) Text("http://host:port/v1", color = BaoziTheme.textMuted, fontSize = 13.sp); inner() },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val normalized = normalizeOpenAIBaseUrl(openAIBaseUrl)
                        if (normalized == null) {
                            authError = "请输入有效的 http 或 https 基础 URL。"
                        } else {
                            scope.launch {
                                isAuthWorking = true
                                try {
                                    apiKeyStore.saveBaseUrl(normalized)
                                    appModel.restartLocalServer()
                                    hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                    if (hasStoredBaseUrl) {
                                        openAIBaseUrl = ""
                                        authError = null
                                    } else {
                                        authError = "Base URL 未能本地保存。"
                                    }
                                } catch (e: Exception) {
                                    authError = e.message
                                } finally {
                                    isAuthWorking = false
                                }
                            }
                        }
                    },
                    enabled = openAIBaseUrl.trim().isNotEmpty() && !isAuthWorking,
                ) {
                    Text(
                        if (hasStoredBaseUrl) "更新" else "保存",
                        color = BaoziTheme.accent,
                        fontSize = 12.sp,
                    )
                }
            }
            if (hasStoredBaseUrl) {
                TextButton(
                    onClick = {
                        scope.launch {
                            isAuthWorking = true
                            try {
                                apiKeyStore.clearBaseUrl()
                                appModel.restartLocalServer()
                                hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                openAIBaseUrl = ""
                                authError = null
                            } catch (e: Exception) {
                                authError = e.message
                            } finally {
                                isAuthWorking = false
                            }
                        }
                    },
                    enabled = !isAuthWorking,
                ) {
                    Text("清除 Base URL", color = BaoziTheme.danger, fontSize = 12.sp)
                }
            }
        } else {
            Text(
                "远程服务器会在需要时发起各自的 OAuth 登录。设置中的登录和 API 密钥输入仅限本地。",
                color = BaoziTheme.textSecondary,
                fontSize = 11.sp,
            )
        }

        authError?.let { Text(it, color = BaoziTheme.danger, fontSize = 11.sp) }
    }
}

private fun normalizeOpenAIBaseUrl(rawValue: String): String? {
    val trimmed = rawValue.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return trimmed
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared Components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text.uppercase(), color = BaoziTheme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun SettingsRow(
    label: String, subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = BaoziTheme.textPrimary, fontSize = 14.sp)
            subtitle?.let { Text(it, color = BaoziTheme.textSecondary, fontSize = 11.sp) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    SettingsRow(
        icon = { Icon(icon, null, tint = BaoziTheme.accent, modifier = Modifier.size(20.dp)) },
        label = label,
        trailing = { Icon(Icons.Default.ChevronRight, null, tint = BaoziTheme.textMuted, modifier = Modifier.size(16.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun FontRow(name: String, fontFamily: FontFamily, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = BaoziTheme.textPrimary, fontSize = 14.sp)
            Text("敏捷的棕色狐狸", color = BaoziTheme.textSecondary, fontSize = 13.sp, fontFamily = fontFamily)
        }
        if (isSelected) Icon(Icons.Default.Check, null, tint = BaoziTheme.accent, modifier = Modifier.size(18.dp))
    }
}
