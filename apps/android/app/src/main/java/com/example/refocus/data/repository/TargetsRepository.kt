package com.example.refocus.data.repository

import com.example.refocus.data.datastore.TargetsDataStore
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.flow.Flow

class TargetsRepository(
    private val targetsDataStore: TargetsDataStore,
    private val eventRecorder: EventRecorder,
) {
    fun observeTargets(): Flow<Set<String>> = targetsDataStore.targetPackagesFlow

    suspend fun setTargets(targets: Set<String>) {
        targetsDataStore.updateTargets(targets)
        eventRecorder.onTargetAppsChanged(targets)
    }
}