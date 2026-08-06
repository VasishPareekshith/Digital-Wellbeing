package com.example.offlineanomaly.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.offlineanomaly.db.AppDatabase
import com.example.offlineanomaly.ui.TimeLossEpisode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimeLossViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).anomalyRecordDao()

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd")
    private val zoneId = ZoneId.systemDefault()

    val episodes = dao.getAllAnomalies()
        .map { entities ->
            entities.map { entity ->
                val instant = Instant.ofEpochMilli(entity.timestamp)
                val dateTime = instant.atZone(zoneId)

                TimeLossEpisode(
                    appName = entity.primaryApp.replaceFirstChar { it.uppercase() },
                    date = dateTime.format(dateFormatter),
                    startTime = dateTime.format(timeFormatter),
                    durationMins = entity.duration,
                    severity = if (entity.score > 0.7) "Critical" else "Warning"
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
}