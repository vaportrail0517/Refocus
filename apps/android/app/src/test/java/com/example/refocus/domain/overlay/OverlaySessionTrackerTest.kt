package com.example.refocus.domain.overlay

import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.overlay.orchestration.OverlaySessionTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlaySessionTrackerTest {

    private class FakeTimeSource(
        var nowMillis: Long,
        var elapsedRealtime: Long,
    ) : TimeSource {
        override fun nowMillis(): Long = nowMillis
        override fun elapsedRealtime(): Long = elapsedRealtime
    }

    @Test
    fun reenterWithinGrace_isNotAffectedByWallClockChange() {
        val time = FakeTimeSource(nowMillis = 1_000_000L, elapsedRealtime = 5_000L)
        val tracker = OverlaySessionTracker(time)

        // enter -> leave (累積 1000ms)
        assertTrue(tracker.onEnterTargetApp(packageName = "a", gracePeriodMillis = 5_000L))
        time.elapsedRealtime = 6_000L
        tracker.onLeaveTargetApp("a")

        // 端末時刻が大きく進んでも，猶予判定は elapsedRealtime を使うため継続扱いになる
        time.nowMillis += 86_400_000L // +1 day
        time.elapsedRealtime = 7_000L // leave から 1s

        assertFalse(tracker.onEnterTargetApp(packageName = "a", gracePeriodMillis = 5_000L))

        // 継続なので累積 1000ms を維持したまま伸びる
        val elapsed = tracker.computeElapsedFor("a", nowElapsedRealtime = 7_500L)
        assertEquals(1_500L, elapsed)
    }

    @Test
    fun reenterAfterGrace_startsNewSession() {
        val time = FakeTimeSource(nowMillis = 1_000_000L, elapsedRealtime = 5_000L)
        val tracker = OverlaySessionTracker(time)

        assertTrue(tracker.onEnterTargetApp(packageName = "a", gracePeriodMillis = 5_000L))
        time.elapsedRealtime = 6_000L
        tracker.onLeaveTargetApp("a")

        // grace を超えて再入場
        time.elapsedRealtime = 20_000L
        assertTrue(tracker.onEnterTargetApp(packageName = "a", gracePeriodMillis = 5_000L))

        // 新規セッションなので累積は 0 から
        val elapsed = tracker.computeElapsedFor("a", nowElapsedRealtime = 20_500L)
        assertEquals(500L, elapsed)
    }
}
