package com.example.refocus.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.refocus.core.model.PermissionSnapshot
import com.example.refocus.domain.permissions.PermissionSnapshotStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.permissionStateDataStore by preferencesDataStore(name = "permission_state")

/**
 * 直近に観測した権限状態を DataStore に保持する実装．
 */
class PermissionStateDataStore(
    private val context: Context,
) : PermissionSnapshotStore {

    object Keys {
        val USAGE_GRANTED = booleanPreferencesKey("usage_granted")
        val OVERLAY_GRANTED = booleanPreferencesKey("overlay_granted")
        val NOTIFICATION_GRANTED = booleanPreferencesKey("notification_granted")
        val LAST_CHECKED_AT = longPreferencesKey("last_checked_at")
    }

    override suspend fun readOrNull(): PermissionSnapshot? {
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
        val notif = prefs[Keys.NOTIFICATION_GRANTED]
        val ts = prefs[Keys.LAST_CHECKED_AT]
        if (usage == null || overlay == null || notif == null || ts == null) return null

        return PermissionSnapshot(
            usageGranted = usage,
            overlayGranted = overlay,
            notificationGranted = notif,
            lastCheckedAtMillis = ts,
        )
    }

    override suspend fun write(snapshot: PermissionSnapshot) {
        context.permissionStateDataStore.edit { prefs ->
            prefs[Keys.USAGE_GRANTED] = snapshot.usageGranted
            prefs[Keys.OVERLAY_GRANTED] = snapshot.overlayGranted
            prefs[Keys.NOTIFICATION_GRANTED] = snapshot.notificationGranted
            prefs[Keys.LAST_CHECKED_AT] = snapshot.lastCheckedAtMillis
        }
    }
}
