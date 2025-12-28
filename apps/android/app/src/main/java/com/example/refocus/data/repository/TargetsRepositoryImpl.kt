package com.example.refocus.data.repository

import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.flow.Flow

class TargetsRepositoryImpl(
    private val targetsDataStore: TargetsDataStore,
    private val eventRecorder: EventRecorder,
) : TargetsRepository {
    override fun observeTargets(): Flow<Set<String>> = targetsDataStore.targetPackagesFlow

    override suspend fun setTargets(
        targets: Set<String>,
        recordEvent: Boolean,
    ) {
        targetsDataStore.updateTargets(targets)
        if (recordEvent) {
            eventRecorder.onTargetAppsChanged(targets)
        }
    }

    override suspend fun clearForReset() {
        setTargets(emptySet(), recordEvent = false)
    }
}
