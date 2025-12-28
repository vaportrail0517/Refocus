package com.example.refocus.domain.settings

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.ServiceConfigEvent
import com.example.refocus.core.model.ServiceConfigKind
import com.example.refocus.core.model.ServiceConfigState
import com.example.refocus.core.model.SettingsChangedEvent
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.testutil.FakeSettingsRepository
import com.example.refocus.testutil.FakeTimelineRepository
import com.example.refocus.testutil.TestTimeSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCommandTest {
    @Test
    fun updateCustomize_returnsFalse_whenNoChange_andDoesNotRecordEvent() =
        runBlocking {
            val settingsRepository =
                FakeSettingsRepository(
                    initialCustomize = Customize(),
                )
            val timelineRepository = FakeTimelineRepository()
            val timeSource = TestTimeSource(initialNowMillis = 1_000L, initialElapsedRealtime = 1_000L)
            val eventRecorder = EventRecorder(timeSource = timeSource, timelineRepository = timelineRepository)
            val command = SettingsCommand(settingsRepository = settingsRepository, eventRecorder = eventRecorder)

            val changed =
                command.updateCustomize(
                    key = SettingsCommand.Keys.MIN_FONT_SIZE_SP,
                    newValueDescription = "unchanged",
                    source = "test",
                    transform = { it }, // 変更なし
                )

            assertEquals(false, changed)

            val events = timelineRepository.getEvents(startMillis = 0L, endMillis = Long.MAX_VALUE)
            assertTrue(events.isEmpty())
        }

    @Test
    fun setOverlayEnabled_recordsServiceConfigEvent_only_whenChanged() =
        runBlocking {
            val settingsRepository =
                FakeSettingsRepository(
                    initialCustomize = Customize(overlayEnabled = false),
                )
            val timelineRepository = FakeTimelineRepository()
            val timeSource = TestTimeSource(initialNowMillis = 1_000L, initialElapsedRealtime = 1_000L)
            val eventRecorder = EventRecorder(timeSource = timeSource, timelineRepository = timelineRepository)
            val command = SettingsCommand(settingsRepository = settingsRepository, eventRecorder = eventRecorder)

            command.setOverlayEnabled(enabled = true, source = "test")

            val events = timelineRepository.getEvents(startMillis = 0L, endMillis = Long.MAX_VALUE)
            assertEquals(1, events.size)

            val event = events.single()
            assertTrue(event is ServiceConfigEvent)
            val serviceConfigEvent = event as ServiceConfigEvent
            assertEquals(ServiceConfigKind.OverlayEnabled, serviceConfigEvent.config)
            assertEquals(ServiceConfigState.Enabled, serviceConfigEvent.state)
        }

    @Test
    fun setOverlayEnabled_doesNotRecord_whenValueIsSame() =
        runBlocking {
            val settingsRepository =
                FakeSettingsRepository(
                    initialCustomize = Customize(overlayEnabled = true),
                )
            val timelineRepository = FakeTimelineRepository()
            val timeSource = TestTimeSource(initialNowMillis = 1_000L, initialElapsedRealtime = 1_000L)
            val eventRecorder = EventRecorder(timeSource = timeSource, timelineRepository = timelineRepository)
            val command = SettingsCommand(settingsRepository = settingsRepository, eventRecorder = eventRecorder)

            command.setOverlayEnabled(enabled = true, source = "test")

            val events = timelineRepository.getEvents(startMillis = 0L, endMillis = Long.MAX_VALUE)
            assertTrue(events.isEmpty())
        }

    @Test
    fun setSettingsPresetIfNeeded_recordsSettingsChangedEvent() =
        runBlocking {
            val settingsRepository = FakeSettingsRepository()
            val timelineRepository = FakeTimelineRepository()
            val timeSource = TestTimeSource(initialNowMillis = 1_000L, initialElapsedRealtime = 1_000L)
            val eventRecorder = EventRecorder(timeSource = timeSource, timelineRepository = timelineRepository)
            val command = SettingsCommand(settingsRepository = settingsRepository, eventRecorder = eventRecorder)

            val changed =
                command.setSettingsPresetIfNeeded(
                    preset = com.example.refocus.core.model.CustomizePreset.Debug,
                    source = "test",
                )
            assertEquals(true, changed)

            val events = timelineRepository.getEvents(startMillis = 0L, endMillis = Long.MAX_VALUE)
            assertEquals(1, events.size)
            assertTrue(events.single() is SettingsChangedEvent)
            val e = events.single() as SettingsChangedEvent
            assertEquals(SettingsCommand.Keys.SETTINGS_PRESET.value, e.key)
        }
}
