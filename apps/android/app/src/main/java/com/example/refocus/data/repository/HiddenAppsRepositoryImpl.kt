package com.example.refocus.data.repository

import com.example.refocus.data.datastore.HiddenAppsDataStore
import com.example.refocus.domain.repository.HiddenAppsRepository
import kotlinx.coroutines.flow.Flow

class HiddenAppsRepositoryImpl(
    private val hiddenAppsDataStore: HiddenAppsDataStore,
) : HiddenAppsRepository {
    override fun observeHiddenApps(): Flow<Set<String>> = hiddenAppsDataStore.hiddenPackagesFlow

    override suspend fun setHiddenApps(
        hiddenApps: Set<String>,
        recordEvent: Boolean,
    ) {
        // Phase2: イベント記録はまだ導入しないため，永続化のみを行う．
        hiddenAppsDataStore.updateHiddenApps(hiddenApps)
        if (recordEvent) {
            // Phase4 で EventRecorder と統合する．
        }
    }

    override suspend fun clearForReset() {
        setHiddenApps(emptySet(), recordEvent = false)
    }
}
