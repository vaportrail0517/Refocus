package com.example.refocus.data.repository

import com.example.refocus.core.model.Session
import kotlinx.coroutines.flow.Flow
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.map
import com.example.refocus.data.db.dao.SessionPauseResumeDao
import com.example.refocus.data.db.entity.SessionPauseResumeEntity
import com.example.refocus.core.model.SessionPauseResume

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

    fun observeAllSessions(): Flow<List<Session>>
    suspend fun getLastFinishedSession(packageName: String): Session?

    suspend fun repairStaleSessions(nowMillis: Long = System.currentTimeMillis())
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val pauseResumeDao: SessionPauseResumeDao,
) : SessionRepository {

    companion object {
        /**
         * 「明らかにおかしい」と判断して強制終了させるアクティブセッションのしきい値。
         * ここでは 10 分より古いものを「異常」とみなす。
         *
         * - 猶予時間は 30 秒なので、本来なら数十秒以内に endedAt が付くはず
         * - アプリ起動時 / OverlayService 起動時に呼ぶ想定なので、
         *   10 分以上続いている endedAt=null は「サービスクラッシュなどの残骸」とみなしてよい
         */
        private const val STALE_ACTIVE_THRESHOLD_MS: Long = 10 * 60 * 1000L // 10分

        /**
         * デグレ修復で付ける最大の「仮想継続時間」。
         * 例えば 5 時間ぶっ通しで使っていたかもしれないが、それはもう復元できないので、
         * とりあえず最大 1 時間ぶんだけを上限として補正する。
         */
        private const val MAX_REPAIR_DURATION_MS: Long = 60 * 60 * 1000L // 1時間
    }

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

    override suspend fun repairStaleSessions(nowMillis: Long) {
        val activeSessions = sessionDao.findAllActiveSessions()
        if (activeSessions.isEmpty()) return
        for (session in activeSessions) {
            // ここは元のロジックに合わせて修復済みの終了時刻を決める
            val repairedEnd = calculateRepairedEndMillis(
                startedAtMillis = session.startedAtMillis,
                nowMillis = nowMillis
            )
            val duration = calculateDurationFromTimestamps(
                session = session.copy(endedAtMillis = repairedEnd),
                nowMillis = repairedEnd
            )
            val updated = session.copy(
                endedAtMillis = repairedEnd,
                durationMillis = duration
            )
            sessionDao.updateSession(updated)
        }
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
    private fun SessionPauseResumeEntity.toDomain(): SessionPauseResume =
        SessionPauseResume(
            id = this.id,
            sessionId = this.sessionId,
            pausedAtMillis = this.pausedAtMillis,
            resumedAtMillis = this.resumedAtMillis,
        )

    private suspend fun calculateDurationFromTimestamps(
        session: SessionEntity,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val start = session.startedAtMillis
        val end = (session.endedAtMillis ?: nowMillis).coerceAtLeast(start)
        // 素の差分（壁時計ベース）
        val base = (end - start).coerceAtLeast(0L)
        // このセッションに紐づく中断イベントを取得
        val events = pauseResumeDao.findBySessionId(session.id)
        // 中断区間の合計
        val pausedTotal = events.fold(0L) { acc, ev ->
            val pauseStart = ev.pausedAtMillis
            val pauseEnd = (ev.resumedAtMillis ?: end).coerceAtLeast(pauseStart)
            val paused = (pauseEnd - pauseStart).coerceAtLeast(0L)
            acc + paused
        }
        // 有効時間 = 全体 − 中断
        return (base - pausedTotal).coerceAtLeast(0L)
    }

    /**
     * デグレ修復用に「妥当そうな endedAtMillis」を計算するヘルパー。
     */
    private fun calculateRepairedEndMillis(
        startedAtMillis: Long,
        nowMillis: Long
    ): Long {
        val rawDuration = nowMillis - startedAtMillis
        // マイナスは念のため 0 に丸める
        val clampedDuration = rawDuration.coerceIn(0L, MAX_REPAIR_DURATION_MS)
        // startedAt + 継続時間（上限付き）
        return (startedAtMillis + clampedDuration)
            .coerceAtMost(nowMillis)
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