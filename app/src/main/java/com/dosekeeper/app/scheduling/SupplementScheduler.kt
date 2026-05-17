package com.dosekeeper.app.scheduling

import com.dosekeeper.app.data.ConflictRule
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseKind
import com.dosekeeper.app.data.ScheduledSupplement
import com.dosekeeper.app.data.SupplementGroup
import com.dosekeeper.app.data.SupplementTemplate
import kotlin.math.abs

object SupplementScheduler {
    private val candidateMinutes = listOf(
        7 * 60 + 30,
        8 * 60,
        9 * 60,
        10 * 60 + 30,
        13 * 60,
        15 * 60 + 30,
        19 * 60,
        21 * 60,
        22 * 60,
    )

    fun buildPlan(
        items: List<DoseItem>,
        templates: List<SupplementTemplate>,
        rules: List<ConflictRule>,
    ): List<SupplementGroup> {
        val templateById = templates.associateBy { it.id }
        val supplementItems = items
            .filter { it.active && it.kind == DoseKind.Supplement && it.supplementTemplateId != null }
            .sortedBy { templateById[it.supplementTemplateId]?.idealMinuteOfDay ?: it.targetMinuteOfDay ?: 12 * 60 }

        val scheduled = mutableListOf<ScheduledSupplement>()
        for (item in supplementItems) {
            val template = templateById[item.supplementTemplateId] ?: continue
            val chosenMinute = candidateMinutes.minBy { candidate ->
                placementCost(candidate, template, item, scheduled, templateById, rules)
            }
            scheduled += ScheduledSupplement(
                item = item,
                minuteOfDay = chosenMinute,
                reason = reasonFor(chosenMinute, template),
            )
        }

        return scheduled
            .groupBy { it.minuteOfDay }
            .map { (minute, entries) ->
                SupplementGroup(
                    minuteOfDay = minute,
                    entries = entries.sortedBy { it.item.name },
                    notes = groupNotes(entries, templateById, rules),
                )
            }
            .sortedBy { it.minuteOfDay }
    }

    fun conflictFor(
        first: DoseItem,
        second: DoseItem,
        rules: List<ConflictRule>,
    ): ConflictRule? {
        val firstId = first.supplementTemplateId ?: return null
        val secondId = second.supplementTemplateId ?: return null
        return rules.firstOrNull {
            (it.firstTemplateId == firstId && it.secondTemplateId == secondId) ||
                (it.firstTemplateId == secondId && it.secondTemplateId == firstId)
        }
    }

    private fun placementCost(
        candidate: Int,
        template: SupplementTemplate,
        item: DoseItem,
        scheduled: List<ScheduledSupplement>,
        templateById: Map<String, SupplementTemplate>,
        rules: List<ConflictRule>,
    ): Int {
        var cost = abs(candidate - template.idealMinuteOfDay)

        if (template.withFood && candidate !in foodWindows) cost += 90
        if (template.avoidFoodMinutes > 0 && candidate in foodWindows) cost += 130

        for (other in scheduled) {
            val conflict = conflictFor(item, other.item, rules) ?: continue
            val distance = circularDistanceMinutes(candidate, other.minuteOfDay)
            if (distance < conflict.separationMinutes) {
                cost += (conflict.separationMinutes - distance) * 8
            }
        }

        val sameSlotTemplates = scheduled
            .filter { it.minuteOfDay == candidate }
            .mapNotNull { templateById[it.item.supplementTemplateId] }
        if (sameSlotTemplates.any { it.avoidFoodMinutes > 0 || template.avoidFoodMinutes > 0 }) cost += 80

        return cost
    }

    private fun reasonFor(minute: Int, template: SupplementTemplate): String = when {
        template.withFood -> "meal-friendly"
        template.avoidFoodMinutes > 0 -> "spaced from meals and mineral conflicts"
        minute >= 21 * 60 -> "evening window"
        minute <= 8 * 60 -> "morning window"
        else -> "best fit"
    }

    private fun groupNotes(
        entries: List<ScheduledSupplement>,
        templateById: Map<String, SupplementTemplate>,
        rules: List<ConflictRule>,
    ): List<String> {
        val notes = mutableListOf<String>()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val conflict = conflictFor(entries[i].item, entries[j].item, rules)
                if (conflict != null) notes += conflict.reason
            }
        }
        if (entries.any { templateById[it.item.supplementTemplateId]?.withFood == true }) {
            notes += "Grouped with food-compatible supplements."
        }
        return notes.distinct()
    }

    private fun circularDistanceMinutes(first: Int, second: Int): Int {
        val distance = abs(first - second)
        return minOf(distance, 24 * 60 - distance)
    }

    private val foodWindows = setOf(8 * 60, 9 * 60, 13 * 60, 19 * 60)
}
