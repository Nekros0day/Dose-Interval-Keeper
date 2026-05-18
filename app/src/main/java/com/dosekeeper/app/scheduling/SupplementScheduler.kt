package com.dosekeeper.app.scheduling

import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseKind
import com.dosekeeper.app.data.DosePlanGroup
import com.dosekeeper.app.data.InteractionRule
import com.dosekeeper.app.data.ScheduledDose
import com.dosekeeper.app.data.SupplementTemplate
import com.dosekeeper.app.data.TimingPreference
import com.dosekeeper.app.data.minuteOfDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object SupplementScheduler {
    fun buildPlan(
        items: List<DoseItem>,
        templates: List<SupplementTemplate>,
        rules: List<InteractionRule>,
        nowMillis: Long = System.currentTimeMillis(),
        horizonHours: Int = 48,
        quietStartMinute: Int = minuteOfDay(22),
        quietEndMinute: Int = minuteOfDay(7),
    ): List<DosePlanGroup> {
        val active = items.filter { it.active && shouldPlan(it) }
        val templateById = templates.associateBy { it.id }
        val scheduled = mutableListOf<ScheduledDose>()

        active.sortedWith(compareBy<DoseItem> { priority(it) }.thenBy { it.name }).forEach { item ->
            scheduled += chooseSlot(
                item = item,
                allItems = active,
                templateById = templateById,
                rules = rules,
                alreadyScheduled = scheduled,
                nowMillis = nowMillis,
                horizonHours = horizonHours,
                quietStartMinute = quietStartMinute,
                quietEndMinute = quietEndMinute,
            )
        }

        return scheduled
            .groupBy { it.scheduledAtMillis }
            .map { (time, entries) ->
                DosePlanGroup(
                    scheduledAtMillis = time,
                    entries = entries.sortedBy { it.item.name },
                    notes = entries.flatMap { it.notes }.distinct().take(4),
                )
            }
            .sortedBy { it.scheduledAtMillis }
    }

    fun interactionRulesFor(
        first: DoseItem,
        second: DoseItem,
        rules: List<InteractionRule>,
    ): List<InteractionRule> = rules.filter {
        (matches(it.firstKey, first) && matches(it.secondKey, second)) ||
            (matches(it.firstKey, second) && matches(it.secondKey, first))
    }

    fun labelForRuleKey(key: String, items: List<DoseItem>, templates: List<SupplementTemplate>): String {
        items.firstOrNull { it.id == key }?.let { return it.name }
        templates.firstOrNull { it.id == key }?.let { return it.name }
        return key.removePrefix("name:").replaceFirstChar { it.uppercase() }
    }

    private fun chooseSlot(
        item: DoseItem,
        allItems: List<DoseItem>,
        templateById: Map<String, SupplementTemplate>,
        rules: List<InteractionRule>,
        alreadyScheduled: List<ScheduledDose>,
        nowMillis: Long,
        horizonHours: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ): ScheduledDose {
        val candidates = candidatesFor(item, nowMillis, horizonHours, quietStartMinute, quietEndMinute)
        val scored = candidates.map { candidate ->
            val scoring = scoreCandidate(candidate, item, allItems, templateById, rules, alreadyScheduled, nowMillis, quietStartMinute, quietEndMinute)
            candidate to scoring
        }
        val (bestMillis, bestScore) = scored.minBy { it.second.cost }
        val template = item.supplementTemplateId?.let(templateById::get)
        val notes = (bestScore.notes + listOfNotNull(template?.interactionNotes?.takeIf { it.isNotBlank() }))
            .distinct()
            .take(4)

        return ScheduledDose(
            item = item,
            scheduledAtMillis = bestMillis,
            reason = reasonFor(item, template),
            notes = notes,
        )
    }

    private fun scoreCandidate(
        candidateMillis: Long,
        item: DoseItem,
        allItems: List<DoseItem>,
        templateById: Map<String, SupplementTemplate>,
        rules: List<InteractionRule>,
        alreadyScheduled: List<ScheduledDose>,
        nowMillis: Long,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ): CandidateScore {
        val zone = ZoneId.systemDefault()
        val candidateMinute = Instant.ofEpochMilli(candidateMillis).atZone(zone).hour * 60 +
            Instant.ofEpochMilli(candidateMillis).atZone(zone).minute
        val targetMinute = item.targetMinuteOfDay ?: templateById[item.supplementTemplateId]?.idealMinuteOfDay ?: fallbackMinute(item)
        var cost = circularDistanceMinutes(candidateMinute, targetMinute)
        val notes = mutableListOf<String>()

        cost += ((candidateMillis - nowMillis) / (60 * 60 * 1_000L)).toInt().coerceAtLeast(0) * 4
        if (isQuietMinute(candidateMinute, quietStartMinute, quietEndMinute)) {
            cost += 20_000
            notes += "Quiet hours push reminders to daytime."
        }

        when (item.timingPreference) {
            TimingPreference.WithBreakfast -> if (candidateMinute !in breakfastWindow) cost += 240
            TimingPreference.WithLunch -> if (candidateMinute !in lunchWindow) cost += 240
            TimingPreference.WithDinner -> if (candidateMinute !in dinnerWindow) cost += 240
            TimingPreference.WithFood -> if (candidateMinute !in foodWindows) cost += 120
            TimingPreference.EmptyStomach -> if (candidateMinute in foodWindows) cost += 300
            TimingPreference.Bedtime -> if (candidateMinute !in bedtimeWindow) cost += 180
            TimingPreference.Anytime -> Unit
        }

        alreadyScheduled.forEach { other ->
            val conflicts = interactionRulesFor(item, other.item, rules)
            val spacingConflicts = conflicts.filter { it.separationMinutes > 0 }
            conflicts.filter { it.separationMinutes == 0 }.forEach { notes += it.reason }
            spacingConflicts.forEach { rule ->
                val distance = abs(candidateMillis - other.scheduledAtMillis) / 60_000L
                if (distance < rule.separationMinutes) {
                    cost += ((rule.separationMinutes - distance).toInt() + 1) * 40
                    notes += rule.reason
                }
            }

            val sameSlot = candidateMillis == other.scheduledAtMillis
            val compatibleMealGroup = sameSlot &&
                item.timingPreference in mealFriendlyPreferences &&
                other.item.timingPreference in mealFriendlyPreferences &&
                spacingConflicts.isEmpty()
            if (compatibleMealGroup) cost -= 35
            if (sameSlot && isEnhancerPair(item, other.item)) cost -= 140
        }

        allItems.filter { it.id != item.id && it.lastDoseAtMillis != null }.forEach { other ->
            interactionRulesFor(item, other, rules).filter { it.separationMinutes > 0 }.forEach { rule ->
                val lastTaken = other.lastDoseAtMillis ?: return@forEach
                if (candidateMillis >= lastTaken) {
                    val distance = (candidateMillis - lastTaken) / 60_000L
                    if (distance < rule.separationMinutes) {
                        cost += ((rule.separationMinutes - distance).toInt() + 1) * 55
                        notes += "Recent ${other.name} dose affects this slot: ${rule.reason}"
                    }
                }
            }
        }

        return CandidateScore(cost = cost, notes = notes)
    }

    private fun candidatesFor(
        item: DoseItem,
        nowMillis: Long,
        horizonHours: Int,
        quietStartMinute: Int,
        quietEndMinute: Int,
    ): List<Long> {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val latest = nowMillis + horizonHours * 60L * 60L * 1_000L
        val earliest = roundUpToTick(maxOf(nowMillis, item.nextAllowedAtMillis() ?: nowMillis), PLAN_TICK_MINUTES)
        val minutes = candidateMinutes(item)
        val candidates = mutableSetOf<Long>()

        if (item.kind == DoseKind.Medication && item.timingPreference == TimingPreference.Anytime && item.lastDoseAtMillis != null) {
            candidates += earliest
        }

        for (dayOffset in 0..3) {
            val date = LocalDate.from(now).plusDays(dayOffset.toLong())
            val start = date.atStartOfDay(zone)
            minutes.forEach { minute ->
                val millis = start.plusMinutes(minute.toLong()).toInstant().toEpochMilli()
                if (millis in earliest..latest) candidates += millis
            }
        }

        var sliding = earliest
        while (sliding <= latest) {
            candidates += sliding
            sliding += PLAN_TICK_MINUTES * 60_000L
        }

        val daytimeCandidates = candidates.filterNot {
            val minute = minuteOfDay(it)
            isQuietMinute(minute, quietStartMinute, quietEndMinute)
        }

        return daytimeCandidates.ifEmpty { listOf(nextQuietEnd(earliest, quietStartMinute, quietEndMinute)) }.sorted()
    }

    private fun candidateMinutes(item: DoseItem): List<Int> {
        val custom = item.targetMinuteOfDay
        return when (item.timingPreference) {
            TimingPreference.WithBreakfast -> listOf(minuteOfDay(7, 30), minuteOfDay(8), minuteOfDay(8, 30), minuteOfDay(9))
            TimingPreference.WithLunch -> listOf(minuteOfDay(12), minuteOfDay(12, 30), minuteOfDay(13), minuteOfDay(13, 30))
            TimingPreference.WithDinner -> listOf(minuteOfDay(18), minuteOfDay(18, 30), minuteOfDay(19), minuteOfDay(19, 30))
            TimingPreference.WithFood -> listOf(minuteOfDay(8), minuteOfDay(12, 30), minuteOfDay(18, 30))
            TimingPreference.EmptyStomach -> listOf(minuteOfDay(7), minuteOfDay(10, 30), minuteOfDay(15, 30), minuteOfDay(21))
            TimingPreference.Bedtime -> listOf(minuteOfDay(21, 30), minuteOfDay(22), minuteOfDay(22, 30))
            TimingPreference.Anytime -> listOfNotNull(custom) + listOf(
                minuteOfDay(8),
                minuteOfDay(10, 30),
                minuteOfDay(12, 30),
                minuteOfDay(15, 30),
                minuteOfDay(18, 30),
                minuteOfDay(21),
            )
        }.distinct().map { it.coerceIn(0, 23 * 60 + 59) }
    }

    private fun around(minute: Int): List<Int> =
        listOf(minute, minute - 60, minute - 30, minute + 30, minute + 60)
            .map { it.coerceIn(0, 23 * 60 + 59) }
            .distinct()

    private fun shouldPlan(item: DoseItem): Boolean = when (item.kind) {
        DoseKind.Supplement -> true
        DoseKind.Medication -> item.lastDoseAtMillis != null || item.timingPreference != TimingPreference.Anytime
    }

    private fun priority(item: DoseItem): Int = when {
        item.timingPreference == TimingPreference.EmptyStomach -> 0
        item.supplementTemplateId == "iron" -> 0
        item.timingPreference == TimingPreference.Bedtime -> 1
        item.timingPreference in mealFriendlyPreferences -> 2
        else -> 3
    }

    private fun reasonFor(item: DoseItem, template: SupplementTemplate?): String = when (item.timingPreference) {
        TimingPreference.WithBreakfast -> "breakfast window"
        TimingPreference.WithLunch -> "lunch window"
        TimingPreference.WithDinner -> "dinner window"
        TimingPreference.WithFood -> "with food"
        TimingPreference.EmptyStomach -> "empty-stomach window"
        TimingPreference.Bedtime -> "bedtime window"
        TimingPreference.Anytime -> template?.timingNotes ?: "best fit"
    }

    private fun fallbackMinute(item: DoseItem): Int = when (item.timingPreference) {
        TimingPreference.WithBreakfast -> minuteOfDay(8)
        TimingPreference.WithLunch -> minuteOfDay(12, 30)
        TimingPreference.WithDinner -> minuteOfDay(18, 30)
        TimingPreference.WithFood -> minuteOfDay(12, 30)
        TimingPreference.EmptyStomach -> minuteOfDay(10, 30)
        TimingPreference.Bedtime -> minuteOfDay(22)
        TimingPreference.Anytime -> minuteOfDay(9)
    }

    private fun isEnhancerPair(first: DoseItem, second: DoseItem): Boolean {
        val ids = setOfNotNull(first.supplementTemplateId, second.supplementTemplateId)
        return ids == setOf("iron", "vitamin_c")
    }

    private fun matches(key: String, item: DoseItem): Boolean {
        if (key == item.id || key == item.supplementTemplateId) return true
        if (key.startsWith("name:")) {
            val normalized = normalizeName(item.name)
            return normalized.contains(normalizeName(key.removePrefix("name:")))
        }
        return false
    }

    private fun normalizeName(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.trim()

    private fun circularDistanceMinutes(first: Int, second: Int): Int {
        val distance = abs(first - second)
        return minOf(distance, 24 * 60 - distance)
    }

    private fun minuteOfDay(millis: Long): Int {
        val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return zoned.hour * 60 + zoned.minute
    }

    private fun roundUpToTick(millis: Long, tickMinutes: Int): Long {
        val tickMillis = tickMinutes * 60_000L
        return ((millis + tickMillis - 1) / tickMillis) * tickMillis
    }

    private fun isQuietMinute(minute: Int, startMinute: Int, endMinute: Int): Boolean =
        if (startMinute == endMinute) {
            false
        } else if (startMinute < endMinute) {
            minute in startMinute until endMinute
        } else {
            minute >= startMinute || minute < endMinute
        }

    private fun nextQuietEnd(millis: Long, quietStartMinute: Int, quietEndMinute: Int): Long {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(millis).atZone(zone)
        val minute = current.hour * 60 + current.minute
        if (!isQuietMinute(minute, quietStartMinute, quietEndMinute)) return millis

        val todayEnd = current.toLocalDate().atStartOfDay(zone).plusMinutes(quietEndMinute.toLong())
        val end = if (quietStartMinute < quietEndMinute || minute < quietEndMinute) {
            todayEnd
        } else {
            todayEnd.plusDays(1)
        }
        return end.toInstant().toEpochMilli()
    }

    private data class CandidateScore(val cost: Int, val notes: List<String>)

    private const val PLAN_TICK_MINUTES = 10

    private val breakfastWindow = minuteOfDay(7, 30)..minuteOfDay(9, 30)
    private val lunchWindow = minuteOfDay(12)..minuteOfDay(14)
    private val dinnerWindow = minuteOfDay(18)..minuteOfDay(20)
    private val bedtimeWindow = minuteOfDay(21)..minuteOfDay(23)
    private val foodWindows = breakfastWindow.toSet() + lunchWindow.toSet() + dinnerWindow.toSet()
    private val mealFriendlyPreferences = setOf(
        TimingPreference.WithBreakfast,
        TimingPreference.WithLunch,
        TimingPreference.WithDinner,
        TimingPreference.WithFood,
    )
}
