package com.biketracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.biketracker.data.local.database.dao.RoutePointDao
import com.biketracker.data.local.database.dao.TripDao
import com.biketracker.data.local.database.dao.WorkSessionDao
import com.biketracker.data.local.database.entity.RoutePointEntity
import com.biketracker.data.local.database.entity.TripEntity
import com.biketracker.data.local.database.entity.WorkSessionEntity

@Database(
    entities = [TripEntity::class, RoutePointEntity::class, WorkSessionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun routePointDao(): RoutePointDao
    abstract fun workSessionDao(): WorkSessionDao
}
