package com.kris99.baozi.android.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kris99.baozi.android.ui.BaoziTheme
import uniffi.codex_mobile_client.AppVoiceSessionPhase
import kotlin.math.abs
import kotlin.math.max

@Composable
fun InlineVoiceStatusStrip(
    phase: AppVoiceSessionPhase,
    inputLevel: Float,
    outputLevel: Float,
    onToggleSpeaker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isListening = phase == AppVoiceSessionPhase.LISTENING
    val isSpeaking = phase == AppVoiceSessionPhase.SPEAKING

    val scaledInputLevel = if (isListening) max(0.08f, inputLevel) else max(0f, inputLevel)
    val scaledOutputLevel = if (isSpeaking) max(0.08f, outputLevel) else max(0f, outputLevel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BaoziTheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // YOU indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        if (isListening) BaoziTheme.accent else BaoziTheme.textMuted.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
            Text(
                text = "你",
                color = if (isListening) BaoziTheme.textPrimary else BaoziTheme.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BaoziTheme.monoFont,
            )
            AudioWaveform(
                level = scaledInputLevel,
                tint = BaoziTheme.accent,
                modifier = Modifier.size(width = 48.dp, height = 14.dp),
            )
        }

        // CODEX indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        if (isSpeaking) BaoziTheme.warning else BaoziTheme.textMuted.copy(alpha = 0.4f),
                        CircleShape,
                    ),
            )
            Text(
                text = "CODEX",
                color = if (isSpeaking) BaoziTheme.textPrimary else BaoziTheme.textMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = BaoziTheme.monoFont,
            )
            AudioWaveform(
                level = scaledOutputLevel,
                tint = BaoziTheme.warning,
                modifier = Modifier.size(width = 48.dp, height = 14.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // Speaker toggle
        Icon(
            Icons.Default.VolumeUp,
            contentDescription = "切换扬声器",
            tint = BaoziTheme.textPrimary,
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onToggleSpeaker),
        )

        // Phase label
        Text(
            text = phaseLabel(phase),
            color = phaseColor(phase),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = BaoziTheme.monoFont,
        )
    }
}

@Composable
private fun AudioWaveform(
    level: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val barCount = 12
    Canvas(modifier = modifier) {
        val barWidth = 2f.dp.toPx()
        val totalBarWidth = barWidth * barCount
        val gap = if (barCount > 1) {
            (size.width - totalBarWidth) / (barCount - 1)
        } else {
            0f
        }
        val midY = size.height / 2f
        val center = (barCount - 1) / 2f

        for (index in 0 until barCount) {
            val distance = abs(index - center) / max(center, 1f)
            val base = 1f - distance * 0.5f
            val activeLevel = max(0.15f, level)
            val barHeight = max(0.1f, base * activeLevel) * size.height
            val x = index * (barWidth + gap)
            val cornerRadius = 1f.dp.toPx()

            val rect = Rect(
                left = x,
                top = midY - barHeight / 2f,
                right = x + barWidth,
                bottom = midY + barHeight / 2f,
            )
            drawPath(
                path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            rect = rect,
                            radiusX = cornerRadius,
                            radiusY = cornerRadius,
                        )
                    )
                },
                color = tint,
                style = Fill,
            )
        }
    }
}

private fun phaseLabel(phase: AppVoiceSessionPhase): String =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING -> "连接中"
        AppVoiceSessionPhase.LISTENING -> "聆听中"
        AppVoiceSessionPhase.SPEAKING -> "说话中"
        AppVoiceSessionPhase.THINKING -> "思考中"
        AppVoiceSessionPhase.HANDOFF -> "交接中"
        AppVoiceSessionPhase.ERROR -> "错误"
    }

private fun phaseColor(phase: AppVoiceSessionPhase): Color =
    when (phase) {
        AppVoiceSessionPhase.CONNECTING,
        AppVoiceSessionPhase.THINKING,
        AppVoiceSessionPhase.HANDOFF,
        -> BaoziTheme.warning
        AppVoiceSessionPhase.LISTENING,
        AppVoiceSessionPhase.SPEAKING,
        -> BaoziTheme.accent
        AppVoiceSessionPhase.ERROR -> BaoziTheme.danger
    }
