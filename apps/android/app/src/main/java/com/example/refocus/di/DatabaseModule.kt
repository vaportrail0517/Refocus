package com.example.refocus.di

import android.content.Context
import androidx.room.Room
import com.example.refocus.data.db.RefocusDatabase
import com.example.refocus.data.db.dao.SessionDao
import com.example.refocus.data.db.dao.SessionEventDao
import com.example.refocus.data.db.dao.SuggestionDao
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
    ): RefocusDatabase = Room.databaseBuilder(
        context,
        RefocusDatabase::class.java,
        "refocus.db"
    )
        .fallbackToDestructiveMigration(true)  // デバッグ用，前バージョンのDBを上書き
        .build()

    @Provides
    fun provideSessionDao(db: RefocusDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideSessionEventDao(db: RefocusDatabase): SessionEventDao = db.sessionEventDao()

    @Provides
    fun provideSuggestionDao(db: RefocusDatabase): SuggestionDao = db.suggestionDao()
}
