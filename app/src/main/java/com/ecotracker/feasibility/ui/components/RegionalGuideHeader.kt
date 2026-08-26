package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.CurrentRegionSource
import com.wildlife.feasibility.InstalledRegionalAchievement
import com.wildlife.feasibility.InstalledRegionalCatalogue
import com.wildlife.feasibility.ProgressionProjection
import com.wildlife.feasibility.ProgressionState
import com.wildlife.feasibility.ui.art.FieldMark
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/** Regional identity, progress, rarity tallies and standing quests for Explore's guide. */
@Composable
fun RegionalGuideHeader(
    collected: Int,
    total: Int,
    commonCount: Int,
    uncommonCount: Int,
    rareCount: Int,
    veryRareCount: Int,
    xp: Int,
    progression: ProgressionState?,
    selectedCatalogue: InstalledRegionalCatalogue?,
    currentRegionSource: CurrentRegionSource,
    achievements: List<InstalledRegionalAchievement>,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    val levels = progression ?: ProgressionProjection.project(xp)
    val title = levels.selectedTitle
    val completion = if (total == 0) 0f else (collected.toFloat() / total).coerceIn(0f, 1f)

    Column(modifier.fillMaxWidth()) {
        RangerHeader(
            regionName = when (currentRegionSource) {
                CurrentRegionSource.LAST_KNOWN_FIX -> selectedCatalogue?.displayName?.let { "Last known · $it" }
                CurrentRegionSource.CURRENT_FIX -> selectedCatalogue?.displayName
                CurrentRegionSource.UNAVAILABLE -> null
            } ?: "Regional guide",
            regionKey = selectedCatalogue?.regionKey,
            levelKey = title.key,
            levelName = title.displayName,
            progressLabel = "$collected OF $total IN THIS REGION",
            progressTrailing = "${(completion * 100).toInt()}%",
            progressFraction = completion,
            stats = listOf(
                RangerStat(commonCount.toString(), "COMMON", WildlifeTheme.colors.rarityCommon, mark = StatMark.DOT),
                RangerStat(uncommonCount.toString(), "UNCOMMON", WildlifeTheme.colors.rarityUncommon, fieldMark = "rarity_uncommon"),
                RangerStat(rareCount.toString(), "RARE", WildlifeTheme.colors.rarityRare, fieldMark = "rarity_rare"),
                RangerStat(veryRareCount.toString(), "VERY RARE", WildlifeTheme.colors.rarityVeryRare, fieldMark = "rarity_very_rare"),
            ),
            showBackgroundScrim = true,
            showBottomEdge = true,
            modifier = Modifier.bleedHorizontally(WildlifeSpacing.Screen),
            belowStats = achievements.takeIf { it.isNotEmpty() }?.let {
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
                    ) {
                        achievements.forEach { achievement ->
                            RegionalQuestBadge(
                                achievement = achievement,
                                observedTaxa = observedTaxa,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun RegionalQuestBadge(
    achievement: InstalledRegionalAchievement,
    observedTaxa: Set<Long>,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val isIcons = achievement.label == "icons"
    val title = if (isIcons) "Icons" else "Essentials"
    val accent = if (isIcons) colors.icon else colors.essential
    val markName = if (isIcons) "regional_icon" else "regional_essential"
    val done = achievement.taxonIds.count { it in observedTaxa }
    val goal = achievement.taxonIds.size
    val complete = goal > 0 && done >= goal
    val fraction = if (goal == 0) 0f else done.toFloat() / goal

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (complete) 1.5.dp else 1.dp,
                color = if (complete) accent else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FieldMark(markName, accent, Modifier.size(21.dp))
            Text(
                text = title.uppercase(),
                style = FieldLabelStyle,
                color = colors.parchmentDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = done.toString(),
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    color = if (complete) accent else colors.parchment,
                )
                Text(
                    text = "/$goal",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.parchmentFaint,
                    modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        JournalProgressBar(fraction = fraction, height = 4.dp, accent = accent)
    }
}
