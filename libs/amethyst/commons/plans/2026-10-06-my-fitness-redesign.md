# My Fitness redesign: goals first, then history

Status: **prototype landed** (shared dashboard + goals + insights model). Follow-ups listed at the end.

## 1. Design review of the current screen

The current `MyFitnessScreen` is well engineered underneath (two-source `TrainingLog`, progressive
Health Connect reads, the "don't flash empty" gating, share-once rules) but the surface is a list of
number grids. Findings, roughly by impact:

| # | Finding | Why it matters |
|---|---------|----------------|
| 1 | **No goals.** The only frame of reference is "+12% vs last week". | Numbers without a target don't answer *am I doing enough?* A week-over-week delta punishes a planned easy week and rewards a one-off. |
| 2 | **No visuals.** Six stacked `OutlinedCard`s of `StatGrid` text, all the same weight. | No hierarchy: the most important answer (on track or not) is not on screen. Trends are invisible when each number is a single point. |
| 3 | **Rolling 7 days ≠ "this week".** `WorkoutStats` compares the last 7×24h with the 7 before. | The window moves every morning. Goals, streaks and "days left" all need a calendar week (locale's first day of week). |
| 4 | **28-day ceiling on everything.** The published log is filtered to `WINDOW_DAYS` too, though kind 1301s go back indefinitely. | "Historical patterns" are impossible with 4 samples per weekday. Only the Health Connect read needs a short window (below). |
| 5 | **Fasting / diet / meditation count as training time.** A 16-hour fast is 960 minutes of "Time" and wins "Longest workout". | Corrupts every total, every average and a best effort. |
| 6 | **Daily streak.** `currentStreakDays` resets on any rest day. | Rest days are part of training; a daily streak nudges people to skip them. A *weekly goal streak* rewards consistency without that. |
| 7 | **Distance is summed across activities.** "Distance 74 km" mixes running and cycling. | Meaningless as a number; distance (and pace) only make sense per activity. |
| 8 | **Redundancy.** Workout count appears in *This week*, the tile row, and the window card. "Last 4 weeks · Weekly average" is jargon. | Noise that pushes the useful parts below the fold. |
| 9 | **Bests have no context.** No date, no activity, no "new". | A record is a story ("Longest run — Tue, new!"), not a cell. |
| 10 | **Accessibility.** Up/down is carried by colour only (primary vs grey). | Fails colour-alone. |
| 11 | **Platform-locked.** All of it lives in `amethyst/` while the direction is one UI in `commonsUI`. | Desktop can't render it. |

## 2. Proposed information architecture

Each card answers one question, in the order people ask them:

1. **This week — am I on track?** Three goal rings (active minutes, workouts, active days), a
   row per goal with *value of target* and a pace label (met / on track / behind, icon + word,
   never colour alone), an optional per-activity distance bar, a Mon–Sun week strip, and one
   sentence: "58 min to go · 5 days left".
   Pace is judged against **completed** days, so an empty Monday morning is not "behind".
2. **Weekly active time — is it trending right?** 12 weeks of bars stacked by activity with a
   dashed goal line; tap a bar to make it the header. Footer: "Goal met 7 of the last 12 weeks",
   "Averaging 3h 05m a week lately, +14% vs the 4 weeks before", and an honest note where the
   weeks predate the 4 weeks Amethyst reads from Health Connect.
3. **Consistency — do I show up?** Weekly-goal streak (current + best) and an 18-week calendar
   heatmap (single-hue ramp: rest / <20 / <45 / <90 / 90+ min).
4. **Your patterns — when do I train?** Busiest weekday and usual time of day as tiles, a
   weekday-average column chart and a time-of-day bar list.
5. **Activity mix — what am I doing more or less of?** 100% share bar for the last 4 weeks, and
   per-activity time per week with the change vs the 4 weeks before.
6. **Personal records** across the loaded history, with activity + date and a **New** badge for
   anything set this week.
7. **Recent workouts** — compact rows with an activity-coloured badge, date, metrics, share icon.

Colour rules (from the dataviz method): activity identity uses the first three slots of the
validated categorical palette, fixed by *total time over the whole history* so a colour follows
the activity across charts and never its rank in one week; everything else folds into a neutral
"Other". Goal rings take three different slots so a ring never reads as an activity. Single-series
charts use the theme primary. No dual axes; thin marks with 4dp rounded data ends and 2dp gaps.

## 3. What the prototype implements

| Piece | Where | Status |
|-------|-------|--------|
| `FitnessGoals` (weekly minutes / workouts / days + optional per-activity distance; versioned encode/decode) | `commons/jvmAndroid/fitness/` | new |
| `FitnessInsights` (calendar weeks, 26-week history, pace, week streaks, heatmap days, weekday/daypart profiles, mix, records, non-movement exclusion) | `commons/jvmAndroid/fitness/` | new, unit-tested |
| `WorkoutStats.bests` | `commons` | reused (made `internal`) |
| Chart primitives: `GoalRings`, `WeeklyBarChart`, `CalendarHeatmap`, `ColumnChart`, `LabeledBar`, `ShareBar`, `WeekStrip` (Canvas, no new dependency) | `commonsUI/commonMain/.../workouts/fitness/FitnessCharts.kt` | new |
| `FitnessDashboard` + `FitnessGoalsDialog` | `commonsUI/jvmAndroid/.../workouts/fitness/` | new, shared (Desktop can render it) |
| Formatting helpers | moved `amethyst/.../MyFitnessFormat.kt` → `commonsUI/.../FitnessFormat.kt` | extracted |
| `MyFitnessViewModel` | `amethyst/` | extended: goals flow, 26-week published window, `FitnessInsights` |
| Goal persistence | `FitnessGoalsPreferences` (per-account private SharedPreferences) | prototype-grade |
| Health Connect banner / pending note / connect prompt | `amethyst/` (header slot) | kept |
| Render test (light + dark, sample half-year) | `commonsUI/jvmTest/.../FitnessDashboardRenderTest.kt` | new; `MY_FITNESS_RENDER_DIR=… ./gradlew :commonsUI:jvmTest --tests '*FitnessDashboardRenderTest*'` writes PNGs |

`WorkoutStats` and its rolling-window `Report` are untouched (still tested); nothing on the new
screen reads them except `bests` and `percentChange`.

## 4. Follow-ups (not in the prototype)

1. **Fetch the user's own history.** My Fitness only sees kind 1301s already in `LocalCache`.
   Add a one-shot `fetchAllPages` for `kinds=[1301], authors=[me], since=26 weeks` on screen open
   (reuse the `accessories/` helpers; don't hand-roll a REQ loop).
2. **Longer Health Connect reads.** Health Connect's default limit is *30 days before the app
   was first granted access*, not a rolling 30 days: after a grant, history accumulates. Reading
   it needs Amethyst to remember its own first-grant date (asking for older data is an error),
   and costs one aggregate IPC per session. `READ_HEALTH_DATA_HISTORY` would add the time before
   the grant. Decided 2026-10-06: keep the 4-week read and no new permissions for now.
3. **Sync goals across devices** as an **encrypted** NIP-78 (kind 30078) app-data event —
   never a public event; a health target is personal.
4. **Per-activity drill-down**: tapping an activity in the mix opens a screen with distance/week,
   pace or speed trend, and HR trend for that activity only.
5. **Intensity minutes** (HR zones) once per-session HR samples are read, so "active minutes"
   can weight vigorous time ×2 like the WHO guideline does.
6. **Localised date patterns.** The prototype uses `"MMM d"`; use the platform's best pattern
   (Android `DateFormat.getBestDateTimePattern`) behind an expect/actual.
7. **Accessibility polish**: stepper buttons need content descriptions; per-bar semantics for the
   trend chart (currently one sentence for the whole chart).
8. **Move the screen shell** (`MyFitnessScreen`'s scaffold + Health Connect actions behind a
   port) into `commonsUI` so Desktop gets the destination, not just the dashboard.
