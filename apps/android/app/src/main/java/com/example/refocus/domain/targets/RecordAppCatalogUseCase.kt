package com.example.refocus.domain.targets

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.util.TimeSource
import com.example.refocus.domain.gateway.AppLabelProvider
import com.example.refocus.domain.repository.AppCatalogRepository
import javax.inject.Inject

class RecordAppCatalogUseCase
    @Inject
    constructor(
        private val appCatalogRepository: AppCatalogRepository,
        private val appLabelProvider: AppLabelProvider,
        private val timeSource: TimeSource,
    ) {
        suspend fun record(packageNames: Set<String>) {
            if (packageNames.isEmpty()) return

            val now = timeSource.nowMillis()
            for (packageName in packageNames) {
                try {
                    val label = appLabelProvider.labelOf(packageName)
                    appCatalogRepository.recordTargetedApp(
                        packageName = packageName,
                        label = label,
                        atMillis = now,
                    )
                } catch (e: Exception) {
                    // 失敗してもアプリ全体は止めない．後で調査できるようにログだけ残す．
                    RefocusLog.wRateLimited(
                        subTag = "AppCatalog",
                        key = "record_fail_$packageName",
                        throwable = e,
                    ) { "Failed to record app catalog for $packageName" }
                }
            }
        }
    }
