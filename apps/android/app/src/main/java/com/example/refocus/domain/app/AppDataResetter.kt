package com.example.refocus.domain.app

import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.repository.OnboardingRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.domain.settings.SettingsCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataResetter @Inject constructor(
    private val database: RefocusDatabase,
    private val settingsCommand: SettingsCommand,
    private val targetsRepository: TargetsRepository,
    private val onboardingRepository: OnboardingRepository,
) {
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        // リセット時はタイムラインも初期化されるため，イベント記録は行わない
        settingsCommand.resetToDefaults(source = "app_reset", recordEvent = false)
        targetsRepository.clearForReset()
        onboardingRepository.setCompleted(false)
    }
}
