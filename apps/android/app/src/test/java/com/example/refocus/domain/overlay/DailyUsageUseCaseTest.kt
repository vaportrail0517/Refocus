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
import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.domain.overlay.usecase.DailyUsageUseCase
import com.example.refocus.testutil.BlockingTimelineRepository
import com.example.refocus.testutil.FakeTimelineRepository
import com.example.refocus.testutil.NonCancellableBlockingTimelineRepository
import com.example.refocus.testutil.TestTimeSource
import com.example.refocus.testutil.UtcTimeZoneRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DailyUsageUseCaseTest {
    @get:Rule
    val tzRule = UtcTimeZoneRule()

    @Test
    fun refreshIfNeeded_computesTodayTotalsFromTimelineProjection() =
        runBlocking<Unit> {
            val pkgA = "com.example.a"
            val pkgB = "com.example.b"

            val dayStart = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
            val tAStart = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
            val tAEnd = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
            val tBStart = Instant.parse("2025-01-01T10:12:00Z").toEpochMilli()
            val tBEnd = Instant.parse("2025-01-01T10:17:00Z").toEpochMilli()
            val now = Instant.parse("2025-01-01T10:20:00Z").toEpochMilli()

            val events: List<TimelineEvent> =
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
            val timeSource = TestTimeSource(initialNowMillis = now, initialElapsedRealtime = 0L)

            val useCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineRepository = repo,
                    lookbackHours = 24,
                )

            val customize =
                Customize(
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

    @Test
    fun onTick_doesNotAccumulateAcrossMidnightWhenSnapshotIsNotReady() =
        runBlocking<Unit> {
            val pkgA = "com.example.a"

            val beforeMidnight = Instant.parse("2025-01-01T23:59:59Z").toEpochMilli()
            val afterMidnight = Instant.parse("2025-01-02T00:00:01Z").toEpochMilli()

            val repo = BlockingTimelineRepository(initialEvents = emptyList<TimelineEvent>())
            val timeSource =
                TestTimeSource(initialNowMillis = afterMidnight, initialElapsedRealtime = 0L)

            val useCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineRepository = repo,
                    lookbackHours = 24,
                )

            val customize =
                Customize(
                    timerTimeMode = TimerTimeMode.TodayAllTargets,
                    gracePeriodMillis = 0L,
                )

            // 1 回目の tick で refresh を要求するが，getEvents が gate でブロックされ snapshot はまだ作られない
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA),
                activePackageName = pkgA,
                nowMillis = beforeMidnight,
            )

            // snapshot が無いまま日付またぎの tick を迎える
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA),
                activePackageName = pkgA,
                nowMillis = afterMidnight,
            )

            // 日付またぎで invalidate され，跨ぎ dt が混入しない
            assertEquals(0L, useCase.getTodayThisTargetMillis(pkgA))
            assertEquals(0L, useCase.getTodayAllTargetsMillis())

            useCase.invalidate()
            repo.gate.complete(Unit)
        }

    @Test
    fun refreshIfNeeded_doesNotDropRuntimeDeltaAccumulatedDuringRefresh() =
        runBlocking<Unit> {
            val pkgA = "com.example.a"
            val pkgB = "com.example.b"

            val dayStart = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
            val tAStart = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
            val tAEnd = Instant.parse("2025-01-01T10:10:00Z").toEpochMilli()
            val tBStart = Instant.parse("2025-01-01T10:12:00Z").toEpochMilli()
            val tBEnd = Instant.parse("2025-01-01T10:17:00Z").toEpochMilli()
            val now = Instant.parse("2025-01-01T10:20:00Z").toEpochMilli()

            val events: List<TimelineEvent> =
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

            val repo = BlockingTimelineRepository(initialEvents = events)
            val timeSource =
                TestTimeSource(initialNowMillis = now + 1_000L, initialElapsedRealtime = 0L)

            val useCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineRepository = repo,
                    lookbackHours = 24,
                )

            val customize =
                Customize(
                    timerTimeMode = TimerTimeMode.TodayAllTargets,
                    gracePeriodMillis = 5 * 60 * 1_000L,
                )

            // refresh を開始（gate で getEvents を止めて，完了を遅らせる）
            useCase.requestRefreshIfNeeded(
                customize = customize,
                targetPackages = setOf(pkgA, pkgB),
                nowMillis = now,
            )

            // refresh 実行中に tick が進み，ランタイム加算が積まれる状況を作る
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA, pkgB),
                activePackageName = pkgA,
                nowMillis = now,
            )
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA, pkgB),
                activePackageName = pkgA,
                nowMillis = now + 1_000L,
            )

            // snapshot が無い段階では，ランタイム加算だけが見える
            assertEquals(1_000L, useCase.getTodayAllTargetsMillis())

            // refresh を解放して完了させる
            repo.gate.complete(Unit)

            // refresh 完了を待つ（base が入るまで）
            withTimeout(2_000L) {
                while (useCase.getTodayAllTargetsMillis() < 15 * 60 * 1_000L) {
                    delay(10L)
                }
            }

            // base（A=10分, B=5分）に，refresh 中に積んだ 1 秒が上乗せされていること
            assertEquals(10 * 60 * 1_000L + 1_000L, useCase.getTodayThisTargetMillis(pkgA))
            assertEquals(5 * 60 * 1_000L, useCase.getTodayThisTargetMillis(pkgB))
            assertEquals(15 * 60 * 1_000L + 1_000L, useCase.getTodayAllTargetsMillis())
        }

    @Test
    fun requestRefreshIfNeeded_doesNotStartAnotherRefreshWhenOldJobFinishesAfterInvalidate() =
        runBlocking<Unit> {
            val pkgA = "com.example.a"

            val dayStart = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
            val now = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()

            val events: List<TimelineEvent> =
                listOf(
                    TargetAppsChangedEvent(
                        timestampMillis = dayStart,
                        targetPackages = setOf(pkgA),
                    ),
                    ServiceLifecycleEvent(
                        timestampMillis = dayStart,
                        state = ServiceState.Started,
                    ),
                    ScreenEvent(
                        timestampMillis = dayStart,
                        state = ScreenState.On,
                    ),
                )

            val repo = NonCancellableBlockingTimelineRepository(initialEvents = events)
            val timeSource = TestTimeSource(initialNowMillis = now, initialElapsedRealtime = 0L)

            val useCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineRepository = repo,
                    lookbackHours = 24,
                )

            val customize =
                Customize(
                    timerTimeMode = TimerTimeMode.TodayAllTargets,
                    gracePeriodMillis = 0L,
                )

            // 1 本目の refresh を開始し，getEvents でブロックさせる
            useCase.requestRefreshIfNeeded(
                customize = customize,
                targetPackages = setOf(pkgA),
                nowMillis = now,
            )

            withTimeout(1_000L) {
                while (repo.getEventsCallCount.get() < 1) {
                    delay(10L)
                }
            }

            // cancel して refreshJob をクリアするが，getEvents は NonCancellable なので
            // 1 本目のジョブは遅れて finally まで到達し得る
            useCase.invalidate()

            // 2 本目の refresh を開始し，こちらも getEvents でブロックさせる
            useCase.requestRefreshIfNeeded(
                customize = customize,
                targetPackages = setOf(pkgA),
                nowMillis = now,
            )

            withTimeout(1_000L) {
                while (repo.getEventsCallCount.get() < 2) {
                    delay(10L)
                }
            }

            // 両方を解放．2 本目は repo 側でわざと遅延されるため，
            // 1 本目の finally が 2 本目の実行中に走る時間窓ができる
            repo.gate.complete(Unit)
            delay(50L)

            // ここで refreshJob の参照が壊れていると，3 本目が起動してしまう
            useCase.requestRefreshIfNeeded(
                customize = customize,
                targetPackages = setOf(pkgA),
                nowMillis = now,
            )

            // 起動してしまう場合は getEventsCallCount が増えるので，少し待って確認する
            delay(150L)
            assertEquals(2, repo.getEventsCallCount.get())

            // バックグラウンドジョブの片付け（テストの安定化）
            delay(400L)
        }

    @Test
    fun onTick_clampsLargeGapToAvoidOvercount() =
        runBlocking<Unit> {
            val pkgA = "com.example.a"

            val t1 = Instant.parse("2025-01-01T10:00:00Z").toEpochMilli()
            val t2 = t1 + 10_000L

            val repo = FakeTimelineRepository(initialEvents = emptyList<TimelineEvent>())
            val timeSource = TestTimeSource(initialNowMillis = t1, initialElapsedRealtime = 0L)

            val useCase =
                DailyUsageUseCase(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    timeSource = timeSource,
                    timelineRepository = repo,
                    lookbackHours = 24,
                )

            val customize =
                Customize(
                    timerTimeMode = TimerTimeMode.TodayAllTargets,
                    gracePeriodMillis = 0L,
                )

            // 先に snapshot を作って，onTick が無駄に refresh を起動しない状態にする
            useCase.refreshIfNeeded(
                customize = customize,
                targetPackages = setOf(pkgA),
                nowMillis = t1,
            )

            // 1回目の tick で lastTick を設定
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA),
                activePackageName = pkgA,
                nowMillis = t1,
            )

            // 10 秒空いたとしても，内部では上限（2 秒）で clamp される
            useCase.onTick(
                customize = customize,
                targetPackages = setOf(pkgA),
                activePackageName = pkgA,
                nowMillis = t2,
            )

            assertEquals(2_000L, useCase.getTodayAllTargetsMillis())
            assertEquals(2_000L, useCase.getTodayThisTargetMillis(pkgA))
        }
}
