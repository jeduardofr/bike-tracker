package com.biketracker.di

import android.content.Context
import androidx.room.Room
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "bike_tracker.db").build()

    @Provides
    fun provideTripDao(db: AppDatabase): TripDao = db.tripDao()

    @Provides
    fun provideRoutePointDao(db: AppDatabase): RoutePointDao = db.routePointDao()

    @Provides
    fun provideWorkSessionDao(db: AppDatabase): WorkSessionDao = db.workSessionDao()
}
