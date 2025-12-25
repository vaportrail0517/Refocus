package com.example.refocus.data.repository

import com.example.refocus.data.datastore.OnboardingDataStore
import com.example.refocus.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow

class OnboardingRepositoryImpl(
    private val dataStore: OnboardingDataStore,
) : OnboardingRepository {
    override val completedFlow: Flow<Boolean> = dataStore.completedFlow

    override suspend fun setCompleted(completed: Boolean) {
        dataStore.setCompleted(completed)
    }
}
