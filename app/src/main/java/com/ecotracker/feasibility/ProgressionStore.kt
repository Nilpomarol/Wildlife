package com.wildlife.feasibility

import android.content.Context

class ProgressionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun selectedLevelKey(userId: Long): String? =
        preferences.getString("$KEY_SELECTED_LEVEL_PREFIX$userId", null)

    fun selectLevel(userId: Long, levelKey: String) {
        preferences.edit().putString("$KEY_SELECTED_LEVEL_PREFIX$userId", levelKey).apply()
    }

    companion object {
        private const val PREFERENCES = "wildlife_progression"
        private const val KEY_SELECTED_LEVEL_PREFIX = "selected_level_"
    }
}
