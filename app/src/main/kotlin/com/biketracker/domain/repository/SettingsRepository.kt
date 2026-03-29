package com.biketracker.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val homeLocation: Flow<LatLng?>
    val officeLocation: Flow<LatLng?>
    val workHours: Flow<Float>
    val geofenceRadiusMeters: Flow<Float>
    val packingChecklist: Flow<List<String>>
    suspend fun setHomeLocation(lat: Double, lng: Double)
    suspend fun setOfficeLocation(lat: Double, lng: Double)
    suspend fun setWorkHours(hours: Float)
    suspend fun setGeofenceRadiusMeters(radius: Float)
    suspend fun setPackingChecklist(items: List<String>)
}
