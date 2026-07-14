package com.hemant.myapplication.location

import android.content.Context

/** Records the one first-launch permission prompt requested by the product flow. */
class LocationPermissionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun wasInitialPromptShown(): Boolean = preferences.getBoolean(KEY_INITIAL_PROMPT_SHOWN, false)

    fun markInitialPromptShown() {
        preferences.edit().putBoolean(KEY_INITIAL_PROMPT_SHOWN, true).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "location_permission"
        const val KEY_INITIAL_PROMPT_SHOWN = "initial_prompt_shown"
    }
}
