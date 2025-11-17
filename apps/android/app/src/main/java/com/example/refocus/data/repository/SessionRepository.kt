package com.example.refocus.data.repository

import com.example.refocus.core.model.Session
import kotlinx.coroutines.flow.Flow
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.entity.SessionEntity
import kotlinx.coroutines.flow.map
import com.example.refocus.data.db.dao.SessionPauseResumeDao
import com.example.refocus.data.db.entity.SessionPauseResumeEntity

interface SessionRepository {

    /**
     * 対象アプリの新しいセッションを開始する。
     * 既に active session があればそのまま返す（2重開始防止）。
     */
    suspend fun startSession(packageName: String, startedAtMillis: Long): Session

    /**
     * 対象アプリのアクティブセッションを終了する。
     * アクティブセッションがなければ何もしない。
     */
    suspend fun endActiveSession(packageName: String, endedAtMillis: Long)

    /**
     * アクティブなセッションに「中断イベント」を追加する。
     * 対象アプリの active session がなければ何もしない。
     */
    suspend fun recordPause(packageName: String, pausedAtMillis: Long)

    /**
     * アクティブなセッションの「最後の未解決の中断イベント」に再開時刻を埋める。
     * active session がない / 未解決イベントがない場合は何もしない。
     */
    suspend fun recordResume(packageName: String, resumedAtMillis: Long)

    /**
     * 全セッションを新しい順で購読（履歴画面用）。
     */
    fun observeAllSessions(): Flow<List<Session>>

    /**
     * バグやクラッシュなどで壊れたセッションを一括修復する。
     * アプリ起動時や OverlayService 起動時に一度呼び出す想定。
     */
    suspend fun repairStaleSessions(nowMillis: Long = System.currentTimeMillis())
}

class SessionRepositoryImpl(
    private val sessionDao: SessionDao,
    private val pauseResumeDao: SessionPauseResumeDao
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
        // すでにアクティブセッションがあるなら、それをそのまま使う
        val existing = sessionDao.findActiveSession(packageName)
        if (existing != null) {
            return existing.toDomain()
        }

        val entity = SessionEntity(
            packageName = packageName,
            startedAtMillis = startedAtMillis,
            endedAtMillis = null
        )
        val newId = sessionDao.insertSession(entity)
        return entity.copy(id = newId).toDomain()
    }

    override suspend fun endActiveSession(
        packageName: String,
        endedAtMillis: Long
    ) {
        val active = sessionDao.findActiveSession(packageName) ?: return
        if (active.endedAtMillis != null) return // 既に終わっていたら何もしない

        val updated = active.copy(endedAtMillis = endedAtMillis)
        sessionDao.updateSession(updated)
    }

    override fun observeAllSessions(): Flow<List<Session>> {
        return sessionDao.observeAllSessions()
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun repairStaleSessions(nowMillis: Long) {
        // 1. endedAtMillis が null のセッション（「永遠に続いている active」）
        val activeSessions = sessionDao.findAllActiveSessions()

        // パッケージごとにグルーピング
        val activeByPackage = activeSessions.groupBy { it.packageName }

        val sessionsToFix = mutableListOf<SessionEntity>()

        activeByPackage.forEach { (pkg, sessions) ->
            // startedAt が新しい順にソート
            val sorted = sessions.sortedByDescending { it.startedAtMillis }
            val latest = sorted.firstOrNull()
            val older = sorted.drop(1)

            // (1-1) 同一パッケージで複数 active がいたら、古いものはすべて強制終了
            older.forEach { orphan ->
                val end = computeRepairedEndMillis(
                    startedAtMillis = orphan.startedAtMillis,
                    nowMillis = nowMillis
                )
                sessionsToFix += orphan.copy(endedAtMillis = end)
            }

            // (1-2) 最新の 1 件も「明らかに長すぎる」なら強制終了
            if (latest != null) {
                val age = nowMillis - latest.startedAtMillis
                if (age > STALE_ACTIVE_THRESHOLD_MS) {
                    val end = computeRepairedEndMillis(
                        startedAtMillis = latest.startedAtMillis,
                        nowMillis = nowMillis
                    )
                    sessionsToFix += latest.copy(endedAtMillis = end)
                }
            }
        }

        // 2. endedAtMillis < startedAtMillis になってしまっている壊れたセッション
        val broken = sessionDao.findBrokenSessions()
        broken.forEach { brokenEntity ->
            // とりあえず「継続時間 0」として丸める（started == ended）
            val fixed = brokenEntity.copy(endedAtMillis = brokenEntity.startedAtMillis)
            sessionsToFix += fixed
        }

        if (sessionsToFix.isNotEmpty()) {
            sessionDao.updateSessions(sessionsToFix)
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

    /**
     * デグレ修復用に「妥当そうな endedAtMillis」を計算するヘルパー。
     */
    private fun computeRepairedEndMillis(
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
        endedAtMillis = this.endedAtMillis
    )
}