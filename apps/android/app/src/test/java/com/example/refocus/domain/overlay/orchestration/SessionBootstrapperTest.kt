package com.example.refocus.domain.overlay.orchestration

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.SuggestionDecision
import com.example.refocus.core.model.SuggestionDecisionEvent
import com.example.refocus.core.model.SuggestionShownEvent
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.timeline.TimelineProjectionService
import com.example.refocus.testutil.FakeTimelineRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBootstrapperTest {
    private class FakeTimeSource(
        var nowMillis: Long,
        var elapsedRealtime: Long,
    ) : TimeSource {
        override fun nowMillis(): Long = nowMillis

        override fun elapsedRealtime(): Long = elapsedRealtime
    }

    @Test
    fun ongoingSession_restoresLastDecisionElapsed_fromOpened() =
        runBlocking {
            val events =
                listOf(
                    TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("a")),
                    ForegroundAppEvent(timestampMillis = 1_000L, packageName = "a"),
                    SuggestionShownEvent(timestampMillis = 4_000L, packageName = "a", suggestionId = 1L),
                    SuggestionDecisionEvent(
                        timestampMillis = 7_000L,
                        packageName = "a",
                        suggestionId = 1L,
                        decision = SuggestionDecision.Opened,
                    ),
                )

            val time = FakeTimeSource(nowMillis = 8_000L, elapsedRealtime = 8_000L)
            val tracker = OverlaySessionTracker(time)
            val timelineRepo = FakeTimelineRepository(initialEvents = events)
            val projectionService = TimelineProjectionService(timelineRepo)
            val bootstrapper = SessionBootstrapper(time, projectionService, lookbackHours = 1L)

            val bootstrap =
                bootstrapper.computeBootstrapFromTimeline(
                    packageName = "a",
                    customize = Customize(gracePeriodMillis = 5_000L),
                    nowMillis = 8_000L,
                    force = true,
                    sessionTracker = tracker,
                )

            assertNotNull(bootstrap)
            assertTrue(bootstrap!!.isOngoingSession)
            assertEquals(7_000L, bootstrap.initialElapsedMillis)
            assertEquals(6_000L, bootstrap.gate.lastDecisionElapsedMillis)
        }

    @Test
    fun endedSession_returnsNotOngoing_andResetsGate() =
        runBlocking {
            val events =
                listOf(
                    TargetAppsChangedEvent(timestampMillis = 0L, targetPackages = setOf("a")),
                    ForegroundAppEvent(timestampMillis = 1_000L, packageName = "a"),
                    // 対象アプリから離脱
                    ForegroundAppEvent(timestampMillis = 2_000L, packageName = null),
                )

            val time = FakeTimeSource(nowMillis = 10_000L, elapsedRealtime = 10_000L)
            val tracker = OverlaySessionTracker(time)
            val timelineRepo = FakeTimelineRepository(initialEvents = events)
            val projectionService = TimelineProjectionService(timelineRepo)
            val bootstrapper = SessionBootstrapper(time, projectionService, lookbackHours = 1L)

            val bootstrap =
                bootstrapper.computeBootstrapFromTimeline(
                    packageName = "a",
                    customize = Customize(gracePeriodMillis = 5_000L),
                    nowMillis = 10_000L,
                    force = true,
                    sessionTracker = tracker,
                )

            assertNotNull(bootstrap)
            assertFalse(bootstrap!!.isOngoingSession)
            assertEquals(0L, bootstrap.initialElapsedMillis)
            assertNull(bootstrap.gate.lastDecisionElapsedMillis)
        }
}
