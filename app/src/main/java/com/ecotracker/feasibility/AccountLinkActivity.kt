package com.wildlife.feasibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wildlife.feasibility.ui.screens.account.AccountLinkScreen
import com.wildlife.feasibility.ui.screens.account.AccountLinkUiState
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import kotlin.concurrent.thread

class AccountLinkActivity : ComponentActivity() {
    private lateinit var accountStore: AccountStore
    private var loading = false
    private var uiState by mutableStateOf(AccountLinkUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.rgb(8, 11, 9)),
        )
        accountStore = AccountStore(this)
        refreshState(
            username = savedInstanceState?.getString(STATE_USERNAME)
                ?: intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
        )
        setContent {
            WildlifeTheme {
                AccountLinkScreen(
                    state = uiState,
                    onBack = ::finish,
                    onUsernameChange = { username ->
                        uiState = uiState.copy(username = username, message = null)
                    },
                    onBeginVerification = ::beginVerification,
                    onCopyCode = ::copyCode,
                    onOpenProfileSettings = {
                        openUrl("https://www.inaturalist.org/users/edit")
                    },
                    onCheckVerification = ::checkVerification,
                    onStartAgain = {
                        accountStore.clearPending()
                        refreshState(message = "Enter the exact username you want to verify.")
                    },
                    onDone = ::finish,
                    onOpenPublicProfile = { login ->
                        openUrl("https://www.inaturalist.org/people/$login")
                    },
                    onUnlink = ::unlink,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_USERNAME, uiState.username)
        super.onSaveInstanceState(outState)
    }

    private fun refreshState(
        username: String = uiState.username,
        message: String? = uiState.message,
    ) {
        val verified = accountStore.verified()
        val pending = if (verified == null) accountStore.pending() else null
        uiState = AccountLinkUiState(
            username = when {
                username.isNotBlank() -> username
                pending != null -> pending.login
                verified != null -> verified.login
                else -> ""
            },
            pending = pending,
            verified = verified,
            loading = loading,
            message = message,
        )
    }

    private fun beginVerification(username: String) {
        val exactUsername = username.trim()
        if (exactUsername.isBlank()) {
            refreshState(message = "Enter your exact iNaturalist username.")
            return
        }
        if (loading) return

        loading = true
        refreshState(
            username = exactUsername,
            message = "Resolving the exact public iNaturalist username…",
        )
        thread(name = "inat-account-resolve") {
            runCatching {
                val user = INaturalistClient().resolveExactUser(exactUsername)
                PendingAccountLink(
                    userId = user.id,
                    login = user.login,
                    code = AccountVerification.newCode(),
                    createdAtMs = System.currentTimeMillis(),
                )
            }.onSuccess { pending ->
                accountStore.savePending(pending)
                runOnUiThread {
                    loading = false
                    refreshState(
                        username = pending.login,
                        message = "Account found. Complete the two steps below.",
                    )
                }
            }.onFailure { error ->
                runOnUiThread {
                    loading = false
                    refreshState(
                        username = exactUsername,
                        message = "Could not start verification: ${error.message}",
                    )
                }
            }
        }
    }

    private fun checkVerification() {
        val pending = accountStore.pending() ?: return
        if (loading) return
        if (AccountVerification.isExpired(pending, System.currentTimeMillis())) {
            accountStore.clearPending()
            refreshState(
                username = pending.login,
                message = "The verification code expired. Generate a new one.",
            )
            return
        }

        loading = true
        refreshState(message = "Checking the public iNaturalist profile…")
        thread(name = "inat-account-check") {
            runCatching { INaturalistClient().publicUserProfile(pending.userId) }
                .onSuccess { profile ->
                    runOnUiThread {
                        loading = false
                        if (AccountVerification.profileContainsCode(profile, pending)) {
                            accountStore.markVerified(pending, System.currentTimeMillis())
                            setResult(RESULT_OK)
                            refreshState(
                                username = pending.login,
                                message = "Verified successfully.",
                            )
                        } else {
                            refreshState(
                                message = "Code not found yet. Save it in your iNaturalist profile description, wait briefly, then check again.",
                            )
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        loading = false
                        refreshState(message = "Verification check failed: ${error.message}")
                    }
                }
        }
    }

    private fun copyCode(code: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Wildlife verification code", code))
        refreshState(message = "Code copied. Paste it into your iNaturalist profile description.")
    }

    private fun unlink() {
        accountStore.unlink()
        setResult(RESULT_OK)
        refreshState(username = "", message = "Account unlinked. No iNaturalist data was changed.")
    }

    private fun openUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            refreshState(message = "No browser is available to open that iNaturalist page.")
        }
    }

    companion object {
        const val EXTRA_USERNAME = "username"
        private const val STATE_USERNAME = "account_username"
    }
}
