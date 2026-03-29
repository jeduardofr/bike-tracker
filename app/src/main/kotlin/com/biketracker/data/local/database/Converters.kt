package com.biketracker.data.local.database

import androidx.room.TypeConverter
import com.biketracker.data.local.database.entity.TripDirection

class Converters {
    @TypeConverter
    fun fromDirection(direction: TripDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): TripDirection = TripDirection.valueOf(value)
}
