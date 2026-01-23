package com.example.refocus.data.repository

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.mapper.TimelineEventEntityMapper
import com.example.refocus.domain.repository.TimelineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

class TimelineRepositoryImpl(
    private val dao: TimelineEventDao,
) : TimelineRepository {
    private val mapper = TimelineEventEntityMapper()
    private val seedEventsLoader = SeedEventsLoader(dao, mapper)

    override suspend fun append(event: TimelineEvent): Long = dao.insert(mapper.toEntity(event))

    override suspend fun getEvents(
        startMillis: Long,
        endMillis: Long,
    ): List<TimelineEvent> =
        dao
            .getEventsBetween(startMillis, endMillis)
            .mapNotNull { mapper.toDomain(it) }

    override suspend fun getEventsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<TimelineEvent> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end =
            date
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        return getEvents(start, end)
    }

    override fun observeEventsBetween(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TimelineEvent>> =
        dao
            .observeEventsBetween(startMillis, endMillis)
            .map { list -> list.mapNotNull { mapper.toDomain(it) } }

    override suspend fun getSeedEventsBefore(beforeMillis: Long): List<TimelineEvent> = seedEventsLoader.load(beforeMillis)

    @Deprecated("全件購読は性能劣化の原因になるため，observeEventsBetween を使う")
    override fun observeEvents(): Flow<List<TimelineEvent>> =
        dao
            .observeAllEvents()
            .map { list -> list.mapNotNull { mapper.toDomain(it) } }
}
