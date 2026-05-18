# Smart Planner And Scheduler

The smart planner is implemented in:

```text
app/src/main/java/com/dosekeeper/app/scheduling/SupplementScheduler.kt
```

Its public entry point is:

```kotlin
SupplementScheduler.buildPlan(
    items,
    templates,
    rules,
    nowMillis,
    horizonHours,
    quietStartMinute,
    quietEndMinute,
)
```

The scheduler returns a sorted list of `DosePlanGroup`. Each group has one scheduled time and one or more `ScheduledDose` entries that can be taken together.

## Design Goal

The planner should answer:

> Given the user's tracked items, last confirmations, timing preferences, quiet hours, and spacing rules, what is the next practical reminder plan?

The planner does not decide dose amounts and does not provide clinical advice. It only chooses reminder timing.

## Current Optimizer Method

This is a deterministic heuristic optimizer, not a heavy mathematical solver.

1. Filter active items with `shouldPlan`.
2. Sort items by priority.
3. For each item, generate candidate times.
4. Score every candidate.
5. Pick the lowest-cost candidate.
6. Group items with the same selected time.
7. Return groups sorted by time.

Because each item is placed after previous higher-priority items, the algorithm is greedy. That keeps it simple and predictable for a small on-device app.

## Candidate Generation

Candidates come from:

- The item's preferred timing bucket
- The item's preferred target time, when provided
- A 10-minute sliding grid from now through the planning horizon
- The item's own minimum interval from `lastDoseAtMillis`

The scheduler avoids quiet hours by filtering candidates whose local minute falls in the quiet window. If every candidate is inside quiet hours, it moves the candidate to the next quiet-hour end.

Default quiet hours are `22:00-07:00`, stored in `DoseState`.

## Cost Function

Each candidate starts with a soft cost based on distance from the item's target minute. Lower cost is better.

The candidate cost then changes based on:

- How far into the future it is
- Whether it fits the timing preference
- Whether it violates spacing rules against already scheduled items
- Whether it violates spacing from recently taken items
- Whether it can share a compatible meal slot
- Whether it pairs with an enhancer, currently iron plus vitamin C
- Whether it falls in quiet hours

Spacing rules have high penalties, so the planner should strongly prefer moving items apart instead of violating rules.

## Timing Preferences

`TimingPreference` is a soft preference:

- `Anytime`
- `WithBreakfast`
- `WithLunch`
- `WithDinner`
- `WithFood`
- `EmptyStomach`
- `Bedtime`

These preferences guide scoring. They do not guarantee the final reminder time because spacing rules, recent confirmations, quiet hours, and interval limits can move the final slot.

## Interaction Rules

Rules are `InteractionRule` records from `DoseRepository`.

- `separationMinutes > 0`: spacing rule. Candidates too close to the other item are heavily penalized.
- `separationMinutes == 0`: advisory rule. The rule adds notes but does not force timing separation.

Rules can match:

- A concrete item id
- A supplement template id, such as `iron`
- A name matcher, such as `name:levothyroxine`

The helper `interactionRulesFor(...)` checks both directions, so rules are symmetrical for scheduling.

## Recent Dose Handling

When the user taps "Taken now", the repository updates `lastDoseAtMillis`, notifications are rescheduled, and the plan is rebuilt.

The scheduler compares candidate times against recent `lastDoseAtMillis` values for any item with spacing rules. Example:

- Iron was taken late at 13:00.
- Calcium has a 2-hour separation rule from iron.
- Calcium candidates before 15:00 get a large penalty.
- The next plan moves calcium to a later valid slot.

The UI refreshes planner output on a 10-minute grid, so late/missed slots drift forward as time passes.

## Quiet Hours And Missed Items

Quiet hours are stored on `DoseState` as start and end minutes. The scheduler avoids notification slots inside that window.

The Today screen also shows a local warning when a daily supplement appears to have missed confirmation yesterday. This is informational only; it does not change history or force catch-up dosing.

## Notifications

`DoseNotificationManager.scheduleAll(...)` builds the same optimized plan and schedules alarms for future groups.

When an alarm fires, `showDueIfCurrent(...)` rebuilds the plan around the trigger time and only shows the notification if the group still matches. This prevents stale notifications from firing after a user confirms something or edits the plan.

Notification action flow:

1. User taps "Taken" in a notification.
2. `DoseAlarmReceiver` records those item ids.
3. The repository updates last confirmation and history.
4. Notifications are rescheduled from the new optimized plan.

## Current Limitations

- The optimizer is greedy, so earlier scheduled high-priority items can shape later choices.
- The planning horizon defaults to 48 hours.
- Target time is a daily preference, not a hard appointment.
- The app does not currently support one-off future target dates for a single dose.
- Candidate scoring constants are hand-tuned and should be adjusted cautiously.
