package com.wildlife.feasibility

data class ProgressionLevel(
    val key: String,
    val displayName: String,
    val thresholdXp: Int,
)

enum class XpEventType(val key: String) {
    CONFIRMED_OBSERVATION("confirmed_observation"),
    FIRST_SPECIES("first_species"),
    RESEARCH_GRADE("research_grade"),
    REGIONAL_DISCOVERY("regional_discovery"),
    REGIONAL_RARITY("regional_rarity"),
    REGIONAL_LEGEND("regional_legend"),
    REGIONAL_ESSENTIALS("regional_essentials"),
    REGIONAL_ICONS("regional_icons"),
    IDENTIFICATION_GIVEN("identification_given"),
    ANOMALY_CONFIRMED("anomaly_confirmed"),
    LEGACY("legacy"),
    ;

    companion object {
        fun fromKey(key: String): XpEventType = entries.firstOrNull { it.key == key } ?: LEGACY
    }
}

data class XpEventRecord(
    val eventKey: String,
    val type: XpEventType,
    val observationUuid: String?,
    val subjectTaxonId: Long?,
    val label: String?,
    val points: Int,
    val createdAtMs: Long,
)

data class ProgressionState(
    val rulesVersion: String,
    val totalXp: Int,
    val currentLevel: ProgressionLevel,
    val selectedTitle: ProgressionLevel,
    val nextLevel: ProgressionLevel?,
    val progressFraction: Float,
    val xpToNextLevel: Int?,
    val earnedLevels: List<ProgressionLevel>,
    val recentEvents: List<XpEventRecord>,
)

object ProgressionRules {
    val VERSION get() = GeneratedProgressionConfig.VERSION
    val CONFIRMED_OBSERVATION_XP get() = GeneratedProgressionConfig.confirmedObservationXp
    val FIRST_SPECIES_XP get() = GeneratedProgressionConfig.firstSpeciesXp

    val RESEARCH_GRADE_XP get() = GeneratedProgressionConfig.researchGradeXp
    val RESEARCH_GRADE_ENABLED get() = GeneratedProgressionConfig.researchGradeEnabled
    val REGIONAL_DISCOVERY_XP get() = GeneratedProgressionConfig.regionalDiscoveryXp
    val REGIONAL_LEGEND_XP get() = GeneratedProgressionConfig.regionalLegendXp
    val REGIONAL_ESSENTIALS_XP get() = GeneratedProgressionConfig.regionalEssentialsXp
    val REGIONAL_ICONS_XP get() = GeneratedProgressionConfig.regionalIconsXp
    fun regionalRarityXp(rarity: EncounterRarity): Int = GeneratedProgressionConfig.rarityXp[rarity] ?: 0
    val IDENTIFICATION_GIVEN_XP get() = GeneratedProgressionConfig.identificationGivenXp
    val IDENTIFICATION_GIVEN_ENABLED get() = GeneratedProgressionConfig.identificationGivenEnabled
    val ANOMALY_CONFIRMED_XP get() = GeneratedProgressionConfig.anomalyConfirmedXp
    val ANOMALY_CONFIRMED_ENABLED get() = GeneratedProgressionConfig.anomalyConfirmedEnabled
    const val RARITY_MULTIPLIERS_ENABLED = false
    const val BADGES_ENABLED = false
    const val STREAKS_ENABLED = false

    val repeatObservationXp get() = GeneratedProgressionConfig.repeatObservationXp

    val levels get() = GeneratedProgressionConfig.levels

    fun confirmedObservationXp(previousSameSpeciesThisWeek: Int): Int =
        repeatObservationXp.getOrElse(previousSameSpeciesThisWeek.coerceAtLeast(0)) { 0 }
}

object ProgressionProjection {
    fun project(
        totalXp: Int,
        events: List<XpEventRecord> = emptyList(),
        recentLimit: Int = 5,
        selectedLevelKey: String? = null,
        highestLevelKey: String? = null,
    ): ProgressionState {
        val safeXp = totalXp.coerceAtLeast(0)
        val currentIndex = ProgressionRules.levels.indexOfLast { safeXp >= it.thresholdXp }
            .coerceAtLeast(0)
        val current = ProgressionRules.levels[currentIndex]
        val next = ProgressionRules.levels.getOrNull(currentIndex + 1)
        val highestIndex = HighestLevelProjection.highestIndex(
            currentIndex = currentIndex,
            previouslyReachedKey = highestLevelKey,
            levels = ProgressionRules.levels,
        )
        val earnedLevels = ProgressionRules.levels.take(highestIndex + 1)
        val selectedTitle = earnedLevels.firstOrNull { it.key == selectedLevelKey } ?: current
        val progress = if (next == null) {
            1f
        } else {
            val span = next.thresholdXp - current.thresholdXp
            ((safeXp - current.thresholdXp).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
        return ProgressionState(
            rulesVersion = ProgressionRules.VERSION,
            totalXp = safeXp,
            currentLevel = current,
            selectedTitle = selectedTitle,
            nextLevel = next,
            progressFraction = progress,
            xpToNextLevel = next?.let { (it.thresholdXp - safeXp).coerceAtLeast(0) },
            earnedLevels = earnedLevels,
            recentEvents = events
                .asSequence()
                .filter { it.points > 0 }
                .sortedByDescending(XpEventRecord::createdAtMs)
                .take(recentLimit.coerceAtLeast(0))
                .toList(),
        )
    }
}

internal object HighestLevelProjection {
    fun highestLevelKey(
        currentLevelKey: String,
        previouslyReachedKey: String?,
        levels: List<ProgressionLevel>,
    ): String = levels[highestIndex(
        currentIndex = levels.indexOfFirst { it.key == currentLevelKey }.coerceAtLeast(0),
        previouslyReachedKey = previouslyReachedKey,
        levels = levels,
    )].key

    fun highestIndex(
        currentIndex: Int,
        previouslyReachedKey: String?,
        levels: List<ProgressionLevel>,
    ): Int = maxOf(currentIndex, levels.indexOfFirst { it.key == previouslyReachedKey }.coerceAtLeast(0))
}
