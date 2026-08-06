package com.example.offlineanomaly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlineanomaly.repo.DashboardRepository
import com.example.offlineanomaly.ui.DashboardMode
import com.example.offlineanomaly.ui.DashboardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DashboardRepository(application)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Toggles between Real System Usage and the Simulation (Mock) Data
     * Ideal for project presentations to show high-risk scenarios.
     */
    fun toggleDemoMode() {
        viewModelScope.launch {
            if (_state.value.isDemoMode) {
                refresh()
            } else {
                _state.update { it.copy(isLoading = true) }

                val mockData = repository.getMockState(LocalDate.now())

                _state.value = mockData
            }
        }
    }

    fun changeDate(newDate: LocalDate) {
        viewModelScope.launch {
            if (_state.value.isDemoMode) {
                val dynamicMock = repository.getMockState(newDate)
                _state.value = dynamicMock
            } else {
                loadDaily(newDate)
            }
        }
    }

    fun switchMode(mode: DashboardMode) {
        viewModelScope.launch {
            if (_state.value.isDemoMode) {
                _state.update { it.copy(mode = mode) }
            } else {
                when (mode) {
                    DashboardMode.DAILY -> loadDaily(_state.value.selectedDate)
                    DashboardMode.WEEKLY -> loadWeekly()
                }
            }
        }
    }

    /**
     * Forces a fresh scan of the UsageStatsManager and ML Inference
     */

    fun refresh() {
        viewModelScope.launch {
            val scenario = com.example.offlineanomaly.repo.MockManager.currentScenario

            val stats = repository.getAggregatedStats(LocalDate.now())

            val (label, reason, status, percent) = when(scenario) {
                "OPTIMAL" -> Quadruple(
                    "Optimal Focus",
                    "Usage patterns align with peak productivity windows. Minimal distractions detected.",
                    "Stable",
                    0.05f
                )
                "RECOVERY" -> Quadruple(
                    "Regaining Balance",
                    "Entertainment spikes are subsiding. Productivity is returning to the 7-day average.",
                    "Recovering",
                    0.35f
                )
                "WEEKEND" -> Quadruple(
                    "Planned Leisure",
                    "Shift to entertainment apps is consistent with weekend behavioral patterns. No risk detected.",
                    "Healthy Break",
                    0.10f
                )
                "BURNOUT" -> Quadruple(
                    "Behavioral Anomaly",
                    "Extreme surge in low-engagement apps (Instagram/YouTube) detected. Digital fatigue likely.",
                    "Critical Risk",
                    0.85f
                )
                "FRAGMENTED" -> Quadruple(
                    "Context Switching",
                    "High frequency of app-swapping detected. Focus depth is below 10%.",
                    "High Volatility",
                    0.65f
                )
                else -> Quadruple("Normal Usage", "Behavior is within standard deviation.", "Stable", 0.10f)
            }

            _state.value = _state.value.copy(
                behaviorLabel = label,
                behaviorReason = reason,
                anomalyStatus = status,
                anomalyPercent = percent,
                totalMinutes = stats.total,
                productivityMinutes = stats.prod,
                entertainmentMinutes = stats.ent,
                socialMinutes = stats.soc,
                topApps = stats.topApps
            )
        }
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    /**
     * Loads daily stats for a specific calendar date
     */
    fun loadDaily(date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isDemoMode = false) }
            val newState = repository.loadDaily(date)
            _state.value = newState
        }
    }

    /**
     * Loads the 7-day trend graph data
     */
    fun loadWeekly() {
        viewModelScope.launch {
            if (_state.value.isDemoMode) {
                _state.update { it.copy(mode = DashboardMode.WEEKLY) }
            } else {
                _state.update { it.copy(isLoading = true) }
                val newState = repository.loadWeekly()
                _state.value = newState
            }
        }
    }

    fun onViewTypeChanged(isWeekly: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val newState = if (isWeekly) {
                repository.loadWeekly()
            } else {
                repository.loadDaily(_state.value.selectedDate)
            }

            _state.value = newState
        }
    }
    private val _uiState = MutableStateFlow(DashboardState())
    val uiState: StateFlow<DashboardState> = _uiState.asStateFlow()
    fun onDateSelected(date: LocalDate) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            val dailyData = repository.getAggregatedStats(date, isWeekly = false)

            val score = repository.runInference(date)

            _state.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    selectedDate = date,
                    topApps = dailyData.topApps,

                    totalMinutes = dailyData.total,
                    productivityMinutes = dailyData.prod,
                    entertainmentMinutes = dailyData.ent,
                    socialMinutes = dailyData.soc,
                    utilityMinutes = dailyData.util,

                    anomalyPercent = score,
                    stabilityScore = (100 - (score * 100)).toInt().coerceIn(0, 100),

                    anomalyStatus = if (score > 0.5f) "HIGH" else "NORMAL",
                    stabilityStatus = if (score < 0.2f) "Stable" else "Drifting",

                    mode = DashboardMode.DAILY
                )
            }
        }
    }
}
