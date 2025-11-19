package com.example.refocus.feature.onboarding

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit

object OnboardingState {
    private const val PREFS_NAME = "refocus_onboarding"
    private const val KEY_COMPLETED = "completed"

    fun isCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_COMPLETED, false)
    }

    fun setCompleted(context: Context, completed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_COMPLETED, completed)
        }
    }
}
