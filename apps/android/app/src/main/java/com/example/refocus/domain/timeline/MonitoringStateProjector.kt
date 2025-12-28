package com.example.refocus.domain.timeline

import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.TimelineEvent
import java.time.LocalDate
import java.time.ZoneId

/*** 「監視可能(= monitoringEnabled) / 監視中(= monitoringActive)」の判定を Stats/Session で共有するための単一ロジック。** - monitoringEnabled: サービス稼働 && 必須権限が Revoked でない（= 未記録は OK 扱い）* - monitoringActive : monitoringEnabled && 画面 ON*/
object MonitoringStateProjector {
    private val requiredPermissions =
        listOf(
            PermissionKind.UsageStats,
            PermissionKind.Overlay,
        )

    /**
     * SessionProjector と同じ「監視可能」判定。
     *
     * 重要: PermissionEvent がまだ記録されていない権限は「OK（Revoked ではない）」として扱う。
     * これにより、権限イベント記録の欠落で統計が 0 になるドリフトを防ぐ。
     */
    fun isMonitoringEnabled(
        serviceRunning: Boolean,
        permissionStates: Map<PermissionKind, PermissionState>,
    ): Boolean {
        val permsOk =
            requiredPermissions.all { kind ->
                permissionStates[kind] != PermissionState.Revoked
            }
        return serviceRunning && permsOk
    }

    /**
     * Stats 側で使う「監視中」判定（= 監視可能 かつ 画面ON）。
     */
    fun isMonitoringActive(
        serviceRunning: Boolean,
        screenOn: Boolean,
        permissionStates: Map<PermissionKind, PermissionState>,
    ): Boolean = screenOn && isMonitoringEnabled(serviceRunning, permissionStates)

    /**
     * 1日分のイベント列から MonitoringPeriod を再構成する（DefaultStatsUseCase から移設）。
     *
     * - 日付開始より前のイベントも適用して「開始時点の状態」を復元する
     * - 期間終端は「その日の終端 or nowMillis の早い方」で必ず閉じる（endMillis を null にしない）
     */
    fun buildMonitoringPeriodsForDate(
        date: LocalDate,
        zoneId: ZoneId,
        events: List<TimelineEvent>,
        nowMillis: Long,
    ): List<MonitoringPeriod> {
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDayExclusive =
            date
                .plusDays(1)
                .atStartOfDay(zoneId)
                .toInstant()
                .toEpochMilli()
        var serviceRunning = false
        var screenOn = false
        val permissionStates = mutableMapOf<PermissionKind, PermissionState>()

        fun applyStateChange(event: TimelineEvent) {
            when (event) {
                is ServiceLifecycleEvent -> {
                    serviceRunning = (event.state == ServiceState.Started)
                }

                is ScreenEvent -> {
                    screenOn = (event.state == ScreenState.On)
                }

                is PermissionEvent -> {
                    permissionStates[event.permission] = event.state
                }

                else -> Unit
            }
        }

        val periods = mutableListOf<MonitoringPeriod>()
        // 1) 日付開始より前のイベントで「初期状態」を復元
        val sorted = events.sortedBy { it.timestampMillis }
        for (event in sorted) {
            if (event.timestampMillis >= startOfDay) break
            applyStateChange(event)
        }
        // 2) 日付開始時点ですでに監視中なら startOfDay から開始
        var monitoring = isMonitoringActive(serviceRunning, screenOn, permissionStates)
        var currentStart: Long? = if (monitoring) startOfDay else null
        // 3) 当日イベントで ON/OFF を切り替える
        for (event in sorted) {
            if (event.timestampMillis < startOfDay) continue
            if (event.timestampMillis >= endOfDayExclusive) break
            val wasMonitoring = monitoring
            applyStateChange(event)
            val newMonitoring = isMonitoringActive(serviceRunning, screenOn, permissionStates)
            if (!wasMonitoring && newMonitoring) {
                // OFF → ON
                currentStart = maxOf(event.timestampMillis, startOfDay)
            } else if (wasMonitoring && !newMonitoring) {
                // ON → OFF
                val start = currentStart ?: startOfDay
                val end = event.timestampMillis
                if (end > start) {
                    periods +=
                        MonitoringPeriod(
                            startMillis = start,
                            endMillis = end,
                        )
                }
                currentStart = null
            }
            monitoring = newMonitoring
        }
        // 4) 当日終端（または now）まで監視が続く場合は閉じる
        if (monitoring) {
            val start = currentStart ?: startOfDay
            val end = minOf(nowMillis, endOfDayExclusive)
            if (end > start) {
                periods += MonitoringPeriod(startMillis = start, endMillis = end)
            }
        }
        return periods
    }
}
