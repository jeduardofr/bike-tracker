package com.biketracker.ui.util

import androidx.compose.ui.graphics.Color
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.ui.theme.BikeGreen
import com.biketracker.ui.theme.BikeGreenLight
import com.biketracker.ui.theme.BikeOrange

fun TripDirection.label(): String = when (this) {
    TripDirection.HOME_TO_OFFICE -> "Home → Office"
    TripDirection.OFFICE_TO_HOME -> "Office → Home"
    TripDirection.FREE -> "Free ride"
}

fun TripDirection.accentColor(): Color = when (this) {
    TripDirection.HOME_TO_OFFICE -> BikeGreen
    TripDirection.OFFICE_TO_HOME -> BikeGreenLight
    TripDirection.FREE -> BikeOrange
}
