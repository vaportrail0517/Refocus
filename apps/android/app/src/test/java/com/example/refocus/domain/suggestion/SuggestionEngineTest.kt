package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.Customize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    private val engine = SuggestionEngine()

    private fun customize(
        suggestionEnabled: Boolean = true,
        triggerSeconds: Int = 10,
        stableSeconds: Int = 2,
        cooldownSeconds: Int = 5,
    ): Customize =
        Customize(
            suggestionEnabled = suggestionEnabled,
            suggestionTriggerSeconds = triggerSeconds,
            suggestionForegroundStableSeconds = stableSeconds,
            suggestionCooldownSeconds = cooldownSeconds,
        )

    @Test
    fun overlayShown_alwaysReturnsFalse() {
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 60_000L,
                sinceForegroundMillis = 60_000L,
                customize = customize(),
                lastDecisionElapsedMillis = null,
                isOverlayShown = true,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun suggestionDisabled_returnsFalse() {
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 60_000L,
                sinceForegroundMillis = 60_000L,
                customize = customize(suggestionEnabled = false),
                lastDecisionElapsedMillis = null,
                isOverlayShown = false,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun triggerSecondsNonPositive_returnsFalse() {
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 60_000L,
                sinceForegroundMillis = 60_000L,
                customize = customize(triggerSeconds = 0),
                lastDecisionElapsedMillis = null,
                isOverlayShown = false,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun elapsedBeforeTrigger_returnsFalse() {
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 9_999L,
                sinceForegroundMillis = 60_000L,
                customize = customize(triggerSeconds = 10),
                lastDecisionElapsedMillis = null,
                isOverlayShown = false,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun sinceForegroundBeforeStable_returnsFalse() {
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 60_000L,
                sinceForegroundMillis = 1_999L,
                customize = customize(stableSeconds = 2),
                lastDecisionElapsedMillis = null,
                isOverlayShown = false,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun cooldownNotElapsed_returnsFalse() {
        val s = customize(triggerSeconds = 10, stableSeconds = 2, cooldownSeconds = 5)
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 14_999L,
                sinceForegroundMillis = 60_000L,
                customize = s,
                lastDecisionElapsedMillis = 10_000L,
                isOverlayShown = false,
            )

        assertFalse(engine.shouldShow(input))
    }

    @Test
    fun cooldownBoundary_allowsShow() {
        val s = customize(triggerSeconds = 10, stableSeconds = 2, cooldownSeconds = 5)
        val input =
            SuggestionEngine.Input(
                elapsedMillis = 15_000L,
                sinceForegroundMillis = 60_000L,
                customize = s,
                lastDecisionElapsedMillis = 10_000L,
                isOverlayShown = false,
            )

        assertTrue(engine.shouldShow(input))
    }
}
