package com.biketracker.data.repository

import com.biketracker.data.local.preferences.AppPreferences
import com.biketracker.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val prefs: AppPreferences
) : SettingsRepository {

    override val homeLocation: Flow<LatLng?> = combine(prefs.homeLat, prefs.homeLng) { lat, lng ->
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }

    override val officeLocation: Flow<LatLng?> = combine(prefs.officeLat, prefs.officeLng) { lat, lng ->
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }

    override val workHours: Flow<Float> = prefs.workHours
    override val geofenceRadiusMeters: Flow<Float> = prefs.geofenceRadiusMeters

    override val packingChecklist: Flow<List<String>> = prefs.packingChecklist.map { json ->
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    }

    override suspend fun setHomeLocation(lat: Double, lng: Double) = prefs.setHomeLocation(lat, lng)
    override suspend fun setOfficeLocation(lat: Double, lng: Double) = prefs.setOfficeLocation(lat, lng)
    override suspend fun setWorkHours(hours: Float) = prefs.setWorkHours(hours)
    override suspend fun setGeofenceRadiusMeters(radius: Float) = prefs.setGeofenceRadiusMeters(radius)

    override suspend fun setPackingChecklist(items: List<String>) {
        val arr = JSONArray().also { arr -> items.forEach { arr.put(it) } }
        prefs.setPackingChecklist(arr.toString())
    }
}
