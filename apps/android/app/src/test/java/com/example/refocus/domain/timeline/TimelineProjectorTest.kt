package com.example.refocus.domain.timeline

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.TargetAppsChangedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimelineProjectorTest {
    @Test
    fun project_splitsSessionPartsAcrossMidnight() {
        val zone = ZoneId.of("UTC")
        val t0 = Instant.parse("2025-01-01T23:00:00Z").toEpochMilli()
        val tStart = Instant.parse("2025-01-01T23:50:00Z").toEpochMilli()
        val tPause = Instant.parse("2025-01-02T00:20:00Z").toEpochMilli()

        val pkg = "com.example.a"

        val events =
            listOf(
                TargetAppsChangedEvent(
                    timestampMillis = t0,
                    targetPackages = setOf(pkg),
                ),
                ServiceLifecycleEvent(
                    timestampMillis = t0,
                    state = ServiceState.Started,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.UsageStats,
                    state = PermissionState.Granted,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.Overlay,
                    state = PermissionState.Granted,
                ),
                ScreenEvent(
                    timestampMillis = t0,
                    state = ScreenState.On,
                ),
                ForegroundAppEvent(
                    timestampMillis = tStart,
                    packageName = pkg,
                ),
                ForegroundAppEvent(
                    timestampMillis = tPause,
                    packageName = null,
                ),
            )

        val projection =
            TimelineProjector.project(
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

        val events =
            listOf(
                TargetAppsChangedEvent(
                    timestampMillis = t0,
                    targetPackages = setOf(pkg),
                ),
                ServiceLifecycleEvent(
                    timestampMillis = t0,
                    state = ServiceState.Started,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.UsageStats,
                    state = PermissionState.Granted,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.Overlay,
                    state = PermissionState.Granted,
                ),
                ScreenEvent(
                    timestampMillis = t0,
                    state = ScreenState.On,
                ),
                ForegroundAppEvent(
                    timestampMillis = tStart,
                    packageName = pkg,
                ),
                ForegroundAppEvent(
                    timestampMillis = tPause,
                    packageName = null,
                ),
                ForegroundAppEvent(
                    timestampMillis = tResume,
                    packageName = pkg,
                ),
            )

        val projection =
            TimelineProjector.project(
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

    @Test
    fun project_interpretationChangesWhenGracePeriodChanges() {
        val zone = ZoneId.of("UTC")
        val t0 = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
        val tStart = Instant.parse("2025-01-01T10:01:00Z").toEpochMilli()
        val tPause = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
        val tResume = Instant.parse("2025-01-01T10:14:30Z").toEpochMilli() // gap 4m30s
        val now = Instant.parse("2025-01-01T10:20:00Z").toEpochMilli()

        val pkg = "com.example.a"

        val events =
            listOf(
                TargetAppsChangedEvent(
                    timestampMillis = t0,
                    targetPackages = setOf(pkg),
                ),
                ServiceLifecycleEvent(
                    timestampMillis = t0,
                    state = ServiceState.Started,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.UsageStats,
                    state = PermissionState.Granted,
                ),
                PermissionEvent(
                    timestampMillis = t0,
                    permission = PermissionKind.Overlay,
                    state = PermissionState.Granted,
                ),
                ScreenEvent(
                    timestampMillis = t0,
                    state = ScreenState.On,
                ),
                ForegroundAppEvent(
                    timestampMillis = tStart,
                    packageName = pkg,
                ),
                ForegroundAppEvent(
                    timestampMillis = tPause,
                    packageName = null,
                ),
                ForegroundAppEvent(
                    timestampMillis = tResume,
                    packageName = pkg,
                ),
            )

        // grace = 5min -> same session (Pause/Resume)
        val projectionGrace5m =
            TimelineProjector.project(
                events = events,
                config = TimelineInterpretationConfig(stopGracePeriodMillis = 5 * 60 * 1_000L),
                nowMillis = now,
                zoneId = zone,
            )
        val sessions5m = projectionGrace5m.sessionsWithEvents.filter { it.session.packageName == pkg }
        assertEquals(1, sessions5m.size)
        val types5m = sessions5m.single().events.map { it.type }
        assertTrue(types5m.contains(com.example.refocus.core.model.SessionEventType.Pause))
        assertTrue(types5m.contains(com.example.refocus.core.model.SessionEventType.Resume))

        // grace = 3min -> split into two sessions
        val projectionGrace3m =
            TimelineProjector.project(
                events = events,
                config = TimelineInterpretationConfig(stopGracePeriodMillis = 3 * 60 * 1_000L),
                nowMillis = now,
                zoneId = zone,
            )
        val sessions3m = projectionGrace3m.sessionsWithEvents.filter { it.session.packageName == pkg }
        assertEquals(2, sessions3m.size)

        // first session should be ended at tPause (inactiveAt) because grace was exceeded by tResume
        val first = sessions3m.first()
        val firstTypes = first.events.map { it.type }
        assertTrue(firstTypes.contains(com.example.refocus.core.model.SessionEventType.Start))
        assertTrue(firstTypes.contains(com.example.refocus.core.model.SessionEventType.Pause))
        assertTrue(firstTypes.contains(com.example.refocus.core.model.SessionEventType.End))

        // second session should start at tResume (new session)
        val second = sessions3m.last()
        val secondTypes = second.events.map { it.type }
        assertTrue(secondTypes.contains(com.example.refocus.core.model.SessionEventType.Start))
    }
}
