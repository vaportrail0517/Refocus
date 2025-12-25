package com.example.refocus.domain.targets

import com.example.refocus.domain.repository.TargetsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateTargetsUseCase @Inject constructor(
    private val targetsRepository: TargetsRepository,
    private val recordAppCatalogUseCase: RecordAppCatalogUseCase,
) {
    suspend fun updateTargets(newTargets: Set<String>) {
        val current = targetsRepository.observeTargets().first()
        val added = newTargets - current

        // 先に targets を更新し，その上で catalog を追記する．
        targetsRepository.setTargets(newTargets, recordEvent = true)

        if (added.isNotEmpty()) {
            recordAppCatalogUseCase.record(added)
        }
    }
}
