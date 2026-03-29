package com.biketracker.ui.screen.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.model.WeeklyStats
import com.biketracker.domain.usecase.GetWeeklyStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class WeeklyViewModel @Inject constructor(getWeeklyStats: GetWeeklyStatsUseCase) : ViewModel() {

    val weeklyStats = getWeeklyStats().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null as WeeklyStats?
    )
}
