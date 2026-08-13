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
    const val VERSION = "progression-0.1-placeholder"
    const val CONFIRMED_OBSERVATION_XP = 10
    const val FIRST_SPECIES_XP = 500

    const val RESEARCH_GRADE_XP = 50
    const val RESEARCH_GRADE_ENABLED = false
    const val IDENTIFICATION_GIVEN_XP = 25
    const val IDENTIFICATION_GIVEN_ENABLED = false
    const val ANOMALY_CONFIRMED_XP = 250
    const val ANOMALY_CONFIRMED_ENABLED = false
    const val RARITY_MULTIPLIERS_ENABLED = false
    const val BADGES_ENABLED = false
    const val STREAKS_ENABLED = false

    val repeatObservationXp = listOf(10, 5, 5)

    val levels = listOf(
        ProgressionLevel("tourist", "Tourist", 0),
        ProgressionLevel("explorer", "Explorer", 500),
        ProgressionLevel("naturalist", "Naturalist", 2_500),
        ProgressionLevel("tracker", "Tracker", 7_500),
        ProgressionLevel("field_ranger", "Field Ranger", 20_000),
        ProgressionLevel("master_ranger", "Master Ranger", 50_000),
        ProgressionLevel("legendary_ranger", "Legendary Ranger", 100_000),
    )

    fun confirmedObservationXp(previousSameSpeciesThisWeek: Int): Int =
        repeatObservationXp.getOrElse(previousSameSpeciesThisWeek.coerceAtLeast(0)) { 0 }
}

object ProgressionProjection {
    fun project(
        totalXp: Int,
        events: List<XpEventRecord> = emptyList(),
        recentLimit: Int = 5,
        selectedLevelKey: String? = null,
    ): ProgressionState {
        val safeXp = totalXp.coerceAtLeast(0)
        val currentIndex = ProgressionRules.levels.indexOfLast { safeXp >= it.thresholdXp }
            .coerceAtLeast(0)
        val current = ProgressionRules.levels[currentIndex]
        val next = ProgressionRules.levels.getOrNull(currentIndex + 1)
        val earnedLevels = ProgressionRules.levels.take(currentIndex + 1)
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
