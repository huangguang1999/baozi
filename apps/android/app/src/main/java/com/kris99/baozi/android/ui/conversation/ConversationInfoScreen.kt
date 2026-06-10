package com.kris99.baozi.android.ui.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.state.connectionModeLabel
import com.kris99.baozi.android.state.contextPercent
import com.kris99.baozi.android.state.displayTitle
import com.kris99.baozi.android.state.displayModelLabel
import com.kris99.baozi.android.state.statusColor
import com.kris99.baozi.android.state.statusLabel
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.LocalTextScale
import com.kris99.baozi.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.AppActivityByDayEntry
import uniffi.codex_mobile_client.AppConversationStats
import uniffi.codex_mobile_client.AppModelUsageEntry
import uniffi.codex_mobile_client.AppServerHealth
import uniffi.codex_mobile_client.AppServerSnapshot
import uniffi.codex_mobile_client.AppServerUsageStats
import uniffi.codex_mobile_client.AppThreadSnapshot
import uniffi.codex_mobile_client.AppTokensByThreadEntry
import uniffi.codex_mobile_client.ThreadKey
import uniffi.codex_mobile_client.AppRenameThreadRequest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun ConversationInfoScreen(
    threadKey: ThreadKey? = null,
    serverId: String? = null,
    onBack: () -> Unit,
    onChangeWallpaper: () -> Unit,
    onOpenShell: (() -> Unit)? = null,
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var showRenameDialog by remember(threadKey) { mutableStateOf(false) }
    var renameText by remember(threadKey) { mutableStateOf("") }

    val isServerOnly = threadKey == null
    val resolvedServerId = threadKey?.serverId ?: serverId

    val thread = remember(snapshot, threadKey) {
        if (threadKey == null) null
        else snapshot?.threads?.find { it.key == threadKey }
    }
    val server = remember(snapshot, resolvedServerId) {
        snapshot?.servers?.find { it.serverId == resolvedServerId }
    }

    val stats = remember(thread) { thread?.stats }
    val serverUsage = remember(server) { server?.usageStats }
    val rateLimits = remember(server) { server?.rateLimits }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaoziTheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(BaoziTheme.surface)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = BaoziTheme.textPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isServerOnly) "服务器信息" else "会话信息",
                color = BaoziTheme.textPrimary,
                fontSize = 16f.scaled,
                fontWeight = FontWeight.SemiBold,
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            // Section A: Thread Details (thread mode only)
            if (!isServerOnly) {
                item {
                    ThreadDetailsSection(thread = thread, isLocal = server?.isLocal == true)
                }
            }

            // Action buttons row
            if (!isServerOnly) {
                item {
                    ActionButtonsRow(
                        onChangeWallpaper = onChangeWallpaper,
                        onFork = {
                            scope.launch {
                                val t = thread ?: return@launch
                                val tk = threadKey ?: return@launch
                                try {
                                    val newKey = appModel.client.forkThread(
                                        tk.serverId,
                                        appModel.launchState.threadForkRequest(
                                            sourceThreadId = tk.threadId,
                                            cwdOverride = t.info.cwd,
                                            threadKey = tk,
                                        ),
                                    )
                                    appModel.store.setActiveThread(newKey)
                                    appModel.refreshThreadSnapshot(newKey)
                                } catch (_: Exception) {}
                            }
                        },
                        onRename = {
                            renameText = thread?.info?.title.orEmpty()
                            showRenameDialog = true
                        },
                    )
                }
            }

            // Server-only: show just the Wallpaper button
            if (isServerOnly) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        ActionCircleButton(
                            icon = Icons.Default.Image,
                            label = "壁纸",
                            onClick = onChangeWallpaper,
                        )
                        if (onOpenShell != null) {
                            ActionCircleButton(
                                icon = Icons.Outlined.Terminal,
                                label = "Shell",
                                onClick = onOpenShell,
                            )
                        }
                    }
                }
            }

            // Context window bar (thread mode only)
            if (!isServerOnly && thread != null) {
                item {
                    ContextWindowBar(thread = thread)
                }
            }

            // Per-conversation stats (thread mode only)
            if (!isServerOnly && stats != null) {
                item {
                    StatsGrid(stats = stats)
                }
            }

            // Section B: Server-Wide Charts
            if (serverUsage != null) {
                item {
                    SectionHeader("服务器用量")
                }

                if (serverUsage.tokensByThread.isNotEmpty()) {
                    item {
                        TokenUsageChart(data = serverUsage.tokensByThread)
                    }
                }

                if (serverUsage.activityByDay.isNotEmpty()) {
                    item {
                        ActivityChart(data = serverUsage.activityByDay)
                    }
                }

                if (serverUsage.modelUsage.isNotEmpty()) {
                    item {
                        ModelBreakdownChart(data = serverUsage.modelUsage)
                    }
                }
            }

            if (rateLimits != null) {
                item {
                    RateLimitGauge(rateLimits = rateLimits)
                }
            }

            // Section C: Server Info
            if (server != null) {
                item {
                    ServerInfoSection(server = server)
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showRenameDialog && threadKey != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
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
                    showRenameDialog = false
                    scope.launch {
                        try {
                            appModel.client.renameThread(
                                threadKey.serverId,
                                AppRenameThreadRequest(
                                    threadId = threadKey.threadId,
                                    name = trimmed,
                                ),
                            )
                            appModel.refreshThreadSnapshot(threadKey)
                        } catch (_: Exception) {}
                    }
                }) {
                    Text("重命名")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = BaoziTheme.textMuted,
        fontSize = BaoziTextStyle.caption2.scaled,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ThreadDetailsSection(thread: AppThreadSnapshot?, isLocal: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = thread?.displayTitle ?: "未命名会话",
            color = BaoziTheme.textPrimary,
            fontSize = 20f.scaled,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))

        if (thread != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = thread.displayModelLabel,
                    color = BaoziTheme.accent,
                    fontSize = BaoziTextStyle.code.scaled,
                    fontWeight = FontWeight.Medium,
                )
                thread.reasoningEffort?.let { effort ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = effort,
                        color = BaoziTheme.textMuted,
                        fontSize = BaoziTextStyle.caption2.scaled,
                        modifier = Modifier
                            .background(BaoziTheme.border.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            thread.info.cwd?.let { cwd ->
                val context = androidx.compose.ui.platform.LocalContext.current
                val abbreviated = if (isLocal) {
                    com.kris99.baozi.android.state.PathDisplay.display(cwd, true, context)
                } else {
                    cwd.replace(Regex("^/home/[^/]+"), "~")
                        .replace(Regex("^/Users/[^/]+"), "~")
                }
                Text(
                    text = abbreviated,
                    color = BaoziTheme.textSecondary,
                    fontSize = BaoziTextStyle.caption.scaled,
                    fontFamily = BaoziTheme.monoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }

            Text(
                text = "id: ${thread.key.threadId}",
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.caption2.scaled,
                fontFamily = BaoziTheme.monoFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                thread.info.createdAt?.let { ts ->
                    InfoLabel("创建于", formatTimestamp(ts))
                }
                thread.info.updatedAt?.let { ts ->
                    InfoLabel("更新于", formatTimestamp(ts))
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(label: String, value: String) {
    Column {
        Text(text = label, color = BaoziTheme.textMuted, fontSize = 10f.scaled)
        Text(text = value, color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled)
    }
}

@Composable
private fun ContextWindowBar(thread: AppThreadSnapshot) {
    val percent = thread.contextPercent
    val used = thread.contextTokensUsed?.toLong() ?: 0
    val window = thread.modelContextWindow?.toLong() ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("上下文窗口", color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled)
            Text("$percent%", color = BaoziTheme.accent, fontSize = BaoziTextStyle.caption.scaled, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = BaoziTheme.accent,
            trackColor = BaoziTheme.border,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${formatTokenCount(used)} / ${formatTokenCount(window)} tokens",
            color = BaoziTheme.textMuted,
            fontSize = 10f.scaled,
        )
    }
}

@Composable
private fun StatsGrid(stats: AppConversationStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("对话统计")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("消息", "${stats.totalMessages}", "${stats.userMessageCount}u / ${stats.assistantMessageCount}a", Modifier.weight(1f))
            StatCard("轮次", "${stats.turnCount}", null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("命令", "${stats.commandsExecuted}", "${stats.commandsSucceeded}\u2713 / ${stats.commandsFailed}\u2717", Modifier.weight(1f))
            StatCard("已改文件", "${stats.filesChanged}", null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("MCP 调用", "${stats.mcpToolCallCount}", null, Modifier.weight(1f))
            StatCard("命令耗时", formatDuration(stats.totalCommandDurationMs), null, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("差异", "+${stats.diffAdditions} / -${stats.diffDeletions}", null, Modifier.weight(1f))
            StatCard("图片", "${stats.imageCount}", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BaoziTheme.codeBackground, RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(text = title, color = BaoziTheme.textMuted, fontSize = 10f.scaled)
        Text(
            text = value,
            color = BaoziTheme.textPrimary,
            fontSize = 18f.scaled,
            fontWeight = FontWeight.Bold,
            fontFamily = BaoziTheme.monoFont,
        )
        if (subtitle != null) {
            Text(text = subtitle, color = BaoziTheme.textSecondary, fontSize = 10f.scaled)
        }
    }
}

// --- Charts ---

@Composable
private fun TokenUsageChart(data: List<AppTokensByThreadEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "tokenChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val maxTokens = data.maxOfOrNull { it.tokens.toLong() } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("各会话 token 用量", color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = BaoziTheme.accent
        val border = BaoziTheme.border
        val labelColor = BaoziTheme.textMuted

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((data.size * 36 + 20).dp),
        ) {
            val barHeight = 20f
            val barSpacing = 36f
            val labelWidth = size.width * 0.3f
            val chartWidth = size.width - labelWidth - 16f

            data.forEachIndexed { index, entry ->
                val y = index * barSpacing + 10f
                val tokens = entry.tokens.toLong()
                val barWidth = (tokens.toFloat() / maxTokens * chartWidth * animatedProgress).coerceAtLeast(2f)

                // Bar
                drawRoundRect(
                    color = accent.copy(alpha = 0.7f),
                    topLeft = Offset(labelWidth + 8f, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )

                // Label
                val title = entry.threadTitle
                val labelText = if (title.length > 18) title.take(18) + "\u2026" else title
                drawText(
                    textMeasurer = textMeasurer,
                    text = labelText,
                    topLeft = Offset(0f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (10f * textScale).sp),
                )

                // Value
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatTokenCount(tokens),
                    topLeft = Offset(labelWidth + barWidth + 12f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                )
            }
        }
    }
}

@Composable
private fun ActivityChart(data: List<AppActivityByDayEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "activityChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val maxCount = data.maxOfOrNull { it.turnCount.toInt() } ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("活动时间线", color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = BaoziTheme.accent
        val border = BaoziTheme.border
        val labelColor = BaoziTheme.textMuted

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val chartHeight = size.height - 20f
            val barWidth = (size.width / data.size.coerceAtLeast(1)).coerceAtMost(40f)
            val gap = 4f

            // Grid lines
            for (i in 0..3) {
                val y = chartHeight * (1f - i / 4f)
                drawLine(border, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
            }

            data.forEachIndexed { index, entry ->
                val x = index * barWidth + gap
                val count = entry.turnCount.toInt()
                val barH = (count.toFloat() / maxCount * chartHeight * animatedProgress).coerceAtLeast(2f)
                val y = chartHeight - barH

                drawRoundRect(
                    color = accent,
                    topLeft = Offset(x, y),
                    size = Size(barWidth - gap * 2, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f),
                )
            }

            // X-axis labels (first and last)
            if (data.isNotEmpty()) {
                val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d")
                val firstDate = Instant.ofEpochSecond(data.first().dateEpoch)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                drawText(
                    textMeasurer = textMeasurer,
                    text = firstDate.format(fmt),
                    topLeft = Offset(0f, chartHeight + 4f),
                    style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                )
                if (data.size > 1) {
                    val lastDate = Instant.ofEpochSecond(data.last().dateEpoch)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    val lastLabel = lastDate.format(fmt)
                    val measured = textMeasurer.measure(lastLabel, TextStyle(fontSize = (9f * textScale).sp))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = lastLabel,
                        topLeft = Offset(size.width - measured.size.width, chartHeight + 4f),
                        style = TextStyle(color = labelColor, fontSize = (9f * textScale).sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelBreakdownChart(data: List<AppModelUsageEntry>) {
    val textMeasurer = rememberTextMeasurer()
    val textScale = LocalTextScale.current
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(800),
        label = "modelChartAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val total = data.sumOf { it.threadCount.toInt() }.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("模型明细", color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = BaoziTheme.accent
        val labelColor = BaoziTheme.textMuted
        val colors = listOf(
            BaoziTheme.accent,
            BaoziTheme.info,
            BaoziTheme.violet,
            BaoziTheme.amber,
            BaoziTheme.teal,
            BaoziTheme.olive,
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((data.size * 32 + 8).dp),
        ) {
            data.forEachIndexed { index, entry ->
                val y = index * 32f + 4f
                val count = entry.threadCount.toInt()
                val ratio = count.toFloat() / total
                val barWidth = size.width * 0.6f * ratio * animatedProgress
                val color = colors[index % colors.size]

                drawRoundRect(
                    color = color.copy(alpha = 0.7f),
                    topLeft = Offset(0f, y),
                    size = Size(barWidth.coerceAtLeast(4f), 20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
                )

                val label = "${entry.model} ($count)"
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    topLeft = Offset(barWidth + 8f, y + 2f),
                    style = TextStyle(color = labelColor, fontSize = (10f * textScale).sp),
                )
            }
        }
    }
}

@Composable
private fun RateLimitGauge(rateLimits: uniffi.codex_mobile_client.RateLimitSnapshot) {
    var animProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = animProgress,
        animationSpec = tween(1000),
        label = "rateLimitAnim",
    )
    LaunchedEffect(Unit) { animProgress = 1f }

    val primaryPercent = rateLimits.primary?.usedPercent ?: 0
    val secondaryPercent = rateLimits.secondary?.usedPercent ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text("速率限制", color = BaoziTheme.textSecondary, fontSize = BaoziTextStyle.caption.scaled, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        val accent = BaoziTheme.accent
        val warning = BaoziTheme.warning
        val danger = BaoziTheme.danger
        val border = BaoziTheme.border

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Primary gauge
            GaugeArc(
                label = "主要",
                percent = primaryPercent,
                animatedProgress = animatedProgress,
                accent = accent,
                warning = warning,
                danger = danger,
                border = border,
            )
            // Secondary gauge
            GaugeArc(
                label = "次要",
                percent = secondaryPercent,
                animatedProgress = animatedProgress,
                accent = accent,
                warning = warning,
                danger = danger,
                border = border,
            )
        }
    }
}

@Composable
private fun GaugeArc(
    label: String,
    percent: Int,
    animatedProgress: Float,
    accent: Color,
    warning: Color,
    danger: Color,
    border: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val gaugeColor = when {
            percent > 80 -> danger
            percent > 50 -> warning
            else -> accent
        }

        Canvas(modifier = Modifier.size(80.dp)) {
            val strokeW = 8f
            val arcSize = size.minDimension - strokeW
            val topLeft = Offset(strokeW / 2, strokeW / 2)

            // Background arc
            drawArc(
                color = border,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Filled arc
            drawArc(
                color = gaugeColor,
                startAngle = 135f,
                sweepAngle = 270f * (percent / 100f) * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$percent%",
            color = BaoziTheme.textPrimary,
            fontSize = 16f.scaled,
            fontWeight = FontWeight.Bold,
            fontFamily = BaoziTheme.monoFont,
        )
        Text(text = label, color = BaoziTheme.textMuted, fontSize = 10f.scaled)
    }
}

@Composable
private fun ServerInfoSection(server: AppServerSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader("服务器")
        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(server.statusColor),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = server.displayName,
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.body.scaled,
                fontWeight = FontWeight.Medium,
            )
        }

        InfoRow("主机", "${server.host}:${server.port}")
        InfoRow("模式", server.connectionModeLabel)
        InfoRow("状态", server.statusLabel)

        server.account?.let { account ->
            when (account) {
                is uniffi.codex_mobile_client.Account.Chatgpt -> {
                    InfoRow("账户", account.email)
                    InfoRow("计划", account.planType.toString())
                }
                is uniffi.codex_mobile_client.Account.ApiKey -> {
                    InfoRow("认证", "API 密钥")
                }
            }
        }

        server.availableModels?.let { models ->
            if (models.isNotEmpty()) {
                Text("可用模型", color = BaoziTheme.textMuted, fontSize = 10f.scaled)
                Text(
                    text = models.joinToString(", ") { it.displayName.ifBlank { it.id } },
                    color = BaoziTheme.textSecondary,
                    fontSize = BaoziTextStyle.caption2.scaled,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = BaoziTheme.textMuted, fontSize = BaoziTextStyle.caption2.scaled)
        Text(
            text = value,
            color = BaoziTheme.textSecondary,
            fontSize = BaoziTextStyle.caption2.scaled,
            fontFamily = BaoziTheme.monoFont,
        )
    }
}

@Composable
private fun ActionButtonsRow(
    onChangeWallpaper: () -> Unit,
    onFork: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionCircleButton(
            icon = Icons.Default.Image,
            label = "壁纸",
            onClick = onChangeWallpaper,
        )
        ActionCircleButton(
            icon = Icons.Default.ContentCopy,
            label = "分叉",
            onClick = onFork,
        )
        ActionCircleButton(
            icon = Icons.Default.Edit,
            label = "重命名",
            onClick = onRename,
        )
    }
}

@Composable
private fun ActionCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(BaoziTheme.surface, RoundedCornerShape(14.dp)),
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = BaoziTheme.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = BaoziTheme.textSecondary,
            fontSize = BaoziTextStyle.caption2.scaled,
            fontWeight = FontWeight.Medium,
        )
    }
}

// --- Utilities ---

private fun formatTimestamp(epochSeconds: Long): String {
    val now = System.currentTimeMillis()
    val ts = epochSeconds * 1000
    val diff = now - ts
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 604_800_000 -> "${diff / 86_400_000} 天前"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(ts))
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

private fun formatDuration(ms: Long): String = when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
    else -> String.format("%.1fm", ms / 60_000.0)
}
