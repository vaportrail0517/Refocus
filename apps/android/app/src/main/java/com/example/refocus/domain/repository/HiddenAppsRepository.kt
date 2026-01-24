package com.example.refocus.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 対象アプリ選択画面における「候補から除外するアプリ」集合（packageName の Set）の永続化と購読の抽象．
 *
 * Phase4 から，必要に応じてタイムラインイベントとしても記録できる．
 */
interface HiddenAppsRepository {
    fun observeHiddenApps(): Flow<Set<String>>

    suspend fun setHiddenApps(
        hiddenApps: Set<String>,
        recordEvent: Boolean = false,
    )

    suspend fun clearForReset()
}
