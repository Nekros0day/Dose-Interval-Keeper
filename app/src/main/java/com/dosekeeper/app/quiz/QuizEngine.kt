package com.dosekeeper.app.quiz

import com.dosekeeper.app.data.SupplementTemplate

enum class QuizQuestionType {
    SingleSelect,
    MultiSelect,
}

enum class RecommendationStatus(val label: String) {
    LikelyUseful("Likely useful"),
    Consider("Consider"),
    Optional("Optional"),
    TestFirst("Test first"),
    ReviewFirst("Review first"),
    Avoid("Avoid"),
}

data class QuizOption(
    val value: String,
    val label: String,
    val tags: Set<String>,
)

data class QuizQuestion(
    val id: String,
    val section: String,
    val prompt: String,
    val type: QuizQuestionType,
    val maxSelections: Int = 1,
    val options: List<QuizOption>,
)

data class QuizRecommendation(
    val supplementId: String,
    val name: String,
    val status: RecommendationStatus,
    val score: Int,
    val why: List<String>,
    val timing: String,
    val warnings: List<String>,
    val labs: List<String> = emptyList(),
) {
    val addable: Boolean =
        status == RecommendationStatus.LikelyUseful ||
            status == RecommendationStatus.Consider ||
            status == RecommendationStatus.Optional
}

data class QuizResult(
    val summary: String,
    val likelyUseful: List<QuizRecommendation>,
    val consider: List<QuizRecommendation>,
    val testFirst: List<QuizRecommendation>,
    val reviewFirst: List<QuizRecommendation>,
    val avoid: List<QuizRecommendation>,
    val timingPlan: Map<String, List<String>>,
    val labs: List<String>,
    val safetyNotes: List<String>,
    val stoppedMessage: String? = null,
) {
    val allRecommendations: List<QuizRecommendation> =
        likelyUseful + consider + testFirst + reviewFirst + avoid
}

object QuizEngine {
    fun questionsFor(answers: Map<String, Set<String>>): List<QuizQuestion> {
        val tags = tagsFor(answers)
        val questions = mutableListOf(q0, q1)
        if ("stop_quiz" in tags || "under_18" in tags) return questions

        questions += listOf(q2, q3, q4, q5, q6, q7a, q7b, q7d, q8b, q8c, q8d, q9)

        if ("goal_energy" in tags) questions += listOf(e1, e2)
        if ("goal_sleep" in tags) questions += s1
        if ("goal_stress" in tags) questions += st1
        if ("goal_focus" in tags) questions += f1
        if ("goal_muscle" in tags) questions += m1
        if ("goal_gut" in tags) questions += g1
        if ("goal_metabolic" in tags) questions += me1
        if ("goal_joints" in tags) questions += j1
        if ("goal_skin_hair_nails" in tags) questions += h1
        if ("goal_libido_hormones" in tags) questions += l1
        if ("goal_female_cycle" in tags || "afab" in tags) questions += fc1
        if ("goal_male_health" in tags || "amab" in tags) questions += mh1

        questions += lab1
        if ("labs_available" in tags) questions += lab2
        return questions.distinctBy { it.id }
    }

    fun tagsFor(answers: Map<String, Set<String>>): Set<String> {
        val byId = allQuestions.associateBy { it.id }
        return answers.flatMap { (questionId, values) ->
            val question = byId[questionId]
            values.flatMap { value ->
                question?.options?.firstOrNull { it.value == value }?.tags.orEmpty()
            }
        }.toSet()
    }

