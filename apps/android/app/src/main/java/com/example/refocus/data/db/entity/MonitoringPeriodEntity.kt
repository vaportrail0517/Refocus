package com.example.refocus.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_periods")
data class MonitoringPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long?, // null = 監視継続中
)
