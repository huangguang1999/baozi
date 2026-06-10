package com.kris99.baozi.android.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class BaoziFeature(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultEnabled: Boolean,
) {
    REALTIME_VOICE(
        id = "realtime_voice",
        displayName = "实时语音",
        description = "在主屏显示实时语音入口。",
        defaultEnabled = true,
    ),
    THINKING_MINIGAME(
        id = "thinking_minigame",
        displayName = "思考小游戏",
        description = "在助手生成时点按「思考」微光，玩一个即时生成的小游戏。",
        defaultEnabled = false,
    ),
    TERMINAL(
        id = "terminal",
        displayName = "终端",
        description = "在主屏显示本地和远程终端入口。",
        defaultEnabled = false,
    ),
}

object ExperimentalFeatures {
    private const val PREFS = "baozi_ui_prefs"
    private const val KEY = "baozi.experimentalFeatures"

    private var overrides by mutableStateOf<Map<String, Boolean>>(emptyMap())

    fun initialize(context: Context) {
        @Suppress("UNCHECKED_CAST")
        overrides = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0 || separator >= entry.lastIndex) {
                    null
                } else {
                    val featureId = entry.substring(0, separator)
                    val value = entry.substring(separator + 1).toBooleanStrictOrNull() ?: return@mapNotNull null
                    featureId to value
                }
            }
            ?.toMap()
            ?: emptyMap()
    }

    fun isEnabled(feature: BaoziFeature): Boolean {
        return overrides[feature.id] ?: feature.defaultEnabled
    }

    fun setEnabled(context: Context, feature: BaoziFeature, enabled: Boolean) {
        val next = overrides.toMutableMap()
        if (enabled == feature.defaultEnabled) {
            next.remove(feature.id)
        } else {
            next[feature.id] = enabled
        }
        overrides = next.toMap()
        persist(context)
    }

    private fun persist(context: Context) {
        val encoded = overrides.entries.mapTo(linkedSetOf()) { (key, value) -> "$key=$value" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, encoded)
            .apply()
    }
}
