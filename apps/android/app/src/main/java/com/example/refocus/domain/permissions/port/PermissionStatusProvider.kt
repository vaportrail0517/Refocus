package com.example.refocus.domain.permissions.port

import com.example.refocus.core.model.PermissionSnapshot

/**
 * UI などから「現在の権限状態」を取得するための domain 側の窓口．
 *
 * 実装は Android API を使うため system 層に置き，feature はこのインタフェースに依存する．
 */
interface PermissionStatusProvider {
    /**
     * 現在の権限状態を同期的にチェックして返す．
     *
     * UI の初期表示で「とりあえず今どう見えるか」を出す用途．
     * タイムライン記録や DataStore 更新は行わない（suspend を避けるため）．
     */
    fun readCurrentInstant(): PermissionSnapshot

    /**
     * 現在の権限状態をチェックし，差分があればタイムラインへ記録する．
     */
    suspend fun refreshAndRecord(): PermissionSnapshot
}
