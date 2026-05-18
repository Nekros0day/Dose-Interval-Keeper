# Dose Interval Keeper Agent Notes

This project is a native Android/Kotlin app. Keep changes scoped and update the docs in `docs/` when behavior changes, especially around scheduling, notifications, supplement defaults, rules, quiz scoring, or storage.

## Where Things Live

- App entry point: `app/src/main/java/com/dosekeeper/app/MainActivity.kt`
- Compose UI: `app/src/main/java/com/dosekeeper/app/ui/AppUi.kt`
- ViewModel/state bridge: `app/src/main/java/com/dosekeeper/app/ui/DoseKeeperViewModel.kt`
- Data models: `app/src/main/java/com/dosekeeper/app/data/Models.kt`
- Local persistence and default supplement/rule library: `app/src/main/java/com/dosekeeper/app/data/DoseRepository.kt`
- Optimized smart planner: `app/src/main/java/com/dosekeeper/app/scheduling/SupplementScheduler.kt`
- Notifications and notification action handling: `app/src/main/java/com/dosekeeper/app/notifications/`
- Supplement quiz/rules engine: `app/src/main/java/com/dosekeeper/app/quiz/QuizEngine.kt`
- Project documentation: `docs/`
  - Data/storage growth notes: `docs/DATA_MODEL_AND_GROWTH.md`

## Build And Verification

On Windows, use Android Studio's bundled JBR if normal Java is not configured:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

## Important Product Rules

- This is a timing aid, not dosing or medical advice.
- Health data stays local in shared preferences.
- Notes were intentionally removed.
- Notifications are compact due-time reminders only. They should not pin countdown timers in the notification shade.
- The Smart Plan, Today screen, and notifications should all use `SupplementScheduler.buildPlan(...)` as the source of truth.
- The Plan tab is for inspecting/editing optimizer preferences. Confirmation happens from Today or notification actions.

## Scheduler Caution

`SupplementScheduler` is intentionally a small heuristic optimizer. When changing it, check:

- Rule spacing from `InteractionRule.separationMinutes`
- Recent `lastDoseAtMillis` interactions
- Quiet hours
- Target time/timing preferences as soft preferences, not guarantees
- Meal-compatible grouping
- Notification staleness checks in `DoseNotificationManager.showDueIfCurrent`

Update `docs/SCHEDULER.md` when scheduler behavior changes.
