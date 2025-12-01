package com.example.refocus.app.di

import android.content.Context
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.suggestion.SuggestionEngine
import com.example.refocus.system.monitor.ForegroundAppMonitor
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
        timeSource: TimeSource
    ): ForegroundAppMonitor = ForegroundAppMonitor(context, timeSource)

    @Provides
    @Singleton
    fun provideSuggestionEngine(): SuggestionEngine = SuggestionEngine()
}
