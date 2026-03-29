package com.biketracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.biketracker.ui.screen.history.HistoryScreen
import com.biketracker.ui.screen.history.TripDetailScreen
import com.biketracker.ui.screen.home.HomeScreen
import com.biketracker.ui.screen.settings.LocationPickerScreen
import com.biketracker.ui.screen.settings.SettingsScreen
import com.biketracker.ui.screen.tracking.TrackingScreen
import com.biketracker.ui.screen.weekly.WeeklyScreen

object Routes {
    const val HOME = "home"
    const val TRACKING = "tracking"
    const val HISTORY = "history"
    const val TRIP_DETAIL = "trip/{tripId}"
    const val WEEKLY = "weekly"
    const val SETTINGS = "settings"
    const val LOCATION_PICKER = "location_picker/{type}"

    fun tripDetail(tripId: Long) = "trip/$tripId"
    fun locationPicker(type: String) = "location_picker/$type"
}

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartTracking = { navController.navigate(Routes.TRACKING) }
            )
        }
        composable(Routes.TRACKING) {
            TrackingScreen(
                onStop = { navController.popBackStack() }
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onTripClick = { tripId -> navController.navigate(Routes.tripDetail(tripId)) }
            )
        }
        composable(Routes.TRIP_DETAIL) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId")?.toLong() ?: return@composable
            TripDetailScreen(tripId = tripId, onBack = { navController.popBackStack() })
        }
        composable(Routes.WEEKLY) {
            WeeklyScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onPickLocation = { type -> navController.navigate(Routes.locationPicker(type)) }
            )
        }
        composable(Routes.LOCATION_PICKER) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "home"
            LocationPickerScreen(
                type = type,
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
