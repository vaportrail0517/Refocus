package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.ForegroundAppEvent
import com.example.refocus.core.model.PermissionEvent
import com.example.refocus.core.model.PermissionKind
import com.example.refocus.core.model.PermissionState
import com.example.refocus.core.model.ScreenEvent
import com.example.refocus.core.model.ScreenState
import com.example.refocus.core.model.ServiceLifecycleEvent
import com.example.refocus.core.model.ServiceState
import com.example.refocus.core.model.TargetAppsChangedEvent
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.testutil.FakeTimelineRepository
import com.example.refocus.testutil.TestTimeSource
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DailyUsageUseCaseTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun setUp() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTz)
    }

    @Test
    fun refreshIfNeeded_computesTodayTotalsFromTimelineProjection() = runBlocking {
        val pkgA = "com.example.a"
        val pkgB = "com.example.b"

        val dayStart = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
        val tAStart = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
        val tAEnd = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
        val tBStart = Instant.parse("2025-01-01T10:12:00Z").toEpochMilli()
        val tBEnd = Instant.parse("2025-01-01T10:17:00Z").toEpochMilli()
        val now = Instant.parse("2025-01-01T10:20:00Z").toEpochMilli()

        val events: List<TimelineEvent> = listOf(
            TargetAppsChangedEvent(
                timestampMillis = dayStart,
                targetPackages = setOf(pkgA, pkgB),
            ),
            ServiceLifecycleEvent(
                timestampMillis = dayStart,
                state = ServiceState.Started,
            ),
            PermissionEvent(
                timestampMillis = dayStart,
                permission = PermissionKind.UsageStats,
                state = PermissionState.Granted,
            ),
            PermissionEvent(
                timestampMillis = dayStart,
                permission = PermissionKind.Overlay,
                state = PermissionState.Granted,
            ),
            ScreenEvent(
                timestampMillis = dayStart,
                state = ScreenState.On,
            ),
            ForegroundAppEvent(
                timestampMillis = tAStart,
                packageName = pkgA,
            ),
            ForegroundAppEvent(
                timestampMillis = tAEnd,
                packageName = null,
            ),
            ForegroundAppEvent(
                timestampMillis = tBStart,
                packageName = pkgB,
            ),
            ForegroundAppEvent(
                timestampMillis = tBEnd,
                packageName = null,
            ),
        )

        val repo = FakeTimelineRepository(events)
        val timeSource = TestTimeSource(initialNowMillis = now, initialElapsedRealtime = 0L)

        val useCase = DailyUsageUseCase(
            scope = CoroutineScope(Dispatchers.Unconfined),
            timeSource = timeSource,
            timelineRepository = repo,
            lookbackHours = 24,
        )

        val customize = Customize(
            timerTimeMode = TimerTimeMode.TodayAllTargets,
            gracePeriodMillis = 5 * 60 * 1_000L,
        )

        useCase.refreshIfNeeded(
            customize = customize,
            targetPackages = setOf(pkgA, pkgB),
            nowMillis = now,
        )

        assertEquals(10 * 60 * 1_000L, useCase.getTodayThisTargetMillis(pkgA))
        assertEquals(5 * 60 * 1_000L, useCase.getTodayThisTargetMillis(pkgB))
        assertEquals(15 * 60 * 1_000L, useCase.getTodayAllTargetsMillis())
    }
}
