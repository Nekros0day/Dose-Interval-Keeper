# Data Model And Growth Notes

This app currently keeps its domain data intentionally simple and local.

## Current Storage

Runtime user data is stored in Android shared preferences through:

```text
app/src/main/java/com/dosekeeper/app/data/DoseRepository.kt
```

Stored user state includes:

- `DoseItem` records for medications and supplements
- Last taken timestamps
- Confirmation history
- Custom interaction rules
- History retention settings
- Quiet hours

Default app knowledge is currently hardcoded in Kotlin:

- Default supplement templates: `DoseRepository.supplementTemplates`
- Default spacing/advisory rules: `DoseRepository.defaultInteractionRules`
- Quiz questions and scoring rules: `QuizEngine`
- Scheduler scoring constants: `SupplementScheduler`

## Is This Good Long Term?

It is good enough for a small offline MVP because it is fast, private, and easy to inspect. It is not ideal if the app grows into a large supplement knowledge base.

The main scaling problems are:

- Large Kotlin lists are awkward to review and update.
- Quiz graph changes require code edits and app releases.
- Supplement templates, default rules, and quiz scoring can drift out of sync.
- Shared preferences are brittle for larger structured data migrations.
- There is no explicit schema version for supplement knowledge.

## Recommended Next Step

Move default knowledge into versioned JSON assets while keeping user data local.

Suggested files:

```text
app/src/main/assets/supplements.json
app/src/main/assets/interaction_rules.json
app/src/main/assets/quiz_questions.json
app/src/main/assets/quiz_scoring_rules.json
```

Keep Kotlin data classes for validation and runtime use, but load defaults from assets. This makes it much easier to add supplements, categories, branches, and rule updates.

## Recommended Later Step

If the app continues to grow, move user state from shared preferences to Room.

Room would help with:

- Safer migrations
- Querying history
- More complex schedule/debug views
- Per-item settings
- Future export/import

Shared preferences can remain acceptable for:

- Small settings like quiet hours
- One-time migration flags
- Simple feature toggles

## Rule Engine Direction

The quiz and scheduler should keep using tag/rule-based logic, but the rules should become declarative.

For quiz scoring, prefer data like:

```json
{
  "ifAllTags": ["vegan"],
  "candidateId": "b12",
  "points": 10,
  "statusHint": "likely_useful",
  "reason": "Plant-based diets can be lower in B12."
}
```

For safety overrides:

```json
{
  "ifAnyTags": ["pregnant", "breastfeeding"],
  "candidateIds": ["ashwagandha", "berberine", "dhea"],
  "status": "review_first",
  "warning": "Pregnancy or breastfeeding requires clinician review."
}
```

This would make graph pruning and QA easier because the app can detect unused tags and orphaned questions automatically.

## Migration Rule

When changing ids, preserve backward compatibility:

- Do not rename supplement ids casually.
- Add aliases if names change.
- Keep old interaction keys valid.
- Version asset files and write migration notes.

The scheduler depends on stable supplement ids such as `iron`, `calcium`, `magnesium`, and `vitamin_c`.
