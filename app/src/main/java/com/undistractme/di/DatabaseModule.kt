package com.undistractme.di

import android.content.Context
import androidx.room.Room
import com.undistractme.data.local.AppConfigDao
import com.undistractme.data.local.BlockedAppDao
import com.undistractme.data.local.FocusSessionDao
import com.undistractme.data.local.UnDistractDatabase
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
    fun provideDatabase(@ApplicationContext ctx: Context): UnDistractDatabase =
        Room.databaseBuilder(
            ctx,
            UnDistractDatabase::class.java,
            "undistract.db"
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideBlockedAppDao(db: UnDistractDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    @Singleton
    fun provideFocusSessionDao(db: UnDistractDatabase): FocusSessionDao = db.focusSessionDao()

    @Provides
    @Singleton
    fun provideAppConfigDao(db: UnDistractDatabase): AppConfigDao = db.appConfigDao()
}
