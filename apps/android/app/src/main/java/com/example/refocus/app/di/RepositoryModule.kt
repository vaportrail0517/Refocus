package com.example.refocus.app.di

import android.content.Context
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.dao.SessionEventDao
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.repository.OnboardingRepository
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SessionRepositoryImpl
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.domain.app.AppDataResetter
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
    fun provideSettingsRepository(
        dataStore: SettingsDataStore
    ): SettingsRepository = SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideSessionRepository(
        sessionDao: SessionDao,
        eventDao: SessionEventDao,
        timeSource: TimeSource
    ): SessionRepository = SessionRepositoryImpl(
        sessionDao = sessionDao,
        eventDao = eventDao,
        timeSource = timeSource
    )

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
    fun provideTargetsRepository(
        dataStore: TargetsDataStore
    ): TargetsRepository = TargetsRepository(dataStore)

    @Provides
    @Singleton
    fun provideOnboardingRepository(
        @ApplicationContext context: Context
    ): OnboardingRepository = OnboardingRepository(context)

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
