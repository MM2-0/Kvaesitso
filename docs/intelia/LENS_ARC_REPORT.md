# Recovery, minimal Home, LENS and ARC — 2026-10-14

Task 020, Codex, branch `codex/intelia-wallpaper-design`, base `c646d97aa`.
Security fixes are a separate commit `88cd426b5`; native/design delivery is a
following commit. No merge or history replacement. Original PR #1/#2 remain
OPEN and unchanged; new review targets their current FLOW branch.

## Recovered state and implementation

The previous native Intelia slice was built and tested on the phone, but its
wallpaper redesign was unfinished/unverified. All tracked/nonignored source was
checkpointed outside the repository before recovery. Missing wallpaper strings
and an undefined icon drawing variable were fixed. The interrupted build had
packaged an APK but failed while writing Gradle's diagnostic report; successful
checks below replaced that ambiguous result.

Changed application identity/manifest and focused native UI files implement:
portrait Home with the real Android wallpaper, restrained clock/date and one
bottom LENS; local search with app rows, exact FLOW preview and eligible public
shortcuts; hold/drag/release ARC with up to five stable user-selected app keys.
No logo, marketing text, tab dock, static wallpaper asset or home app grid.
No HALO, TRACE, AI, global overlay or new permission/dependency. Existing widget
hosting/inherited modules remain in source; widgets were already outside the
native slice and are still not exposed.

ARC does not dispatch on hold. Upward threshold plus a valid target hit is
required before release; returning to LENS, out-of-range/multitouch/cancellation,
Home/Back/stop and repeated release fail safely. Hidden/unavailable apps leave
their slots empty. Search rows offer an explicit alternative. Preferences retain
backward-compatible backup defaults and validate mode/slot bounds.

Audit found and fixed unnecessarily eager shortcut indexing, permission-revocation
failure paths, hidden-shortcut filtering, repeated icon decoding, needless window
relayout while blur is disabled and competing size animation during IME resize.
Icons have a bounded 4 MiB revision-keyed LRU loaded on IO; cross-window blur
listeners are removed on disposal. No arbitrary imported intent or URL execution
was added. Local source/hash checks are not a guarantee of universal safety.

## Build, unit tests and artifact checks

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:app:assembleDefaultDebug :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest --offline --console=plain --max-workers=2 --no-problems-report
```

PASS: final code build 20 s; 1052 tasks, 10 executed / 1042 up-to-date. Previous
full modified test run passed; final unchanged test tasks were up-to-date. XML:
104 passed / one existing locations skip / zero failures/errors: FLOW 16,
core/base 60, locations 16+1 skip, native preference codec five, ARC reducer seven.
These are JVM tests, not an Android instrumentation suite.

`git diff --check`: PASS. `audit-files.py`: PASS, known key patterns, unexpected
binary/signing/log artifacts and APK CRC. `audit-native-apk.py`: PASS, bundled
source/archive CRC, seven key files byte-identical, mandatory licenses/wrapper,
no private cache/machine/signing entries. Source has 2766 ZIP entries including
directories. OSV runtime audit from previous day: 321 components, zero reported
findings after Jackson/jsoup fixes; no new production dependency in this task.
No new live OSV query is claimed. APK `apksigner verify --verbose`: PASS v2.
`aapt dump permissions`: QUERY_ALL_PACKAGES plus own private receiver permission;
no INTERNET, sensitive-data, overlay, notification or Accessibility capability.

`npm.cmd run docs:build` from `docs`: PASS in 8.99 s with link checking.
Known Vite configuration-loader warnings remain non-fatal.

Final APK: `app/app/build/outputs/apk/default/debug/app-default-debug.apk`.
SHA-256 `3162da94af13ddff88590c1b746562742f5a7a9e63bb12fe87805ee0d831dfaf`.
Embedded corresponding source SHA-256
`cd4c2ec678c668a476e0931362d78c51493fcf96e1f8d0e187dd0fdc01459fb7`.
Actual package `dev.intelia.launcher.debug`, version 0.1.0, code 2026101301.
Required GPL/library copyrights remain in Legal/source; no vendor asset/font was
copied and no trademark/legal certification is claimed.

## Physical phone evidence

A059 / Android 16 / API 36, authorized USB, 2026-10-14. PASS:

- Actual `adb install -r` update with own data intact; default Home remains
  Intelia. Nothing launcher, personal files/backups and other apps preserved.
- LENS tap opens search/IME. Stored exact alias preview and public shortcuts
  appear without automatic activation. Explicit Run opens the actual user's
  chosen target; only its focused component metadata was inspected.
- Hold reveals ARC; release without selection stays Home. Drag back to LENS
  cancels. Valid upward center selection/release opens the corresponding app.
  User's five saved slots remain unchanged. No target screen was captured/read.
- Clear-wallpaper mode and return to automatic mode work. `dumpsys window`
  reports `mBlurEnabled=false`; actual evidence verifies fallback, not enabled
  GPU blur or private Nothing effects. A proposed `wm disable-blur` diagnostic
  was rejected by automatic review and never executed; safe dumpsys replaced it.
- Preferences/ARC XML before and after own process restart match exactly.
  Portrait manifest and portrait UI bounds verified; no global rotation setting
  changed. Temporary own KEEP_SCREEN_ON flag false at completion.
- Local screenshots: Home, held ARC, no-match Search. Device serial, app/private
  shortcut content and raw logs/screenshots are excluded from Git/source bundle.

### Performance acceptance: FAIL / unresolved

Same A059, debug builds, ten cycles of LENS tap, 350 ms pause, Android Home,
350 ms pause; `dumpsys gfxinfo ... reset` before each run, then gfxinfo/meminfo.
No other app was closed to improve the measurements.

| Debug iteration | Frames | Janky | p95 | PSS KiB |
| --- | ---: | ---: | ---: | ---: |
| Initial LENS | 695 | 25.18% | 61 ms | 203139 |
| Bounded icon cache | 407 | 40.79% | 113 ms | 144790 |
| Window/size-animation fix, final | 370 | 51.08% | 117 ms | 148587 |

Final p50 24 ms, p90 81 ms, p99 200 ms; RSS 296440 KiB. Slow bitmap uploads
fell from 12 to one, but jank did not improve. These sequential observations are
not controlled statistical attribution: keyboard/wallpaper/system scheduling
and device state participate. The old 2.46% app-tab workload did not open the
keyboard and is not comparable. Do not call this smooth/optimized or fixed FPS.
Release frame/startup profiling and the remaining main-thread work need a focused
follow-up. Battery/energy acceptance was not measured; snapshots cannot prove it.

## Remaining before final approval

Release performance, enabled-blur and older-device fallback testing; physical
multitouch/interrupt/rotation matrix; full TalkBack/font/keyboard accessibility;
new preference backup roundtrip on Android (codec roundtrip passed). Public
shortcuts were displayed; launch behavior has guarded reuse but was not separately
physically tested. Selected system/global actions requiring inaccessible services
are not presented as working; ordinary Settings remains available from Search.
No final design/performance approval. Next smallest task is controlled release
profiling of LENS/IME transitions and completion of accessibility acceptance.
