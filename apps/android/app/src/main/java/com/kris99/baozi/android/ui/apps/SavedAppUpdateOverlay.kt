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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.kris99.baozi.android.ui.BaoziTextStyle
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.ui.scaled

@Composable
fun SavedAppUpdateOverlay(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isSubmitting: Boolean,
) {
    var prompt by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    // Fade the dim out while the update is running so the user can see the
    // widget regenerating underneath. Mirrors iOS SavedAppUpdateOverlay.swift:18.
    val dimAlpha by animateFloatAsState(
        targetValue = if (isSubmitting) 0f else 0.55f,
        animationSpec = tween(durationMillis = 240),
        label = "update-overlay-dim",
    )

    LaunchedEffect(Unit) {
        // Brief delay lets the overlay animation settle before pulling focus so
        // the IME doesn't fight the dim fade. Mirrors iOS' 200ms asyncAfter.
        delay(200)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
            .pointerInput(isSubmitting) {
                // Consume taps so they don't fall through to the underlying
                // WebView. Dismiss on tap outside the card only when not
                // submitting.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (!isSubmitting) {
                            event.changes.forEach { it.consume() }
                        } else {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .clickable(enabled = !isSubmitting, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .imePadding()
                .clip(RoundedCornerShape(18.dp))
                .background(BaoziTheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSubmitting) "正在更新「$currentTitle」" else "更新应用",
                    color = BaoziTheme.textPrimary,
                    fontSize = BaoziTextStyle.headline.scaled,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (!isSubmitting) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = BaoziTheme.textSecondary,
                        )
                    }
                }
            }

            if (isSubmitting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = BaoziTheme.accent,
                    )
                    Text(
                        text = "正在处理你的更新…",
                        color = BaoziTheme.textSecondary,
                        fontSize = BaoziTextStyle.footnote.scaled,
                    )
                }
            } else {
                Text(
                    text = "描述你想要的改动。模型会保留你已保存的状态。",
                    color = BaoziTheme.textSecondary,
                    fontSize = BaoziTextStyle.footnote.scaled,
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text("例如：把按钮放大一些") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = BaoziTheme.textSecondary)
                    }
                    Spacer(Modifier.size(8.dp))
                    TextButton(
                        enabled = prompt.isNotBlank(),
                        onClick = {
                            val value = prompt.trim()
                            if (value.isNotEmpty()) onSubmit(value)
                        },
                    ) {
                        Text("提交", color = BaoziTheme.accent)
                    }
                }
            }
        }
    }
}