    fun evaluate(answers: Map<String, Set<String>>, templates: List<SupplementTemplate>): QuizResult {
        val tags = tagsFor(answers)
        if ("stop_quiz" in tags) {
            return QuizResult(
                summary = "Quiz stopped.",
                likelyUseful = emptyList(),
                consider = emptyList(),
                testFirst = emptyList(),
                reviewFirst = emptyList(),
                avoid = emptyList(),
                timingPlan = emptyMap(),
                labs = emptyList(),
                safetyNotes = emptyList(),
                stoppedMessage = "No supplement guidance was generated.",
            )
        }
        if ("under_18" in tags) {
            return QuizResult(
                summary = "Pediatric-safe branch only.",
                likelyUseful = emptyList(),
                consider = emptyList(),
                testFirst = emptyList(),
                reviewFirst = emptyList(),
                avoid = emptyList(),
                timingPlan = emptyMap(),
                labs = listOf("Parent/guardian and pediatric clinician review"),
                safetyNotes = listOf("This quiz does not recommend supplements for children without pediatric guidance."),
                stoppedMessage = "For users under 18, use this as basic nutrition education and involve a parent/guardian and clinician.",
            )
        }

        val byId = templates.associateBy { it.id }
        val candidates = candidateIds.associateWith { Candidate(it, byId[it]?.name ?: displayName(it)) }.toMutableMap()
        fun add(id: String, points: Int, reason: String, testFirst: Boolean = false, reviewFirst: Boolean = false) {
            val candidate = candidates.getOrPut(id) { Candidate(id, byId[id]?.name ?: displayName(id)) }
            candidate.score += points
            candidate.reasons += reason
            if (testFirst) candidate.testFirst = true
            if (reviewFirst) candidate.reviewFirst = true
        }

        val has = tags::contains
        fun any(vararg values: String) = values.any(has)

        if (has("vegan")) {
            add("b12", 10, "You selected a vegan or fully plant-based diet.")
            add("algal_oil", if (has("low_fish")) 9 else 5, "Vegan users often use algal DHA/EPA when oily fish intake is low.")
            add("iodine", 3, "Plant-based diets can miss iodine if iodized salt or sea vegetables are not used.")
            add("zinc", 3, "Plant-based diets can be lower in absorbable zinc.")
        }
        if (has("vegetarian")) add("b12", 6, "Vegetarian diets can be lower in B12.")
        if (has("older_adult")) add("b12", 3, "Older adults can have higher B12 risk.")
        if (has("metformin_flag")) add("b12", 4, "Metformin can lower B12 status over time.")
        if (has("acid_suppressor_flag")) add("b12", 3, "Acid-suppressing medication can lower B12 status over time.")
        if (has("low_b12_known")) add("b12", 10, "You reported low B12 labs.")
        if (has("high_phytate_diet")) {
            add("zinc", 3, "Very high fiber, legumes, and grains can lower mineral absorption.")
            add("iron", 2, "High-phytate diets can reduce iron absorption, so iron is test-first if symptoms fit.", testFirst = true)
        }
        if (has("low_calorie_intake")) add("multivitamin", 4, "Low appetite or low-calorie intake can make a broad nutrition check reasonable.")
        if (has("low_sun")) add("vitamin_d", 8, "You selected low sun exposure.")
        if (has("low_vitamin_d_known")) add("vitamin_d", 10, "You reported low vitamin D labs.")
        if (has("low_fish") && !has("vegan")) add("omega_3", 8, "You reported low oily fish intake.")
        if (any("triglyceride_goal", "high_triglyceride_labs")) {
            add(if (has("vegan")) "algal_oil" else "omega_3", 6, "Triglyceride goals make EPA/DHA worth considering; high therapeutic dosing needs clinician guidance.")
        }
        if (has("low_calcium_food")) add("calcium", 7, "You reported low calcium-rich food intake.")
        if (has("postmenopause_goal")) {
            add("calcium", 5, "Postmenopause bone-health goals benefit from checking calcium intake.")
            add("vitamin_d", 4, "Vitamin D is relevant to bone-health screening.")
        }
        if (has("heavy_periods")) add("iron", 8, "Heavy periods can overlap with low ferritin risk.", testFirst = true)
        if (has("low_ferritin_known")) add("iron", 10, "You reported known low ferritin or iron deficiency.")
        if (has("restless_legs")) add("iron", 5, "Restless legs can overlap with low ferritin risk.", testFirst = true)
        if (has("afab")) add("iron", 3, "Female/AFAB nutrition screening can make iron status worth checking, especially with periods or fatigue.", testFirst = true)
        if ((has("vegan") || has("vegetarian")) && any("goal_energy", "fatigue_all_day", "fatigue_exercise")) {
            add("iron", 4, "Plant-based diet plus fatigue is a reason to test iron status.", testFirst = true)
        }
        if (has("constipation")) {
            add("psyllium", 8, "Psyllium is a strong match for constipation support.")
            add("magnesium", 4, "Magnesium citrate can be considered for constipation if kidney safety is clear.")
        }
        if (has("antibiotic_gut_context")) add("probiotic", 4, "A targeted probiotic can be considered around antibiotic-associated gut concerns.", reviewFirst = true)
        if (any("ibs_flag", "general_gut_goal", "bloating", "diarrhea")) add("probiotic", 3, "Probiotics are strain-specific and may be considered for selected gut goals.")
        if (any("ldl_goal", "high_ldl_labs")) add("psyllium", 8, "Psyllium is a strong match for LDL support.")
        if (any("glucose_goal", "glucose_flag", "high_a1c", "pcos_metabolic_goal", "appetite_goal", "weight_goal", "cravings_goal")) {
            add("psyllium", 5, "Psyllium can support glucose stability.")
            if (any("glucose_goal", "glucose_flag", "high_a1c", "pcos_metabolic_goal")) {
                add("inositol", 6, "Inositol is commonly considered for PCOS or insulin-resistance contexts.")
                add("berberine", 4, "Berberine is a goal match but needs medication and pregnancy review.", reviewFirst = true)
            }
        }
        if (any("strength_training", "mixed_training", "high_intensity_training", "strength_power_goal", "muscle_gain_goal")) {
            add("creatine", if (has("vegan")) 12 else 10, "Creatine is a strong match for strength, muscle, and high-intensity training.")
        }
        if (has("endurance_training")) add("omega_3", 3, "Endurance training can make omega-3 intake worth considering if fish intake is low.")
        if (has("recovery_goal")) add("omega_3", 3, "Omega-3 can be considered for training recovery goals.")
        if (any("tendon_goal", "skin_aging_goal", "skin_elasticity_goal")) add("collagen", 7, "Collagen is a reasonable option for tendon, ligament, or skin elasticity goals.")
        if (has("osteoarthritis_goal")) {
            add("glucosamine", 4, "Glucosamine is commonly considered for osteoarthritis-type joint goals.")
            add("msm", 3, "MSM is often paired with glucosamine for joint symptoms.")
            add("turmeric", 4, "Curcumin is a joint/inflammation match if liver and bleeding flags are clear.", reviewFirst = true)
        }
        if (any("inflammation_goal", "goal_joints", "soreness_goal")) {
            add("omega_3", 4, "Omega-3 can be a reasonable inflammation/joint support option.")
            add("turmeric", 4, "Curcumin can be considered for inflammation goals if safe.", reviewFirst = true)
        }
        if (any("sleep_onset_issue", "circadian_shift")) add("melatonin", 7, "Melatonin is a fit for sleep-onset or circadian timing issues.")
        if (has("sleep_maintenance_issue")) add("magnesium", 4, "Magnesium is often considered for sleep maintenance or tension.")
        if (any("anxious_stress", "caffeine_sensitive", "caffeine_related_focus")) add("l_theanine", 6, "L-theanine can fit anxious stress, caffeine jitters, or calm focus.")
        if (any("muscle_tension_stress", "cramps_goal")) add("magnesium", 4, "Magnesium can be considered for muscle tension or cramp-oriented goals if safety flags are clear.")
        if (has("high_stress")) add("ashwagandha", 5, "Ashwagandha is a stress/sleep match only if safety flags are clear.", reviewFirst = true)
        if (has("burnout_stress")) add("rhodiola", 4, "Rhodiola can fit burnout-style morning fatigue if safe.", reviewFirst = true)
        if (any("brain_fog", "stress_related_focus")) add("lions_mane", 3, "Lion's mane is an optional, lower-confidence nootropic after sleep/labs/nutrition basics.")
        if (has("goal_immune")) {
            if (has("low_sun")) add("vitamin_d", 4, "Immune-support goal plus low sun makes vitamin D more relevant.")
            add("zinc", 3, "Zinc can be considered short-term for immune support, but chronic high-dose use is not a default.")
        }
        if (has("brittle_nails")) add("biotin", 2, "Biotin is low-confidence for brittle nails and has lab-test warnings.", reviewFirst = true)
        if (has("hair_loss")) {
            add("iron", 5, "Hair shedding is test-first: ferritin, thyroid, vitamin D, B12, CBC, and protein intake matter.", testFirst = true)
            add("vitamin_d", 3, "Hair loss screening commonly includes vitamin D status.", testFirst = true)
            add("b12", 3, "Hair loss screening may include B12 depending diet and symptoms.", testFirst = true)
        }
        if (has("pms_goal")) {
            add("magnesium", 4, "Magnesium can be considered for PMS symptoms.")
            add("calcium", 3, "Calcium intake check can be relevant for PMS symptoms.")
        }
        if (has("pcos_goal")) {
            add("inositol", 6, "Inositol is a reasonable PCOS-context option.")
            add("vitamin_d", 3, "Vitamin D is relevant if low or insufficient.")
        }
        if (has("prostate_symptoms")) add("saw_palmetto", 2, "Urinary/prostate symptoms need review first, not automatic saw palmetto.", reviewFirst = true)
        if (has("low_testosterone_symptoms")) {
            add("tongkat_ali", 1, "Testosterone/libido concerns should be lab-first, not quiz-based hormone supplement starts.", reviewFirst = true)
            add("dhea", 1, "DHEA should not be started from a quiz without clinician/lab context.", reviewFirst = true)
        }
        if (has("fertility_goal")) {
            add("folate", 4, "Fertility and preconception contexts often start with folate/prenatal nutrient review.")
            add("choline", 3, "Choline intake can be relevant in preconception nutrition.")
            add(if (has("vegan")) "algal_oil" else "omega_3", 3, "Omega-3 intake can be part of fertility nutrition review.")
        }
        if (has("male_fertility_goal")) {
            add("zinc", 3, "Zinc can be supportive if intake is low in male-fertility contexts.")
            add("selenium", 3, "Selenium may be considered when intake is low, but avoid stacking high doses.")
            add("omega_3", 3, "Omega-3 is a supportive, not diagnostic, male-fertility nutrition option.")
        }
        if (any("trying_to_conceive", "pregnant", "breastfeeding")) {
            add("folate", 8, "Pregnancy/trying/breastfeeding branch makes folate/prenatal nutrient review relevant.")
            add("iodine", 4, "Iodine may be relevant in pregnancy contexts if appropriate.")
            add("choline", 4, "Choline intake can be relevant in pregnancy/trying contexts.")
            add("algal_oil", 4, "DHA/EPA is often considered in prenatal-style nutrition.")
            add("vitamin_d", 3, "Vitamin D is relevant if low sun or labs suggest low status.")
        }
        if (has("statin_flag")) add("coq10", 4, "CoQ10 is often considered around statin-associated symptoms, but interactions still matter.")

        applySafety(tags, candidates)
        val labs = labSuggestions(tags)
        candidates.values.filter { it.score > 0 }.forEach { candidate ->
            candidate.warnings += timingWarning(candidate.id, tags)
            candidate.labs += labsFor(candidate.id, tags)
        }

        val recommendations = candidates.values
            .filter { it.score > 0 || it.reviewFirst || it.avoid }
            .map { it.toRecommendation(byId[it.id]) }
            .sortedWith(compareBy<QuizRecommendation> { statusRank(it.status) }.thenByDescending { it.score }.thenBy { it.name })

        val likely = recommendations.filter { it.status == RecommendationStatus.LikelyUseful }.take(6)
        val consider = recommendations.filter { it.status == RecommendationStatus.Consider || it.status == RecommendationStatus.Optional }.take(8)
        val test = recommendations.filter { it.status == RecommendationStatus.TestFirst }.take(8)
        val review = recommendations.filter { it.status == RecommendationStatus.ReviewFirst }.take(10)
        val avoid = recommendations.filter { it.status == RecommendationStatus.Avoid }.take(8)
        val strongest = (likely + consider).take(3).joinToString(", ") { it.name }.ifBlank { "no low-risk supplement starts" }

        return QuizResult(
            summary = "Based on your answers, your strongest low-risk matches are $strongest. Test-first and review-first items are kept separate.",
            likelyUseful = likely,
            consider = consider,
            testFirst = test,
            reviewFirst = review,
            avoid = avoid,
            timingPlan = timingPlan(likely + consider + test),
            labs = labs,
            safetyNotes = safetyNotes(tags),
        )
    }

