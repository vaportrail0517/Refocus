package com.example.refocus.data

import android.app.Application
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.repository.SessionRepository
import com.example.refocus.data.repository.SessionRepositoryImpl
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.data.repository.TargetsRepository
import com.example.refocus.data.db.dao.SessionPauseResumeDao
/**
 * Application から Repository を組み立てるヘルパー。
 * 将来 SessionRepository / SettingsRepository もここに集約する。
 */
class RepositoryProvider(
    private val application: Application
) {

    val targetsRepository: TargetsRepository by lazy {
        val dataStore = TargetsDataStore(application)
        TargetsRepository(dataStore)
    }
    val sessionRepository: SessionRepository by lazy {
        val db = RefocusDatabase.getInstance(application)
        val sessionDao = db.sessionDao()
        val pauseResumeDao = db.sessionPauseResumeDao()
        SessionRepositoryImpl(
            sessionDao = sessionDao,
            pauseResumeDao = pauseResumeDao
        )
    }
    val settingsRepository: SettingsRepository by lazy {
        val dataStore = SettingsDataStore(application)
        SettingsRepository(dataStore)
    }

}
