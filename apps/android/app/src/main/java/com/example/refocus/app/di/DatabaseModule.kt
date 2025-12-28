package com.example.refocus.app.di

import android.content.Context
import androidx.room.Room
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
        @ApplicationContext context: Context,
    ): RefocusDatabase =
        Room
            .databaseBuilder(
                context,
                RefocusDatabase::class.java,
                REFOCUS_DB_NAME,
            )
            // リリース前のデバッグ段階では，スキーマ変更のたびに古いDBを破棄して再作成してよい．
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideTimelineEventDao(db: RefocusDatabase): TimelineEventDao = db.timelineEventDao()

    @Provides
    fun provideSuggestionDao(db: RefocusDatabase): SuggestionDao = db.suggestionDao()

    @Provides
    fun provideAppCatalogDao(db: RefocusDatabase): AppCatalogDao = db.appCatalogDao()
}
