package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.permissionStateDataStore by preferencesDataStore(name = "permission_state")

/**
 * 直近に観測した権限状態のスナップショット。
 *
 * - 変更検知（差分イベント記録）に使う
 * - DB（タイムライン）とは別に DataStore に保持する
 */
data class PermissionSnapshot(
    val usageGranted: Boolean,
    val overlayGranted: Boolean,
    val lastCheckedAtMillis: Long,
) {
    fun hasAllCorePermissions(): Boolean = usageGranted && overlayGranted
}

class PermissionStateDataStore(
    private val context: Context,
) {
    object Keys {
        val USAGE_GRANTED = booleanPreferencesKey("usage_granted")
        val OVERLAY_GRANTED = booleanPreferencesKey("overlay_granted")
        val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
    }

    suspend fun readOrNull(): PermissionSnapshot? {
        val prefs: Preferences = context.permissionStateDataStore.data
            .catch { e ->
                if (e is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw e
                }
            }
            .first()

        val usage = prefs[Keys.USAGE_GRANTED]
        val overlay = prefs[Keys.OVERLAY_GRANTED]
        val ts = prefs[Keys.LAST_CHECKED_AT]
        if (usage == null || overlay == null || ts == null) return null

        return PermissionSnapshot(
            usageGranted = usage,
            overlayGranted = overlay,
            lastCheckedAtMillis = ts,
        )
    }

    suspend fun write(snapshot: PermissionSnapshot) {
        context.permissionStateDataStore.edit { prefs ->
            prefs[Keys.USAGE_GRANTED] = snapshot.usageGranted
            prefs[Keys.OVERLAY_GRANTED] = snapshot.overlayGranted
            prefs[Keys.LAST_CHECKED_AT] = snapshot.lastCheckedAtMillis
        }
    }
}
