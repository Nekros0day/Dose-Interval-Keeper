package com.dosekeeper.app.data

import java.time.Instant
import java.time.LocalTime

enum class DoseKind {
    Medication,
    Supplement,
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
    val active: Boolean = true,
) {
    fun nextSafeAtMillis(): Long? = lastDoseAtMillis?.plus(intervalMinutes * 60_000L)
}

data class DoseHistoryEntry(
    val id: String,
    val itemId: String,
    val itemName: String,
    val itemKind: DoseKind,
    val takenAtMillis: Long,
)

data class DoseState(
    val items: List<DoseItem> = emptyList(),
    val history: List<DoseHistoryEntry> = emptyList(),
) {
    val activeItems: List<DoseItem> = items.filter { it.active }
}

data class SupplementTemplate(
    val id: String,
    val name: String,
    val defaultIntervalMinutes: Int,
    val idealMinuteOfDay: Int,
    val accentColor: Long,
    val withFood: Boolean = false,
    val avoidFoodMinutes: Int = 0,
    val notes: String,
)

data class ConflictRule(
    val firstTemplateId: String,
    val secondTemplateId: String,
    val separationMinutes: Int,
    val reason: String,
)

data class ScheduledSupplement(
    val item: DoseItem,
    val minuteOfDay: Int,
    val reason: String,
)

data class SupplementGroup(
    val minuteOfDay: Int,
    val entries: List<ScheduledSupplement>,
    val notes: List<String>,
) {
    val itemIds: List<String> = entries.map { it.item.id }
    val title: String = entries.joinToString(" + ") { it.item.name }
}

fun minuteOfDay(hour: Int, minute: Int = 0): Int = hour * 60 + minute

fun Int.asLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)

fun Long.asInstant(): Instant = Instant.ofEpochMilli(this)
