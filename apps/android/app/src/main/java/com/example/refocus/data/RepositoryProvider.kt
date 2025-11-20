package com.example.refocus.data

import android.app.Application
import com.example.refocus.core.util.SystemTimeSource
import com.example.refocus.core.util.TimeSource
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.SuggestionsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SessionRepositoryImpl
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.SuggestionsRepository
import com.example.refocus.data.repository.TargetsRepository

class RepositoryProvider(
    private val application: Application
) {
    private val timeSource: TimeSource by lazy { SystemTimeSource() }
    val targetsRepository: TargetsRepository by lazy {
        val dataStore = TargetsDataStore(application)
        TargetsRepository(dataStore)
    }
    val sessionRepository: SessionRepository by lazy {
        val db = RefocusDatabase.getInstance(application)
        val sessionDao = db.sessionDao()
        val eventDao = db.sessionEventDao()
        SessionRepositoryImpl(
            sessionDao = sessionDao,
            eventDao = eventDao,
            timeSource = timeSource,
        )
    }
    val settingsRepository: SettingsRepository by lazy {
        val dataStore = SettingsDataStore(application)
        SettingsRepository(dataStore)
    }
    val suggestionsRepository: SuggestionsRepository by lazy {
        val dataStore = SuggestionsDataStore(application)
        SuggestionsRepository(
            dataStore = dataStore,
            timeSource = timeSource
        )
    }
}
