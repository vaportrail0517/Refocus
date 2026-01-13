package com.example.refocus.system.overlay.service

import android.content.Context
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.system.overlay.tryStartOverlayService
import com.example.refocus.system.overlay.tryStopOverlayService
import com.example.refocus.system.permissions.PermissionHelper

class OverlayServiceControllerImpl(
    private val appContext: Context,
) : OverlayServiceController {
    override fun startIfReady(source: String): Boolean {
        val ok = PermissionHelper.hasAllCorePermissions(appContext)
        if (!ok) {
            RefocusLog.d("OverlayServiceController") {
                "skip startOverlayService, permissions not granted, source=$source"
            }
            return false
        }
        RefocusLog.d("OverlayServiceController") { "startOverlayService, source=$source" }
        return appContext.tryStartOverlayService(source = source)
    }

    override fun stop(source: String) {
        RefocusLog.d("OverlayServiceController") { "stopOverlayService, source=$source" }
        appContext.tryStopOverlayService(source = source)
    }
}
