package com.example.refocus.domain.app

import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.repository.OnboardingRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.TargetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataResetter @Inject constructor(
    private val database: RefocusDatabase,
    private val settingsRepository: SettingsRepository,
    private val targetsRepository: TargetsRepository,
    private val onboardingRepository: OnboardingRepository,
) {
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        settingsRepository.resetToDefaults()
        targetsRepository.clearForReset()
        onboardingRepository.setCompleted(false)
    }
}