    private fun applySafety(tags: Set<String>, candidates: MutableMap<String, Candidate>) {
        fun mark(ids: List<String>, review: Boolean = false, avoid: Boolean = false, warning: String) {
            ids.forEach { id ->
                candidates[id]?.let {
                    if (review) it.reviewFirst = true
                    if (avoid) it.avoid = true
                    it.warnings += warning
                }
            }
        }
        val pregnancy = tags.any { it in pregnancyTags }
        if (pregnancy) mark(
            listOf("ashwagandha", "rhodiola", "berberine", "dhea", "tongkat_ali", "saw_palmetto", "green_tea_extract", "turmeric", "melatonin"),
            review = true,
            warning = "Pregnancy/trying/breastfeeding requires clinician review for this supplement.",
        )
        if (tags.any { it in setOf("warfarin_flag", "anticoagulant_flag", "bleeding_flag", "surgery_flag") }) mark(
            listOf("omega_3", "algal_oil", "krill_oil", "turmeric", "glucosamine", "coq10", "vitamin_k"),
            review = true,
            warning = "Blood thinner, bleeding, or surgery flags mean this should be reviewed first.",
        )
        if ("kidney_flag" in tags) mark(
            listOf("magnesium", "creatine", "vitamin_d", "calcium", "zinc"),
            review = true,
            warning = "Kidney disease or low eGFR requires review before high-dose minerals, vitamin D, or creatine.",
        )
        if ("liver_flag" in tags) mark(
            listOf("ashwagandha", "green_tea_extract", "turmeric", "berberine"),
            review = true,
            warning = "Liver flags make this review-first.",
        )
        if (tags.any { it in setOf("mania_flag", "neuropsych_med_flag") }) mark(
            listOf("rhodiola", "dhea", "tongkat_ali", "green_tea_extract"),
            review = true,
            warning = "Mood history or neuropsych meds make stimulant-like or hormone products review-first.",
        )
        if (tags.any { it in setOf("immune_suppressed_flag", "immune_med_flag") }) mark(
            listOf("probiotic", "lions_mane", "ashwagandha"),
            review = true,
            warning = "Immune suppression makes probiotics, mushroom extracts, and immune-active botanicals review-first.",
        )
        if ("diabetes_med_flag" in tags) mark(listOf("berberine", "inositol", "ashwagandha"), review = true, warning = "Diabetes medication use needs review for glucose-lowering supplements.")
        if ("bp_med_flag" in tags) mark(listOf("coq10", "magnesium", "l_theanine", "berberine", "l_arginine"), review = true, warning = "Blood-pressure medication use makes this review-first.")
        if ("sedative_flag" in tags) mark(listOf("melatonin", "magnesium", "l_theanine", "ashwagandha"), review = true, warning = "Sedatives, opioids, sleep meds, or alcohol-for-sleep make calming supplements review-first.")
        if ("thyroid_flag" in tags) mark(listOf("iodine", "selenium", "ashwagandha", "biotin"), review = true, warning = "Thyroid conditions or thyroid medication require review and lab/timing awareness.")
        if ("abnormal_thyroid_labs" in tags) mark(listOf("iodine", "selenium", "ashwagandha", "biotin"), review = true, warning = "Abnormal thyroid labs make thyroid-active supplements and biotin review-first.")
        if ("antibiotic_flag" in tags) mark(listOf("iron", "calcium", "magnesium", "zinc", "multivitamin", "probiotic"), review = true, warning = "Antibiotic use can require mineral spacing and product-specific probiotic advice.")
        if ("bisphosphonate_flag" in tags) mark(listOf("calcium", "magnesium", "zinc", "multivitamin"), review = true, warning = "Bisphosphonates need their own empty-stomach absorption window before minerals, food, coffee, or other pills.")
        if ("autoimmune_flag" in tags) mark(listOf("ashwagandha", "lions_mane", "probiotic"), review = true, warning = "Autoimmune conditions make immune-active botanicals, mushrooms, and probiotics review-first.")
        if ("seizure_flag" in tags) mark(listOf("melatonin"), review = true, warning = "Seizure history makes sleep supplements review-first.")
        if ("cardiac_flag" in tags) mark(listOf("coq10", "magnesium", "l_theanine"), review = true, warning = "Heart rhythm, heart disease, or heart failure history makes cardiovascular or relaxing supplements review-first.")
        if (tags.any { it in setOf("cancer_flag", "hormone_med_flag") }) mark(
            listOf("dhea", "tongkat_ali", "saw_palmetto"),
            review = true,
            warning = "Hormone-sensitive conditions or hormone therapy make hormone-modulating supplements review-first.",
        )

        candidates["iron"]?.let {
            if (it.score > 0 && "low_ferritin_known" !in tags) it.testFirst = true
        }
        candidates["dhea"]?.let {
            if (it.score > 0) it.avoid = true
        }
        listOf("tongkat_ali", "saw_palmetto").forEach { id ->
            candidates[id]?.let {
                if (it.score > 0) it.reviewFirst = true
            }
        }
    }

