package com.example.refocus.app.di

import android.content.Context
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.gateway.AppLabelProvider
import com.example.refocus.domain.gateway.ForegroundAppObserver
import com.example.refocus.domain.overlay.port.OverlayServiceController
import com.example.refocus.domain.overlay.port.OverlayServiceStatusProvider
import com.example.refocus.domain.permissions.PermissionStatusProvider
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.suggestion.GaussianCircularTimeSlotWeightModel
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.domain.suggestion.SuggestionSelector
import com.example.refocus.domain.suggestion.TimeSlotWeightModel
import com.example.refocus.domain.timeline.EventRecorder
import com.example.refocus.gateway.AppIconProvider
import com.example.refocus.gateway.LaunchableAppProvider
import com.example.refocus.gateway.PermissionNavigator
import com.example.refocus.system.appinfo.AndroidAppIconResolver
import com.example.refocus.system.appinfo.AndroidAppLabelResolver
import com.example.refocus.system.appinfo.AndroidLaunchableAppProvider
import com.example.refocus.system.appinfo.AppLabelProviderImpl
import com.example.refocus.system.appinfo.AppLabelResolver
import com.example.refocus.system.monitor.ForegroundAppMonitor
import com.example.refocus.system.monitor.ForegroundAppObserverImpl
import com.example.refocus.system.overlay.service.OverlayServiceControllerImpl
import com.example.refocus.system.overlay.service.OverlayServiceStatusProviderImpl
import com.example.refocus.system.permissions.AndroidPermissionNavigator
import com.example.refocus.system.permissions.AndroidPermissionStatusProvider
import com.example.refocus.system.time.SystemTimeSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {
    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = SystemTimeSource()

    @Provides
    @Singleton
    fun provideForegroundAppMonitor(
        @ApplicationContext context: Context,
        timeSource: TimeSource,
    ): ForegroundAppMonitor = ForegroundAppMonitor(context, timeSource)

    @Provides
    @Singleton
    fun provideForegroundAppObserver(monitor: ForegroundAppMonitor): ForegroundAppObserver =
        ForegroundAppObserverImpl(monitor)

    @Provides
    @Singleton
    fun provideSuggestionEngine(): SuggestionEngine = SuggestionEngine()

    @Provides
    @Singleton
    fun provideTimeSlotWeightModel(): TimeSlotWeightModel = GaussianCircularTimeSlotWeightModel()

    @Provides
    @Singleton
    fun provideSuggestionSelector(timeSlotWeightModel: TimeSlotWeightModel): SuggestionSelector =
        SuggestionSelector(timeSlotWeightModel)

    @Provides
    @Singleton
    fun provideEventRecorder(
        timeSource: TimeSource,
        timelineRepository: TimelineRepository,
    ): EventRecorder = EventRecorder(timeSource, timelineRepository)

    @Provides
    @Singleton
    fun provideAppLabelResolver(
        @ApplicationContext context: Context,
    ): AppLabelResolver = AndroidAppLabelResolver(context)

    @Provides
    @Singleton
    fun provideAppLabelProvider(resolver: AppLabelResolver): AppLabelProvider = AppLabelProviderImpl(resolver)

    @Provides
    @Singleton
    fun provideAppIconProvider(
        @ApplicationContext context: Context,
    ): AppIconProvider = AndroidAppIconResolver(context)

    @Provides
    @Singleton
    fun provideLaunchableAppProvider(
        @ApplicationContext context: Context,
    ): LaunchableAppProvider = AndroidLaunchableAppProvider(context)

    @Provides
    @Singleton
    fun providePermissionStatusProvider(impl: AndroidPermissionStatusProvider): PermissionStatusProvider = impl

    @Provides
    @Singleton
    fun providePermissionNavigator(impl: AndroidPermissionNavigator): PermissionNavigator = impl

    @Provides
    @Singleton
    fun provideOverlayServiceController(
        @ApplicationContext context: Context,
    ): OverlayServiceController = OverlayServiceControllerImpl(context)

    @Provides
    @Singleton
    fun provideOverlayServiceStatusProvider(): OverlayServiceStatusProvider = OverlayServiceStatusProviderImpl()
}
