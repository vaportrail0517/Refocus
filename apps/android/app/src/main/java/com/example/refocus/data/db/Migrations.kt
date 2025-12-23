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