    private fun labSuggestions(tags: Set<String>): List<String> {
        val labs = mutableListOf<String>()
        if (tags.any { it.startsWith("fatigue_") } || "goal_energy" in tags) labs += listOf("CBC", "Ferritin", "B12", "Vitamin D", "TSH", "Metabolic panel")
        if ("heavy_periods" in tags) labs += listOf("CBC", "Ferritin", "Iron studies if clinician wants")
        if ("hair_loss" in tags) labs += listOf("Ferritin", "TSH", "Vitamin D", "B12", "CBC", "Protein/diet review")
        if (tags.any { it in setOf("low_libido", "low_testosterone_symptoms", "erectile_concern") }) labs += listOf("Clinician-directed hormone labs", "TSH", "Vitamin D", "A1c if relevant")
        if (tags.any { it in setOf("glucose_goal", "pcos_metabolic_goal", "high_a1c") }) labs += listOf("A1c", "Fasting glucose", "Lipids", "Liver/kidney markers")
        if (tags.any { it in setOf("low_calcium_food", "postmenopause_goal") }) labs += listOf("Vitamin D", "Calcium intake estimate", "Kidney function before high-dose minerals")
        return labs.distinct()
    }

    private fun labsFor(id: String, tags: Set<String>): List<String> = when (id) {
        "iron" -> listOf("CBC", "Ferritin")
        "vitamin_d" -> if ("low_vitamin_d_known" !in tags) listOf("25(OH) vitamin D if using higher doses") else emptyList()
        "b12" -> if (tags.any { it in setOf("goal_energy", "brain_fog", "hair_loss") }) listOf("B12 if symptoms are present") else emptyList()
        "biotin" -> listOf("Tell your clinician/lab because biotin can interfere with tests.")
        else -> emptyList()
    }

