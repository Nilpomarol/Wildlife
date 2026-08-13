package com.wildlife.feasibility.ui.screens.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.XpEventRecord
import com.wildlife.feasibility.XpEventType
import com.wildlife.feasibility.ui.components.WildlifeScaffold
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun ProfileScreen(
    state: ShellUiState,
    onManageAccount: () -> Unit,
    onOpenPublicProfile: (String) -> Unit,
    onSelectProgressionTitle: (String) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    var choosingTitle by remember { mutableStateOf(false) }
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
            state.account?.let {
                item {
                    ProgressionCard(
                        progression = state.progression ?: ProgressionProjection.project(0),
                        onChooseTitle = { choosingTitle = true },
                    )
                }
            }
            state.errorMessage?.let { message ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        elevation = CardDefaults.cardElevation(0.dp),
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(WildlifeSpacing.Card),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
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

    val progression = state.progression
    if (choosingTitle && progression != null) {
        AlertDialog(
            onDismissRequest = { choosingTitle = false },
            icon = { Icon(Icons.Outlined.EmojiEvents, contentDescription = null) },
            title = { Text("Choose field title") },
            text = {
                Column {
                    progression.earnedLevels.forEach { level ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectProgressionTitle(level.key)
                                    choosingTitle = false
                                }
                                .padding(vertical = WildlifeSpacing.Small),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = progression.selectedTitle.key == level.key,
                                onClick = null,
                            )
                            Text(level.displayName, Modifier.padding(start = WildlifeSpacing.Small))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingTitle = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun ProgressionCard(
    progression: com.wildlife.feasibility.ProgressionState,
    onChooseTitle: () -> Unit,
) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progression.selectedTitle.displayName,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "Current field title",
                        style = MaterialTheme.typography.bodySmall,
                        color = WildlifeTheme.colors.mutedText,
                    )
                }
                Text(
                    text = "${progression.totalXp} XP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (progression.earnedLevels.size > 1) {
                TextButton(onClick = onChooseTitle) { Text("Choose earned title") }
            }

            val next = progression.nextLevel
            if (next != null) {
                val progressDescription =
                    "${progression.xpToNextLevel} XP until ${next.displayName}"
                LinearProgressIndicator(
                    progress = { progression.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = progressDescription },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = progressDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Highest placeholder level reached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = WildlifeSpacing.Micro),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small)) {
                Icon(
                    imageVector = Icons.Outlined.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Text("Recent rewards", style = MaterialTheme.typography.titleMedium)
            }
            if (progression.recentEvents.isEmpty()) {
                Text(
                    text = "Confirm a new public observation to begin earning XP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WildlifeTheme.colors.mutedText,
                )
            } else {
                progression.recentEvents.forEachIndexed { index, event ->
                    RewardRow(event)
                    if (index != progression.recentEvents.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RewardRow(event: XpEventRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = WildlifeSpacing.Micro),
        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rewardLabel(event),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(event.createdAtMs)),
                style = MaterialTheme.typography.bodySmall,
                color = WildlifeTheme.colors.mutedText,
            )
        }
        Text(
            text = "+${event.points} XP",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

private fun rewardLabel(event: XpEventRecord): String = when (event.type) {
    XpEventType.CONFIRMED_OBSERVATION -> event.label
        ?.takeIf(String::isNotBlank)
        ?.let { "Observation confirmed · $it" }
        ?: "Observation confirmed"
    XpEventType.FIRST_SPECIES -> event.label
        ?.takeIf(String::isNotBlank)
        ?.let { "First species · $it" }
        ?: "First species"
    XpEventType.RESEARCH_GRADE -> "Reached Research Grade"
    XpEventType.IDENTIFICATION_GIVEN -> "Identification contributed"
    XpEventType.ANOMALY_CONFIRMED -> "Reviewed range anomaly"
    XpEventType.LEGACY -> "Earlier Wildlife progress"
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 780)
@Composable
private fun ProfilePreview() {
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            bottomBar = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF080B09, widthDp = 390, heightDp = 900)
@Composable
private fun ProfileProgressionPreview() {
    val events = listOf(
        XpEventRecord(
            eventKey = "first-species:42",
            type = XpEventType.FIRST_SPECIES,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 500,
            createdAtMs = 1_723_000_000_000,
        ),
        XpEventRecord(
            eventKey = "observation:robin",
            type = XpEventType.CONFIRMED_OBSERVATION,
            observationUuid = "robin",
            subjectTaxonId = 42,
            label = "European robin",
            points = 10,
            createdAtMs = 1_722_999_000_000,
        ),
    )
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(
                account = com.wildlife.feasibility.VerifiedAccount(42, "naturalist", 1),
                totalXp = 510,
                progression = ProgressionProjection.project(510, events),
            ),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            bottomBar = {},
        )
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFF080B09,
    widthDp = 390,
    heightDp = 1_000,
    fontScale = 1.8f,
)
@Composable
private fun ProfileProgressionLargeTextPreview() {
    WildlifeTheme {
        ProfileScreen(
            state = ShellUiState(
                account = com.wildlife.feasibility.VerifiedAccount(42, "naturalist", 1),
                totalXp = 2_550,
                progression = ProgressionProjection.project(2_550),
            ),
            onManageAccount = {},
            onOpenPublicProfile = {},
            onSelectProgressionTitle = {},
            bottomBar = {},
        )
    }
}
