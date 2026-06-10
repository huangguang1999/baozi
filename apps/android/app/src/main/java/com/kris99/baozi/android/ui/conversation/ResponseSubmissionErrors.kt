package com.kris99.baozi.android.ui.conversation

internal fun responseSubmissionErrorMessage(error: Throwable): String {
    val message = error.message?.trim().orEmpty()
    return if (error.isDisconnectedTransportError()) {
        "连接已断开，等包子重连后再试。"
    } else {
        message.ifEmpty { "提交回复失败。" }
    }
}

internal fun Throwable.isDisconnectedTransportError(): Boolean {
    val message = this.message?.lowercase().orEmpty()
    return "disconnected" in message ||
        ("transport error" in message && "not connected" in message)
}
