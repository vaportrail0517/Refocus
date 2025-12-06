package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.refocus.data.db.entity.MonitoringPeriodEntity

@Dao
interface MonitoringPeriodDao {

    @Insert
    suspend fun insert(period: MonitoringPeriodEntity): Long

    @Query(
        """
        UPDATE monitoring_periods
        SET endMillis = :endMillis
        WHERE id = :id AND endMillis IS NULL
    """
    )
    suspend fun closePeriod(id: Long, endMillis: Long)

    /**
     * [startMillis, endMillis) の範囲にかすっている監視期間を取得。
     */
    @Query(
        """
        SELECT * FROM monitoring_periods
        WHERE endMillis IS NULL
          OR (endMillis >= :startMillis AND startMillis <= :endMillis)
    """
    )
    suspend fun getPeriodsOverlapping(
        startMillis: Long,
        endMillis: Long,
    ): List<MonitoringPeriodEntity>
}
