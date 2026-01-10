package com.example.refocus.gateway.di
import android.content.Context
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayHealthStoreEntryPoint {
    fun overlayHealthStore(): OverlayHealthStore
}

private const val TAG = "OverlayHealthStoreEntryPoint"

fun Context.getOverlayHealthStoreOrNull(): OverlayHealthStore? {
    val appContext = applicationContext
    return try {
        EntryPointAccessors
            .fromApplication(
                appContext,
                OverlayHealthStoreEntryPoint::class.java,
            ).overlayHealthStore()
    } catch (e: Exception) {
        RefocusLog.w(TAG, e) { "Failed to get OverlayHealthStore" }
        null
    }
}
