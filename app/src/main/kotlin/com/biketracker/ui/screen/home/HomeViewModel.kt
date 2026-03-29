package com.biketracker.ui.screen.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.usecase.GetDailyStatsUseCase
import com.biketracker.domain.usecase.StartTripUseCase
import com.biketracker.service.AppNotificationManager
import com.biketracker.service.TrackingService
import com.biketracker.service.TrackingState
import com.biketracker.service.TrackingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val startTripUseCase: StartTripUseCase,
    private val trackingStateHolder: TrackingStateHolder,
    private val getDailyStats: GetDailyStatsUseCase
) : ViewModel() {

    val trackingState = trackingStateHolder.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), TrackingState.Idle
    )

    val dailyStats = getDailyStats().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    fun startTrip(direction: TripDirection) {
        viewModelScope.launch {
            val result = startTripUseCase(direction)
            result
                .onSuccess { tripId ->
                    Log.d(TAG, "Trip started id=$tripId, launching service")
                    val intent = Intent(context, TrackingService::class.java).apply {
                        action = TrackingService.ACTION_START
                        putExtra(TrackingService.EXTRA_TRIP_ID, tripId)
                    }
                    context.startForegroundService(intent)
                }
                .onFailure { e ->
                    Log.e(TAG, "startTripUseCase failed", e)
                }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }

    fun stopTrip() {
        val intent = Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_STOP
        }
        context.startService(intent)
    }
}
