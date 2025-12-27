package com.example.refocus.system.overlay.service

import com.example.refocus.domain.overlay.port.OverlayServiceStatusProvider
import com.example.refocus.system.overlay.OverlayService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayServiceStatusProviderImpl @Inject constructor() : OverlayServiceStatusProvider {
    override fun isRunning(): Boolean = OverlayService.isRunning
}
