package com.example.refocus.domain.app

import com.example.refocus.data.db.RefocusDatabase
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
) {

    /**
     * アプリの「ユーザーデータ」を全消去し、設定を工場出荷状態に戻す。
     *
     * - Room テーブル（sessions / session_events / suggestions）を全削除
     * - Customize を SettingsDefaults に戻す（preset = Default）
     * - 監視対象アプリ一覧を空にする
     * - オンボーディング完了フラグを false に戻す
     */
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        // 1) Room DB を全削除
        database.clearAllTables()

        // 2) 設定をデフォルトへ
        settingsRepository.resetToDefaults()

        // 3) 対象アプリ（ターゲット）を空に
        targetsRepository.setTargets(emptySet())
    }
}
