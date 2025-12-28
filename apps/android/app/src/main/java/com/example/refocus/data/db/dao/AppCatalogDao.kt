package com.example.refocus.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.refocus.data.db.entity.AppCatalogEntity

@Dao
interface AppCatalogDao {
    /**
     * 初回値（firstTargeted*）を守りたいので IGNORE を使う．
     * 返り値は挿入できた場合 rowId，既に存在した場合 -1 が返る．
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: AppCatalogEntity): Long

    @Query(
        """
        UPDATE app_catalog
        SET lastKnownLabel = :label,
            lastUpdatedAtMillis = :updatedAtMillis
        WHERE packageName = :packageName
        """,
    )
    suspend fun updateLastKnownLabel(
        packageName: String,
        label: String,
        updatedAtMillis: Long,
    ): Int

    @Query(
        """
        SELECT firstTargetedLabel
        FROM app_catalog
        WHERE packageName = :packageName
        LIMIT 1
        """,
    )
    suspend fun getFirstTargetedLabel(packageName: String): String?

    @Query(
        """
        SELECT lastKnownLabel
        FROM app_catalog
        WHERE packageName = :packageName
        LIMIT 1
        """,
    )
    suspend fun getLastKnownLabel(packageName: String): String?
}
