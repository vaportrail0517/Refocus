package com.example.refocus.domain.session

import com.example.refocus.core.model.SessionEvent
import com.example.refocus.core.model.SessionEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDurationCalculatorTest {
    @Test
    fun buildActiveSegments_splitsByPauseResumeAndIgnoresSuggestionEvents() {
        val events =
            listOf(
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.Start, timestampMillis = 10L),
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.Pause, timestampMillis = 20L),
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.Resume, timestampMillis = 30L),
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.SuggestionShown, timestampMillis = 35L),
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.End, timestampMillis = 40L),
            )

        val segments = SessionDurationCalculator.buildActiveSegments(events = events, nowMillis = 999L)
        assertEquals(
            listOf(
                SessionDurationCalculator.ActiveSegment(10L, 20L),
                SessionDurationCalculator.ActiveSegment(30L, 40L),
            ),
            segments,
        )

        assertEquals(20L, SessionDurationCalculator.calculateDurationMillis(events = events, nowMillis = 999L))
    }

    @Test
    fun buildActiveSegments_runningSessionUsesNowMillisAsEnd() {
        val events =
            listOf(
                SessionEvent(id = null, sessionId = 1L, type = SessionEventType.Start, timestampMillis = 10L),
            )

        val segments = SessionDurationCalculator.buildActiveSegments(events = events, nowMillis = 25L)
        assertEquals(listOf(SessionDurationCalculator.ActiveSegment(10L, 25L)), segments)
        assertEquals(15L, SessionDurationCalculator.calculateDurationMillis(events = events, nowMillis = 25L))
    }
}
