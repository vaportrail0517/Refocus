package com.example.refocus.app.reset

import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.settings.SettingsCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端末内の永続データを全消去するユースケース。
 *
 * data 層（Room/Datastore）の具体を触るため，domain ではなく app 層に置く。
 */
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
