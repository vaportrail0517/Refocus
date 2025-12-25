package com.example.refocus.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations.
 *
 * 注意:
 * - リリースビルドでは destructive migration を原則禁止しているため，
 *   DB version を上げる場合は必ず migration を追加すること．
 */

/**
 * v9:
 * - timeline_events に extraKey/extraValue を追加し，設定変更イベントを正規化して保存できるようにする．
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) 列追加
        db.execSQL("ALTER TABLE timeline_events ADD COLUMN extraKey TEXT")
        db.execSQL("ALTER TABLE timeline_events ADD COLUMN extraValue TEXT")

        // 2) 既存データの移行
        // v8 までは SettingsChangedEvent を extra に "key=value" として保存していたため，
        // それを extraKey/extraValue に展開しておく．
        // '=' が存在しない場合は key=extra, value='' とする．
        db.execSQL(
            """
            UPDATE timeline_events
            SET
              extraKey = CASE
                WHEN extra IS NULL THEN NULL
                WHEN instr(extra, '=') = 0 THEN extra
                ELSE substr(extra, 1, instr(extra, '=') - 1)
              END,
              extraValue = CASE
                WHEN extra IS NULL THEN NULL
                WHEN instr(extra, '=') = 0 THEN ''
                ELSE substr(extra, instr(extra, '=') + 1)
              END
            WHERE kind = 'SettingsChanged'
              AND (extraKey IS NULL AND extraValue IS NULL)
            """.trimIndent()
        )
    }
}

/**
 * v10:
 * - app_catalog を追加し，一度でも対象にしたアプリの表示名と packageName を永続化する．
 */
val MIGRATION_9_10: Migration = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_catalog (
                packageName TEXT NOT NULL,
                firstTargetedAtMillis INTEGER NOT NULL,
                firstTargetedLabel TEXT NOT NULL,
                lastKnownLabel TEXT NOT NULL,
                lastUpdatedAtMillis INTEGER NOT NULL,
                PRIMARY KEY(packageName)
            )
            """.trimIndent()
        )

        // 既存の targets をここで埋めることはしない（DataStore 由来で Room からは参照できないため）
        // 起動時の bootstrap（EnsureAppCatalogForCurrentTargetsUseCase）で補完する．
    }
}
