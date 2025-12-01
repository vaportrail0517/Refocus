package com.example.refocus.data.repository

import android.content.Context
import com.example.refocus.data.datastore.OnboardingDataStore
import kotlinx.coroutines.flow.Flow

class OnboardingRepository(
    context: Context
) {
    private val dataStore = OnboardingDataStore(context)

    val completedFlow: Flow<Boolean> = dataStore.completedFlow

    suspend fun setCompleted(completed: Boolean = true) {
        dataStore.setCompleted(completed)
    }
}
