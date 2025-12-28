package com.example.refocus.feature.common.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.domain.permissions.PermissionStatusProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Composable から Hilt の Singleton 依存を参照するための EntryPoint．
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PermissionStatusEntryPoint {
    fun permissionStatusProvider(): PermissionStatusProvider
}

@Composable
fun rememberPermissionStatusProvider(): PermissionStatusProvider {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val entryPoint =
        remember(appContext) {
            EntryPointAccessors.fromApplication(appContext, PermissionStatusEntryPoint::class.java)
        }
    return remember(entryPoint) {
        entryPoint.permissionStatusProvider()
    }
}
