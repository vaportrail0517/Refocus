package com.example.refocus.data.repository

import com.example.refocus.data.datastore.TargetsDataStore
import kotlinx.coroutines.flow.Flow

class TargetsRepository(
    private val targetsDataStore: TargetsDataStore
) {
    fun observeTargets(): Flow<Set<String>> = targetsDataStore.targetPackagesFlow

    suspend fun setTargets(targets: Set<String>) {
        targetsDataStore.updateTargets(targets)
    }
}
