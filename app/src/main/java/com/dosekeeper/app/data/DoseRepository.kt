package com.dosekeeper.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DoseRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dose_keeper_store", Context.MODE_PRIVATE)
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(cleanupHistory(loadState()))

    val state: kotlinx.coroutines.flow.StateFlow<DoseState> = _state

    fun templates(): List<SupplementTemplate> = supplementTemplates

    fun defaultInteractions(): List<InteractionRule> = defaultInteractionRules

    fun interactions(): List<InteractionRule> = defaultInteractionRules + _state.value.customInteractions

    @Synchronized
    fun addMedication(
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference = TimingPreference.Anytime,
        targetMinuteOfDay: Int? = null,
    ): DoseItem = addItem(
        name = name,
        kind = DoseKind.Medication,
        intervalMinutes = intervalMinutes,
        timingPreference = timingPreference,
        targetMinuteOfDay = targetMinuteOfDay,
        description = "Custom medication timing aid.",
    )

    @Synchronized
    fun addCustomSupplement(
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
        description: String,
    ): DoseItem = addItem(
        name = name,
        kind = DoseKind.Supplement,
        intervalMinutes = intervalMinutes,
        timingPreference = timingPreference,
        targetMinuteOfDay = targetMinuteOfDay,
        description = description.ifBlank { "Custom supplement." },
    )

    @Synchronized
    fun addSupplement(templateId: String): DoseItem? {
        val template = supplementTemplates.firstOrNull { it.id == templateId } ?: return null
        val existing = _state.value.items.firstOrNull { it.supplementTemplateId == templateId && it.active }
        if (existing != null) return existing

        return addItem(
            name = template.name,
            kind = DoseKind.Supplement,
            intervalMinutes = template.defaultIntervalMinutes,
            timingPreference = template.timingPreference,
            targetMinuteOfDay = template.idealMinuteOfDay,
            description = "${template.description} ${template.timingNotes}".trim(),
            supplementTemplateId = template.id,
            accentColor = template.accentColor,
        )
    }

    @Synchronized
    fun updateItemSchedule(
        itemId: String,
        name: String,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
        description: String,
    ) {
        update(
            cleanupHistory(
                _state.value.copy(
                    items = _state.value.items.map { item ->
                        if (item.id == itemId) {
                            item.copy(
                                name = name.trim().ifBlank { item.name },
                                intervalMinutes = intervalMinutes.coerceAtLeast(30),
                                timingPreference = timingPreference,
                                targetMinuteOfDay = targetMinuteOfDay,
                                description = description.ifBlank { item.description },
                            )
                        } else {
                            item
                        }
                    },
                ),
            ),
        )
    }

    @Synchronized
    fun removeItem(itemId: String) {
        val next = _state.value.copy(
            items = _state.value.items.filterNot { it.id == itemId },
            customInteractions = _state.value.customInteractions.filterNot {
                it.firstKey == itemId || it.secondKey == itemId
            },
        )
        update(cleanupHistory(next))
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
        update(cleanupHistory(current.copy(items = updatedItems, history = newHistory + current.history)))
        return touched
    }

    @Synchronized
    fun addInteraction(firstItemId: String, secondItemId: String, separationMinutes: Int, reason: String) {
        if (firstItemId == secondItemId) return
        val rule = InteractionRule(
            id = UUID.randomUUID().toString(),
            firstKey = firstItemId,
            secondKey = secondItemId,
            separationMinutes = separationMinutes.coerceAtLeast(15),
            reason = reason.ifBlank { "Keep these apart." },
            editable = true,
        )
        update(_state.value.copy(customInteractions = _state.value.customInteractions + rule))
    }

    @Synchronized
    fun deleteInteraction(ruleId: String) {
        update(_state.value.copy(customInteractions = _state.value.customInteractions.filterNot { it.id == ruleId }))
    }

    @Synchronized
    fun updateInteraction(ruleId: String, firstItemId: String, secondItemId: String, separationMinutes: Int, reason: String) {
        if (firstItemId == secondItemId) return
        update(
            _state.value.copy(
                customInteractions = _state.value.customInteractions.map {
                    if (it.id == ruleId) {
                        it.copy(
                            firstKey = firstItemId,
                            secondKey = secondItemId,
                            separationMinutes = separationMinutes.coerceAtLeast(15),
                            reason = reason.ifBlank { "Keep these apart." },
                        )
                    } else {
                        it
                    }
                },
            ),
        )
    }

    @Synchronized
    fun clearHistory() {
        update(_state.value.copy(history = emptyList()))
    }

    @Synchronized
    fun setHistoryRetentionDays(days: Int?) {
        update(cleanupHistory(_state.value.copy(historyRetentionDays = days?.coerceAtLeast(1))))
    }

    @Synchronized
    fun setQuietHours(startMinute: Int, endMinute: Int) {
        update(
            _state.value.copy(
                quietStartMinute = startMinute.coerceIn(0, 23 * 60 + 59),
                quietEndMinute = endMinute.coerceIn(0, 23 * 60 + 59),
            ),
        )
    }

    private fun addItem(
        name: String,
        kind: DoseKind,
        intervalMinutes: Int,
        timingPreference: TimingPreference,
        targetMinuteOfDay: Int?,
        description: String,
        supplementTemplateId: String? = null,
        accentColor: Long? = null,
    ): DoseItem {
        val item = DoseItem(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { if (kind == DoseKind.Medication) "Medication" else "Supplement" },
            kind = kind,
            intervalMinutes = intervalMinutes.coerceAtLeast(30),
            accentColor = accentColor ?: paletteFor(_state.value.items.size),
            supplementTemplateId = supplementTemplateId,
            targetMinuteOfDay = targetMinuteOfDay,
            timingPreference = timingPreference,
            description = description,
        )
        update(cleanupHistory(_state.value.copy(items = _state.value.items + item)))
        return item
    }

    private fun update(next: DoseState) {
        saveState(next)
        _state.value = next
    }

    private fun cleanupHistory(state: DoseState): DoseState {
        val days = state.historyRetentionDays ?: return state
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1_000L
        return state.copy(history = state.history.filter { it.takenAtMillis >= cutoff }.take(500))
    }

    private fun loadState(): DoseState {
        val rawItems = prefs.getString(KEY_ITEMS, null)
        val rawHistory = prefs.getString(KEY_HISTORY, null)
        val rawInteractions = prefs.getString(KEY_CUSTOM_INTERACTIONS, null)
        val retentionDays = if (prefs.contains(KEY_RETENTION_DAYS)) {
            prefs.getInt(KEY_RETENTION_DAYS, DEFAULT_RETENTION_DAYS).takeIf { it > 0 }
        } else {
            DEFAULT_RETENTION_DAYS
        }
        val quietStartMinute = prefs.getInt(KEY_QUIET_START_MINUTE, DEFAULT_QUIET_START_MINUTE)
            .coerceIn(0, 23 * 60 + 59)
        val quietEndMinute = prefs.getInt(KEY_QUIET_END_MINUTE, DEFAULT_QUIET_END_MINUTE)
            .coerceIn(0, 23 * 60 + 59)

        if (rawItems == null) {
            val seeded = DoseState(
                items = seedItems(),
                historyRetentionDays = retentionDays,
                quietStartMinute = quietStartMinute,
                quietEndMinute = quietEndMinute,
            )
            saveState(seeded)
            return seeded
        }

        return DoseState(
            items = JSONArray(rawItems).mapObjects(::itemFromJson),
            history = rawHistory?.let { JSONArray(it).mapObjects(::historyFromJson) }.orEmpty(),
            customInteractions = rawInteractions?.let { JSONArray(it).mapObjects(::interactionFromJson) }.orEmpty(),
            historyRetentionDays = retentionDays,
            quietStartMinute = quietStartMinute,
            quietEndMinute = quietEndMinute,
        )
    }

    private fun saveState(state: DoseState) {
        prefs.edit {
            putString(KEY_ITEMS, JSONArray(state.items.map(::itemToJson)).toString())
            putString(KEY_HISTORY, JSONArray(state.history.map(::historyToJson)).toString())
            putString(KEY_CUSTOM_INTERACTIONS, JSONArray(state.customInteractions.map(::interactionToJson)).toString())
            putInt(KEY_RETENTION_DAYS, state.historyRetentionDays ?: 0)
            putInt(KEY_QUIET_START_MINUTE, state.quietStartMinute)
            putInt(KEY_QUIET_END_MINUTE, state.quietEndMinute)
        }
    }

    private fun seedItems(): List<DoseItem> = listOf(
        DoseItem(
            id = UUID.randomUUID().toString(),
            name = "Migraine relief",
            kind = DoseKind.Medication,
            intervalMinutes = 6 * 60,
            accentColor = 0xFF2F6FED,
            description = "As-needed medication. This app only tracks timing; follow the label or clinician instructions.",
        ),
        templateItem("vitamin_d"),
        templateItem("magnesium"),
    )

    private fun templateItem(templateId: String): DoseItem {
        val template = supplementTemplates.first { it.id == templateId }
        return DoseItem(
            id = UUID.randomUUID().toString(),
            name = template.name,
            kind = DoseKind.Supplement,
            intervalMinutes = template.defaultIntervalMinutes,
            accentColor = template.accentColor,
            supplementTemplateId = template.id,
            targetMinuteOfDay = template.idealMinuteOfDay,
            timingPreference = template.timingPreference,
            description = "${template.description} ${template.timingNotes}".trim(),
        )
    }

    private fun itemToJson(item: DoseItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("kind", item.kind.name)
        .put("intervalMinutes", item.intervalMinutes)
        .put("accentColor", item.accentColor)
        .put("lastDoseAtMillis", item.lastDoseAtMillis ?: JSONObject.NULL)
        .put("supplementTemplateId", item.supplementTemplateId ?: JSONObject.NULL)
        .put("targetMinuteOfDay", item.targetMinuteOfDay ?: JSONObject.NULL)
        .put("timingPreference", item.timingPreference.name)
        .put("description", item.description)
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
        timingPreference = timingPreferenceFromJson(
            json.optNullableString("timingPreference"),
            json.optNullableString("supplementTemplateId"),
        ),
        description = json.optString("description", ""),
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

    private fun interactionToJson(rule: InteractionRule): JSONObject = JSONObject()
        .put("id", rule.id)
        .put("firstKey", rule.firstKey)
        .put("secondKey", rule.secondKey)
        .put("separationMinutes", rule.separationMinutes)
        .put("reason", rule.reason)
        .put("editable", rule.editable)

    private fun interactionFromJson(json: JSONObject): InteractionRule = InteractionRule(
        id = json.getString("id"),
        firstKey = json.getString("firstKey"),
        secondKey = json.getString("secondKey"),
        separationMinutes = json.getInt("separationMinutes"),
        reason = json.optString("reason", "Keep these apart."),
        editable = json.optBoolean("editable", true),
    )

    private fun defaultTimingFor(templateId: String?): TimingPreference =
        supplementTemplates.firstOrNull { it.id == templateId }?.timingPreference ?: TimingPreference.Anytime

    private fun timingPreferenceFromJson(raw: String?, templateId: String?): TimingPreference =
        raw
            ?.takeUnless { it == "Custom" }
            ?.let { runCatching { TimingPreference.valueOf(it) }.getOrNull() }
            ?: defaultTimingFor(templateId)

    private fun paletteFor(index: Int): Long = listOf(
        0xFF2F6FED,
        0xFF1DBA91,
        0xFFFFB86B,
        0xFFEF5DA8,
        0xFF8D6BFF,
        0xFF14A3A8,
        0xFFFF7A59,
        0xFF5BB974,
    )[index % 8]

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
        private const val KEY_CUSTOM_INTERACTIONS = "custom_interactions"
        private const val KEY_RETENTION_DAYS = "history_retention_days"
        private const val KEY_QUIET_START_MINUTE = "quiet_start_minute"
        private const val KEY_QUIET_END_MINUTE = "quiet_end_minute"
        private const val DEFAULT_RETENTION_DAYS = 30
        private const val DEFAULT_QUIET_START_MINUTE = 22 * 60
        private const val DEFAULT_QUIET_END_MINUTE = 7 * 60

        val supplementTemplates = listOf(
            supplement("multivitamin", "Multivitamin", "Vitamins", 24 * 60, minuteOfDay(8, 30), TimingPreference.WithBreakfast, 0xFF2F6FED, "Broad vitamin/mineral coverage for dietary gaps.", "Once daily with a meal.", "Mineral contents can compete with iron, calcium, magnesium, and zinc."),
            supplement("vitamin_d", "Vitamin D", "Vitamins", 24 * 60, minuteOfDay(8, 30), TimingPreference.WithFood, 0xFFFFB86B, "Fat-soluble vitamin for bone health and low vitamin D status.", "With a fat-containing meal.", "Excess with calcium can raise hypercalcemia risk."),
            supplement("vitamin_c", "Vitamin C", "Vitamins", 24 * 60, minuteOfDay(10), TimingPreference.Anytime, 0xFFFF7A59, "Water-soluble antioxidant often paired with iron.", "Flexible; with food if it irritates the stomach.", "Can improve nonheme iron absorption."),
            supplement("b12", "Vitamin B12", "Vitamins", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFF5BB974, "Supports low intake or absorption risk groups.", "Daily with or without food.", "Metformin and acid suppressants affect status over time."),
            supplement("folate", "Folate", "Vitamins", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFF6C5CE7, "Supports DNA synthesis and neural tube defect prevention.", "Daily with or without food.", "High folic acid can mask B12 deficiency."),
            supplement("choline", "Choline", "Vitamins", 24 * 60, minuteOfDay(8, 30), TimingPreference.WithBreakfast, 0xFF5E60CE, "Food-first nutrient relevant to pregnancy, liver, and metabolic contexts.", "With meals.", "High doses can cause GI effects or fishy odor; pregnancy use should align with clinician guidance."),
            supplement("biotin", "Biotin", "Vitamins", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFFFFAFCC, "Low-priority hair/nail supplement mostly relevant to brittle nails or known deficiency.", "Any time.", "Can interfere with lab tests, especially thyroid and cardiac markers."),
            supplement("vitamin_k", "Vitamin K", "Vitamins", 24 * 60, minuteOfDay(8, 30), TimingPreference.WithFood, 0xFF2EC4B6, "Clotting and sometimes bone-directed support.", "Daily with food; consistency matters with warfarin.", "Major interaction context is warfarin."),
            supplement("vitamin_a", "Vitamin A", "Vitamins", 24 * 60, minuteOfDay(8, 30), TimingPreference.WithFood, 0xFFFF9F1C, "Vision, immune, and deficiency support.", "With a fat-containing meal.", "Avoid high-dose preformed vitamin A in pregnancy unless prescribed."),
            supplement("iron", "Iron", "Minerals", 24 * 60, minuteOfDay(10, 30), TimingPreference.EmptyStomach, 0xFFEF5DA8, "Used for iron deficiency and iron-deficiency anemia.", "Best empty stomach; pair with vitamin C if tolerated.", "Keep away from calcium, magnesium, zinc, dairy, tea, and coffee."),
            supplement("calcium", "Calcium", "Minerals", 24 * 60, minuteOfDay(13), TimingPreference.WithLunch, 0xFF3A86FF, "Bone-health mineral.", "Carbonate with food; citrate with or without food.", "Keep away from iron and some medicines."),
            supplement("magnesium", "Magnesium", "Minerals", 24 * 60, minuteOfDay(21), TimingPreference.WithDinner, 0xFF8D6BFF, "Mineral used for low intake, migraine, or constipation support.", "With food or split doses to reduce diarrhea.", "Can chelate some antibiotics and competes with iron."),
            supplement("zinc", "Zinc", "Minerals", 24 * 60, minuteOfDay(19), TimingPreference.WithDinner, 0xFF1DBA91, "Used for deficiency, immune support, and wound healing.", "With food to avoid nausea.", "Keep away from iron, calcium, and some antibiotics."),
            supplement("selenium", "Selenium", "Minerals", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFF9B5DE5, "Trace mineral often used for thyroid or antioxidant reasons.", "Daily; clock timing is not important.", "Too much can cause selenosis."),
            supplement("iodine", "Iodine", "Minerals", 24 * 60, minuteOfDay(8), TimingPreference.WithFood, 0xFF00BBF9, "Supports thyroid hormone synthesis.", "Daily with food if it upsets the stomach.", "Thyroid disease and antithyroid drugs need clinician guidance."),
            supplement("omega_3", "Omega-3 fish oil", "Omega-3", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFF14A3A8, "EPA/DHA source for cardiometabolic and general supplementation.", "With meals, ideally with fat.", "Review with anticoagulants/antiplatelets; fish allergy caution."),
            supplement("krill_oil", "Omega-3 krill oil", "Omega-3", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFF00A6A6, "Marine omega-3 source often marketed for absorption.", "With meals.", "Similar omega-3 cautions; consider shellfish allergy."),
            supplement("algal_oil", "Omega-3 algal oil", "Omega-3", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFF00C2FF, "Vegan DHA/EPA source.", "With meals.", "General omega-3 anticoagulant caution; useful when avoiding fish."),
            supplement("probiotic", "Probiotic", "Gut & Fiber", 24 * 60, minuteOfDay(7, 30), TimingPreference.WithBreakfast, 0xFF4CAF50, "Live microorganisms; benefits are strain-specific.", "Product-specific; often daily with food for tolerance.", "Vulnerable or immunocompromised users should review use clinically."),
            supplement("psyllium", "Fiber / psyllium", "Gut & Fiber", 24 * 60, minuteOfDay(12), TimingPreference.WithLunch, 0xFF7CB342, "Soluble fiber for constipation, LDL, satiety, and glycemic support.", "Often before meals with plenty of water.", "Can reduce absorption of oral meds/supplements; space when practical."),
            supplement("creatine", "Creatine", "Sports & Performance", 24 * 60, minuteOfDay(13), TimingPreference.WithLunch, 0xFF00A8E8, "Ergogenic aid for strength, repeated high-intensity work, and lean mass.", "Daily consistency matters; often with meals or after training.", "Carbs or carbs plus protein can increase retention."),
            supplement("l_arginine", "L-arginine", "Sports & Performance", 24 * 60, minuteOfDay(10), TimingPreference.EmptyStomach, 0xFF457B9D, "Amino acid used for nitric-oxide blood-flow goals.", "Between meals or before activity; with food if GI upset.", "Use caution with nitrates, PDE5 inhibitors, BP, and diabetes drugs."),
            supplement("collagen", "Collagen", "Joint & Skin", 24 * 60, minuteOfDay(9), TimingPreference.Anytime, 0xFFF2A7A5, "Protein supplement for skin and joint support.", "Any time daily; consistency matters.", "Check source allergies and combo products."),
            supplement("glucosamine", "Glucosamine", "Joint & Skin", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFFB56576, "Joint supplement most often used for osteoarthritis symptoms.", "With meals or divided for tolerance.", "Warfarin bleeding risk has been reported."),
            supplement("msm", "MSM", "Joint & Skin", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFF9D8189, "Sulfur-containing compound often paired with glucosamine.", "With meals or divided; formal timing evidence is limited.", "Interactions are largely unspecified; evidence is limited."),
            supplement("turmeric", "Turmeric / curcumin", "Joint & Skin", 24 * 60, minuteOfDay(19), TimingPreference.WithDinner, 0xFFE76F51, "Botanical used for inflammatory or joint symptom support.", "With food, ideally with fat; split dosing is common.", "Caution with anticoagulants, gallbladder disease, and liver symptoms."),
            supplement("coq10", "CoQ10", "Heart & Metabolic", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFFF4A261, "Mitochondrial cofactor used for statin myalgia, migraine, or energy support.", "With a fat-containing meal; many prefer morning/noon.", "Can interact with warfarin and glucose-lowering regimens."),
            supplement("berberine", "Berberine", "Heart & Metabolic", 8 * 60, minuteOfDay(12, 30), TimingPreference.WithFood, 0xFFBC6C25, "Plant alkaloid marketed for glucose, lipids, and weight-related goals.", "With meals, often 2-3 times daily.", "Can interact with cyclosporine and CYP-metabolized drugs."),
            supplement("green_tea_extract", "Green tea extract", "Heart & Metabolic", 24 * 60, minuteOfDay(9), TimingPreference.WithBreakfast, 0xFF2D6A4F, "Concentrated catechin/EGCG product for cardiometabolic or weight goals.", "Morning/early afternoon if caffeinated; with food.", "Can affect some drug levels; liver injury is a known extract risk."),
            supplement("melatonin", "Melatonin", "Sleep & Stress", 24 * 60, minuteOfDay(22), TimingPreference.Bedtime, 0xFF6C5CE7, "Sleep-onset and circadian timing supplement.", "Usually 30-60 minutes before bedtime.", "Additive sedation is the main practical concern."),
            supplement("ashwagandha", "Ashwagandha", "Sleep & Stress", 24 * 60, minuteOfDay(20), TimingPreference.WithDinner, 0xFF8E7DBE, "Adaptogenic botanical studied for stress, anxiety, and sleep.", "Morning or evening by goal; with food if GI upset.", "Caution with thyroid, diabetes, BP, immune, sedative drugs; avoid pregnancy."),
            supplement("rhodiola", "Rhodiola", "Sleep & Stress", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFFB08968, "Adaptogen marketed for fatigue, stress, and mood.", "Morning or early afternoon; avoid late dosing.", "Reported losartan interaction; insomnia can occur."),
            supplement("lions_mane", "Lion's mane", "Botanicals", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFFDDA15E, "Mushroom marketed for cognition, mood, or nerve support.", "Morning or early day is pragmatic.", "Formal interaction data are sparse; avoid if mushroom-allergic."),
            supplement("nac", "NAC", "Antioxidants", 24 * 60, minuteOfDay(10), TimingPreference.Anytime, 0xFF577590, "N-acetylcysteine for antioxidant/glutathione support.", "With or without food; with food if nauseating.", "Medication review is prudent, especially nitrate therapy."),
            supplement("l_theanine", "L-theanine", "Sleep & Stress", 24 * 60, minuteOfDay(15), TimingPreference.Anytime, 0xFF8AC926, "Calm-focus amino acid often used for caffeine jitters or evening wind-down.", "With caffeine for focus or evening for relaxation.", "Review with sedatives, low blood pressure, or blood-pressure meds."),
            supplement("inositol", "Inositol", "Heart & Metabolic", 24 * 60, minuteOfDay(8, 30), TimingPreference.Anytime, 0xFF6A994E, "Often considered for PCOS and insulin-resistance contexts.", "Daily, often split; with or without food.", "Review with diabetes meds and pregnancy/fertility care."),
            supplement("saw_palmetto", "Saw palmetto", "Hormonal", 24 * 60, minuteOfDay(13), TimingPreference.WithFood, 0xFF7F5539, "Prostate/urinary supplement that should not be auto-started from symptoms alone.", "With meals if clinician-approved.", "Review first for urinary symptoms, blood thinners, surgery, and hormone-sensitive conditions."),
            supplement("tongkat_ali", "Tongkat ali", "Hormonal", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFF9C6644, "Hormone/libido product that should be lab- and clinician-first.", "Morning if clinician-approved.", "Review first for prostate, hormone-sensitive, liver/kidney, insomnia, anxiety, and product-quality concerns."),
            supplement("dhea", "DHEA", "Hormonal", 24 * 60, minuteOfDay(8), TimingPreference.Anytime, 0xFF9B2226, "Hormone precursor that can raise androgen/estrogen exposure.", "Usually morning.", "Clinician review for hormone-sensitive conditions, pregnancy, mood disorders."),
        )

        private fun supplement(
            id: String,
            name: String,
            category: String,
            defaultIntervalMinutes: Int,
            idealMinuteOfDay: Int,
            timingPreference: TimingPreference,
            accentColor: Long,
            description: String,
            timingNotes: String,
            interactionNotes: String,
        ): SupplementTemplate = SupplementTemplate(
            id = id,
            name = name,
            category = category,
            defaultIntervalMinutes = defaultIntervalMinutes,
            idealMinuteOfDay = idealMinuteOfDay,
            timingPreference = timingPreference,
            accentColor = accentColor,
            description = description,
            timingNotes = timingNotes,
            interactionNotes = interactionNotes,
        )

        val defaultInteractionRules = listOf(
            InteractionRule("default_iron_calcium", "iron", "calcium", 120, "Iron and calcium can compete; keep them about 2 hours apart.", editable = false),
            InteractionRule("default_iron_magnesium", "iron", "magnesium", 120, "Iron is usually kept away from magnesium.", editable = false),
            InteractionRule("default_iron_zinc", "iron", "zinc", 120, "Iron and zinc are commonly separated.", editable = false),
            InteractionRule("default_iron_multivitamin", "iron", "multivitamin", 120, "Multivitamins may contain competing minerals; avoid stacking with iron.", editable = false),
            InteractionRule("default_multivitamin_calcium", "multivitamin", "calcium", 120, "Multivitamins can contain minerals that compete with calcium.", editable = false),
            InteractionRule("default_multivitamin_magnesium", "multivitamin", "magnesium", 120, "Multivitamins can contain minerals that compete with magnesium.", editable = false),
            InteractionRule("default_multivitamin_zinc", "multivitamin", "zinc", 120, "Avoid stacking overlapping mineral products.", editable = false),
            InteractionRule("default_iron_coffee", "iron", "name:coffee", 120, "Coffee can reduce iron absorption.", editable = false),
            InteractionRule("default_iron_tea", "iron", "name:tea", 120, "Tea can reduce iron absorption.", editable = false),
            InteractionRule("default_iron_dairy", "iron", "name:dairy", 120, "Dairy or calcium-rich meals can reduce iron absorption.", editable = false),
            InteractionRule("default_iron_high_fiber", "iron", "name:high fiber", 120, "High-fiber meals can reduce iron absorption.", editable = false),
            InteractionRule("default_calcium_zinc", "calcium", "zinc", 120, "Calcium may reduce zinc absorption.", editable = false),
            InteractionRule("default_probiotic_zinc", "probiotic", "zinc", 60, "Keep probiotic timing distinct from mineral-heavy doses.", editable = false),
            InteractionRule("default_iron_levothyroxine", "iron", "name:levothyroxine", 240, "Levothyroxine should be separated from iron by at least 4 hours.", editable = false),
            InteractionRule("default_calcium_levothyroxine", "calcium", "name:levothyroxine", 240, "Levothyroxine should be separated from calcium by at least 4 hours.", editable = false),
            InteractionRule("default_iron_doxycycline", "iron", "name:doxycycline", 180, "Doxycycline is commonly separated from iron by several hours.", editable = false),
            InteractionRule("default_calcium_doxycycline", "calcium", "name:doxycycline", 180, "Calcium can chelate tetracyclines.", editable = false),
            InteractionRule("default_magnesium_doxycycline", "magnesium", "name:doxycycline", 180, "Magnesium can chelate tetracyclines.", editable = false),
            InteractionRule("default_zinc_doxycycline", "zinc", "name:doxycycline", 180, "Zinc can chelate tetracyclines.", editable = false),
            InteractionRule("default_iron_tetracycline", "iron", "name:tetracycline", 180, "Tetracyclines are commonly separated from iron.", editable = false),
            InteractionRule("default_calcium_tetracycline", "calcium", "name:tetracycline", 180, "Calcium can chelate tetracyclines.", editable = false),
            InteractionRule("default_magnesium_tetracycline", "magnesium", "name:tetracycline", 180, "Magnesium can chelate tetracyclines.", editable = false),
            InteractionRule("default_zinc_tetracycline", "zinc", "name:tetracycline", 180, "Zinc can chelate tetracyclines.", editable = false),
            InteractionRule("default_iron_ciprofloxacin", "iron", "name:ciprofloxacin", 360, "Ciprofloxacin needs wide spacing from iron and minerals.", editable = false),
            InteractionRule("default_calcium_ciprofloxacin", "calcium", "name:ciprofloxacin", 360, "Ciprofloxacin needs wide spacing from calcium.", editable = false),
            InteractionRule("default_zinc_ciprofloxacin", "zinc", "name:ciprofloxacin", 360, "Ciprofloxacin needs wide spacing from zinc.", editable = false),
            InteractionRule("default_magnesium_ciprofloxacin", "magnesium", "name:ciprofloxacin", 360, "Ciprofloxacin needs wide spacing from magnesium.", editable = false),
            InteractionRule("default_iron_quinolone", "iron", "name:quinolone", 360, "Quinolone antibiotics need wide spacing from iron.", editable = false),
            InteractionRule("default_calcium_quinolone", "calcium", "name:quinolone", 360, "Quinolone antibiotics need wide spacing from calcium.", editable = false),
            InteractionRule("default_magnesium_quinolone", "magnesium", "name:quinolone", 360, "Quinolone antibiotics need wide spacing from magnesium.", editable = false),
            InteractionRule("default_zinc_quinolone", "zinc", "name:quinolone", 360, "Quinolone antibiotics need wide spacing from zinc.", editable = false),
            InteractionRule("default_alendronate_calcium", "name:alendronate", "calcium", 30, "Avoid calcium and other pills right after alendronate.", editable = false),
            InteractionRule("default_alendronate_magnesium", "name:alendronate", "magnesium", 30, "Avoid magnesium and other pills right after alendronate.", editable = false),
            InteractionRule("default_alendronate_zinc", "name:alendronate", "zinc", 30, "Avoid zinc and other pills right after alendronate.", editable = false),
            InteractionRule("default_alendronate_multivitamin", "name:alendronate", "multivitamin", 30, "Avoid other pills right after alendronate.", editable = false),
            InteractionRule("default_iron_ppi", "iron", "name:ppi", 0, "Acid suppression can reduce iron absorption; this is not a simple hour-by-hour fix.", editable = false),
            InteractionRule("default_iron_omeprazole", "iron", "name:omeprazole", 0, "PPIs can reduce iron absorption over time.", editable = false),
            InteractionRule("default_vitamin_d_orlistat", "vitamin_d", "name:orlistat", 0, "Orlistat can reduce fat-soluble vitamin absorption.", editable = false),
            InteractionRule("default_vitamin_a_orlistat", "vitamin_a", "name:orlistat", 0, "Orlistat can reduce fat-soluble vitamin absorption.", editable = false),
            InteractionRule("default_vitamin_k_orlistat", "vitamin_k", "name:orlistat", 0, "Orlistat can reduce fat-soluble vitamin absorption.", editable = false),
            InteractionRule("default_vitamin_d_calcium", "vitamin_d", "calcium", 0, "Excess vitamin D with calcium can raise hypercalcemia risk.", editable = false),
            InteractionRule("default_vitamin_c_iron", "vitamin_c", "iron", 0, "Vitamin C can enhance nonheme iron absorption and may pair well with iron.", editable = false),
            InteractionRule("default_b12_metformin", "b12", "name:metformin", 0, "Metformin can reduce B12 status over time.", editable = false),
            InteractionRule("default_b12_ppi", "b12", "name:ppi", 0, "PPIs and H2 blockers can reduce B12 status over time.", editable = false),
            InteractionRule("default_folate_b12", "folate", "b12", 0, "High folic acid can mask B12 deficiency.", editable = false),
            InteractionRule("default_folate_methotrexate", "folate", "name:methotrexate", 0, "Folate timing with methotrexate is regimen-specific.", editable = false),
            InteractionRule("default_biotin_thyroid_labs", "biotin", "name:thyroid labs", 0, "Biotin can interfere with thyroid and cardiac lab tests.", editable = false),
            InteractionRule("default_choline_pregnancy", "choline", "name:pregnancy", 0, "Choline in pregnancy should align with prenatal clinician guidance.", editable = false),
            InteractionRule("default_omega3_anticoagulant", "omega_3", "name:anticoagulant", 0, "Omega-3 may need clinician review with anticoagulants or antiplatelets.", editable = false),
            InteractionRule("default_omega3_warfarin", "omega_3", "name:warfarin", 0, "Omega-3 may need clinician review with warfarin.", editable = false),
            InteractionRule("default_krill_anticoagulant", "krill_oil", "name:anticoagulant", 0, "Krill oil has the same general anticoagulant caution as other omega-3s.", editable = false),
            InteractionRule("default_krill_warfarin", "krill_oil", "name:warfarin", 0, "Krill oil may need clinician review with warfarin.", editable = false),
            InteractionRule("default_krill_shellfish", "krill_oil", "name:shellfish allergy", 0, "Consider shellfish allergy with krill oil.", editable = false),
            InteractionRule("default_algal_anticoagulant", "algal_oil", "name:anticoagulant", 0, "Algal oil has the same general omega-3 anticoagulant caution.", editable = false),
            InteractionRule("default_algal_warfarin", "algal_oil", "name:warfarin", 0, "Algal oil may need clinician review with warfarin.", editable = false),
            InteractionRule("default_probiotic_antibiotic", "probiotic", "name:antibiotic", 0, "Probiotic timing around antibiotics is product-specific; official hour spacing is not standardized.", editable = false),
            InteractionRule("default_psyllium_iron", "psyllium", "iron", 120, "Psyllium can reduce absorption of oral supplements; leave a practical gap.", editable = false),
            InteractionRule("default_psyllium_calcium", "psyllium", "calcium", 120, "Psyllium can reduce absorption of oral supplements; leave a practical gap.", editable = false),
            InteractionRule("default_psyllium_magnesium", "psyllium", "magnesium", 120, "Psyllium can reduce absorption of oral supplements; leave a practical gap.", editable = false),
            InteractionRule("default_psyllium_zinc", "psyllium", "zinc", 120, "Psyllium can reduce absorption of oral supplements; leave a practical gap.", editable = false),
            InteractionRule("default_psyllium_multivitamin", "psyllium", "multivitamin", 120, "Psyllium can reduce absorption of oral supplements; leave a practical gap.", editable = false),
            InteractionRule("default_vitamin_k_warfarin", "vitamin_k", "name:warfarin", 0, "With warfarin, consistent vitamin K intake matters more than avoidance.", editable = false),
            InteractionRule("default_vitamin_a_retinoid", "vitamin_a", "name:retinoid", 0, "Combining vitamin A with oral retinoids can increase toxicity risk.", editable = false),
            InteractionRule("default_selenium_cisplatin", "selenium", "name:cisplatin", 0, "Cisplatin can affect selenium status.", editable = false),
            InteractionRule("default_iodine_antithyroid", "iodine", "name:antithyroid", 0, "Iodine use with thyroid disease or antithyroid drugs needs clinician guidance.", editable = false),
            InteractionRule("default_glucosamine_warfarin", "glucosamine", "name:warfarin", 0, "Glucosamine may increase bleeding risk with warfarin.", editable = false),
            InteractionRule("default_turmeric_anticoagulant", "turmeric", "name:anticoagulant", 0, "Concentrated turmeric products warrant caution with anticoagulants.", editable = false),
            InteractionRule("default_turmeric_warfarin", "turmeric", "name:warfarin", 0, "Concentrated turmeric products warrant caution with warfarin.", editable = false),
            InteractionRule("default_turmeric_gallbladder", "turmeric", "name:gallbladder", 0, "Use caution with gallbladder disease.", editable = false),
            InteractionRule("default_turmeric_liver", "turmeric", "name:liver", 0, "Enhanced-bioavailability curcumin products have liver-injury cautions.", editable = false),
            InteractionRule("default_coq10_warfarin", "coq10", "name:warfarin", 0, "CoQ10 can interact with warfarin.", editable = false),
            InteractionRule("default_coq10_insulin", "coq10", "name:insulin", 0, "CoQ10 may affect glucose-lowering regimens.", editable = false),
            InteractionRule("default_melatonin_sedative", "melatonin", "name:sedative", 0, "Use caution combining melatonin with sedatives.", editable = false),
            InteractionRule("default_ltheanine_sedative", "l_theanine", "name:sedative", 0, "L-theanine may add to sedating regimens.", editable = false),
            InteractionRule("default_ltheanine_bp", "l_theanine", "name:blood pressure", 0, "L-theanine needs review with low blood pressure or BP medications.", editable = false),
            InteractionRule("default_lions_mane_mushroom_allergy", "lions_mane", "name:mushroom allergy", 0, "Avoid lion's mane if mushroom-allergic.", editable = false),
            InteractionRule("default_ashwagandha_thyroid", "ashwagandha", "name:thyroid", 0, "Ashwagandha may alter thyroid hormones or interact with thyroid medication.", editable = false),
            InteractionRule("default_ashwagandha_antidiabetes", "ashwagandha", "name:diabetes", 0, "Ashwagandha may interact with antidiabetes drugs.", editable = false),
            InteractionRule("default_ashwagandha_antihypertensive", "ashwagandha", "name:antihypertensive", 0, "Ashwagandha may interact with blood-pressure drugs.", editable = false),
            InteractionRule("default_ashwagandha_immunosuppressant", "ashwagandha", "name:immunosuppressant", 0, "Ashwagandha may interact with immunosuppressants.", editable = false),
            InteractionRule("default_ashwagandha_sedative", "ashwagandha", "name:sedative", 0, "Ashwagandha may add to sedative effects.", editable = false),
            InteractionRule("default_ashwagandha_pregnancy", "ashwagandha", "name:pregnancy", 0, "Avoid ashwagandha in pregnancy unless clinician-directed.", editable = false),
            InteractionRule("default_ashwagandha_liver", "ashwagandha", "name:liver", 0, "Liver injury cases have been reported with ashwagandha.", editable = false),
            InteractionRule("default_rhodiola_losartan", "rhodiola", "name:losartan", 0, "Rhodiola has a reported interaction with losartan.", editable = false),
            InteractionRule("default_nac_nitrate", "nac", "name:nitrate", 0, "Review NAC with nitrate therapy.", editable = false),
            InteractionRule("default_berberine_cyclosporine", "berberine", "name:cyclosporine", 0, "Berberine can interact with cyclosporine.", editable = false),
            InteractionRule("default_berberine_cyp", "berberine", "name:cyp", 0, "Berberine can affect CYP2D6, CYP2C9, and CYP3A4 drug handling.", editable = false),
            InteractionRule("default_berberine_pregnancy", "berberine", "name:pregnancy", 0, "Berberine use in pregnancy/lactation should be clinician-directed.", editable = false),
            InteractionRule("default_inositol_diabetes", "inositol", "name:diabetes", 0, "Inositol should be reviewed with diabetes medications.", editable = false),
            InteractionRule("default_inositol_pregnancy", "inositol", "name:pregnancy", 0, "Inositol in pregnancy/fertility care should be clinician-reviewed.", editable = false),
            InteractionRule("default_green_tea_nadolol", "green_tea_extract", "name:nadolol", 0, "Green tea extract can lower nadolol blood levels.", editable = false),
            InteractionRule("default_green_tea_atorvastatin", "green_tea_extract", "name:atorvastatin", 0, "Green tea extract can affect atorvastatin levels.", editable = false),
            InteractionRule("default_green_tea_raloxifene", "green_tea_extract", "name:raloxifene", 0, "Green tea extract can affect raloxifene levels.", editable = false),
            InteractionRule("default_green_tea_liver", "green_tea_extract", "name:liver", 0, "Concentrated green tea extract has a liver-injury warning.", editable = false),
            InteractionRule("default_larginine_nitrates", "l_arginine", "name:nitrate", 0, "L-arginine needs caution with nitrates.", editable = false),
            InteractionRule("default_larginine_pde5", "l_arginine", "name:pde5", 0, "L-arginine needs caution with PDE5 inhibitors.", editable = false),
            InteractionRule("default_larginine_antihypertensive", "l_arginine", "name:antihypertensive", 0, "L-arginine may add to blood-pressure lowering.", editable = false),
            InteractionRule("default_larginine_diabetes", "l_arginine", "name:diabetes", 0, "L-arginine needs caution with diabetes drugs.", editable = false),
            InteractionRule("default_dhea_hormone_therapy", "dhea", "name:hormone therapy", 0, "DHEA should be reviewed with hormone therapy.", editable = false),
            InteractionRule("default_dhea_hormone_sensitive", "dhea", "name:hormone-sensitive", 0, "DHEA needs caution in hormone-sensitive conditions.", editable = false),
            InteractionRule("default_dhea_pregnancy", "dhea", "name:pregnancy", 0, "Avoid DHEA in pregnancy/lactation unless clinician-directed.", editable = false),
            InteractionRule("default_dhea_mood", "dhea", "name:mood disorder", 0, "DHEA needs caution in mood disorders.", editable = false),
            InteractionRule("default_saw_palmetto_warfarin", "saw_palmetto", "name:warfarin", 0, "Saw palmetto needs review with blood thinners.", editable = false),
            InteractionRule("default_saw_palmetto_surgery", "saw_palmetto", "name:surgery", 0, "Saw palmetto needs review before surgery.", editable = false),
            InteractionRule("default_tongkat_hormone_sensitive", "tongkat_ali", "name:hormone-sensitive", 0, "Tongkat ali needs clinician review in hormone-sensitive contexts.", editable = false),
            InteractionRule("default_tongkat_prostate", "tongkat_ali", "name:prostate", 0, "Tongkat ali needs review with prostate concerns.", editable = false),
        )
    }
}
