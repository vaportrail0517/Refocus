package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.refocus.data.db.entity.SessionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionEventDao {

    @Insert
    suspend fun insert(event: SessionEventEntity): Long

    @Query(
        """
        SELECT * FROM session_events
        WHERE sessionId = :sessionId
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun findBySessionId(sessionId: Long): List<SessionEventEntity>

    @Query(
        """
        SELECT * FROM session_events
        WHERE sessionId IN (:sessionIds)
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun findBySessionIds(sessionIds: List<Long>): List<SessionEventEntity>

    @Query(
        """
        SELECT * FROM session_events
        WHERE sessionId = :sessionId
        ORDER BY timestampMillis DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun findLastEvent(sessionId: Long): SessionEventEntity?

    @Query(
        """
        UPDATE session_events
        SET type = :newType
        WHERE id = :eventId
        """
    )
    suspend fun updateEventType(
        eventId: Long,
        newType: String
    )

    @Query(
        """
        SELECT * FROM session_events
        """
    )
    fun observeAllEvents(): Flow<List<SessionEventEntity>>
}
