package com.example.refocus.data.repository

import com.example.refocus.core.model.TimelineEvent
import com.example.refocus.data.db.dao.TimelineEventDao
import com.example.refocus.data.db.entity.TimelineEventEntity
import com.example.refocus.data.db.mapper.TimelineEventEntityMapper

internal class SeedEventsLoader(
    private val dao: TimelineEventDao,
    private val mapper: TimelineEventEntityMapper,
) {
    suspend fun load(beforeMillis: Long): List<TimelineEvent> {
        // 状態復元に必要な最小限の「直前イベント」を拾う．
        // 完璧なスナップショットではなく，ウィンドウ購読の欠損を埋めるための保険．
        val seedEntities = mutableListOf<TimelineEventEntity>()

        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_SERVICE,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_SCREEN,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_FOREGROUND_APP,
                beforeMillis,
            )?.let { seedEntities += it }
        dao
            .getLatestEventOfKindBefore(
                TimelineEventEntityMapper.KIND_TARGET_APPS_CHANGED,
                beforeMillis,
            )?.let { seedEntities += it }

        // Permission は permissionKind ごとに直前 1 件が欲しい．
        // SQL 側で group by するより，件数が小さいことを前提に Kotlin 側でユニーク化する．
        val recentPerms =
            dao.getLatestEventsOfKindBefore(
                kind = TimelineEventEntityMapper.KIND_PERMISSION,
                beforeMillis = beforeMillis,
                limit = 32,
            )
        val pickedPermissionKinds = mutableSetOf<String>()
        for (e in recentPerms) {
            val k = e.permissionKind ?: continue
            if (pickedPermissionKinds.add(k)) {
                seedEntities += e
            }
        }

        return seedEntities
            .mapNotNull { mapper.toDomain(it) }
            .sortedBy { it.timestampMillis }
    }
}
