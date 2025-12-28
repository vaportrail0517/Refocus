package com.example.refocus.domain.targets

import com.example.refocus.domain.repository.TargetsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class EnsureAppCatalogForCurrentTargetsUseCase
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val recordAppCatalogUseCase: RecordAppCatalogUseCase,
    ) {
        suspend fun ensure() {
            val current = targetsRepository.observeTargets().first()
            recordAppCatalogUseCase.record(current)
        }
    }
