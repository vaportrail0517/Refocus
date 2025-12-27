package com.example.refocus.domain.timeline

import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
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

    @Test
    fun `target apps change removes active session and ends at change timestamp`() {
        val grace = 100L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            // A を対象から外す
            TargetAppsChangedEvent(timestampMillis = 20L, targetPackages = emptySet()),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 200L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()
        val types = session.events.map { it.type }
        assertEquals(listOf(SessionEventType.Start, SessionEventType.Pause, SessionEventType.End), types)
        assertEquals(listOf(10L, 20L, 20L), session.events.map { it.timestampMillis })
    }

    @Test
    fun `suggestion events are appended only while session is active`() {
        val grace = 0L
        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("A")),
            ForegroundAppEvent(timestampMillis = 10L, packageName = "A"),
            SuggestionShownEvent(
                timestampMillis = 15L,
                packageName = "A",
                suggestionId = 1L,
            ),
            // 離脱（inactive）
            ForegroundAppEvent(timestampMillis = 20L, packageName = null),
            // grace=0 のため，ここで session は close 済みになり，この decision は attach されない
            SuggestionDecisionEvent(
                timestampMillis = 25L,
                packageName = "A",
                suggestionId = 1L,
                decision = SuggestionDecision.Dismissed,
            ),
        )

        val projected = SessionProjector.projectSessions(
            events = events,
            stopGracePeriodMillis = grace,
            nowMillis = 30L,
        )

        assertEquals(1, projected.size)
        val session = projected.single()
        val types = session.events.map { it.type }

        // shown は active 中なので入る
        assertTrue(types.contains(SessionEventType.SuggestionShown))
        // decision は close 後なので入らない
        assertFalse(types.contains(SessionEventType.SuggestionDismissed))
    }
}
