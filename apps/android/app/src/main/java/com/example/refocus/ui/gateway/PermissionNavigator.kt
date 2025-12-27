package com.example.refocus.ui.gateway

import android.app.Activity

/**
 * 権限設定画面へ遷移するための gateway．
 *
 * feature から system.permissions.PermissionHelper へ直接依存しないようにする．
 */
interface PermissionNavigator {
    fun openUsageAccessSettings(activity: Activity)
    fun openOverlaySettings(activity: Activity)
    fun openNotificationSettings(activity: Activity)
}
