package com.example.refocus.data.db.dao

import androidx.room.*
import com.example.refocus.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    /**
     * endedAtMillis が null の「進行中セッション」を1件だけ取得。
     * 通常は packageName ごとに1件までに制御する想定。
     */
    @Query("SELECT * FROM sessions WHERE packageName = :packageName AND endedAtMillis IS NULL LIMIT 1")
    suspend fun findActiveSession(packageName: String): SessionEntity?

    /**
     * 最近終了したセッション（将来、猶予時間判定に使うかもしれない）。
     */
    @Query("""
        SELECT * FROM sessions 
        WHERE packageName = :packageName AND endedAtMillis IS NOT NULL
        ORDER BY endedAtMillis DESC
        LIMIT 1
    """)
    suspend fun findLastFinishedSession(packageName: String): SessionEntity?

    /**
     * 履歴表示用：新しい順に全セッション。
     */
    @Query("SELECT * FROM sessions ORDER BY startedAtMillis DESC")
    fun observeAllSessions(): Flow<List<SessionEntity>>
}
