package com.example.refocus.data.repository

import com.example.refocus.data.db.dao.AppCatalogDao
import com.example.refocus.data.db.entity.AppCatalogEntity
import com.example.refocus.domain.repository.AppCatalogRepository

class AppCatalogRepositoryImpl(
    private val dao: AppCatalogDao,
) : AppCatalogRepository {

    override suspend fun recordTargetedApp(
        packageName: String,
        label: String,
        atMillis: Long,
    ) {
        // 初回値を守るため IGNORE で insert し，常に lastKnown を更新する．
        dao.insertIgnore(
            AppCatalogEntity(
                packageName = packageName,
                firstTargetedAtMillis = atMillis,
                firstTargetedLabel = label,
                lastKnownLabel = label,
                lastUpdatedAtMillis = atMillis,
            )
        )

        // 既に存在していても lastKnown は更新したいので UPDATE する．
        dao.updateLastKnownLabel(
            packageName = packageName,
            label = label,
            updatedAtMillis = atMillis,
        )
    }

    override suspend fun getFirstTargetedLabel(packageName: String): String? =
        dao.getFirstTargetedLabel(packageName)

    override suspend fun getLastKnownLabel(packageName: String): String? =
        dao.getLastKnownLabel(packageName)
}
