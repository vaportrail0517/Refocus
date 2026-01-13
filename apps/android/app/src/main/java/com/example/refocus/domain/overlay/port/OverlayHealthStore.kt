package com.example.refocus.domain.overlay.port

import com.example.refocus.core.model.OverlayHealthSnapshot

/**
 * オーバーレイ監視サービスの稼働健全性を永続化するためのストア．
 *
 * 実装は DataStore など Android 依存で良いが，domain からは本インタフェース越しに扱う．
 */
interface OverlayHealthStore {
    /** 保存されているスナップショットを読み出す．未書き込みの場合はデフォルト値を返す． */
    suspend fun read(): OverlayHealthSnapshot

    /** スナップショットを丸ごと書き換える．null フィールドは削除扱いとする． */
    suspend fun write(snapshot: OverlayHealthSnapshot)

    /**
     * 現在値を読み出してから変換し，書き戻す．
     *
     * DataStore 実装では 1 回の transaction として扱えるため，並行更新による取りこぼしを抑えられる．
     */
    suspend fun update(transform: (OverlayHealthSnapshot) -> OverlayHealthSnapshot)

    /** 全フィールドを初期化する． */
    suspend fun clear()
}
