package com.example.refocus.data.repository

import android.util.Log
import com.example.refocus.core.model.Session
import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.dao.SessionEventDao
import com.example.refocus.data.db.entity.SessionEntity
import com.example.refocus.data.db.entity.SessionEventEntity
import com.example.refocus.core.util.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

data class SessionsWithEvents(
    val sessions: List<Session>,
    val eventsBySessionId: Map<Long, List<SessionEvent>>,
)
interface SessionRepository {
    suspend fun startSession(packageName: String, startedAtMillis: Long): Session
    suspend fun endActiveSession(
        packageName: String,
        endedAtMillis: Long,
    )
    suspend fun recordPause(packageName: String, pausedAtMillis: Long)
    suspend fun recordResume(packageName: String, resumedAtMillis: Long)
    /**
     * セッションID → イベント列（時刻昇順）を返す。
     */
    suspend fun getEvents(sessionId: Long): List<SessionEvent>
    /**
     * 複数セッション ID → イベント列のマップ。
     */
    suspend fun getEventsForSessions(
        sessionIds: List<Long>
    ): Map<Long, List<SessionEvent>>
    /**
     * DB 上の全セッションを観測。
     * 開始／終了時刻や duration は、必要に応じてイベント列から計算する。
     */
    fun observeAllSessions(): Flow<List<Session>>
    // セッションとイベントをまとめて監視
    fun observeAllSessionsWithEvents(): Flow<SessionsWithEvents>
    /**
     * 指定パッケージの「アクティブな」セッションがあれば、
     * 現在時刻までの有効経過時間(ミリ秒)を返す。
     */
    suspend fun getActiveSessionDuration(
        packageName: String,
        nowMillis: Long,
    ): Long?
    /**
     * 指定パッケージの「アクティブな」セッションがあれば、
     * その最後のイベント種別（Start / Pause / Resume / End など）を返す。
     * アクティブセッションがなければ null。
     */
    suspend fun getLastEventTypeForActiveSession(
        packageName: String
    ): SessionEventType?
    /**
     * 端末再起動後などに呼び出し、
     * 猶予時間を超えて中断したままのセッションを終了させる。
     */
    suspend fun repairActiveSessionsAfterRestart(
        gracePeriodMillis: Long,
        nowMillis: Long,
    )
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val eventDao: SessionEventDao,
    private val timeSource: TimeSource,
) : SessionRepository {
    companion object {
        private const val TAG = "SessionRepository"
    }
    // region public API
    override suspend fun startSession(
        packageName: String,
        startedAtMillis: Long
    ): Session {
        // 既に active なセッションがあればそれを返す
        findActiveSessionByPackage(packageName)?.let { return it }
        val entity = SessionEntity(
            packageName = packageName,
        )
        val id = sessionDao.insertSession(entity)
        eventDao.insert(
            SessionEventEntity(
                sessionId = id,
                type = SessionEventType.Start.name,
                timestampMillis = startedAtMillis
            )
        )
        return Session(id = id, packageName = packageName)
    }
    override suspend fun endActiveSession(
        packageName: String,
        endedAtMillis: Long,
    ) {
        val active = findActiveSessionByPackage(packageName) ?: return
        val sessionId = active.id ?: return
        val last = eventDao.findLastEvent(sessionId)
        if (last != null && last.type == SessionEventType.End.name) {
            // すでに終了済み
            return
        }
        if (last != null &&
            last.type == SessionEventType.Pause.name &&
            last.timestampMillis == endedAtMillis
        ) {
            // ★ ケース1: 直前に Pause を打っていて、
            //  その Pause と同じ時刻で「終了」とみなしたい場合
            //  → 新しい End を挿入せず、Pause を End に変える
            eventDao.updateEventType(
                eventId = last.id,
                newType = SessionEventType.End.name
            )
            return
        }
        // ★ それ以外のケース：普通に End イベントを追加
        //   （必要なら timestamp の単調増加もここで保証する）
        val base = last?.timestampMillis ?: endedAtMillis
        val adjustedEndedAt = maxOf(endedAtMillis, base + 1)
        eventDao.insert(
            SessionEventEntity(
                sessionId = sessionId,
                type = SessionEventType.End.name,
                timestampMillis = adjustedEndedAt
            )
        )
    }

    override suspend fun recordPause(packageName: String, pausedAtMillis: Long) {
        val active = findActiveSessionByPackage(packageName) ?: return
        eventDao.insert(
            SessionEventEntity(
                sessionId = active.id!!,
                type = SessionEventType.Pause.name,
                timestampMillis = pausedAtMillis
            )
        )
    }
    override suspend fun recordResume(packageName: String, resumedAtMillis: Long) {
        val active = findActiveSessionByPackage(packageName) ?: return
        eventDao.insert(
            SessionEventEntity(
                sessionId = active.id!!,
                type = SessionEventType.Resume.name,
                timestampMillis = resumedAtMillis
            )
        )
    }
    override suspend fun getEvents(sessionId: Long): List<SessionEvent> {
        return eventDao.findBySessionId(sessionId).map { it.toDomain() }
    }
    override suspend fun getEventsForSessions(
        sessionIds: List<Long>
    ): Map<Long, List<SessionEvent>> {
        if (sessionIds.isEmpty()) return emptyMap()
        val entities = eventDao.findBySessionIds(sessionIds)
        return entities
            .groupBy { it.sessionId }
            .mapValues { (_, list) -> list.sortedBy { it.timestampMillis }.map { it.toDomain() } }
    }
    override fun observeAllSessions(): Flow<List<Session>> {
        return sessionDao.observeAllSessions()
            .map { list -> list.map { it.toDomain() } }
    }
    override fun observeAllSessionsWithEvents(): Flow<SessionsWithEvents> {
        // セッションテーブルとイベントテーブルを両方 Flow で監視し、常に最新の組み合わせを返す
        return combine(
            sessionDao.observeAllSessions(),
            eventDao.observeAllEvents()
        ) { sessionEntities, eventEntities ->
            val sessions = sessionEntities.map { it.toDomain() }
            // sessionId ごとにイベントをまとめてドメインモデルに変換
            val eventsBySessionId: Map<Long, List<SessionEvent>> =
                eventEntities
                    .groupBy { it.sessionId }
                    .mapValues { (_, list) ->
                        list.sortedBy { it.timestampMillis }
                            .map { it.toDomain() }
                    }
            SessionsWithEvents(
                sessions = sessions,
                eventsBySessionId = eventsBySessionId
            )
        }
    }
    override suspend fun getActiveSessionDuration(
        packageName: String,
        nowMillis: Long,
    ): Long? {
        val active = findActiveSessionByPackage(packageName) ?: return null
        val events = eventDao.findBySessionId(active.id!!)
        if (events.isEmpty()) return null
        return calculateDurationFromEvents(events, nowMillis)
    }
    override suspend fun getLastEventTypeForActiveSession(
        packageName: String
    ): SessionEventType? {
        val active = findActiveSessionByPackage(packageName) ?: return null
        val last = eventDao.findLastEvent(active.id!!) ?: return null
        return SessionEventType.valueOf(last.type)
    }
    override suspend fun repairActiveSessionsAfterRestart(
        gracePeriodMillis: Long,
        nowMillis: Long,
    ) {
        val allSessions = sessionDao.findAll()
        for (entity in allSessions) {
            val events = eventDao.findBySessionId(entity.id)
            if (events.isEmpty()) continue
            val last = events.maxBy { it.timestampMillis }
            val lastType = SessionEventType.valueOf(last.type)
            if (lastType == SessionEventType.End) continue
            val gap = nowMillis - last.timestampMillis
            if (gap <= gracePeriodMillis) {
                // 猶予内 → まだ継続中とみなす
                continue
            }
            when (lastType) {
                SessionEventType.Pause -> {
                    // ★ Pause のまま放置 → その Pause を End に書き換える
                    eventDao.updateEventType(
                        eventId = last.id,
                        newType = SessionEventType.End.name
                    )
                }
                SessionEventType.Start,
                SessionEventType.Resume -> {
                    // ★ Start/Resume のまま放置 → その時刻で End を新規追加
                    eventDao.insert(
                        SessionEventEntity(
                            sessionId = entity.id,
                            type = SessionEventType.End.name,
                            timestampMillis = last.timestampMillis + 1 // 単調増加のため +1
                        )
                    )
                }
                else -> {}
            }
        }
    }


    // endregion

    // region private helpers
    /**
     * 指定パッケージの active セッションを 1 件探す。
     * - 「最後のイベントが End でない」ものを active とみなす。
     */
    private suspend fun findActiveSessionByPackage(
        packageName: String
    ): Session? {
        val sessions = sessionDao.findByPackageName(packageName)
        for (entity in sessions) {
            val last = eventDao.findLastEvent(entity.id) ?: continue
            val type = SessionEventType.valueOf(last.type)
            if (type != SessionEventType.End) {
                return entity.toDomain()
            }
        }
        return null
    }
    /**
     * Start / Pause / Resume / End のイベント列から、
     * 「実際にアプリを使っていた時間」の合計を求める。
     */
    private fun calculateDurationFromEvents(
        events: List<SessionEventEntity>,
        nowMillis: Long,
    ): Long {
        if (events.isEmpty()) return 0L
        val sorted = events.sortedBy { it.timestampMillis }
        var lastStart: Long? = null
        var totalActive = 0L
        for (e in sorted) {
            val type = SessionEventType.valueOf(e.type)
            when (type) {
                SessionEventType.Start -> {
                    lastStart = e.timestampMillis
                }
                SessionEventType.Pause -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }
                SessionEventType.Resume -> {
                    if (lastStart == null) {
                        lastStart = e.timestampMillis
                    }
                }
                SessionEventType.End -> {
                    if (lastStart != null) {
                        totalActive += (e.timestampMillis - lastStart!!).coerceAtLeast(0L)
                        lastStart = null
                    }
                }
            }
        }
        // まだ Start されたまま（End が来ていない）場合は now まで加算
        if (lastStart != null) {
            totalActive += (nowMillis - lastStart!!).coerceAtLeast(0L)
        }
        return totalActive.coerceAtLeast(0L)
    }
    // --- Entity <-> Domain 変換 ---
    private fun SessionEntity.toDomain(): Session =
        Session(
            id = this.id,
            packageName = this.packageName,
        )
    private fun SessionEventEntity.toDomain(): SessionEvent =
        SessionEvent(
            id = this.id,
            sessionId = this.sessionId,
            type = SessionEventType.valueOf(this.type),
            timestampMillis = this.timestampMillis,
        )
    // endregion
}
