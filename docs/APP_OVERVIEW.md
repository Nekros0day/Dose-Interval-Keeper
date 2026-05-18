# Dose Interval Keeper App Overview

Dose Interval Keeper is a private Android timing aid for as-needed medication intervals and supplement planning. It keeps data on device, builds a smart reminder plan from local rules, and uses notifications only when something is due.

## Main Screens

- Today: shows tracked items and their next optimized slots. "Taken now" records a confirmation and triggers replanning.
- Smart Plan: shows the grouped optimized schedule. Items can be edited here by changing optimizer preferences such as interval, timing window, and preferred target time.
- History: shows local confirmation history, supports clearing, and supports retention settings.
- Quiz: asks a safety-first supplement questionnaire and suggests supplement matches without diagnosing.
- Rules: shows default spacing/interference rules and lets users add or edit custom interactions.

## Core Data Flow

1. `DoseRepository` loads and saves `DoseState`.
2. `DoseKeeperViewModel` exposes `DoseKeeperUiState`.
3. `SupplementScheduler.buildPlan(...)` creates the optimized reminder plan.
4. `AppUi.kt` renders Today, Smart Plan, History, Quiz, and Rules.
5. `DoseNotificationManager` schedules due-time alarms from the optimized plan.
6. `DoseAlarmReceiver` records notification "Taken" actions and reschedules the plan.

## Storage

The app uses Android shared preferences through `DoseRepository`. Stored data includes:

- Tracked medications and supplements
- Last confirmation timestamps
- History entries
- Custom interaction rules
- History retention setting
- Quiet-hour setting

No server sync exists.

## Supplement Defaults And Rules

Default supplement templates and default interaction rules live in:

```text
app/src/main/java/com/dosekeeper/app/data/DoseRepository.kt
```

Each default supplement has:

- `id`
- display name
- category
- default interval
- preferred target time
- timing preference
- description
- timing notes
- interaction notes

Spacing and advisory rules are represented as `InteractionRule`. A rule can target either an item/template id or a `name:` matcher such as `name:levothyroxine`.

## Quiz

The quiz engine lives in:

```text
app/src/main/java/com/dosekeeper/app/quiz/QuizEngine.kt
```

It uses static questions, answer tags, candidate scoring, safety blockers, lab/test-first logic, and timing output. The quiz should keep using safe language: reasonable options, test-first, review-first, avoid. It should not say the user "needs" a supplement.

## Updating Documentation

When adding or changing major behavior, update:

- `docs/APP_OVERVIEW.md` for screen/data-flow changes
- `docs/SCHEDULER.md` for planner/optimizer changes
- `docs/DATA_MODEL_AND_GROWTH.md` for storage or long-term data design changes
- `AGENTS.md` for navigation or build guidance changes
