package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.refocus.data.db.entity.SessionPauseResumeEntity

@Dao
interface SessionPauseResumeDao {
    @Insert
    suspend fun insert(event: SessionPauseResumeEntity): Long

    @Update
    suspend fun update(event: SessionPauseResumeEntity)

    @Query(
        """
        SELECT * FROM session_pause_resume_events
        WHERE sessionId = :sessionId
        ORDER BY pausedAtMillis ASC
        """
    )
    suspend fun findBySessionId(sessionId: Long): List<SessionPauseResumeEntity>

    @Query(
        """
        SELECT * FROM session_pause_resume_events
        WHERE sessionId IN (:sessionIds)
        ORDER BY pausedAtMillis ASC
        """
    )
    suspend fun findBySessionIds(sessionIds: List<Long>): List<SessionPauseResumeEntity>

    @Query(
        """
        SELECT * FROM session_pause_resume_events
        WHERE sessionId = :sessionId AND resumedAtMillis IS NULL
        ORDER BY pausedAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun findLastUnresolvedPause(sessionId: Long): SessionPauseResumeEntity?
}