    private fun timingWarning(id: String, tags: Set<String>): List<String> {
        val warnings = mutableListOf<String>()
        if (id in setOf("iron", "calcium", "magnesium", "zinc", "multivitamin") && "levothyroxine_flag" in tags) {
            warnings += "Keep minerals and multivitamins at least 4 hours away from levothyroxine unless told otherwise."
        }
        if (id in setOf("iron", "calcium", "magnesium", "zinc", "multivitamin") && "antibiotic_flag" in tags) {
            warnings += "Do not take minerals at the same time as tetracycline or fluoroquinolone antibiotics; follow pharmacist instructions."
        }
        if (id in setOf("calcium", "magnesium", "zinc", "multivitamin") && "bisphosphonate_flag" in tags) {
            warnings += "Keep minerals and other pills out of the bisphosphonate absorption window unless your prescriber gave different instructions."
        }
        if (id == "omega_3" && tags.any { it in setOf("warfarin_flag", "anticoagulant_flag", "bleeding_flag", "surgery_flag") }) {
            warnings += "Omega-3 may need clinician review with blood thinner, bleeding, or surgery factors."
        }
        if (id == "biotin") warnings += "Biotin can interfere with some lab tests; tell your clinician or lab."
        return warnings
    }

    private fun safetyNotes(tags: Set<String>): List<String> = buildList {
        if (tags.any { it in pregnancyTags }) add("Pregnancy, trying to conceive, or breastfeeding shifts many botanicals and hormone products to review-first.")
        if ("levothyroxine_flag" in tags) add("Take thyroid medication as prescribed and keep calcium, iron, magnesium, zinc, and multivitamins 4 hours away unless instructed otherwise.")
        if ("condition_unsure" in tags || "meds_unsure" in tags) add("Because you marked unsure, review any supplement starts with a clinician or pharmacist.")
        if ("iron_red_flag" in tags) add("Pale, dizzy, short-of-breath, or exertional symptoms deserve medical evaluation rather than supplement-only troubleshooting.")
        if ("fatigue_sleepiness" in tags || "poor_sleep_quality" in tags) add("Sleepiness or unrefreshing sleep may need sleep-quality or medical review before stacking sleep supplements.")
        if ("panic_flag" in tags || "low_mood_flag" in tags) add("Panic attacks or low mood should not be handled as a supplement-only problem.")
        if ("memory_concern" in tags) add("Progressive memory concerns should be reviewed clinically; nootropic supplements should not be overpromised.")
        if ("complex_hormone_context" in tags) add("Complex biological hormone context makes hormone-modulating supplements review-first rather than quiz-directed.")
    }

    private fun timingPlan(recommendations: List<QuizRecommendation>): Map<String, List<String>> {
        val buckets = linkedMapOf(
            "Morning" to mutableListOf<String>(),
            "With meal" to mutableListOf(),
            "With fat-containing meal" to mutableListOf(),
            "Away from meds/minerals" to mutableListOf(),
            "Evening" to mutableListOf(),
            "Bedtime" to mutableListOf(),
        )
        recommendations.forEach { rec ->
            when (rec.supplementId) {
                "b12", "rhodiola", "lions_mane", "dhea", "tongkat_ali" -> buckets.getValue("Morning") += rec.name
                "vitamin_d", "omega_3", "algal_oil", "krill_oil", "coq10", "turmeric", "vitamin_a", "vitamin_k" -> buckets.getValue("With fat-containing meal") += rec.name
                "iron" -> buckets.getValue("Away from meds/minerals") += "${rec.name} only if confirmed; separate from calcium/coffee/tea/minerals"
                "magnesium", "ashwagandha", "l_theanine" -> buckets.getValue("Evening") += rec.name
                "melatonin" -> buckets.getValue("Bedtime") += "${rec.name} 30-60 min before target bedtime"
                else -> buckets.getValue("With meal") += rec.name
            }
        }
        return buckets.filterValues { it.isNotEmpty() }
    }

    private fun Candidate.toRecommendation(template: SupplementTemplate?): QuizRecommendation {
        val status = when {
            avoid -> RecommendationStatus.Avoid
            reviewFirst -> RecommendationStatus.ReviewFirst
            testFirst -> RecommendationStatus.TestFirst
            score >= 8 -> RecommendationStatus.LikelyUseful
            score >= 4 -> RecommendationStatus.Consider
            else -> RecommendationStatus.Optional
        }
        return QuizRecommendation(
            supplementId = id,
            name = template?.name ?: name,
            status = status,
            score = score,
            why = reasons.distinct().take(4),
            timing = template?.timingNotes ?: defaultTiming(id),
            warnings = warnings.distinct().take(5),
            labs = labs.distinct(),
        )
    }

    private data class Candidate(
        val id: String,
        val name: String,
        var score: Int = 0,
        var reviewFirst: Boolean = false,
        var testFirst: Boolean = false,
        var avoid: Boolean = false,
        val reasons: MutableList<String> = mutableListOf(),
        val warnings: MutableList<String> = mutableListOf(),
        val labs: MutableList<String> = mutableListOf(),
    )

