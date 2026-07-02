package com.fliptofocus.di

import android.content.Context
import androidx.room.Room
import com.fliptofocus.data.local.AppConfigDao
import com.fliptofocus.data.local.BlockedAppDao
import com.fliptofocus.data.local.FocusSessionDao
import com.fliptofocus.data.local.FlipToFocusDatabase
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
    fun provideDatabase(@ApplicationContext ctx: Context): FlipToFocusDatabase =
        Room.databaseBuilder(
            ctx,
            FlipToFocusDatabase::class.java,
            "fliptofocus.db"
        )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideBlockedAppDao(db: FlipToFocusDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    @Singleton
    fun provideFocusSessionDao(db: FlipToFocusDatabase): FocusSessionDao = db.focusSessionDao()

    @Provides
    @Singleton
    fun provideAppConfigDao(db: FlipToFocusDatabase): AppConfigDao = db.appConfigDao()
}
