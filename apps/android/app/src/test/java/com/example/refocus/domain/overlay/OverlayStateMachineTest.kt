package com.example.refocus.domain.overlay

import com.example.refocus.core.model.Customize
import com.example.refocus.domain.overlay.engine.OverlayEvent
import com.example.refocus.domain.overlay.engine.OverlayState
import com.example.refocus.domain.overlay.engine.OverlayStateMachine
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayStateMachineTest {
    private val sm = OverlayStateMachine()

    @Test
    fun idle_enterTargetApp_transitionsToTracking() {
        val next =
            sm.transition(
                current = OverlayState.Idle,
                event =
                    OverlayEvent.EnterTargetApp(
                        packageName = "com.example.a",
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Tracking("com.example.a"), next)
    }

    @Test
    fun tracking_leaveTargetApp_transitionsToIdle() {
        val next =
            sm.transition(
                current = OverlayState.Tracking("com.example.a"),
                event =
                    OverlayEvent.LeaveTargetApp(
                        packageName = "com.example.a",
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Idle, next)
    }

    @Test
    fun tracking_screenOff_transitionsToIdle() {
        val next =
            sm.transition(
                current = OverlayState.Tracking("com.example.a"),
                event =
                    OverlayEvent.ScreenOff(
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Idle, next)
    }

    @Test
    fun settingsChanged_overlayDisabled_entersDisabledFromIdleAndTracking() {
        val disabledCustomize = Customize(overlayEnabled = false)

        val fromIdle =
            sm.transition(
                current = OverlayState.Idle,
                event = OverlayEvent.SettingsChanged(disabledCustomize),
            )
        assertEquals(OverlayState.Disabled, fromIdle)

        val fromTracking =
            sm.transition(
                current = OverlayState.Tracking("com.example.a"),
                event = OverlayEvent.SettingsChanged(disabledCustomize),
            )
        assertEquals(OverlayState.Disabled, fromTracking)
    }

    @Test
    fun disabled_settingsChanged_overlayEnabled_returnsToIdle() {
        val enabledCustomize = Customize(overlayEnabled = true)

        val next =
            sm.transition(
                current = OverlayState.Disabled,
                event = OverlayEvent.SettingsChanged(enabledCustomize),
            )
        assertEquals(OverlayState.Idle, next)
    }

    @Test
    fun overlayDisabledEvent_entersDisabled() {
        val fromIdle =
            sm.transition(
                current = OverlayState.Idle,
                event = OverlayEvent.OverlayDisabled,
            )
        assertEquals(OverlayState.Disabled, fromIdle)

        val fromTracking =
            sm.transition(
                current = OverlayState.Tracking("com.example.a"),
                event = OverlayEvent.OverlayDisabled,
            )
        assertEquals(OverlayState.Disabled, fromTracking)
    }

    @Test
    fun paused_behavesLikeIdleOrTrackingForNow() {
        val next1 =
            sm.transition(
                current = OverlayState.Paused("com.example.a"),
                event =
                    OverlayEvent.EnterTargetApp(
                        packageName = "com.example.a",
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Tracking("com.example.a"), next1)

        val next2 =
            sm.transition(
                current = OverlayState.Paused("com.example.a"),
                event =
                    OverlayEvent.ScreenOff(
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Idle, next2)
    }

    @Test
    fun suggesting_leavesToIdleOnLeaveOrScreenOff() {
        val next1 =
            sm.transition(
                current = OverlayState.Suggesting("com.example.a"),
                event =
                    OverlayEvent.LeaveTargetApp(
                        packageName = "com.example.a",
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Idle, next1)

        val next2 =
            sm.transition(
                current = OverlayState.Suggesting("com.example.a"),
                event =
                    OverlayEvent.ScreenOff(
                        nowMillis = 0L,
                        nowElapsedRealtime = 0L,
                    ),
            )
        assertEquals(OverlayState.Idle, next2)
    }
}
