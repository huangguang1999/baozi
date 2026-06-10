package com.kris99.baozi.android.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kris99.baozi.android.state.SavedAppsStore
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.common.SwipeAction
import com.kris99.baozi.android.ui.common.SwipeableRow
import com.kris99.baozi.android.ui.scaled
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.SavedApp
import java.util.concurrent.TimeUnit

@Composable
fun AppsListScreen(
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps by SavedAppsStore.apps.collectAsState()
    var renameTarget by remember { mutableStateOf<SavedApp?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        SavedAppsStore.reload(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BaoziTheme.background)
            .systemBarsPadding(),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = BaoziTheme.textPrimary,
                )
            }
            Text(
                text = "应用",
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.headline.scaled,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        renameTarget?.let { app ->
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名 app") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("标题") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isEmpty()) return@TextButton
                        scope.launch {
                            try {
                                SavedAppsStore.rename(context, app.id, trimmed)
                            } catch (_: Exception) {}
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

        if (apps.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(apps, key = { it.id }) { app ->
                    SwipeableRow(
                        leadingAction = SwipeAction(
                            icon = Icons.Filled.Edit,
                            label = "重命名",
                            tint = BaoziTheme.accent,
                            onTrigger = {
                                renameText = app.title
                                renameTarget = app
                            },
                        ),
                        trailingAction = SwipeAction(
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            tint = BaoziTheme.textMuted,
                            onTrigger = {
                                scope.launch {
                                    try {
                                        SavedAppsStore.delete(context, app.id)
                                    } catch (_: Exception) {}
                                }
                            },
                        ),
                    ) {
                        AppRow(app = app, onClick = { onOpenApp(app.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: SavedApp,
    onClick: () -> Unit,
) {
    val monogram = monogramInitials(app.title)
    val tint = monogramTint(app.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = monogram,
                color = tint,
                fontSize = BaoziTextStyle.headline.scaled,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.title.ifBlank { "未命名应用" },
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.callout.scaled,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = relativeTime(app.updatedAtMs),
                color = BaoziTheme.textMuted,
                fontSize = BaoziTextStyle.caption2.scaled,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = null,
                tint = BaoziTheme.textMuted,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "暂无应用",
                color = BaoziTheme.textPrimary,
                fontSize = BaoziTextStyle.headline.scaled,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "当 AI 生成带 app_id 的交互式小组件时，会自动保存到此处。",
                color = BaoziTheme.textSecondary,
                fontSize = BaoziTextStyle.footnote.scaled,
            )
        }
    }
}

/**
 * Deterministic per-app tint drawn from a small palette of theme accents so
 * existing apps keep the same color across launches. Mirrors iOS
 * `AppsListView.monogramTint(for:)`.
 */
@Composable
private fun monogramTint(id: String): Color {
    val palette = listOf(
        BaoziTheme.accent,
        BaoziTheme.accentStrong,
        BaoziTheme.success,
        BaoziTheme.warning,
        BaoziTheme.danger,
        BaoziTheme.textSystem,
    )
    val idx = (id.hashCode().toLong() and 0x7FFFFFFFL).toInt() % palette.size
    return palette[idx]
}

/**
 * Monogram: first letter of the first two whitespace-separated words of the
 * title, uppercased. Mirrors iOS `AppsListView.monogramInitials` so avatars
 * stay recognizable across platforms. Falls back to `?` on empty titles.
 */
private fun monogramInitials(title: String): String {
    val words = title.trim().split("\\s+".toRegex()).take(2)
    val letters = words.mapNotNull { it.firstOrNull() }.joinToString("")
    return if (letters.isEmpty()) "?" else letters.uppercase()
}

private fun relativeTime(millis: Long): String {
    val delta = System.currentTimeMillis() - millis
    if (delta < 0) return "刚刚"
    val seconds = TimeUnit.MILLISECONDS.toSeconds(delta)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
        seconds < 60 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        days < 7 -> "${days} 天前"
        else -> "${days / 7} 周前"
    }
}
