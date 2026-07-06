package com.biketracker.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biketracker.data.local.database.AppDatabase
import com.biketracker.data.local.database.dao.RoutePointDao
import com.biketracker.data.local.database.dao.TripDao
import com.biketracker.data.local.database.dao.WorkSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE trips ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE trips ADD COLUMN syncedAt INTEGER")
            // backfill existing trips with a random stable id
            db.execSQL("UPDATE trips SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bike_tracker.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun provideRoutePointDao(db: AppDatabase): RoutePointDao = db.routePointDao()

    @Provides
    fun provideWorkSessionDao(db: AppDatabase): WorkSessionDao = db.workSessionDao()
}
