package com.kris99.baozi.android.state

data class MinigameContent(
    val html: String,
    val title: String,
    val width: Float,
    val height: Float,
)

sealed interface MinigameOverlayState {
    data object Idle : MinigameOverlayState
    data object Loading : MinigameOverlayState
    data class Shown(val content: MinigameContent) : MinigameOverlayState
    data class Failed(val message: String) : MinigameOverlayState
}
