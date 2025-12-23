package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.refocus.data.db.entity.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {

    @Insert
    suspend fun insert(event: TimelineEventEntity): Long

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE timestampMillis >= :startMillis
          AND timestampMillis < :endMillis
        ORDER BY timestampMillis ASC
        """
    )
    suspend fun getEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEventEntity>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE timestampMillis >= :startMillis
          AND timestampMillis < :endMillis
        ORDER BY timestampMillis ASC
        """
    )
    fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEventEntity>>

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE kind = :kind
          AND timestampMillis < :beforeMillis
        ORDER BY timestampMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatestEventOfKindBefore(
        kind: String,
        beforeMillis: Long,
    ): TimelineEventEntity?

    @Query(
        """
        SELECT * FROM timeline_events
        WHERE kind = :kind
          AND timestampMillis < :beforeMillis
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestEventsOfKindBefore(
        kind: String,
        beforeMillis: Long,
        limit: Int,
    ): List<TimelineEventEntity>

    @Query(
        """
        SELECT * FROM timeline_events
        ORDER BY timestampMillis ASC
        """
    )
    fun observeAllEvents(): Flow<List<TimelineEventEntity>>
}
