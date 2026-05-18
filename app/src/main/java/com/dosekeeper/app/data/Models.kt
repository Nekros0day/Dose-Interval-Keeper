package com.dosekeeper.app.data

import java.time.Instant
import java.time.LocalTime

enum class DoseKind {
    Medication,
    Supplement,
}

enum class TimingPreference {
    Anytime,
    WithBreakfast,
    WithLunch,
    WithDinner,
    WithFood,
    EmptyStomach,
    Bedtime,
}

data class DoseItem(
    val id: String,
    val name: String,
    val kind: DoseKind,
    val intervalMinutes: Int,
    val accentColor: Long,
    val lastDoseAtMillis: Long? = null,
    val supplementTemplateId: String? = null,
    val targetMinuteOfDay: Int? = null,
    val timingPreference: TimingPreference = TimingPreference.Anytime,
    val description: String = "",
    val active: Boolean = true,
) {
    fun nextAllowedAtMillis(): Long? = lastDoseAtMillis?.plus(intervalMinutes * 60_000L)
}

data class DoseHistoryEntry(
    val id: String,
    val itemId: String,
    val itemName: String,
    val itemKind: DoseKind,
    val takenAtMillis: Long,
)

data class InteractionRule(
    val id: String,
    val firstKey: String,
    val secondKey: String,
    val separationMinutes: Int,
    val reason: String,
    val editable: Boolean = true,
)

data class DoseState(
    val items: List<DoseItem> = emptyList(),
    val history: List<DoseHistoryEntry> = emptyList(),
    val customInteractions: List<InteractionRule> = emptyList(),
    val historyRetentionDays: Int? = 30,
    val quietStartMinute: Int = minuteOfDay(22),
    val quietEndMinute: Int = minuteOfDay(7),
) {
    val activeItems: List<DoseItem> = items.filter { it.active }
}

data class SupplementTemplate(
    val id: String,
    val name: String,
    val category: String,
    val defaultIntervalMinutes: Int,
    val idealMinuteOfDay: Int,
    val timingPreference: TimingPreference,
    val accentColor: Long,
    val description: String,
    val timingNotes: String,
    val interactionNotes: String,
)

data class ScheduledDose(
    val item: DoseItem,
    val scheduledAtMillis: Long,
    val reason: String,
    val notes: List<String>,
)

data class DosePlanGroup(
    val scheduledAtMillis: Long,
    val entries: List<ScheduledDose>,
    val notes: List<String>,
) {
    val itemIds: List<String> = entries.map { it.item.id }
    val title: String = entries.joinToString(" + ") { it.item.name }
}

fun minuteOfDay(hour: Int, minute: Int = 0): Int = hour * 60 + minute

fun Int.asLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

fun Long.asInstant(): Instant = Instant.ofEpochMilli(this)
