package com.undistractme.di

import com.undistractme.data.repository.AppConfigRepositoryImpl
import com.undistractme.data.repository.BlockedAppRepositoryImpl
import com.undistractme.data.repository.FocusSessionRepositoryImpl
import com.undistractme.domain.repository.AppConfigRepository
import com.undistractme.domain.repository.BlockedAppRepository
import com.undistractme.domain.repository.FocusSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlockedAppRepository(
        impl: BlockedAppRepositoryImpl
    ): BlockedAppRepository

    @Binds
    @Singleton
    abstract fun bindFocusSessionRepository(
        impl: FocusSessionRepositoryImpl
    ): FocusSessionRepository

    @Binds
    @Singleton
    abstract fun bindAppConfigRepository(
        impl: AppConfigRepositoryImpl
    ): AppConfigRepository
}