    private fun statusRank(status: RecommendationStatus): Int = when (status) {
        RecommendationStatus.LikelyUseful -> 0
        RecommendationStatus.Consider -> 1
        RecommendationStatus.Optional -> 2
        RecommendationStatus.TestFirst -> 3
        RecommendationStatus.ReviewFirst -> 4
        RecommendationStatus.Avoid -> 5
    }

    private fun defaultTiming(id: String): String = when (id) {
        "iron" -> "Away from calcium, coffee, tea, and mineral supplements. Vitamin C may help."
        "melatonin" -> "30-60 minutes before target bedtime."
        else -> "Use the supplement label timing."
    }

    private fun displayName(id: String): String = id.replace("_", " ").replaceFirstChar { it.uppercase() }

    private val candidateIds = listOf(
        "multivitamin", "b12", "vitamin_d", "algal_oil", "omega_3", "iron", "calcium", "magnesium", "zinc",
        "creatine", "psyllium", "melatonin", "l_theanine", "collagen", "inositol", "berberine",
        "ashwagandha", "turmeric", "biotin", "probiotic", "rhodiola", "lions_mane", "glucosamine",
        "msm", "coq10", "choline", "iodine", "selenium", "folate", "saw_palmetto", "tongkat_ali",
        "dhea",
    )

    private val pregnancyTags = setOf("pregnant", "trying_to_conceive", "breastfeeding")

    private fun single(id: String, section: String, prompt: String, vararg options: QuizOption): QuizQuestion =
        QuizQuestion(id, section, prompt, QuizQuestionType.SingleSelect, 1, options.toList())

    private fun multi(id: String, section: String, prompt: String, max: Int, vararg options: QuizOption): QuizQuestion =
        QuizQuestion(id, section, prompt, QuizQuestionType.MultiSelect, max, options.toList())

    private fun option(value: String, label: String, vararg tags: String): QuizOption =
        QuizOption(value, label, tags.toSet())

