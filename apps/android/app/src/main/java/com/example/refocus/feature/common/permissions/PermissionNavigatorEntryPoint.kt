package com.example.refocus.feature.common.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.gateway.PermissionNavigator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PermissionNavigatorEntryPoint {
    fun permissionNavigator(): PermissionNavigator
}

@Composable
fun rememberPermissionNavigator(): PermissionNavigator {
    val context = LocalContext.current
    val appContext = context.applicationContext

    val entryPoint =
        remember(appContext) {
            EntryPointAccessors.fromApplication(appContext, PermissionNavigatorEntryPoint::class.java)
        }
    return remember(entryPoint) {
        entryPoint.permissionNavigator()
    }
}
