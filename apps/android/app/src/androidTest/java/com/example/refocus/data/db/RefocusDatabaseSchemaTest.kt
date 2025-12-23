package com.example.refocus.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefocusDatabaseSchemaTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RefocusDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * 今後 DB version を上げる場合は，ここに migration を追加し，
     * 古い version から新しい version へ upgrade できることをテストで保証する．
     *
     * 現時点は「公開前」段階なので，少なくとも現在スキーマが schema JSON と整合していることを検証する．
     */
    @Test
    fun validateCurrentSchema() {
        val dbName = "schema-validation.db"

        helper.createDatabase(dbName, REFOCUS_DB_VERSION).apply {
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            REFOCUS_DB_VERSION,
            true
        )
    }
}
