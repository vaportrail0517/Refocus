package com.example.refocus.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefocusDatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RefocusDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate8To9_settingsChangedExtraIsNormalized() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration-8-9.db"

        // v8 のDBを schema JSON から作成し，旧形式（extra="key=value"）の行を投入する．
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                """
                INSERT INTO timeline_events(timestampMillis, kind, extra)
                VALUES(1000, 'SettingsChanged', 'timerDisplayMode=session=elapsed')
                """.trimIndent()
            )
            close()
        }

        // Room を通常通り開く（MIGRATION_8_9 を適用）
        val db = Room.databaseBuilder(context, RefocusDatabase::class.java, dbName)
            .addMigrations(MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        val cursor = db.openHelper.writableDatabase.query(
            "SELECT extraKey, extraValue FROM timeline_events WHERE kind='SettingsChanged' LIMIT 1"
        )

        cursor.use {
            assertTrue(it.moveToFirst())
            val k = it.getString(0)
            val v = it.getString(1)

            // 最初の '=' で split し，残りは value に残ることを保証する．
            assertEquals("timerDisplayMode", k)
            assertEquals("session=elapsed", v)
        }

        db.close()
    }
}
