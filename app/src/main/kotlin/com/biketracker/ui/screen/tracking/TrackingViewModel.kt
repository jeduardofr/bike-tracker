package com.biketracker.ui.screen.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.service.TrackingState
import com.biketracker.service.TrackingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackingStateHolder: TrackingStateHolder
) : ViewModel() {

    val trackingState = trackingStateHolder.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), TrackingState.Idle
    )
}
