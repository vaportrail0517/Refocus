package com.example.refocus.feature.common.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.domain.overlay.OverlayServiceStatusProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayServiceStatusEntryPoint {
    fun overlayServiceStatusProvider(): OverlayServiceStatusProvider
}

@Composable
fun rememberOverlayServiceStatusProvider(): OverlayServiceStatusProvider {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val entryPoint = remember(appContext) {
        EntryPointAccessors.fromApplication(appContext, OverlayServiceStatusEntryPoint::class.java)
    }
    return remember(entryPoint) {
        entryPoint.overlayServiceStatusProvider()
    }
}
