package com.biketracker.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LNG = doublePreferencesKey("home_lng")
        val OFFICE_LAT = doublePreferencesKey("office_lat")
        val OFFICE_LNG = doublePreferencesKey("office_lng")
        val WORK_HOURS = floatPreferencesKey("work_hours")
        val GEOFENCE_RADIUS_M = floatPreferencesKey("geofence_radius_m")
        val PACKING_CHECKLIST = stringPreferencesKey("packing_checklist")
    }

    val homeLat: Flow<Double?> = context.dataStore.data.map { it[Keys.HOME_LAT] }
    val homeLng: Flow<Double?> = context.dataStore.data.map { it[Keys.HOME_LNG] }
    val officeLat: Flow<Double?> = context.dataStore.data.map { it[Keys.OFFICE_LAT] }
    val officeLng: Flow<Double?> = context.dataStore.data.map { it[Keys.OFFICE_LNG] }
    val workHours: Flow<Float> = context.dataStore.data.map { it[Keys.WORK_HOURS] ?: 8f }
    val geofenceRadiusMeters: Flow<Float> = context.dataStore.data.map { it[Keys.GEOFENCE_RADIUS_M] ?: 150f }
    val packingChecklist: Flow<String> = context.dataStore.data.map { it[Keys.PACKING_CHECKLIST] ?: "[]" }

    suspend fun setHomeLocation(lat: Double, lng: Double) {
        context.dataStore.edit {
            it[Keys.HOME_LAT] = lat
            it[Keys.HOME_LNG] = lng
        }
    }

    suspend fun setOfficeLocation(lat: Double, lng: Double) {
        context.dataStore.edit {
            it[Keys.OFFICE_LAT] = lat
            it[Keys.OFFICE_LNG] = lng
        }
    }

    suspend fun setWorkHours(hours: Float) {
        context.dataStore.edit { it[Keys.WORK_HOURS] = hours }
    }

    suspend fun setGeofenceRadiusMeters(radius: Float) {
        context.dataStore.edit { it[Keys.GEOFENCE_RADIUS_M] = radius }
    }

    suspend fun setPackingChecklist(json: String) {
        context.dataStore.edit { it[Keys.PACKING_CHECKLIST] = json }
    }
}
