package com.dosekeeper.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DoseRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dose_keeper_store", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(loadState())

    val state: StateFlow<DoseState> = _state

    fun templates(): List<SupplementTemplate> = supplementTemplates

    fun conflicts(): List<ConflictRule> = conflictRules

    @Synchronized
    fun addMedication(name: String, intervalMinutes: Int): DoseItem {
        val item = DoseItem(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "As-needed medication" },
            kind = DoseKind.Medication,
            intervalMinutes = intervalMinutes.coerceAtLeast(30),
            accentColor = paletteFor(_state.value.items.size),
        )
        update(_state.value.copy(items = _state.value.items + item))
        return item
    }

    @Synchronized
    fun addSupplement(templateId: String): DoseItem? {
        val template = supplementTemplates.firstOrNull { it.id == templateId } ?: return null
        val existing = _state.value.items.firstOrNull { it.supplementTemplateId == templateId && it.active }
        if (existing != null) return existing

        val item = DoseItem(
            id = UUID.randomUUID().toString(),
            name = template.name,
            kind = DoseKind.Supplement,
            intervalMinutes = template.defaultIntervalMinutes,
            accentColor = template.accentColor,
            supplementTemplateId = template.id,
            targetMinuteOfDay = template.idealMinuteOfDay,
        )
        update(_state.value.copy(items = _state.value.items + item))
        return item
    }

    @Synchronized
    fun removeItem(itemId: String) {
        update(_state.value.copy(items = _state.value.items.filterNot { it.id == itemId }))
    }

    @Synchronized
    fun recordDose(itemIds: List<String>, takenAtMillis: Long = System.currentTimeMillis()): List<DoseItem> {
        val ids = itemIds.toSet()
        val current = _state.value
        val updatedItems = current.items.map {
            if (it.id in ids) it.copy(lastDoseAtMillis = takenAtMillis) else it
        }
        val touched = updatedItems.filter { it.id in ids }
        val newHistory = touched.map {
            DoseHistoryEntry(
                id = UUID.randomUUID().toString(),
                itemId = it.id,
                itemName = it.name,
                itemKind = it.kind,
                takenAtMillis = takenAtMillis,
            )
        }
        update(current.copy(items = updatedItems, history = (newHistory + current.history).take(250)))
        return touched
    }

    @Synchronized
    fun replaceItem(item: DoseItem) {
        update(_state.value.copy(items = _state.value.items.map { if (it.id == item.id) item else it }))
    }

    private fun update(next: DoseState) {
        saveState(next)
        _state.value = next
    }

    private fun loadState(): DoseState {
        val rawItems = prefs.getString(KEY_ITEMS, null)
        val rawHistory = prefs.getString(KEY_HISTORY, null)
        if (rawItems == null) {
            val seeded = DoseState(items = seedItems())
            saveState(seeded)
            return seeded
        }

        return DoseState(
            items = JSONArray(rawItems).mapObjects(::itemFromJson),
            history = rawHistory?.let { JSONArray(it).mapObjects(::historyFromJson) }.orEmpty(),
        )
    }

    private fun saveState(state: DoseState) {
        prefs.edit {
            putString(KEY_ITEMS, JSONArray(state.items.map(::itemToJson)).toString())
            putString(KEY_HISTORY, JSONArray(state.history.map(::historyToJson)).toString())
        }
    }

    private fun seedItems(): List<DoseItem> = listOf(
        DoseItem(
            id = UUID.randomUUID().toString(),
            name = "Migraine relief",
            kind = DoseKind.Medication,
            intervalMinutes = 6 * 60,
            accentColor = 0xFF2F6FED,
        ),
        DoseItem(
            id = UUID.randomUUID().toString(),
            name = "Vitamin D",
            kind = DoseKind.Supplement,
            intervalMinutes = 24 * 60,
            accentColor = 0xFFFFB86B,
            supplementTemplateId = "vitamin_d",
            targetMinuteOfDay = minuteOfDay(9),
        ),
        DoseItem(
            id = UUID.randomUUID().toString(),
            name = "Magnesium",
            kind = DoseKind.Supplement,
            intervalMinutes = 24 * 60,
            accentColor = 0xFF8D6BFF,
            supplementTemplateId = "magnesium",
            targetMinuteOfDay = minuteOfDay(21),
        ),
    )

    private fun itemToJson(item: DoseItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("kind", item.kind.name)
        .put("intervalMinutes", item.intervalMinutes)
        .put("accentColor", item.accentColor)
        .put("lastDoseAtMillis", item.lastDoseAtMillis ?: JSONObject.NULL)
        .put("supplementTemplateId", item.supplementTemplateId ?: JSONObject.NULL)
        .put("targetMinuteOfDay", item.targetMinuteOfDay ?: JSONObject.NULL)
        .put("active", item.active)

    private fun itemFromJson(json: JSONObject): DoseItem = DoseItem(
        id = json.getString("id"),
        name = json.getString("name"),
        kind = DoseKind.valueOf(json.getString("kind")),
        intervalMinutes = json.getInt("intervalMinutes"),
        accentColor = json.optLong("accentColor", 0xFF2F6FED),
        lastDoseAtMillis = json.optNullableLong("lastDoseAtMillis"),
        supplementTemplateId = json.optNullableString("supplementTemplateId"),
        targetMinuteOfDay = json.optNullableInt("targetMinuteOfDay"),
        active = json.optBoolean("active", true),
    )

    private fun historyToJson(entry: DoseHistoryEntry): JSONObject = JSONObject()
        .put("id", entry.id)
        .put("itemId", entry.itemId)
        .put("itemName", entry.itemName)
        .put("itemKind", entry.itemKind.name)
        .put("takenAtMillis", entry.takenAtMillis)

    private fun historyFromJson(json: JSONObject): DoseHistoryEntry = DoseHistoryEntry(
        id = json.getString("id"),
        itemId = json.getString("itemId"),
        itemName = json.getString("itemName"),
        itemKind = DoseKind.valueOf(json.getString("itemKind")),
        takenAtMillis = json.getLong("takenAtMillis"),
    )

    private fun paletteFor(index: Int): Long = listOf(
        0xFF2F6FED,
        0xFF1DBA91,
        0xFFFFB86B,
        0xFFEF5DA8,
        0xFF8D6BFF,
        0xFF14A3A8,
    )[index % 6]

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_HISTORY = "history"

        private val supplementTemplates = listOf(
            SupplementTemplate("vitamin_d", "Vitamin D", 24 * 60, minuteOfDay(9), 0xFFFFB86B, withFood = true, notes = "Pairs well with a meal containing fat."),
            SupplementTemplate("omega_3", "Omega-3", 24 * 60, minuteOfDay(9), 0xFF14A3A8, withFood = true, notes = "Often easiest with breakfast or lunch."),
            SupplementTemplate("iron", "Iron", 24 * 60, minuteOfDay(10, 30), 0xFFEF5DA8, avoidFoodMinutes = 60, notes = "Keep away from calcium, zinc, magnesium, coffee, tea, and large meals."),
            SupplementTemplate("calcium", "Calcium", 24 * 60, minuteOfDay(13), 0xFF2F6FED, withFood = true, notes = "Take away from iron and zinc."),
            SupplementTemplate("magnesium", "Magnesium", 24 * 60, minuteOfDay(21), 0xFF8D6BFF, notes = "Many people prefer evening timing."),
            SupplementTemplate("zinc", "Zinc", 24 * 60, minuteOfDay(19), 0xFF1DBA91, withFood = true, notes = "Take with food; separate from iron and calcium."),
            SupplementTemplate("b12", "B12", 24 * 60, minuteOfDay(8), 0xFFFF7A59, notes = "Usually a morning supplement."),
            SupplementTemplate("probiotic", "Probiotic", 24 * 60, minuteOfDay(7, 30), 0xFF5BB974, notes = "Often scheduled before breakfast."),
            SupplementTemplate("melatonin", "Melatonin", 24 * 60, minuteOfDay(22), 0xFF6C5CE7, notes = "Bedtime reminder."),
            SupplementTemplate("creatine", "Creatine", 24 * 60, minuteOfDay(13), 0xFF00A8E8, withFood = true, notes = "Can usually share a meal slot with other compatible supplements."),
        )

        private val conflictRules = listOf(
            ConflictRule("iron", "calcium", 120, "Iron and calcium can compete; leave a wider gap."),
            ConflictRule("iron", "magnesium", 120, "Iron is usually kept away from magnesium."),
            ConflictRule("iron", "zinc", 120, "Iron and zinc are commonly separated."),
            ConflictRule("calcium", "zinc", 120, "Calcium may reduce zinc absorption."),
            ConflictRule("probiotic", "zinc", 60, "Keep probiotic timing distinct from mineral-heavy doses."),
        )
    }
}
