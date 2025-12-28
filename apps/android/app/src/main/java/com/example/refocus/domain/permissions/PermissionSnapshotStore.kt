package com.example.refocus.domain.permissions

import com.example.refocus.core.model.PermissionSnapshot

/**
 * 権限状態の差分検知のための，直近スナップショットを保持するストア．
 *
 * 実装は DataStore など Android 依存で良いが，system/domain からは本インタフェース越しに扱う．
 */
interface PermissionSnapshotStore {
    suspend fun readOrNull(): PermissionSnapshot?

    suspend fun write(snapshot: PermissionSnapshot)
}
