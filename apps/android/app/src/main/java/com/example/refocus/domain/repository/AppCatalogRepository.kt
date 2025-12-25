package com.example.refocus.domain.repository

/**
 * 一度でも対象にしたアプリの表示名を永続化する repository．
 */
interface AppCatalogRepository {

    /**
     * packageName の行が無ければ初回値（firstTargeted*）を確定し，
     * 常に lastKnownLabel を更新する．
     */
    suspend fun recordTargetedApp(
        packageName: String,
        label: String,
        atMillis: Long,
    )

    suspend fun getFirstTargetedLabel(packageName: String): String?

    suspend fun getLastKnownLabel(packageName: String): String?
}
