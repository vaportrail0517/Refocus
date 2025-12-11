package com.example.refocus.app.di

import android.content.Context
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.repository.OnboardingRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.repository.TimelineRepository
import com.example.refocus.data.repository.TimelineRepositoryImpl
import com.example.refocus.domain.app.AppDataResetter
import com.example.refocus.domain.timeline.EventRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideOnboardingRepository(
        @ApplicationContext context: Context,
    ): OnboardingRepository = OnboardingRepository(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: SettingsDataStore,
    ): SettingsRepository = SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideTargetsRepository(
        dataStore: TargetsDataStore,
        eventRecorder: EventRecorder,
    ): TargetsRepository = TargetsRepository(dataStore, eventRecorder)

    @Provides
    @Singleton
    fun provideSuggestionsRepository(
        suggestionDao: SuggestionDao,
        timeSource: TimeSource,
    ): SuggestionsRepository = SuggestionsRepository(
        suggestionDao = suggestionDao,
        timeSource = timeSource,
    )

    @Provides
    @Singleton
    fun provideTimelineRepository(
        timelineEventDao: TimelineEventDao,
    ): TimelineRepository = TimelineRepositoryImpl(timelineEventDao)

    @Provides
    @Singleton
    fun provideAppDataResetter(
        database: RefocusDatabase,
        settingsRepository: SettingsRepository,
        targetsRepository: TargetsRepository,
    ): AppDataResetter = AppDataResetter(
        database = database,
        settingsRepository = settingsRepository,
        targetsRepository = targetsRepository,
    )
}
