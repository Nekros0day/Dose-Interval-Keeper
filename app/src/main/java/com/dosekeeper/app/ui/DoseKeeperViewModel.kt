package com.dosekeeper.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DosePlanGroup
import com.dosekeeper.app.data.DoseRepository
import com.dosekeeper.app.data.DoseState
import com.dosekeeper.app.data.InteractionRule
import com.dosekeeper.app.data.SupplementTemplate
import com.dosekeeper.app.data.TimingPreference
import com.dosekeeper.app.notifications.DoseNotificationManager
import com.dosekeeper.app.scheduling.SupplementScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DoseKeeperUiState(
    val doseState: DoseState = DoseState(),
    val templates: List<SupplementTemplate> = emptyList(),
    val interactions: List<InteractionRule> = emptyList(),
    val plan: List<DosePlanGroup> = emptyList(),
)

class DoseKeeperViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DoseRepository(application)

    val uiState: StateFlow<DoseKeeperUiState> = repository.state.map { doseState ->
        DoseKeeperUiState(
            doseState = doseState,
            templates = repository.templates(),
            interactions = repository.interactions(),
            plan = SupplementScheduler.buildPlan(
                items = doseState.activeItems,
                templates = repository.templates(),
                rules = repository.interactions(),
                quietStartMinute = doseState.quietStartMinute,
                quietEndMinute = doseState.quietEndMinute,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(repository.state.value),
    )

    init {
        reschedule()
    }

    fun addMedication(
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
    ) {
        repository.addMedication(name, intervalMinutes, timingPreference, targetMinuteOfDay)
        reschedule()
    }

    fun addCustomSupplement(
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
        description: String,
    ) {
        repository.addCustomSupplement(name, intervalMinutes, timingPreference, targetMinuteOfDay, description)
        reschedule()
    }

    fun addSupplement(templateId: String) {
        repository.addSupplement(templateId)
        reschedule()
    }

    fun addSupplements(templateIds: List<String>) {
        templateIds.distinct().forEach { repository.addSupplement(it) }
        reschedule()
    }

    fun updateItemSchedule(
        item: DoseItem,
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
        description: String,
    ) {
        repository.updateItemSchedule(
            itemId = item.id,
            name = name,
            intervalMinutes = intervalMinutes,
            timingPreference = timingPreference,
            targetMinuteOfDay = targetMinuteOfDay,
            description = description,
        )
        reschedule()
    }

    fun removeItem(item: DoseItem) {
        repository.removeItem(item.id)
        reschedule()
    }

    fun recordDose(item: DoseItem) {
        repository.recordDose(listOf(item.id))
        reschedule()
    }

    fun recordGroup(itemIds: List<String>) {
        repository.recordDose(itemIds)
        reschedule()
    }

    fun addInteraction(firstItemId: String, secondItemId: String, separationMinutes: Int, reason: String) {
        repository.addInteraction(firstItemId, secondItemId, separationMinutes, reason)
        reschedule()
    }

    fun deleteInteraction(ruleId: String) {
        repository.deleteInteraction(ruleId)
        reschedule()
    }

    fun updateInteraction(ruleId: String, firstItemId: String, secondItemId: String, separationMinutes: Int, reason: String) {
        repository.updateInteraction(ruleId, firstItemId, secondItemId, separationMinutes, reason)
        reschedule()
    }

    fun clearHistory() {
        repository.clearHistory()
        reschedule()
    }

    fun setHistoryRetentionDays(days: Int?) {
        repository.setHistoryRetentionDays(days)
        reschedule()
    }

    fun setQuietHours(startMinute: Int, endMinute: Int) {
        repository.setQuietHours(startMinute, endMinute)
        reschedule()
    }

    private fun reschedule() {
        DoseNotificationManager.ensureChannels(getApplication())
        DoseNotificationManager.scheduleAll(getApplication(), repository)
    }

    private fun buildUiState(doseState: DoseState): DoseKeeperUiState = DoseKeeperUiState(
        doseState = doseState,
        templates = repository.templates(),
        interactions = repository.interactions(),
        plan = SupplementScheduler.buildPlan(
            items = doseState.activeItems,
            templates = repository.templates(),
            rules = repository.interactions(),
            quietStartMinute = doseState.quietStartMinute,
            quietEndMinute = doseState.quietEndMinute,
        ),
    )
}
