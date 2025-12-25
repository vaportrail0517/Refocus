package com.example.refocus.app.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import com.example.refocus.data.db.MIGRATION_8_9
import com.example.refocus.data.db.MIGRATION_9_10
import com.example.refocus.data.db.REFOCUS_DB_NAME
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.db.dao.AppCatalogDao
import com.example.refocus.data.db.dao.SuggestionDao
import com.example.refocus.data.db.dao.TimelineEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): RefocusDatabase {
        val builder = Room.databaseBuilder(
            context,
            RefocusDatabase::class.java,
            REFOCUS_DB_NAME
        )
            .addMigrations(MIGRATION_8_9, MIGRATION_9_10)

        val isDebuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        if (isDebuggable) {
            // 開発中はスキーマ変更が頻繁に起きるため，古いDBを破棄して再作成してよい
            builder.fallbackToDestructiveMigration(true)
        } else {
            // リリースビルドでは，意図しないデータ消失を防ぐため destructive を原則禁止する
            // ただし，リリース前の内部版（DB version 2..7）からのアップデートでクラッシュさせないため，
            // それらのバージョンに限って破棄を許可する．最初の公開版以降は migration を追加すること．
            builder.fallbackToDestructiveMigrationFrom(2, 3, 4, 5, 6, 7)
        }

        return builder.build()
    }

    @Provides
    fun provideTimelineEventDao(db: RefocusDatabase): TimelineEventDao =
        db.timelineEventDao()

    @Provides
    fun provideSuggestionDao(db: RefocusDatabase): SuggestionDao =
        db.suggestionDao()

    @Provides
    fun provideAppCatalogDao(db: RefocusDatabase): AppCatalogDao =
        db.appCatalogDao()
}
