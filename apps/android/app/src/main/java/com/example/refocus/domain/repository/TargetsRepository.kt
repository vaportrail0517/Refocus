package com.example.refocus.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * 対象アプリ集合（packageName の Set）の永続化と購読の抽象。
 */
interface TargetsRepository {
    fun observeTargets(): Flow<Set<String>>

    suspend fun setTargets(targets: Set<String>, recordEvent: Boolean = true)

    suspend fun clearForReset()
}
