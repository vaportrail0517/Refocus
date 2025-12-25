package com.example.refocus.domain.timeline

import com.example.refocus.core.model.TimelineEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineProjectorTest {

    @Test
    fun project_splitsSessionPartsAcrossMidnight() {
        val zone = ZoneId.of("UTC")
        val t0 = Instant.parse("2025-01-01T23:00:00Z").toEpochMilli()
        val tStart = Instant.parse("2025-01-01T23:50:00Z").toEpochMilli()
        val tPause = Instant.parse("2025-01-02T00:20:00Z").toEpochMilli()

        val pkg = "com.example.a"

        val events = listOf(
            TimelineEvent.TargetAppsChangedEvent(
                timestampMillis = t0,
                targetPackageNames = setOf(pkg),
            ),
            TimelineEvent.ServiceLifecycleEvent(
                timestampMillis = t0,
                isStarted = true,
            ),
            TimelineEvent.PermissionEvent(
                timestampMillis = t0,
                permissionType = TimelineEvent.PermissionEvent.PermissionType.UsageStats,
                isGranted = true,
            ),
            TimelineEvent.PermissionEvent(
                timestampMillis = t0,
                permissionType = TimelineEvent.PermissionEvent.PermissionType.Overlay,
                isGranted = true,
            ),
            TimelineEvent.ScreenEvent(
                timestampMillis = t0,
                isScreenOn = true,
            ),
            TimelineEvent.ForegroundAppEvent(
                timestampMillis = tStart,
                packageName = pkg,
            ),
            TimelineEvent.ForegroundAppEvent(
                timestampMillis = tPause,
                packageName = null,
            ),
        )

        val projection = TimelineProjector.project(
            events = events,
            config = TimelineInterpretationConfig(stopGracePeriodMillis = 5 * 60 * 1_000L),
            nowMillis = tPause,
            zoneId = zone,
        )

        val parts = projection.sessionParts.filter { it.packageName == pkg }
        assertEquals(2, parts.size)

        val partDay1 = parts.first { it.date == LocalDate.of(2025, 1, 1) }
        val partDay2 = parts.first { it.date == LocalDate.of(2025, 1, 2) }

        assertEquals(10 * 60 * 1_000L, partDay1.durationMillis)
        assertEquals(20 * 60 * 1_000L, partDay2.durationMillis)
    }

    @Test
    fun project_resumesWithinGracePeriodAsSameSession() {
        val zone = ZoneId.of("UTC")
        val t0 = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
        val tStart = Instant.parse("2025-01-01T10:01:00Z").toEpochMilli()
        val tPause = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
        val tResume = Instant.parse("2025-01-01T10:11:00Z").toEpochMilli()
        val now = tResume

        val pkg = "com.example.a"

        val events = listOf(
            TimelineEvent.TargetAppsChangedEvent(
                timestampMillis = t0,
                targetPackageNames = setOf(pkg),
            ),
            TimelineEvent.ServiceLifecycleEvent(
                timestampMillis = t0,
                isStarted = true,
            ),
            TimelineEvent.PermissionEvent(
                timestampMillis = t0,
                permissionType = TimelineEvent.PermissionEvent.PermissionType.UsageStats,
                isGranted = true,
            ),
            TimelineEvent.PermissionEvent(
                timestampMillis = t0,
                permissionType = TimelineEvent.PermissionEvent.PermissionType.Overlay,
                isGranted = true,
            ),
            TimelineEvent.ScreenEvent(
                timestampMillis = t0,
                isScreenOn = true,
            ),
            TimelineEvent.ForegroundAppEvent(
                timestampMillis = tStart,
                packageName = pkg,
            ),
            TimelineEvent.ForegroundAppEvent(
                timestampMillis = tPause,
                packageName = null,
            ),
            TimelineEvent.ForegroundAppEvent(
                timestampMillis = tResume,
                packageName = pkg,
            ),
        )

        val projection = TimelineProjector.project(
            events = events,
            config = TimelineInterpretationConfig(stopGracePeriodMillis = 5 * 60 * 1_000L),
            nowMillis = now,
            zoneId = zone,
        )

        val sessions = projection.sessionsWithEvents.filter { it.session.packageName == pkg }
        assertEquals(1, sessions.size)

        val eventTypes = sessions.single().events.map { it.type }
        assertTrue(eventTypes.contains(com.example.refocus.core.model.SessionEventType.Start))
        assertTrue(eventTypes.contains(com.example.refocus.core.model.SessionEventType.Pause))
        assertTrue(eventTypes.contains(com.example.refocus.core.model.SessionEventType.Resume))
        assertFalse(eventTypes.contains(com.example.refocus.core.model.SessionEventType.End))
    }
}
