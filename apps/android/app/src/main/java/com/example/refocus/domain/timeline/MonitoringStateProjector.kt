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

/**
 * 「監視可能（monitoringEnabled）／監視中（monitoringActive）」の判定を，
 * セッション投影と統計投影で共有するための単一ロジック．
 *
 * - monitoringEnabled: サービス稼働 && 必須権限が Revoked でない（= 未記録は OK 扱い）
 * - monitoringActive : monitoringEnabled && 画面 ON
 */
object MonitoringStateProjector {
    private val requiredPermissions =
        listOf(
            PermissionKind.UsageStats,
            PermissionKind.Overlay,
        )

    /**
     * SessionProjector と同じ「監視可能」判定．
     *
     * 重要: PermissionEvent がまだ記録されていない権限は「OK（Revoked ではない）」として扱う．
     * これにより，権限イベント記録の欠落で統計が 0 になるドリフトを防ぐ．
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

    /** Stats 側で使う「監視中」判定（= 監視可能 かつ 画面ON）． */
    fun isMonitoringActive(
        serviceRunning: Boolean,
        screenOn: Boolean,
        permissionStates: Map<PermissionKind, PermissionState>,
    ): Boolean = screenOn && isMonitoringEnabled(serviceRunning, permissionStates)

    private data class State(
        var serviceRunning: Boolean = false,
        var screenOn: Boolean = false,
        val permissionStates: MutableMap<PermissionKind, PermissionState> = mutableMapOf(),
    ) {
        fun apply(event: TimelineEvent) {
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

        fun isActive(): Boolean = isMonitoringActive(serviceRunning, screenOn, permissionStates)
    }

    /**
     * 1日分のイベント列から MonitoringPeriod を再構成する．
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

        // 未来日など，終端が開始より前に来るケースは空にする
        val effectiveEnd = minOf(nowMillis, endOfDayExclusive)
        if (effectiveEnd <= startOfDay) return emptyList()

        val sorted = events.sortedBy { it.timestampMillis }

        val state = State()

        // 1) 日付開始より前のイベントで「初期状態」を復元
        for (event in sorted) {
            if (event.timestampMillis >= startOfDay) break
            state.apply(event)
        }

        val periods = mutableListOf<MonitoringPeriod>()

        // 2) 日付開始時点ですでに監視中なら startOfDay から開始
        var active = state.isActive()
        var currentStart: Long? = if (active) startOfDay else null

        // 3) 当日イベントで ON/OFF を切り替える
        for (event in sorted) {
            val ts = event.timestampMillis
            if (ts < startOfDay) continue
            if (ts >= endOfDayExclusive) break

            val wasActive = active
            state.apply(event)
            val newActive = state.isActive()

            if (!wasActive && newActive) {
                // OFF → ON
                currentStart = maxOf(ts, startOfDay)
            } else if (wasActive && !newActive) {
                // ON → OFF
                val start = currentStart ?: startOfDay
                val end = ts
                if (end > start) {
                    periods += MonitoringPeriod(startMillis = start, endMillis = end)
                }
                currentStart = null
            }
            active = newActive
        }

        // 4) 当日終端（または now）まで監視が続く場合は閉じる
        if (active) {
            val start = currentStart ?: startOfDay
            val end = effectiveEnd
            if (end > start) {
                periods += MonitoringPeriod(startMillis = start, endMillis = end)
            }
        }

        return periods
    }
}
