package com.example.refocus.domain.timeline

import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.testutil.UtcTimeZoneRule
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MonitoringStateProjectorTest {

    @get:Rule
    val tzRule = UtcTimeZoneRule()

    @Test
    fun `isMonitoringEnabled treats missing permission events as ok`() {
        assertTrue(MonitoringStateProjector.isMonitoringEnabled(serviceRunning = true, permissionStates = emptyMap()))
        assertFalse(
            MonitoringStateProjector.isMonitoringEnabled(
                serviceRunning = true,
                permissionStates = mapOf(PermissionKind.UsageStats to PermissionState.Revoked),
            )
        )
        assertFalse(MonitoringStateProjector.isMonitoringEnabled(serviceRunning = false, permissionStates = emptyMap()))
    }

    @Test
    fun `buildMonitoringPeriodsForDate builds one open period until now`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2025, 1, 1)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val events: List<TimelineEvent> = listOf(
            ServiceLifecycleEvent(timestampMillis = dayStart + 100, state = ServiceState.Started),
            ScreenEvent(timestampMillis = dayStart + 200, state = ScreenState.On),
        )

        val nowMillis = dayStart + 1000
        val periods = MonitoringStateProjector.buildMonitoringPeriodsForDate(
            date = date,
            zoneId = zone,
            events = events,
            nowMillis = nowMillis,
        )

        assertEquals(1, periods.size)
        assertEquals(dayStart + 200, periods[0].startMillis)
        assertEquals(nowMillis, periods[0].endMillis)
    }

    @Test
    fun `buildMonitoringPeriodsForDate closes on permission revoke`() {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2025, 1, 1)
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()

        val events: List<TimelineEvent> = listOf(
            ServiceLifecycleEvent(timestampMillis = dayStart + 100, state = ServiceState.Started),
            ScreenEvent(timestampMillis = dayStart + 200, state = ScreenState.On),
            PermissionEvent(
                timestampMillis = dayStart + 500,
                permission = PermissionKind.UsageStats,
                state = PermissionState.Revoked,
            ),
        )

        val nowMillis = dayStart + 1000
        val periods = MonitoringStateProjector.buildMonitoringPeriodsForDate(
            date = date,
            zoneId = zone,
            events = events,
            nowMillis = nowMillis,
        )

        assertEquals(1, periods.size)
        assertEquals(dayStart + 200, periods[0].startMillis)
        assertEquals(dayStart + 500, periods[0].endMillis)
    }
}
