package com.example.refocus.data.repository

import com.example.refocus.core.model.Session
import kotlinx.coroutines.flow.Flow
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.map
import com.example.refocus.data.db.dao.SessionPauseResumeDao
import com.example.refocus.data.db.entity.SessionPauseResumeEntity
import com.example.refocus.core.model.SessionPauseResume
import com.example.refocus.core.util.TimeSource

interface SessionRepository {
    suspend fun startSession(packageName: String, startedAtMillis: Long): Session
    suspend fun endActiveSession(
        packageName: String,
        endedAtMillis: Long,
        durationMillis: Long? = null,
    )
    suspend fun recordPause(packageName: String, pausedAtMillis: Long)
    suspend fun recordResume(packageName: String, resumedAtMillis: Long)
    suspend fun getPauseResumeEvents(sessionId: Long): List<SessionPauseResume>
    suspend fun getPauseResumeEventsForSessions(
        sessionIds: List<Long>
    ): Map<Long, List<SessionPauseResume>>
    fun observeAllSessions(): Flow<List<Session>>
    suspend fun getLastFinishedSession(packageName: String): Session?
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val pauseResumeDao: SessionPauseResumeDao,
    private val timeSource: TimeSource,
) : SessionRepository {

    override suspend fun startSession(
        packageName: String,
        startedAtMillis: Long
    ): Session {
        val active = sessionDao.findActiveSession(packageName)
        if (active != null) {
            return active.toDomain()
        }
        val entity = SessionEntity(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            endedAtMillis = null,
            durationMillis = null,
        )
        val id = sessionDao.insertSession(entity)
        val inserted = entity.copy(id = id)
        return inserted.toDomain()
    }

    override suspend fun endActiveSession(
        packageName: String,
        endedAtMillis: Long,
        durationMillis: Long?
    ) {
        val active = sessionDao.findActiveSession(packageName) ?: return
        if (active.endedAtMillis != null) return
        // まず終了時刻を確定（将来 degrade 修復ロジックを噛ませるならここで）
        val fixedEnd = endedAtMillis.coerceAtLeast(active.startedAtMillis)
        // 1. OverlayService から durationMillis が渡されていればそれを優先
        // 2. 渡されていなければ、中断/再開イベントから計算
        val effectiveDuration = durationMillis
            ?: calculateDurationFromTimestamps(
                session = active.copy(endedAtMillis = fixedEnd),
                nowMillis = fixedEnd
            )
        val updated = active.copy(
            endedAtMillis = fixedEnd,
            durationMillis = effectiveDuration,
        )
        sessionDao.updateSession(updated)
    }

    override fun observeAllSessions(): Flow<List<Session>> {
        return sessionDao.observeAllSessions()
            .map { list ->
                list.map { it.toDomain() }
            }
    }

    override suspend fun getLastFinishedSession(packageName: String): Session? {
        val entity = sessionDao.findLastFinishedSession(packageName) ?: return null
        return entity.toDomain()
    }

    override suspend fun recordPause(packageName: String, pausedAtMillis: Long) {
        // 対象アプリの active session を取得
        val active = sessionDao.findActiveSession(packageName) ?: return
        // このセッションに新しい pause イベントを追加
        val event = SessionPauseResumeEntity(
            sessionId = active.id,
            pausedAtMillis = pausedAtMillis,
            resumedAtMillis = null
        )
        pauseResumeDao.insert(event)
    }

    override suspend fun recordResume(packageName: String, resumedAtMillis: Long) {
        // active session がなければ何もしない
        val active = sessionDao.findActiveSession(packageName) ?: return
        // このセッションの「最後の未解決の pause イベント」を取得
        val lastPause = pauseResumeDao.findLastUnresolvedPause(active.id) ?: return
        // 再開時刻を埋める
        val updated = lastPause.copy(resumedAtMillis = resumedAtMillis)
        pauseResumeDao.update(updated)
    }

    override suspend fun getPauseResumeEvents(sessionId: Long): List<SessionPauseResume> {
        val entities = pauseResumeDao.findBySessionId(sessionId)
        return entities.map { it.toDomain() }
    }

    override suspend fun getPauseResumeEventsForSessions(
        sessionIds: List<Long>
    ): Map<Long, List<SessionPauseResume>> {
        if (sessionIds.isEmpty()) return emptyMap()
        val entities = pauseResumeDao.findBySessionIds(sessionIds)
        return entities
            .map { it.toDomain() }
            .groupBy { it.sessionId }
    }

    private fun SessionPauseResumeEntity.toDomain(): SessionPauseResume =
        SessionPauseResume(
            id = this.id,
            sessionId = this.sessionId,
            pausedAtMillis = this.pausedAtMillis,
            resumedAtMillis = this.resumedAtMillis,
        )

    private suspend fun calculateDurationFromTimestamps(
        session: SessionEntity,
        nowMillis: Long = timeSource.nowMillis(),
    ): Long {
        val start = session.startedAtMillis
        val end = (session.endedAtMillis ?: nowMillis).coerceAtLeast(start)
        val base = (end - start).coerceAtLeast(0L)
        val events = pauseResumeDao.findBySessionId(session.id)
        val pausedTotal = events.fold(0L) { acc, ev ->
            val pauseStart = ev.pausedAtMillis
            val pauseEnd = (ev.resumedAtMillis ?: end).coerceAtLeast(pauseStart)
            val paused = (pauseEnd - pauseStart).coerceAtLeast(0L)
            acc + paused
        }
        return (base - pausedTotal).coerceAtLeast(0L)
    }

    // --- Entity <-> Domain 変換 ---
    private fun SessionEntity.toDomain(): Session = Session(
        id = this.id,
        packageName = this.packageName,
        startedAtMillis = this.startedAtMillis,
        endedAtMillis = this.endedAtMillis,
        durationMillis = this.durationMillis,
    )
}
