package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

@Composable
fun ProfileScreen(
    state: ShellUiState,
    onManageAccount: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    WildlifeScaffold(title = "Profile", bottomBar = bottomBar) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(WildlifeSpacing.Screen),
            verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Screen),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(WildlifeSpacing.Card),
                        verticalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                    ) {
                        Text(
                            text = state.account?.login ?: "No linked account",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = state.account?.let {
                                "Verified iNaturalist user #${it.userId}"
                            } ?: "Link your public iNaturalist identity to sync your collection and XP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                OutlinedButton(onClick = onManageAccount, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, contentDescription = null)
                    Text(
                        if (state.account == null) "Link iNaturalist" else "Manage linked account",
                        Modifier.padding(start = WildlifeSpacing.Small),
                    )
                }
            }
            state.account?.let { account ->
                item {
                    OutlinedButton(
                        onClick = { onOpenPublicProfile(account.login) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text("Open public iNaturalist profile", Modifier.padding(start = WildlifeSpacing.Small))
                    }
                }
            }
            item {
                Text(
                    text = "Wildlife uses only public iNaturalist data. It cannot create, edit, or delete observations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WildlifeTheme.colors.mutedText,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Composable
private fun ProfilePreview() {
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(), onManageAccount = {}, onOpenPublicProfile = {}, bottomBar = {},
        )
    }
}
