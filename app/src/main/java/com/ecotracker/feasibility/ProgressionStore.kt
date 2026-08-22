package com.wildlife.feasibility

import android.content.Context

class ProgressionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selectedLevelKey(userId: Long): String? =
        preferences.getString("$KEY_SELECTED_LEVEL_PREFIX$userId", null)

    fun highestLevelKey(userId: Long): String? =
        preferences.getString("$KEY_HIGHEST_LEVEL_PREFIX$userId", null)

    fun selectLevel(userId: Long, levelKey: String) {
        preferences.edit().putString("$KEY_SELECTED_LEVEL_PREFIX$userId", levelKey).apply()
    }

    /** Persists title entitlement only; lifetime XP remains the ledger sum in ObservationStore. */
    fun recordHighestLevel(userId: Long, currentLevelKey: String): String {
        val highest = HighestLevelProjection.highestLevelKey(
            currentLevelKey = currentLevelKey,
            previouslyReachedKey = highestLevelKey(userId),
            levels = ProgressionRules.levels,
        )
        if (highest != highestLevelKey(userId)) {
            preferences.edit().putString("$KEY_HIGHEST_LEVEL_PREFIX$userId", highest).apply()
        }
        return highest
    }

    fun restore(userId: Long, selectedLevelKey: String?, highestLevelKey: String?) {
        preferences.edit()
            .putString("$KEY_SELECTED_LEVEL_PREFIX$userId", selectedLevelKey)
            .putString("$KEY_HIGHEST_LEVEL_PREFIX$userId", highestLevelKey)
            .apply()
    }

    fun clear(): Boolean = preferences.edit().clear().commit()

    companion object {
        internal const val PREFERENCES = "wildlife_progression"
        private const val KEY_SELECTED_LEVEL_PREFIX = "selected_level_"
        private const val KEY_HIGHEST_LEVEL_PREFIX = "highest_level_"
    }
}
