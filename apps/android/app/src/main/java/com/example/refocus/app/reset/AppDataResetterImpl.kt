package com.example.refocus.app.reset

import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.reset.port.AppDataResetter
import com.example.refocus.domain.settings.SettingsCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 端末内の永続データを全消去するユースケースの実装。
 *
 * - feature 層は [AppDataResetter]（domain の抽象）に依存する
 * - 具体実装は data の具体（Room/Datastore）を触るため app 層に置く
 */
@Singleton
class AppDataResetterImpl
    @Inject
    constructor(
        private val database: RefocusDatabase,
        private val settingsCommand: SettingsCommand,
        private val targetsRepository: TargetsRepository,
        private val onboardingRepository: OnboardingRepository,
    ) : AppDataResetter {
        override suspend fun resetAll() =
            withContext(Dispatchers.IO) {
                database.clearAllTables()
                // リセット時はタイムラインも初期化されるため，イベント記録は行わない
                settingsCommand.resetToDefaults(source = "app_reset", recordEvent = false)
                targetsRepository.clearForReset()
                onboardingRepository.setCompleted(false)
            }
    }
