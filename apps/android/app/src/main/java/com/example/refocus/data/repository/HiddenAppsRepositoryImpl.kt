package com.example.refocus.data.repository

import com.example.refocus.data.datastore.HiddenAppsDataStore
import com.example.refocus.domain.repository.HiddenAppsRepository
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class HiddenAppsRepositoryImpl(
    private val hiddenAppsDataStore: HiddenAppsDataStore,
    private val eventRecorder: EventRecorder,
) : HiddenAppsRepository {
    override fun observeHiddenApps(): Flow<Set<String>> = hiddenAppsDataStore.hiddenPackagesFlow

    override suspend fun setHiddenApps(
        hiddenApps: Set<String>,
        recordEvent: Boolean,
    ) {
        val current = hiddenAppsDataStore.hiddenPackagesFlow.first()
        if (current == hiddenApps) return

        hiddenAppsDataStore.updateHiddenApps(hiddenApps)
        if (recordEvent) {
            eventRecorder.onHiddenAppsChanged(hiddenApps)
        }
    }

    override suspend fun clearForReset() {
        setHiddenApps(emptySet(), recordEvent = false)
    }
}
