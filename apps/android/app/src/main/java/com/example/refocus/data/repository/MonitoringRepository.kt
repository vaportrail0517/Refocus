package com.example.refocus.data.repository

import com.example.refocus.core.model.MonitoringPeriod
import com.example.refocus.data.db.dao.MonitoringPeriodDao
import com.example.refocus.data.db.entity.MonitoringPeriodEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId


interface MonitoringRepository {

    /**
     * 監視期間を開始し、内部で ID を管理する。
     */
    suspend fun startMonitoring(nowMillis: Long)

    /**
     * 現在開いている監視期間を終了する。
     * 既に終了している場合は何もしない。
     */
    suspend fun stopMonitoring(nowMillis: Long)

    /**
     * 指定した日付にかすっている監視期間を取得する。
     */
    suspend fun getMonitoringPeriodsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<MonitoringPeriod>
}


class MonitoringRepositoryImpl(
    private val dao: MonitoringPeriodDao,
) : MonitoringRepository {

    // シンプルにプロセス内で ID を持っておく（プロセス死のリカバリは start 側で対処）
    private val lock = Mutex()
    private var currentId: Long? = null

    override suspend fun startMonitoring(nowMillis: Long) {
        lock.withLock {
            // 既に開いているレコードがあれば一旦閉じる（クラッシュなどのリカバリ）
            currentId?.let { id ->
                dao.closePeriod(id, nowMillis)
            }
            val id = dao.insert(
                MonitoringPeriodEntity(
                    startMillis = nowMillis,
                    endMillis = null,
                )
            )
            currentId = id
        }
    }

    override suspend fun stopMonitoring(nowMillis: Long) {
        lock.withLock {
            val id = currentId ?: return
            dao.closePeriod(id, nowMillis)
            currentId = null
        }
    }

    override suspend fun getMonitoringPeriodsForDate(
        date: LocalDate,
        zoneId: ZoneId,
    ): List<MonitoringPeriod> {
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return dao.getPeriodsOverlapping(startOfDay, endOfDay).map { entity ->
            MonitoringPeriod(
                startMillis = entity.startMillis,
                endMillis = entity.endMillis,
            )
        }
    }
}
