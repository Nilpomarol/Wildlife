package com.wildlife.feasibility.ui.screens.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.wildlife.feasibility.PendingAccountLink
import com.wildlife.feasibility.VerifiedAccount
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class AccountLinkStage { START, PENDING, VERIFIED }

data class AccountLinkUiState(
    val username: String = "",
    val pending: PendingAccountLink? = null,
    val verified: VerifiedAccount? = null,
    val loading: Boolean = false,
    val message: String? = null,
) {
    val stage: AccountLinkStage
        get() = when {
            verified != null -> AccountLinkStage.VERIFIED
            pending != null -> AccountLinkStage.PENDING
            else -> AccountLinkStage.START
        }
}

@Composable
fun AccountLinkScreen(
    state: AccountLinkUiState,
    onBack: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onBeginVerification: (String) -> Unit,
    onCopyCode: (String) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onCheckVerification: () -> Unit,
    onStartAgain: () -> Unit,
    onDone: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onUnlink: () -> Unit,
) {
    var confirmUnlink by remember { mutableStateOf(false) }

    WildlifeScaffold(title = "Link iNaturalist", onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = WildlifeSpacing.Screen,
                end = WildlifeSpacing.Screen,
                bottom = WildlifeSpacing.Section,
            ),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                    Text(
                        text = "Verify your public account without OAuth or a password.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Wildlife reads public iNaturalist data only. It cannot create, edit, or delete observations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WildlifeTheme.colors.mutedText,
                    )
                }
            }

            state.message?.let { message ->
                item { StatusCard(message = message, loading = state.loading) }
            }

            when (state.stage) {
                AccountLinkStage.START -> item {
                    StartContent(
                        state = state,
                        onUsernameChange = onUsernameChange,
                        onBeginVerification = onBeginVerification,
                    )
                }

                AccountLinkStage.PENDING -> item {
                    PendingContent(
                        pending = requireNotNull(state.pending),
                        loading = state.loading,
                        onCopyCode = onCopyCode,
                        onOpenProfileSettings = onOpenProfileSettings,
                        onCheckVerification = onCheckVerification,
                        onStartAgain = onStartAgain,
                    )
                }

                AccountLinkStage.VERIFIED -> item {
                    VerifiedContent(
                        account = requireNotNull(state.verified),
                        onDone = onDone,
                        onOpenPublicProfile = onOpenPublicProfile,
                        onRequestUnlink = { confirmUnlink = true },
                    )
                }
            }
        }
    }

    if (confirmUnlink) {
        val login = state.verified?.login.orEmpty()
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            title = { Text("Unlink $login?") },
            text = {
                Text("Pending observations will stop checking until an account is verified again. No iNaturalist data will be changed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnlink = false
                        onUnlink()
                    },
                ) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlink = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StartContent(
    state: AccountLinkUiState,
    onUsernameChange: (String) -> Unit,
    onBeginVerification: (String) -> Unit,
) {
    SectionCard {
        StepTitle(number = "1", title = "Enter your exact username")
        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
            singleLine = true,
            label = { Text("iNaturalist username") },
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { if (!state.loading) onBeginVerification(state.username) },
            ),
        )
        Button(
            onClick = { onBeginVerification(state.username) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null)
            Text("Generate verification code", Modifier.padding(start = WildlifeSpacing.Small))
        }
        Text(
            text = "Wildlife resolves the username to its permanent numeric iNaturalist user ID before creating a temporary code.",
            style = MaterialTheme.typography.bodySmall,
            color = WildlifeTheme.colors.mutedText,
        )
    }
}

@Composable
private fun PendingContent(
    pending: PendingAccountLink,
    loading: Boolean,
    onCopyCode: (String) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onCheckVerification: () -> Unit,
    onStartAgain: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen)) {
        SectionCard {
            StepTitle(number = "1", title = "Account found")
            AccountIdentity(login = pending.login, userId = pending.userId)
        }
        SectionCard {
            StepTitle(number = "2", title = "Add this code to your profile")
            SelectionContainer {
                Text(
                    text = pending.code,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            OutlinedButton(
                onClick = { onCopyCode(pending.code) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text("Copy code", Modifier.padding(start = WildlifeSpacing.Small))
            }
            Text(
                text = "Paste the code into your iNaturalist profile description and save. You can remove it after verification. The code expires after 24 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenProfileSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("Open profile settings", Modifier.padding(start = WildlifeSpacing.Small))
            }
        }
        SectionCard {
            StepTitle(number = "3", title = "Check the public profile")
            Button(
                onClick = onCheckVerification,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text("Check verification", Modifier.padding(start = WildlifeSpacing.Small))
            }
            TextButton(
                onClick = onStartAgain,
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) { Text("Start again with another username") }
        }
    }
}

@Composable
private fun VerifiedContent(
    account: VerifiedAccount,
    onDone: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onRequestUnlink: () -> Unit,
) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = WildlifeTheme.colors.confirmed,
            )
            Text("Verified account", style = MaterialTheme.typography.titleLarge)
        }
        AccountIdentity(login = account.login, userId = account.userId)
        Text(
            text = "Wildlife fetches and matches only public observations belonging to this immutable user ID. You may remove the temporary code from your iNaturalist profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        OutlinedButton(
            onClick = { onOpenPublicProfile(account.login) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            Text("Open public profile", Modifier.padding(start = WildlifeSpacing.Small))
        }
        TextButton(onClick = onRequestUnlink, modifier = Modifier.fillMaxWidth()) {
            Text("Unlink account", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AccountIdentity(login: String, userId: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Micro),
        ) {
            Text(login, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "iNaturalist user ID #$userId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            content = content,
        )
    }
}

@Composable
private fun StepTitle(number: String, title: String) {
    Text(
        text = "$number · $title",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun StatusCard(message: String, loading: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(WildlifeSpacing.Card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(WildlifeSpacing.Micro),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Composable
private fun AccountLinkStartPreview() {
    WildlifeTheme { PreviewScreen(AccountLinkUiState(username = "naturalist")) }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 900)
@Composable
private fun AccountLinkPendingPreview() {
    WildlifeTheme {
        PreviewScreen(
            AccountLinkUiState(
                username = "naturalist",
                pending = PendingAccountLink(42, "naturalist", "WILDLIFE-3K8P7R", 1),
                message = "Account found. Complete the two steps below.",
            ),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Composable
private fun AccountLinkVerifiedPreview() {
    WildlifeTheme {
        PreviewScreen(
            AccountLinkUiState(
                verified = VerifiedAccount(42, "naturalist", 1),
                message = "Verified successfully.",
            ),
        )
    }
}

@Composable
private fun PreviewScreen(state: AccountLinkUiState) {
    AccountLinkScreen(
        state = state,
        onBack = {},
        onUsernameChange = {},
        onBeginVerification = {},
        onCopyCode = {},
        onOpenProfileSettings = {},
        onCheckVerification = {},
        onStartAgain = {},
        onDone = {},
        onOpenPublicProfile = {},
        onUnlink = {},
    )
}
