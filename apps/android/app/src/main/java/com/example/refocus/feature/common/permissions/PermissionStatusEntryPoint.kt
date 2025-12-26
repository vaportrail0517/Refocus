package com.example.refocus.feature.common.permissions

import com.example.refocus.system.permissions.PermissionStatusProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Composable から Hilt の Singleton 依存を参照するための EntryPoint．
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PermissionStatusEntryPoint {
    fun permissionStatusProvider(): PermissionStatusProvider
}
