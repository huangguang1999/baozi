package com.kris99.baozi.android.ui.settings

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kris99.baozi.android.auth.ChatGPTOAuthActivity
import com.kris99.baozi.android.state.ChatGPTOAuth
import com.kris99.baozi.android.state.ChatGPTOAuthTokenStore
import com.kris99.baozi.android.state.OpenAIApiKeyStore
import com.kris99.baozi.android.ui.LocalAppModel
import com.kris99.baozi.android.ui.BaoziTheme
import com.kris99.baozi.android.util.LLog
import kotlinx.coroutines.launch
import uniffi.codex_mobile_client.Account
import uniffi.codex_mobile_client.AppRefreshAccountRequest
import uniffi.codex_mobile_client.AppLoginAccountRequest

/**
 * Account login/logout management for a specific server.
 */
@Composable
fun AccountSheet(
    serverId: String,
    onDismiss: () -> Unit,
) {
    val appModel = LocalAppModel.current
    val context = LocalContext.current
    val snapshot by appModel.snapshot.collectAsState()
    val scope = rememberCoroutineScope()

    val server = remember(snapshot, serverId) {
        snapshot?.servers?.find { it.serverId == serverId }
    }
    val account = server?.account
    val apiKeyStore = remember(context) { OpenAIApiKeyStore(context.applicationContext) }
    var apiKey by remember { mutableStateOf("") }
    var openAIBaseUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isAuthWorking by remember { mutableStateOf(false) }
    var hasStoredApiKey by remember { mutableStateOf(apiKeyStore.hasStoredKey()) }
    var hasStoredBaseUrl by remember { mutableStateOf(apiKeyStore.hasStoredBaseUrl()) }
    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        isAuthWorking = false
        LLog.d("ChatGPTOAuth", "account sheet auth result", fields = mapOf("resultCode" to result.resultCode))
        if (result.resultCode == Activity.RESULT_OK) {
            val tokens = ChatGPTOAuthActivity.parseResult(result.data)
            if (tokens == null) {
                error = "ChatGPT login returned incomplete credentials."
                LLog.w("ChatGPTOAuth", "account sheet auth result missing tokens")
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                try {
                    LLog.d("ChatGPTOAuth", "account sheet loginAccount starting")
                    appModel.client.loginAccount(
                        serverId,
                        AppLoginAccountRequest.ChatgptAuthTokens(
                            accessToken = tokens.accessToken,
                            chatgptAccountId = tokens.accountId,
                            chatgptPlanType = tokens.planType,
                        ),
                    )
                    appModel.refreshSnapshot()
                    error = null
                    LLog.i("ChatGPTOAuth", "account sheet loginAccount succeeded")
                } catch (e: Exception) {
                    error = e.localizedMessage ?: e.message
                    LLog.e("ChatGPTOAuth", "account sheet loginAccount failed", e)
                }
            }
        } else {
            error = result.data?.getStringExtra(ChatGPTOAuthActivity.EXTRA_ERROR)
            error?.let { LLog.w("ChatGPTOAuth", "account sheet auth canceled", fields = mapOf("error" to it)) }
        }
    }

    val allowsLocalEnvApiKey = server?.isLocal == true
    val isChatGPTAccount = account is Account.Chatgpt

    androidx.compose.runtime.LaunchedEffect(serverId, account) {
        hasStoredApiKey = apiKeyStore.hasStoredKey()
        hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
    }

    androidx.compose.runtime.LaunchedEffect(serverId) {
        runCatching {
            appModel.client.refreshAccount(
                serverId,
                AppRefreshAccountRequest(refreshToken = false),
            )
            appModel.refreshSnapshot()
            error = null
        }.onFailure { throwable ->
            error = throwable.localizedMessage ?: throwable.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "账户",
            color = BaoziTheme.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = server?.displayName ?: serverId,
            color = BaoziTheme.textSecondary,
            fontSize = 13.sp,
        )

        // Current account status
        when (account) {
            is Account.Chatgpt -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BaoziTheme.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    Text("已登录", color = BaoziTheme.accent, fontSize = 13.sp)
                    Text(account.email, color = BaoziTheme.textPrimary, fontSize = 14.sp)
                }
            }

            is Account.ApiKey -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BaoziTheme.surface, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                ) {
                    Text("API 密钥已配置", color = BaoziTheme.accent, fontSize = 13.sp)
                }
            }

            null -> Unit
        }

        if (server?.isLocal == true && hasStoredApiKey) {
            Text(
                "本地 OpenAI API 密钥已保存。",
                color = BaoziTheme.accent,
                fontSize = 12.sp,
            )
        }

        if (server?.isLocal == true && hasStoredBaseUrl) {
            Text(
                "OpenAI 兼容的基础 URL 已保存。",
                color = BaoziTheme.accent,
                fontSize = 12.sp,
            )
        }

        if (server?.isLocal == true && account != null) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                            ChatGPTOAuthTokenStore(context).clear()
                            apiKeyStore.clear()
                            appModel.client.logoutAccount(serverId)
                            appModel.restartLocalServer()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("退出登录")
            }
        }

        if (server?.isLocal == true && !isChatGPTAccount) {
            Button(
                onClick = {
                    try {
                        error = null
                        isAuthWorking = true
                        authLauncher.launch(
                            ChatGPTOAuthActivity.createIntent(
                                context,
                                ChatGPTOAuth.createLoginAttempt(),
                            ),
                        )
                    } catch (e: Exception) {
                        isAuthWorking = false
                        error = e.localizedMessage ?: e.message
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BaoziTheme.accent,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAuthWorking,
            ) {
                Text("使用 ChatGPT 登录")
            }
        }

        if (allowsLocalEnvApiKey) {
            if (hasStoredApiKey) {
                Text(
                    "OpenAI API 密钥已保存在本地环境中。",
                    color = BaoziTheme.textSecondary,
                    fontSize = 12.sp,
                )
            } else if (isChatGPTAccount) {
                Text(
                    "在本地 Codex 环境中保存 OpenAI API 密钥。",
                    color = BaoziTheme.textSecondary,
                    fontSize = 12.sp,
                )
            } else {
                Text("或为本地环境保存一个 API 密钥：", color = BaoziTheme.textSecondary, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API 密钥") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                apiKeyStore.save(apiKey.trim())
                                if (account is Account.ApiKey) {
                                    appModel.client.logoutAccount(serverId)
                                }
                                appModel.restartLocalServer()
                                hasStoredApiKey = apiKeyStore.hasStoredKey()
                                if (hasStoredApiKey) {
                                    apiKey = ""
                                } else {
                                    error = "API 密钥未能本地保存。"
                                    return@launch
                                }
                                error = null
                            } catch (e: Exception) {
                                error = e.message
                            }
                        }
                    },
                    enabled = apiKey.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaoziTheme.accent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(if (hasStoredApiKey) "更新 API 密钥" else "保存 API 密钥")
                }
            }

            Text(
                if (hasStoredBaseUrl) {
                    "已为本地 Codex 服务器保存自定义的 OpenAI 兼容端点。"
                } else {
                    "用于本地模型的可选 OpenAI 兼容端点。"
                },
                color = BaoziTheme.textSecondary,
                fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = openAIBaseUrl,
                    onValueChange = { openAIBaseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("http://host:port/v1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val normalized = normalizeOpenAIBaseUrl(openAIBaseUrl)
                        if (normalized == null) {
                            error = "Enter a valid http or https base URL."
                        } else {
                            scope.launch {
                                isAuthWorking = true
                                try {
                                    apiKeyStore.saveBaseUrl(normalized)
                                    appModel.restartLocalServer()
                                    hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                    if (hasStoredBaseUrl) {
                                        openAIBaseUrl = ""
                                        error = null
                                    } else {
                                        error = "Base URL did not persist locally."
                                    }
                                } catch (e: Exception) {
                                    error = e.message
                                } finally {
                                    isAuthWorking = false
                                }
                            }
                        }
                    },
                    enabled = openAIBaseUrl.isNotBlank() && !isAuthWorking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BaoziTheme.accent,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(if (hasStoredBaseUrl) "更新" else "保存")
                }
            }
            if (hasStoredBaseUrl) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isAuthWorking = true
                            try {
                                apiKeyStore.clearBaseUrl()
                                appModel.restartLocalServer()
                                hasStoredBaseUrl = apiKeyStore.hasStoredBaseUrl()
                                openAIBaseUrl = ""
                                error = null
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isAuthWorking = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAuthWorking,
                ) {
                    Text("清除 Base URL")
                }
            }
        } else if (server?.isLocal == false) {
            Text(
                "Remote servers request their own OAuth login when needed. Account login and API key entry stay local-only.",
                color = BaoziTheme.textSecondary,
                fontSize = 12.sp,
            )
        }

        error?.let {
            Text(it, color = BaoziTheme.danger, fontSize = 12.sp)
        }
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
