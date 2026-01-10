package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.refocus.core.model.OverlayHealthSnapshot
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.overlayHealthDataStore by preferencesDataStore(name = "overlay_health")

/**
 * オーバーレイ監視サービスの稼働健全性を DataStore に保持する実装．
 */
class OverlayHealthDataStore(
    private val context: Context,
) : OverlayHealthStore {
    object Keys {
        val LAST_HEARTBEAT_ELAPSED = longPreferencesKey("last_heartbeat_elapsed")
        val LAST_HEARTBEAT_WALL = longPreferencesKey("last_heartbeat_wall")
        val LAST_FOREGROUND_SAMPLE_ELAPSED = longPreferencesKey("last_foreground_sample_elapsed")
        val MONITOR_RESTART_COUNT = intPreferencesKey("monitor_restart_count")
        val LAST_ERROR_SUMMARY = stringPreferencesKey("last_error_summary")

        val KEEPALIVE_RUN_COUNT = intPreferencesKey("keepalive_run_count")
        val KEEPALIVE_START_ATTEMPT_COUNT = intPreferencesKey("keepalive_start_attempt_count")
        val KEEPALIVE_START_SUCCESS_COUNT = intPreferencesKey("keepalive_start_success_count")
        val KEEPALIVE_START_FAILURE_COUNT = intPreferencesKey("keepalive_start_failure_count")
        val LAST_KEEPALIVE_RUN_ELAPSED = longPreferencesKey("last_keepalive_run_elapsed")
        val LAST_KEEPALIVE_RUN_WALL = longPreferencesKey("last_keepalive_run_wall")
        val LAST_KEEPALIVE_DECISION = stringPreferencesKey("last_keepalive_decision")
        val LAST_KEEPALIVE_ERROR_SUMMARY = stringPreferencesKey("last_keepalive_error_summary")
    }

    override suspend fun read(): OverlayHealthSnapshot {
        val prefs: Preferences =
            context.overlayHealthDataStore.data
                .catch { e ->
                    if (e is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw e
                    }
                }.first()

        return snapshotFrom(prefs)
    }

    override suspend fun write(snapshot: OverlayHealthSnapshot) {
        context.overlayHealthDataStore.edit { prefs ->
            writeTo(prefs, snapshot)
        }
    }

    override suspend fun update(transform: (OverlayHealthSnapshot) -> OverlayHealthSnapshot) {
        context.overlayHealthDataStore.edit { prefs ->
            val current = snapshotFrom(prefs)
            val next = transform(current)
            writeTo(prefs, next)
        }
    }

    override suspend fun clear() {
        context.overlayHealthDataStore.edit { it.clear() }
    }

    private fun snapshotFrom(prefs: Preferences): OverlayHealthSnapshot {
        return OverlayHealthSnapshot(
            lastHeartbeatElapsedRealtimeMillis = prefs[Keys.LAST_HEARTBEAT_ELAPSED],
            lastHeartbeatWallClockMillis = prefs[Keys.LAST_HEARTBEAT_WALL],
            lastForegroundSampleElapsedRealtimeMillis = prefs[Keys.LAST_FOREGROUND_SAMPLE_ELAPSED],
            monitorRestartCount = prefs[Keys.MONITOR_RESTART_COUNT] ?: 0,
            lastErrorSummary = prefs[Keys.LAST_ERROR_SUMMARY],

            keepAliveRunCount = prefs[Keys.KEEPALIVE_RUN_COUNT] ?: 0,
            keepAliveStartAttemptCount = prefs[Keys.KEEPALIVE_START_ATTEMPT_COUNT] ?: 0,
            keepAliveStartSuccessCount = prefs[Keys.KEEPALIVE_START_SUCCESS_COUNT] ?: 0,
            keepAliveStartFailureCount = prefs[Keys.KEEPALIVE_START_FAILURE_COUNT] ?: 0,

            lastKeepAliveRunElapsedRealtimeMillis = prefs[Keys.LAST_KEEPALIVE_RUN_ELAPSED],
            lastKeepAliveRunWallClockMillis = prefs[Keys.LAST_KEEPALIVE_RUN_WALL],
            lastKeepAliveDecision = prefs[Keys.LAST_KEEPALIVE_DECISION],
            lastKeepAliveErrorSummary = prefs[Keys.LAST_KEEPALIVE_ERROR_SUMMARY],
        )
    }

    private fun writeTo(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        snapshot: OverlayHealthSnapshot,
    ) {
        putOrRemove(prefs, Keys.LAST_HEARTBEAT_ELAPSED, snapshot.lastHeartbeatElapsedRealtimeMillis)
        putOrRemove(prefs, Keys.LAST_HEARTBEAT_WALL, snapshot.lastHeartbeatWallClockMillis)
        putOrRemove(prefs, Keys.LAST_FOREGROUND_SAMPLE_ELAPSED, snapshot.lastForegroundSampleElapsedRealtimeMillis)
        prefs[Keys.MONITOR_RESTART_COUNT] = snapshot.monitorRestartCount
        putOrRemove(prefs, Keys.LAST_ERROR_SUMMARY, snapshot.lastErrorSummary)

        prefs[Keys.KEEPALIVE_RUN_COUNT] = snapshot.keepAliveRunCount
        prefs[Keys.KEEPALIVE_START_ATTEMPT_COUNT] = snapshot.keepAliveStartAttemptCount
        prefs[Keys.KEEPALIVE_START_SUCCESS_COUNT] = snapshot.keepAliveStartSuccessCount
        prefs[Keys.KEEPALIVE_START_FAILURE_COUNT] = snapshot.keepAliveStartFailureCount

        putOrRemove(prefs, Keys.LAST_KEEPALIVE_RUN_ELAPSED, snapshot.lastKeepAliveRunElapsedRealtimeMillis)
        putOrRemove(prefs, Keys.LAST_KEEPALIVE_RUN_WALL, snapshot.lastKeepAliveRunWallClockMillis)
        putOrRemove(prefs, Keys.LAST_KEEPALIVE_DECISION, snapshot.lastKeepAliveDecision)
        putOrRemove(prefs, Keys.LAST_KEEPALIVE_ERROR_SUMMARY, snapshot.lastKeepAliveErrorSummary)
    }

    private fun <T> putOrRemove(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) {
            prefs.remove(key)
        } else {
            prefs[key] = value
        }
    }
}
