package com.example.refocus.domain.timeline

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.SessionEventType
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimelineEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionProjectorTest {

    @Test
    fun `stops after grace and closes at inactive timestamp`() {
        val grace = 100L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            ForegroundAppEvent(timestampMillis = 20L, packageName = "B"),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 200L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()
        assertEquals("A", session.session.packageName)

        val types = session.events.map { it.type }
        assertEquals(listOf(SessionEventType.Start, SessionEventType.Pause, SessionEventType.End), types)

        val timestamps = session.events.map { it.timestampMillis }
        assertEquals(listOf(10L, 20L, 20L), timestamps)
    }

    @Test
    fun `returns to target within grace resumes without ending`() {
        val grace = 100L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            ForegroundAppEvent(timestampMillis = 20L, packageName = "B"),
            ForegroundAppEvent(timestampMillis = 80L, packageName = "A"),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 90L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()

        val types = session.events.map { it.type }
        assertEquals(listOf(SessionEventType.Start, SessionEventType.Pause, SessionEventType.Resume), types)
        assertFalse(types.contains(SessionEventType.End))

        val timestamps = session.events.map { it.timestampMillis }
        assertEquals(listOf(10L, 20L, 80L), timestamps)
    }

    @Test
    fun `screen off pauses and screen on resumes current foreground`() {
        val grace = 100L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            ScreenEvent(timestampMillis = 30L, state = ScreenState.Off),
            ScreenEvent(timestampMillis = 50L, state = ScreenState.On),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 60L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()

        val types = session.events.map { it.type }
        assertEquals(listOf(SessionEventType.Start, SessionEventType.Pause, SessionEventType.Resume), types)

        val timestamps = session.events.map { it.timestampMillis }
        assertEquals(listOf(10L, 30L, 50L), timestamps)
    }

    @Test
    fun `monitoring disabled clears foreground and requires new foreground event to resume`() {
        val grace = 100L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            PermissionEvent(
                timestampMillis = 20L,
                permission = PermissionKind.UsageStats,
                state = PermissionState.Revoked,
            ),
            PermissionEvent(
                timestampMillis = 40L,
                permission = PermissionKind.UsageStats,
                state = PermissionState.Granted,
            ),
            // 監視不能期間の間に前面が変わっていた可能性があるため，
            // 再度 ForegroundAppEvent が来ない限り Resume しない
            ForegroundAppEvent(timestampMillis = 50L, packageName = "A"),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 60L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()
        val types = session.events.map { it.type }

        assertEquals(listOf(SessionEventType.Start, SessionEventType.Pause, SessionEventType.Resume), types)

        val timestamps = session.events.map { it.timestampMillis }
        assertEquals(listOf(10L, 20L, 50L), timestamps)

        assertTrue(session.events.none { it.timestampMillis == 40L && it.type == SessionEventType.Resume })
    }
}
