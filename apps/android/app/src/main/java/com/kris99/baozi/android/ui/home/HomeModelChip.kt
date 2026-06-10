package com.kris99.baozi.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.common.ModelSelectorPanel
import com.kris99.baozi.android.ui.common.matchesModelSelection
import com.kris99.baozi.android.ui.common.modelPickerDisplayName
import com.kris99.baozi.android.ui.conversation.HeaderOverrides
import com.kris99.baozi.android.ui.scaled
import uniffi.codex_mobile_client.ModelInfo

/**
 * Home-composer model picker chip. Mirrors iOS `HomeModelChip.swift`:
 * shows current model + reasoning label, tap opens a bottom sheet hosting
 * the same [ModelSelectorPanel] the conversation header uses — so the two
 * surfaces stay visually and behaviorally identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeModelChip(
    serverId: String?,
    disabled: Boolean,
    onSheetStateChange: (Boolean) -> Unit = {},
) {
    val appModel = LocalAppModel.current
    val snapshot by appModel.snapshot.collectAsState()
    val launchState by appModel.launchState.snapshot.collectAsState()

    val server = remember(snapshot, serverId) {
        snapshot?.servers?.firstOrNull { it.serverId == serverId }
    }
    val availableModels: List<ModelInfo> = server?.availableModels.orEmpty()

    val selectedId = launchState.selectedModel
        .takeIf { it.isNotBlank() }
        ?: availableModels.firstOrNull { it.isDefault }?.id
        ?: availableModels.firstOrNull()?.id
        ?: ""
    val selectedRuntime = launchState.selectedAgentRuntimeKind
        ?: availableModels.firstOrNull { it.id == selectedId || it.model == selectedId }?.agentRuntimeKind

    val selectedLabel = remember(selectedId, selectedRuntime, availableModels) {
        availableModels.firstOrNull { it.matchesModelSelection(selectedId, selectedRuntime) }?.modelPickerDisplayName()?.ifBlank { selectedId }
            ?: selectedId.ifBlank { "模型" }
    }

    LaunchedEffect(serverId) {
        if (!serverId.isNullOrBlank()) {
            runCatching { appModel.loadConversationMetadataIfNeeded(serverId) }
        }
    }

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(showSheet) {
        onSheetStateChange(showSheet)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BaoziTheme.surface.copy(alpha = 0.9f))
            .border(0.8.dp, BaoziTheme.textMuted.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .clickable(enabled = !disabled) { showSheet = true }
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .alpha(if (disabled) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = null,
            tint = if (disabled) BaoziTheme.textMuted else BaoziTheme.accent,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = selectedLabel,
            color = if (disabled) BaoziTheme.textSecondary else BaoziTheme.textPrimary,
            fontSize = BaoziTextStyle.caption.scaled,
            fontWeight = FontWeight.Medium,
            fontFamily = BaoziTheme.monoFont,
            maxLines = 1,
        )
        val effortLabel = launchState.reasoningEffort.trim()
        if (effortLabel.isNotEmpty()) {
            Text(
                text = effortLabel,
                color = BaoziTheme.textSecondary.copy(alpha = 0.85f),
                fontSize = BaoziTextStyle.caption2.scaled,
                fontFamily = BaoziTheme.monoFont,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = BaoziTheme.textMuted,
            modifier = Modifier.size(12.dp),
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = BaoziTheme.surface,
        ) {
            // No thread yet — the panel hides the Plan toggle, and the
            // Full-access toggle writes to the shared `AppLaunchState`
            // defaults via `updateThreadPermissions(threadKey = null)`.
            ModelSelectorPanel(
                thread = null,
                availableModels = availableModels,
                onToggleMode = null,
                fastMode = HeaderOverrides.pendingFastMode,
                onFastModeChange = { HeaderOverrides.pendingFastMode = it },
            )
        }
    }
}
