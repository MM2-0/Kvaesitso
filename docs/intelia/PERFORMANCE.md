# Performance and battery strategy

No Intelia-specific latency, memory, frame or battery benchmark has been measured
in this cloud baseline. Treat targets below as initial budgets to validate, not
claims about the inherited app or guarantees for all devices.

## Engineering constraints

- Query handling must be cancellation-aware. No database writes, network calls or
  execution side effects just because the user typed an alias.
- Collect feature state with the appropriate lifecycle. Reuse repository streams,
  clients, icon pipelines and bounded caches instead of opening resources per
  query. Close owned resources and preserve recent leak fixes.
- Keep expensive parsing, disk access, bitmap decoding and long computations off
  the main thread. Do not introduce permanent services, wake locks or timers for
  features that can be event-driven.
- Limit invalidation and Compose recomposition to changed state. Measure before
  adding memoization, extra modules or a second reactive pipeline.
- Animations convey state, cancel cleanly and respect system settings. Reduce
  work for idle/hidden surfaces; avoid perpetual animations by default.

## Proposed budgets and measurements

| Area | Initial goal / method |
| --- | --- |
| FLOW alias matching | Pure matcher p95 below 1 ms for 100 aliases; measure warmed runs separately from persistence/Android resolution. |
| Query-to-local-action UI | Aim for p95 below 50 ms on the chosen reference device; measure end-to-end trace timestamps, not just matcher time. |
| Frames | Fit the actual refresh-rate budget (about 16.7 ms at 60 Hz, 8.3 ms at 120 Hz); report jank and p95/p99 frame duration. |
| Memory | No retained activities, scopes or growing per-query caches; compare heap/PSS after repeated searches, cancellations and app returns. |
| Idle battery | No feature-induced periodic wakeups, polling or network use; compare screen-off and launcher-idle traces against the same baseline. |
| Startup | Record cold/warm startup separately on an optimized, non-debuggable build; do not use Gradle build duration as runtime evidence. |

Choose a documented low/mid-range reference device plus a newer device before
turning these goals into release gates. Record model, Android version, refresh
rate, build variant/commit, thermal state, battery conditions, workload, sample
count and measurement tool. Prefer medians and tail percentiles to one run.

## Incremental benchmark plan

1. Establish a same-device baseline for launch, search, scrolling, gesture
   cancellation and returning from another app. Use Perfetto/system tracing and
   Android frame/memory tools first; add benchmark dependencies only in a scoped
   task with a demonstrated need.
2. For FLOW v0, stress exact matching with empty, long, Unicode and unmatched
   input; confirm execution is never called during query evaluation. Exercise
   rapid query changes and app removal.
3. Repeat the identical workload with the feature enabled and disabled on the
   same device. Investigate a repeatable latency/memory/frame regression before
   adding optimizations; record idle wakeup/network observations separately.
4. Test cancellation, rotation/resume and a long repeated-use session for retained
   state. Keep sensitive user data out of traces and committed artifacts.

The cloud machine can compile and run JVM tests. It cannot establish Android
gesture quality or battery efficiency. Device evidence remains explicitly unrun
until collected; a debug APK and passing unit tests are prerequisites only.
