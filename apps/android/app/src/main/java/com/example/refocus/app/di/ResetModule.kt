package com.example.refocus.app.di

import com.example.refocus.app.reset.AppDataResetterImpl
import com.example.refocus.domain.reset.port.AppDataResetter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ResetModule {
    @Provides
    @Singleton
    fun provideAppDataResetter(impl: AppDataResetterImpl): AppDataResetter = impl
}
