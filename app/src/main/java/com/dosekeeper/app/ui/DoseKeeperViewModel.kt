package com.dosekeeper.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseRepository
import com.dosekeeper.app.data.DoseState
import com.dosekeeper.app.data.SecureNoteStore
import com.dosekeeper.app.data.SupplementGroup
import com.dosekeeper.app.data.SupplementTemplate
import com.dosekeeper.app.notifications.DoseNotificationManager
import com.dosekeeper.app.scheduling.SupplementScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class DoseKeeperUiState(
    val doseState: DoseState = DoseState(),
    val templates: List<SupplementTemplate> = emptyList(),
    val plan: List<SupplementGroup> = emptyList(),
    val unlockedNotes: Map<String, String> = emptyMap(),
    val noteError: String? = null,
)

class DoseKeeperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DoseRepository(application)
    private val secureNotes = SecureNoteStore(application)
    private val unlockedNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    private val noteError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DoseKeeperUiState> = combine(
        repository.state,
        unlockedNotes,
        noteError,
    ) { doseState, notes, error ->
        DoseKeeperUiState(
            doseState = doseState,
            templates = repository.templates(),
            plan = SupplementScheduler.buildPlan(
                items = doseState.activeItems,
                templates = repository.templates(),
                rules = repository.conflicts(),
            ),
            unlockedNotes = notes,
            noteError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DoseKeeperUiState(
            doseState = repository.state.value,
            templates = repository.templates(),
            plan = SupplementScheduler.buildPlan(
                items = repository.state.value.activeItems,
                templates = repository.templates(),
                rules = repository.conflicts(),
            ),
        ),
    )

    init {
        DoseNotificationManager.ensureChannels(application)
        DoseNotificationManager.scheduleAll(application, repository)
    }

    fun addMedication(name: String, intervalMinutes: Int) {
        repository.addMedication(name, intervalMinutes)
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    fun addSupplement(templateId: String) {
        repository.addSupplement(templateId)
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    fun removeItem(item: DoseItem) {
        repository.removeItem(item.id)
        unlockedNotes.update { it - item.id }
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    fun recordDose(item: DoseItem) {
        val touched = repository.recordDose(listOf(item.id))
        touched.forEach { DoseNotificationManager.showCountdown(getApplication(), it) }
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    fun recordGroup(itemIds: List<String>) {
        val touched = repository.recordDose(itemIds)
        touched.forEach { DoseNotificationManager.showCountdown(getApplication(), it) }
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    fun unlockNotes(itemId: String) {
        runCatching { secureNotes.readNote(itemId) }
            .onSuccess { note ->
                noteError.value = null
                unlockedNotes.update { it + (itemId to note) }
            }
            .onFailure {
                noteError.value = "Could not unlock notes on this device."
            }
    }

    fun saveNote(itemId: String, note: String) {
        runCatching { secureNotes.saveNote(itemId, note) }
            .onSuccess {
                noteError.value = null
                unlockedNotes.update { it + (itemId to note) }
            }
            .onFailure {
                noteError.value = "Could not save encrypted note."
            }
    }
}
