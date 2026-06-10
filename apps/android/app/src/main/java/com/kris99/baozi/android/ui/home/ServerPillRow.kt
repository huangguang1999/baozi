package com.kris99.baozi.android.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kris99.baozi.android.state.statusDotState
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.common.StatusDot
import com.kris99.baozi.android.ui.common.AgentIconView
import com.kris99.baozi.android.ui.common.runtimeSortIndex
import com.kris99.baozi.android.ui.scaled
import uniffi.codex_mobile_client.AgentRuntimeInfo
import com.kris99.baozi.android.ui.common.AgentRuntimeKind
import uniffi.codex_mobile_client.AppServerSnapshot

private const val MaxRuntimeBadgesWithoutOverflow = 4
private const val RuntimeBadgesWhenOverflowing = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServerPillRow(
    servers: List<AppServerSnapshot>,
    selectedServerId: String?,
    onTap: (AppServerSnapshot) -> Unit,
    onReconnect: (AppServerSnapshot) -> Unit,
    onRestartAppServer: (AppServerSnapshot) -> Unit,
    onRename: (AppServerSnapshot) -> Unit,
    onRemove: (AppServerSnapshot) -> Unit,
    onAdd: () -> Unit,
    onAddBoundsChanged: (Rect) -> Unit = {},
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .horizontalScroll(scroll)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        servers.forEach { server ->
            ServerPill(
                server = server,
                isSelected = server.serverId == selectedServerId,
                onTap = { onTap(server) },
                onReconnect = { onReconnect(server) },
                onRestartAppServer = { onRestartAppServer(server) },
                onRename = { onRename(server) },
                onRemove = { onRemove(server) },
            )
        }
        AddServerPill(
            onTap = onAdd,
            onBoundsChanged = onAddBoundsChanged,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerPill(
    server: AppServerSnapshot,
    isSelected: Boolean,
    onTap: () -> Unit,
    onReconnect: () -> Unit,
    onRestartAppServer: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isSelected) BaoziTheme.accent.copy(alpha = 0.22f)
                    else BaoziTheme.surface.copy(alpha = 0.9f),
                )
                .border(
                    width = if (isSelected) 1.2.dp else 0.8.dp,
                    color = if (isSelected) BaoziTheme.accent.copy(alpha = 0.9f)
                    else BaoziTheme.textPrimary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(20.dp),
                )
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { showMenu = true },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusDot(state = server.statusDotState, size = 8.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (server.displayName == "This Device") "本设备" else server.displayName,
                    color = BaoziTheme.textPrimary,
                    fontSize = BaoziTextStyle.footnote.scaled,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = BaoziTheme.monoFont,
                    maxLines = 1,
                )
                AgentRuntimeBadgeStack(runtimes = server.agentRuntimes)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("重新连接") },
                onClick = { showMenu = false; onReconnect() },
            )
            DropdownMenuItem(
                text = { Text("重启 app 服务器") },
                onClick = { showMenu = false; onRestartAppServer() },
            )
            if (!server.isLocal) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = { showMenu = false; onRename() },
                )
            }
            DropdownMenuItem(
                text = { Text("移除") },
                onClick = { showMenu = false; onRemove() },
            )
        }
    }
}

@Composable
private fun AgentRuntimeBadgeStack(runtimes: List<AgentRuntimeInfo>) {
    val visible = runtimes
        .filter { it.available }
        .sortedBy { it.kind.runtimeSortIndex }
        .distinctBy { it.kind }
    if (visible.isEmpty()) return
    val isOverflowing = visible.size > MaxRuntimeBadgesWithoutOverflow
    val displayed = if (isOverflowing) visible.take(RuntimeBadgesWhenOverflowing) else visible
    val overflowCount = if (isOverflowing) visible.size - displayed.size else 0

    Row(
        horizontalArrangement = Arrangement.spacedBy((-7).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayed.forEachIndexed { index, runtime ->
            AgentRuntimeBadge(
                runtime = runtime,
                modifier = Modifier.zIndex(index.toFloat()),
            )
        }
        if (overflowCount > 0) {
            AgentRuntimeOverflowBadge(
                count = overflowCount,
                modifier = Modifier.zIndex(displayed.size.toFloat()),
            )
        }
    }
}

@Composable
private fun AgentRuntimeOverflowBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .widthIn(min = 18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(0.55.dp, BaoziTheme.textPrimary.copy(alpha = 0.28f), RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+$count",
            color = BaoziTheme.textPrimary,
            fontSize = BaoziTextStyle.caption2.scaled,
            fontWeight = FontWeight.Bold,
            fontFamily = BaoziTheme.monoFont,
            maxLines = 1,
        )
    }
}

@Composable
private fun AgentRuntimeBadge(
    runtime: AgentRuntimeInfo,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(0.55.dp, BaoziTheme.textPrimary.copy(alpha = 0.28f), RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        AgentIconView(kind = runtime.kind, sizeDp = 14)
    }
}

@Composable
private fun AddServerPill(
    onTap: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    Row(
        modifier = Modifier
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .clip(RoundedCornerShape(20.dp))
            .background(BaoziTheme.textPrimary.copy(alpha = 0.06f))
            .border(0.6.dp, BaoziTheme.accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加服务器",
            tint = BaoziTheme.accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "服务器",
            color = BaoziTheme.accent,
            fontSize = BaoziTextStyle.footnote.scaled,
            fontWeight = FontWeight.SemiBold,
            fontFamily = BaoziTheme.monoFont,
        )
    }
}