    private val q0 = single("q0", "safety", "This quiz gives educational supplement guidance. It does not diagnose conditions or replace a clinician. Continue?", option("consent_yes", "Yes, continue", "consent_given"), option("consent_no", "No", "stop_quiz"))
    private val q1 = single("q1", "profile", "What is your age group?", option("under_18", "Under 18", "under_18", "pediatric_branch"), option("age_18_24", "18-24", "adult"), option("age_25_44", "25-44", "adult"), option("age_45_64", "45-64", "adult", "midlife"), option("age_65_plus", "65+", "adult", "older_adult"))
    private val q2 = single("q2", "profile", "Which best describes your biological health context for nutrition screening?", option("female_afab", "Female / assigned female at birth", "afab", "female_related_screen"), option("male_amab", "Male / assigned male at birth", "amab", "male_related_screen"), option("intersex", "Intersex / differences in sex development", "complex_hormone_context"), option("prefer_not", "Prefer not to say", "sex_unknown"))
    private val q3 = single("q3", "safety", "Are any of these true for you right now?", option("pregnant", "Pregnant", "pregnant", "pregnancy_safe_branch"), option("trying_to_conceive", "Trying to conceive", "trying_to_conceive", "pregnancy_safe_branch"), option("breastfeeding", "Breastfeeding / lactating", "breastfeeding", "pregnancy_safe_branch"), option("none", "None of these", "not_pregnant"), option("not_applicable", "Not applicable", "not_pregnant"))
    private val q4 = multi("q4", "safety", "Do you have any of these conditions or medical situations?", 20, option("kidney_disease", "Kidney disease or reduced kidney function", "kidney_flag"), option("liver_disease", "Liver disease or high liver enzymes", "liver_flag"), option("thyroid_condition", "Thyroid disease or thyroid medication", "thyroid_flag"), option("autoimmune", "Autoimmune condition", "autoimmune_flag"), option("bleeding_disorder", "Bleeding disorder or easy bleeding", "bleeding_flag"), option("heart_condition", "Heart rhythm problem, heart disease, or heart failure", "cardiac_flag"), option("diabetes", "Diabetes, prediabetes, insulin resistance, or PCOS glucose issues", "glucose_flag"), option("bipolar_mania", "Bipolar disorder, mania, or manic episodes", "mania_flag"), option("seizure_disorder", "Seizure disorder or epilepsy", "seizure_flag"), option("cancer_history", "Cancer history or hormone-sensitive cancer risk", "cancer_flag"), option("transplant_immunosuppressed", "Transplant, immunosuppressed, chemotherapy, biologics, or high-dose steroids", "immune_suppressed_flag"), option("surgery_soon", "Surgery, dental surgery, or procedure in next 2 weeks", "surgery_flag"), option("none", "None of these", "no_major_condition"), option("unsure", "Not sure", "condition_unsure"))
    private val q5 = multi("q5", "safety", "Are you currently taking any of these medications?", 20, option("warfarin", "Warfarin/Coumadin", "warfarin_flag"), option("anticoagulant_antiplatelet", "Blood thinner or antiplatelet", "anticoagulant_flag"), option("levothyroxine", "Levothyroxine or thyroid hormone", "levothyroxine_flag"), option("diabetes_meds", "Insulin, metformin, GLP-1, or other diabetes medication", "diabetes_med_flag"), option("bp_meds", "Blood pressure medication", "bp_med_flag"), option("statin", "Statin or cholesterol medication", "statin_flag"), option("ppi_h2", "PPI/H2 blocker", "acid_suppressor_flag"), option("metformin", "Metformin", "metformin_flag"), option("ssri_snri_maoi", "SSRI, SNRI, MAOI, stimulant, ADHD medication", "neuropsych_med_flag"), option("sedative_sleep", "Sleep medication, sedative, opioid, or alcohol use for sleep", "sedative_flag"), option("antibiotics_current", "Current antibiotic course", "antibiotic_flag"), option("bisphosphonate", "Alendronate/risedronate/bisphosphonate", "bisphosphonate_flag"), option("birth_control_hormones", "Hormonal birth control or hormone therapy", "hormone_med_flag"), option("immunosuppressant", "Immunosuppressant, biologic, transplant medication, chemotherapy", "immune_med_flag"), option("none", "None of these", "no_meds"), option("unsure", "Not sure", "meds_unsure"))
    private val q6 = single("q6", "diet", "Which best describes your usual diet?", option("vegan", "Vegan / fully plant-based", "vegan"), option("vegetarian", "Vegetarian", "vegetarian"), option("pescatarian", "Pescatarian", "pescatarian"), option("omnivore", "Mixed diet including meat/fish", "omnivore"), option("low_carb_keto", "Low-carb or keto", "keto_low_carb"), option("low_calorie", "Low appetite, dieting, or low-calorie intake", "low_calorie_intake"), option("high_fiber_legumes", "Very high fiber/legumes/grains", "high_phytate_diet"), option("unsure", "Not sure", "diet_unsure"))
    private val q7a = single("q7a", "diet", "How often do you eat oily fish?", option("fish_never", "Never", "low_fish"), option("fish_less_weekly", "Less than once per week", "low_fish"), option("fish_1_weekly", "About once per week", "moderate_fish"), option("fish_2_plus_weekly", "2+ times per week", "adequate_fish"))
    private val q7b = single("q7b", "diet", "How many calcium-rich servings do you get most days?", option("calcium_0_1", "0-1 servings/day", "low_calcium_food"), option("calcium_2", "About 2 servings/day", "moderate_calcium_food"), option("calcium_3_plus", "3+ servings/day", "adequate_calcium_food"), option("unsure", "Not sure", "calcium_unsure"))
    private val q7d = single("q7d", "diet", "How much direct sun exposure do you usually get?", option("sun_low", "Low: indoors/covered/sunscreen/winter/northern climate", "low_sun"), option("sun_moderate", "Moderate", "moderate_sun"), option("sun_high", "High", "high_sun"), option("unsure", "Not sure", "sun_unsure"))
    private val q8b = single("q8b", "lifestyle", "How stressed do you feel most days?", option("stress_low", "Low", "low_stress"), option("stress_moderate", "Moderate", "moderate_stress"), option("stress_high", "High", "high_stress"), option("stress_burnout", "Burned out / wired but tired", "burnout_stress"))
    private val q8c = single("q8c", "lifestyle", "What type of exercise do you do most weeks?", option("none", "Little/no exercise", "sedentary"), option("cardio", "Mostly cardio", "cardio_training"), option("strength", "Strength training", "strength_training"), option("mixed", "Mix of cardio and strength", "mixed_training"), option("endurance", "Endurance training", "endurance_training"), option("high_intensity", "High-intensity/sprint/team sport", "high_intensity_training"))
    private val q8d = single("q8d", "lifestyle", "How do you respond to caffeine?", option("caffeine_fine", "Fine", "caffeine_ok"), option("caffeine_jittery", "Jittery/anxious", "caffeine_sensitive"), option("caffeine_sleep", "It hurts my sleep", "caffeine_sleep_issue"), option("no_caffeine", "I avoid caffeine", "caffeine_avoid"))
    private val q9 = multi("q9", "goals", "Pick up to 3 goals you care about most.", 3, option("energy", "Energy / fatigue", "goal_energy"), option("sleep", "Sleep", "goal_sleep"), option("stress", "Stress / anxiety", "goal_stress"), option("focus", "Focus / brain fog", "goal_focus"), option("immune", "Immune support", "goal_immune"), option("muscle", "Strength / muscle / performance", "goal_muscle"), option("gut", "Gut health / constipation / bloating", "goal_gut"), option("metabolic", "Cholesterol / glucose / weight", "goal_metabolic"), option("joints", "Joint pain / tendons / inflammation", "goal_joints"), option("skin_hair_nails", "Skin / hair / nails", "goal_skin_hair_nails"), option("libido_hormones", "Libido / testosterone / hormones", "goal_libido_hormones"), option("female_cycle", "PMS / cramps / heavy periods / menopause / PCOS", "goal_female_cycle"), option("male_health", "Prostate / male fertility / male hormones", "goal_male_health"))

