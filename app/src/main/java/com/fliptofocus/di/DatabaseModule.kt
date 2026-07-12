package com.fliptofocus.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fliptofocus.data.local.AppConfigDao
import com.fliptofocus.data.local.BlockedAppDao
import com.fliptofocus.data.local.FlipToFocusDatabase
import com.fliptofocus.data.local.FocusSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Adds the `difficulty` column introduced in schema v3, defaulting existing rows to MEDIUM.
     * Using an explicit migration (instead of destructive) preserves the user's blocklist and
     * focus history across the update.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE app_config ADD COLUMN difficulty TEXT NOT NULL DEFAULT 'MEDIUM'"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): FlipToFocusDatabase =
        Room.databaseBuilder(
            ctx,
            FlipToFocusDatabase::class.java,
            "fliptofocus.db"
        )
            .addMigrations(MIGRATION_2_3)
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
