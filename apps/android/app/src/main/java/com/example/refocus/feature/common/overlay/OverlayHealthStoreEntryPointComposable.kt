package com.example.refocus.feature.common.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.gateway.di.OverlayHealthStoreEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun rememberOverlayHealthStore(): OverlayHealthStore {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val entryPoint =
        remember(appContext) {
            EntryPointAccessors.fromApplication(
                appContext,
                OverlayHealthStoreEntryPoint::class.java,
            )
        }

    return remember(entryPoint) {
        entryPoint.overlayHealthStore()
    }
}