    private val e1 = single("e1", "branch", "Which best describes your fatigue?", option("fatigue_all_day", "Tired all day", "fatigue_all_day"), option("fatigue_morning", "Worst in the morning", "fatigue_morning"), option("fatigue_afternoon", "Afternoon crash", "fatigue_afternoon_crash"), option("fatigue_exercise", "Exercise feels unusually hard", "fatigue_exercise_intolerance"), option("fatigue_sleepy", "Sleepy/drowsy", "fatigue_sleepiness"), option("fatigue_unknown", "Not sure", "fatigue_unsure"))
    private val e2 = multi("e2", "branch", "Do any iron-risk symptoms apply?", 5, option("heavy_periods", "Heavy periods", "heavy_periods"), option("restless_legs", "Restless legs", "restless_legs"), option("pale_dizzy", "Pale, dizzy, or short of breath with exertion", "iron_red_flag"), option("low_ferritin_known", "Known low ferritin or iron deficiency", "low_ferritin_known"), option("none", "None", "no_iron_symptoms"))
    private val s1 = single("s1", "branch", "What is the main sleep issue?", option("sleep_onset", "Trouble falling asleep", "sleep_onset_issue"), option("sleep_maintenance", "Waking during the night", "sleep_maintenance_issue"), option("early_waking", "Waking too early", "early_waking_issue"), option("jet_lag", "Jet lag or time-zone shift", "circadian_shift"), option("shift_work", "Shift work schedule", "circadian_shift"), option("poor_quality", "Long enough but not refreshing", "poor_sleep_quality"))
    private val st1 = single("st1", "branch", "What does your stress feel like?", option("anxious_racing", "Racing thoughts/anxious tension", "anxious_stress"), option("wired_tired", "Wired but tired", "burnout_stress"), option("low_mood", "Low mood or motivation", "low_mood_flag"), option("physical_tension", "Muscle tension", "muscle_tension_stress"), option("panic", "Panic attacks", "panic_flag"))
    private val f1 = single("f1", "branch", "What best describes your focus issue?", option("brain_fog", "Brain fog / cloudy thinking", "brain_fog"), option("caffeine_crash", "Caffeine crash or jitters", "caffeine_related_focus"), option("memory", "Memory concerns", "memory_concern"), option("stress_focus", "Focus worsens with stress", "stress_related_focus"))
    private val m1 = single("m1", "branch", "What is your training goal?", option("strength_power", "Strength or power", "strength_power_goal"), option("muscle_gain", "Muscle gain", "muscle_gain_goal"), option("endurance", "Endurance", "endurance_goal"), option("recovery", "Recovery", "recovery_goal"), option("injury_tendon", "Tendon/ligament support", "tendon_goal"))
    private val g1 = single("g1", "branch", "What gut issue are you targeting?", option("constipation", "Constipation", "constipation"), option("diarrhea", "Diarrhea", "diarrhea"), option("bloating", "Bloating", "bloating"), option("ibs", "IBS-type symptoms", "ibs_flag"), option("antibiotics_recent", "Recent/current antibiotics", "antibiotic_gut_context"), option("general_gut", "General gut health", "general_gut_goal"))
    private val me1 = single("me1", "branch", "Which metabolic goal matters most?", option("ldl", "LDL cholesterol", "ldl_goal"), option("triglycerides", "Triglycerides", "triglyceride_goal"), option("glucose", "Blood sugar / A1c / insulin resistance", "glucose_goal"), option("cravings", "Cravings / appetite", "cravings_goal", "appetite_goal"), option("weight", "Weight management", "weight_goal"), option("pcos_metabolic", "PCOS / insulin resistance", "pcos_metabolic_goal"))
    private val j1 = single("j1", "branch", "What are you targeting?", option("osteoarthritis", "Osteoarthritis-type joint pain", "osteoarthritis_goal"), option("tendon", "Tendon/ligament discomfort", "tendon_goal"), option("exercise_soreness", "Exercise soreness/recovery", "soreness_goal"), option("inflammation_general", "General inflammation", "inflammation_goal"), option("skin_elasticity", "Skin elasticity", "skin_elasticity_goal"))
    private val h1 = single("h1", "branch", "What are you trying to improve?", option("hair_loss", "Hair shedding or hair loss", "hair_loss"), option("brittle_nails", "Brittle nails", "brittle_nails"), option("skin_aging", "Skin aging / elasticity", "skin_aging_goal"))
    private val l1 = single("l1", "branch", "Which best describes the concern?", option("low_libido", "Low libido", "low_libido"), option("erectile", "Erectile function", "erectile_concern"), option("low_testosterone_symptoms", "Low testosterone symptoms", "low_testosterone_symptoms"), option("fertility", "Fertility support", "fertility_goal"))
    private val fc1 = single("fc1", "branch", "Which applies most?", option("heavy_periods", "Heavy periods", "heavy_periods"), option("cramps", "Period cramps", "cramps_goal"), option("pms", "PMS mood or cravings", "pms_goal"), option("pcos", "PCOS or irregular cycles", "pcos_goal"), option("perimenopause", "Perimenopause symptoms", "perimenopause_goal"), option("postmenopause", "Postmenopause / bone health", "postmenopause_goal"))
    private val mh1 = single("mh1", "branch", "Which applies most?", option("prostate_urinary", "Frequent urination/weak stream/prostate symptoms", "prostate_symptoms"), option("fertility_sperm", "Sperm/fertility support", "male_fertility_goal"), option("low_testosterone", "Low testosterone symptoms", "low_testosterone_symptoms"), option("muscle_male", "Muscle/performance", "strength_power_goal"))
    private val lab1 = single("lab1", "labs", "Do you have recent lab results from the last 6-12 months?", option("labs_yes", "Yes", "labs_available"), option("labs_no", "No", "labs_unavailable"), option("labs_unsure", "Not sure", "labs_unsure"))
    private val lab2 = multi("lab2", "labs", "Have any of these been low or abnormal?", 10, option("low_ferritin", "Low ferritin/iron", "low_ferritin_known"), option("low_b12", "Low B12", "low_b12_known"), option("low_vitamin_d", "Low vitamin D", "low_vitamin_d_known"), option("abnormal_tsh", "Abnormal thyroid markers", "abnormal_thyroid_labs"), option("high_a1c", "High fasting glucose or A1c", "high_a1c"), option("high_ldl", "High LDL cholesterol", "high_ldl_labs"), option("high_triglycerides", "High triglycerides", "high_triglyceride_labs"), option("low_egfr", "Low eGFR/kidney marker", "kidney_flag"), option("high_liver_enzymes", "High liver enzymes", "liver_flag"), option("none", "None of these", "labs_normal"), option("unsure", "Not sure", "labs_unsure"))

    private val allQuestions = listOf(q0, q1, q2, q3, q4, q5, q6, q7a, q7b, q7d, q8b, q8c, q8d, q9, e1, e2, s1, st1, f1, m1, g1, me1, j1, h1, l1, fc1, mh1, lab1, lab2)
}
