package com.example.refocus.system.permissions

import android.app.Activity
import com.example.refocus.ui.gateway.PermissionNavigator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPermissionNavigator @Inject constructor() : PermissionNavigator {

    override fun openUsageAccessSettings(activity: Activity) {
        PermissionHelper.openUsageAccessSettings(activity)
    }

    override fun openOverlaySettings(activity: Activity) {
        PermissionHelper.openOverlaySettings(activity)
    }

    override fun openNotificationSettings(activity: Activity) {
        PermissionHelper.openNotificationSettings(activity)
    }
}
