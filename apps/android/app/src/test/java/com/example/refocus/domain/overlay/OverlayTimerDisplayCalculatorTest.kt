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
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import com.example.refocus.domain.overlay.policy.OverlayTimerDisplayCalculator
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase
import com.example.refocus.domain.timeline.TimelineProjectionService
import com.example.refocus.testutil.FakeTimelineRepository
import com.example.refocus.testutil.TestTimeSource
import com.example.refocus.testutil.UtcTimeZoneRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class OverlayTimerDisplayCalculatorTest {
    @get:Rule
    val tzRule = UtcTimeZoneRule()

    @Test
    fun createProviders_displayAndVisualAreDecoupledByTimerVisualTimeBasis() =
        runBlocking {
            val pkgA = "com.example.a"
            val pkgB = "com.example.b"

            val dayStart = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
            val tAStart = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
            val tAEnd = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
            val tBStart = Instant.parse("2025-01-01T10:12:00Z").toEpochMilli()
            val tBEnd = Instant.parse("2025-01-01T10:17:00Z").toEpochMilli()
            val now = Instant.parse("2025-01-01T10:20:00Z").toEpochMilli()

            val events =
                listOf(
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
            val timeSource = TestTimeSource(initialNowMillis = now, initialElapsedRealtime = 1_000_000L)

            val dailyUsageUseCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineProjectionService = TimelineProjectionService(repo),
                    lookbackHours = 24,
                )

            // スナップショットを作っておく（display 側が参照できる状態にする）
            dailyUsageUseCase.refreshIfNeeded(
                customize =
                    Customize(
                        timerTimeMode = TimerTimeMode.TodayAllTargets,
                        gracePeriodMillis = 5 * 60 * 1_000L,
                    ),
                targetPackages = setOf(pkgA, pkgB),
                nowMillis = now,
            )

            val sessionTracker = OverlaySessionTracker(timeSource)
            // オーバーレイ上のランタイムセッションを開始
            sessionTracker.onEnterTargetApp(
                packageName = pkgA,
                gracePeriodMillis = 5 * 60 * 1_000L,
            )

            // 30秒経過
            timeSource.advanceMillis(30_000L)

            var customize =
                Customize(
                    timerTimeMode = TimerTimeMode.TodayAllTargets,
                    timerVisualTimeBasis = TimerVisualTimeBasis.SessionElapsed,
                    gracePeriodMillis = 5 * 60 * 1_000L,
                )

            val calculator =
                OverlayTimerDisplayCalculator(
                    timeSource = timeSource,
                    sessionTracker = sessionTracker,
                    dailyUsageUseCase = dailyUsageUseCase,
                    customizeProvider = { customize },
                    lastTargetPackagesProvider = { setOf(pkgA, pkgB) },
                )

            val providers = calculator.createProviders(pkgA)

            // display: 日次累計（A=10分, B=5分）
            assertEquals(
                15 * 60 * 1_000L,
                providers.displayMillisProvider(timeSource.elapsedRealtime()),
            )

            // visual: セッション経過（30秒）
            assertEquals(30_000L, providers.visualMillisProvider(timeSource.elapsedRealtime()))

            // visual を FollowDisplayTime にすると，display に追従する
            customize = customize.copy(timerVisualTimeBasis = TimerVisualTimeBasis.FollowDisplayTime)
            assertEquals(15 * 60 * 1_000L, providers.visualMillisProvider(timeSource.elapsedRealtime()))

            // display 自体を SessionElapsed に切り替えると，display も 30 秒になる
            customize = customize.copy(timerTimeMode = TimerTimeMode.SessionElapsed)
            assertEquals(30_000L, providers.displayMillisProvider(timeSource.elapsedRealtime()))
            // FollowDisplayTime のままなら visual も display と一致する
            assertEquals(30_000L, providers.visualMillisProvider(timeSource.elapsedRealtime()))
        }
}
