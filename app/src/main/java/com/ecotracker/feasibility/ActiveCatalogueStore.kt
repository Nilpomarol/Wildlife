package com.wildlife.feasibility

import android.content.Context

/** Holds only a user choice; location may suggest a region but never changes it automatically. */
class ActiveCatalogueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun selectedRegionKey(): String = preferences.getString(KEY_SELECTED_REGION, null)
        ?: CatalogueStore.CATALONIA

    fun selectRegion(regionKey: String) {
        require(regionKey.isNotBlank()) { "A regional catalogue key is required." }
        preferences.edit().putString(KEY_SELECTED_REGION, regionKey).apply()
    }

    fun clear() = preferences.edit().clear().commit()

    companion object {
        private const val PREFERENCES = "active_catalogue"
        private const val KEY_SELECTED_REGION = "selected_region_key"
    }
}
