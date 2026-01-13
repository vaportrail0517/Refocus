package com.example.refocus.app.di

import android.content.Context
import com.example.refocus.data.datastore.OnboardingDataStore
import com.example.refocus.data.datastore.OverlayHealthDataStore
import com.example.refocus.data.datastore.PermissionStateDataStore
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.domain.overlay.port.OverlayHealthStore
import com.example.refocus.domain.permissions.port.PermissionSnapshotStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): SettingsDataStore = SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideOnboardingDataStore(
        @ApplicationContext context: Context,
    ): OnboardingDataStore = OnboardingDataStore(context)

    @Provides
    @Singleton
    fun provideTargetsDataStore(
        @ApplicationContext context: Context,
    ): TargetsDataStore = TargetsDataStore(context)

    @Provides
    @Singleton
    fun provideOverlayHealthStore(
        @ApplicationContext context: Context,
    ): OverlayHealthStore = OverlayHealthDataStore(context)

    @Provides
    @Singleton
    fun providePermissionSnapshotStore(
        @ApplicationContext context: Context,
    ): PermissionSnapshotStore = PermissionStateDataStore(context)
}
