package com.example.refocus.app.di

import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.datastore.OnboardingDataStore
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.repository.OnboardingRepositoryImpl
import com.example.refocus.data.repository.SettingsRepositoryImpl
import com.example.refocus.data.repository.SuggestionsRepositoryImpl
import com.example.refocus.data.repository.TargetsRepositoryImpl
import com.example.refocus.data.repository.TimelineRepositoryImpl
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.repository.SuggestionsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.repository.TimelineRepository
import com.example.refocus.domain.timeline.EventRecorder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideOnboardingRepository(
        dataStore: OnboardingDataStore,
    ): OnboardingRepository = OnboardingRepositoryImpl(dataStore)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: SettingsDataStore,
    ): SettingsRepository = SettingsRepositoryImpl(dataStore)

    @Provides
    @Singleton
    fun provideTargetsRepository(
        dataStore: TargetsDataStore,
        eventRecorder: EventRecorder,
    ): TargetsRepository = TargetsRepositoryImpl(dataStore, eventRecorder)

    @Provides
    @Singleton
    fun provideSuggestionsRepository(
        suggestionDao: SuggestionDao,
        timeSource: TimeSource,
    ): SuggestionsRepository = SuggestionsRepositoryImpl(
        suggestionDao = suggestionDao,
        timeSource = timeSource,
    )

    @Provides
    @Singleton
    fun provideTimelineRepository(
        timelineEventDao: TimelineEventDao,
    ): TimelineRepository = TimelineRepositoryImpl(timelineEventDao)

}
