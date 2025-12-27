package com.example.refocus.feature.common.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.domain.overlay.OverlayServiceController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayServiceControllerEntryPoint {
    fun overlayServiceController(): OverlayServiceController
}

@Composable
fun rememberOverlayServiceController(): OverlayServiceController {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, OverlayServiceControllerEntryPoint::class.java)
    }
    return remember(entryPoint) {
        entryPoint.overlayServiceController()
    }
}
