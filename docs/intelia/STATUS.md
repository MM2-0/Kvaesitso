# Intelia status

## Keyboard / ARC accessibility follow-up — 2026-10-14

Existing preview updated without data wipe. LENS keyboard focus, Enter/Space
search, Up into five saved ARC apps and Escape cancellation from focused icons
passed on A059/API 36. Localized descriptions/roles, explicit 48 dp ARC targets,
bounded labels and layer-driven fading preserve the minimal Home. 104 existing
JVM tests passed, one existing skip; release build/signature/embedded source/
scoped file checks passed. No permissions/dependencies/services were added.
ARC jank was similar (5.39% before, 5.29% after); rapid LENS measured 6.73% versus
previous preview 5.71%, so no additional speed improvement is established.
Full accessibility and performance acceptance remain open. See
[verification and remaining limits](LENS_ACCESSIBILITY_REPORT.md).

## Release preview / performance follow-up — 2026-10-14

User requested continuation on PR #3's open jank issue and explicitly approved
a new backup only on the phone plus updating existing Intelia without data wipe.
The installed package is still `dev.intelia.launcher.debug`, now a non-debuggable
release preview built with `-Pintelia.localPreview=true`. Home role, stored FLOW,
five ARC slots and Nothing were preserved. The temporary fresh `.release`
measurement install was removed; its absence was checked before creation.

The matched own-Home/800 ms workload recorded 9.49% jank in debug, 4.57%/3.94%
in baseline release, and 4.29%/3.93% in the updated preview. Rapid LENS/system
Home/350 ms workload recorded 5.71% in preview (older debug result 51.08%).
This is substantial practical progress, largely removal of debug overhead;
the smaller animation/window changes did not show a separate convincing gain.
Frame tails and full performance/energy acceptance remain open. 104 unit tests
passed, one existing skip; build/signature/source/permission checks passed.
See [performance follow-up](LENS_PERFORMANCE_REPORT.md) for exact variants,
commands, hashes, limitations and rollback/backup locations.

## Current recovery / LENS / ARC — 2026-10-14

Task 020, branch `codex/intelia-wallpaper-design`, supersedes the previous
header/dock Home. Recovery preserved all local work in an external source ZIP.
Original PR #1/#2 remain OPEN; their remote heads were not changed. Security
changes committed separately as `88cd426b5`.

Native portrait Home now shows the system wallpaper, optional time/date and one
LENS. Tap opens local app/FLOW/public-shortcut search; hold opens user-configured
shallow ARC with stable positions. No HALO/TRACE/AI/global overlay was introduced.
On A059 / Android 16, updated APK installation, tap/search, hold without launch,
drag-back cancellation, valid release launch, explicit FLOW, Home return and
preference/ARC restart persistence were physically verified. Current HOME role
remains Intelia; Nothing/data/other apps preserved. Android reports blur disabled;
actual phone evidence is wallpaper with contrast fallback, not enabled GPU blur.

104 tests passed, one existing skip. APK signature/archive/source/file audits
passed; active permissions remain package discovery plus private receiver only.
**Performance acceptance is FAIL/open:** ten rapid LENS/keyboard/Home cycles on
the final debug build recorded 51.08% janky frames. No release/FPS/battery claim.
See [recovery report](LENS_ARC_REPORT.md) for exact evidence and unresolved checks.
This is a reviewable design draft, not final UX/performance approval.

## Current native Intelia slice — 2026-10-13

Task 015 is verified locally on `codex/intelia-native-ui`, based on `c646d97aa`;
changes are uncommitted. An original Compose Home/library/FLOW/settings surface,
icon, package `dev.intelia.launcher.debug` and `.intelia` backups now run on A059,
Android 16. This package is the actual default Home. Nothing launcher/data were
preserved; only our earlier debug launcher and disposable target were removed.

95 unit tests passed, one existing locations test skipped. Exact alias matching,
explicit public-app launch, duplicate rejection, hidden-target suppression,
source export, backup/theme/FLOW restoration and restart persistence passed on
the phone. The APK has no INTERNET, contacts/calendar/storage, notification or
Accessibility permission/service. Required source/license attribution remains.
Measured 20 Home/library transitions: 773 frames, 19 janky (2.46%), p95 13 ms;
this is a debug observation, not a performance acceptance or battery claim.

See [native report](INTELIA_NATIVE_REPORT.md) for hashes, commands, scope and
remaining acceptance. Historical results below describe previous installations.
Multi-step FLOW, full profile support, widgets and global ORBIT remain outside
this slice. The next smallest task is release-build frame profiling/optimization
and accessibility acceptance, subject to a new task boundary.

## Local physical-device validation — 2026-10-13

On A059 / Android 16 (API 36), built and installed the actual default debug APK
from `c646d97aa` with a focused editor fix on `codex/flow-device-validation`.
The first local build and tests passed: 84 tests passed, 1 existing skip,
zero failures/errors. After the fix, offline assembly passed; test tasks were
up-to-date. APK v2 verification and both installations passed.

Fixed the observed partially expanded FLOW sheet hiding its footer: open fully
expanded and allow outer content scrolling. Physical-device checks passed for
creation, exact matching/casing/spaces, typing without execution, explicit launch
of a private test helper, blank/length validation, rename/restart persistence,
missing-target settings and delete/restart persistence. Ordinary action presence
was preserved. Detailed commands, APK hash and limits are in
[FLOW_DEVICE_RESULTS](FLOW_DEVICE_RESULTS.md).

Device acceptance is still partial. Offline dispatch, duplicate validation,
hidden/suspended/profile targets, backup/restore, accessibility, the full lifecycle
matrix and performance remain unrun. No unrelated app or system configuration was
changed, and no personal app data was cleared. Continue task 010's remaining
checks before starting another product feature.

## Resumed validation preparation — 2026-10-13

Restored feature branch at `6c74e9b47` in a new cloud chat. GitHub API confirmed
PR #1 and PR #2 are OPEN; PR #2 still targets `docs/intelia-bootstrap` and is
mergeable. No merge was performed.

Re-executed the FLOW verification command below: PASS in 20s, 1018 tasks
(3 executed, 1015 up-to-date). All three test tasks executed: 16 FLOW, 52
core/base and 16 locations passed; 1 existing locations test skipped, no
failures/errors. APK assembly was up-to-date, not a fresh compilation;
`apksigner verify --verbose` independently passed with v2 signing.
APK SHA-256: `f7a4d1ceee21f50e8729d908a5fcd0f04e6c056e4cc1af1d0b6320c72e2d17e0`.
`npm run docs:build` passed in 2.41s after using a GitHub URL for the task
outside the VitePress root; link checking remains enabled. `git diff --check`
passed. Application source and behavior were unchanged by this follow-up.

`adb devices -l` succeeded outside the local-socket sandbox and returned no
devices. SDK emulator/system images and `/dev/kvm` are absent. Device acceptance
and performance remain UNRUN. Added the
[device validation runbook](FLOW_DEVICE_VALIDATION.md) with executable install
and force-stop commands, explicit expected results and a result template. The
next action requires an authorized connected Android test device; use that
runbook rather than repeating JVM tests as evidence of device behavior.
The user chose a local Codex session with their USB-debugging phone for this
follow-up. The runbook includes the GitHub checkout and local ADB handoff;
clearing personal app data or restoring over everyday settings needs separate
explicit consent. No device testing has occurred yet.

## FLOW v0 implementation — 2026-10-12

Branch: `feat/flow-aliases`, based on bootstrap commit `5b7aa3445`. Bootstrap
PR #1 is still open; the feature PR is stacked on `docs/intelia-bootstrap` to
keep application review separate from bootstrap documentation.
[Feature PR #2](https://github.com/ondrejmacko-it/Intelia-Launcher/pull/2) was created
after pushing implementation commit `5d4ce7423`. Neither PR is merged automatically.

Implemented: create/edit/delete local app aliases in Search Actions settings,
NFKC / whitespace / Locale.ROOT exact matching, version-1 JSON payloads in the
existing SearchAction records, visible alias → target chips and explicit
lifecycle-scoped activation. Targets are exact activities in the current personal
profile; hidden, suspended, missing and private/work targets are unavailable.
Availability is resolved again on activation/dispatch. Repeated in-flight requests
are guarded; cancellation is checked before dispatch. Ordinary result ranking and
best-match activation exclude aliases from the automatic fallback.

The editor has a dedicated FLOW sheet on the existing settings route rather than
extending the sealed generic intent editor. Aliases retain stable IDs across edits;
unavailable targets can be repaired/deleted without retaining their app label.
Raw legacy and unknown-version records are preserved by alias mutations. Backup
uses the existing export/restore fields; no schema migration was added.

Verification from the checkout root using WORKFLOW tool activation:

```sh
bash ./gradlew :app:app:assembleDefaultDebug \
  :data:search-actions:testDebugUnitTest --rerun \
  :core:base:testDebugUnitTest --rerun :data:locations:testDebugUnitTest --rerun \
  --offline --console=plain --max-workers=4
```

PASS, 11s, 1018 tasks (13 executed, 1005 up-to-date). Fresh JUnit XML:
16 FLOW tests passed, 52 core/base tests passed, 16 locations tests passed and
1 pre-existing locations test skipped; 0 failures/errors. The final lifecycle-stop
and shared backup/restore lock check rebuilt the APK and reran all 16 FLOW tests:
PASS in 10s (1005 tasks: 13 executed, 992 up-to-date). APK v2 signing and
VitePress documentation build also passed. FLOW tests exercise
Unicode/locale/exact matching, validation, codec failures/round-trips, mutation
preservation and create/edit/delete, eligible targets, dispatch failure, duplicate
in-flight activation and cancellation. The earlier first build passed after
fetching the existing transitive test artifact exifinterface 1.3.7; offline mode
initially lacked that artifact. No production external dependency was added.

Device UI, actual Room persistence across process restart, real backup/restore,
TalkBack/large-font checks and offline app dispatch on a device remain unrun.
JVM record-operation tests do not prove Android database or UI behavior. The
manual task 010 checklist is the next validation task before claiming device UX
readiness; ORBIT, MORPH, POCKET, multi-step FLOW and AI remain unimplemented.

## Historical bootstrap evidence

Verified 2026-10-12. Current work: documentation bootstrap on
`docs/intelia-bootstrap`, based on `main` commit
`3d18f3056dce2276569aa335343175ad2ca6700e` (remote HEAD verified through Git).
The tree was clean before bootstrap. Recent crash/input/database fixes
`556883681` and resource/memory fixes `3d18f3056` are preserved in ancestry.

## Current capabilities

- Existing Kvaesitso-derived Kotlin/Compose Android launcher, 51 Gradle modules.
- Default debug APK builds with full Temurin JDK 21.0.12.1+1, Gradle 9.5.0,
  Android SDK 37.1, AGP 9.3.1 and SDK build-tools 36.0.0.
- Existing application ID remains `de.mm20.launcher2.debug`; version 1.41.0.
- At bootstrap, FLOW, ORBIT, MORPH and POCKET were product plans only; see the
  current FLOW implementation above for the subsequent feature change.
- Root shared agent instructions, architecture, design, performance strategy and
  task contracts are established by this bootstrap. No application code,
  permissions, dependencies, lockfiles or licenses are changed.

## Baseline evidence

Commands run from the checkout root after activating tools as in WORKFLOW:

```sh
bash ./gradlew :app:app:assembleDefaultDebug \
  :core:base:testDebugUnitTest :data:locations:testDebugUnitTest \
  --console=plain --max-workers=4
```

Onboarding full run: PASS, 12m 47s, 1005 tasks (721 executed, 284 from cache).
Repeated setup: PASS, build phase 7s, 1005 tasks up-to-date. Bootstrap branch
offline verification of the same targets: PASS, 5s, 1005 tasks up-to-date.
Cached execution is not counted as a new test run.

Fresh bootstrap execution on 2026-10-12:

```sh
bash ./gradlew :core:base:testDebugUnitTest --rerun \
  :data:locations:testDebugUnitTest --rerun \
  --offline --console=plain --max-workers=4
```

PASS, 4s, 123 tasks (2 test tasks executed, 121 up-to-date). Fresh XML timestamps
were 18:05:29 UTC: `core/base` 52 passed, `data/locations` 16 passed / 1 skipped;
0 failures/errors. This independently confirms the onboarding test counts.

Documentation: `npm ci --cache /workspace/.cache/intelia/npm --no-audit --no-fund`
from `docs` passed without changing the lockfile. `npm run docs:build` passed in
2.47s, including rendering the new Intelia pages and normal VitePress link checks.
The inherited sidebar files produce a warning about a future Vite configuration
loader default; it did not fail the build and no warning was suppressed.

Onboarding JUnit reports: `core/base` 52 passed; `data/locations` 16 passed and
1 skipped; 0 failures/errors. `OpeningHoursTest.testEvenOddWeeks` is annotated
`@Ignore("Expected to fail with the current implementation")`; no assertion was
disabled by bootstrap. Reports live under each module's
`build/test-results/testDebugUnitTest` directory.

APK: `app/app/build/outputs/apk/default/debug/app-default-debug.apk`, approximately
34 MiB. `build-tools/36.0.0/apksigner verify --verbose <APK>` passed (v2 signature).
`aapt2 dump badging <APK>` confirmed debug ID, minimum API 26 and target API 37.
No device install/launch, UI smoke, emulator, instrumented test or performance
benchmark has been run. `/dev/kvm` is absent. Compiler deprecation/opt-in warnings
and unstripped bundled native-library warnings did not fail the debug build.

## Environment and publication

Writable caches/full JDK/platform proxy configuration resolved setup failures
from the read-only home, missing `javac` and Java's default direct networking.
An initial bootstrap sandbox run failed before build startup because Gradle could
not determine a usable wildcard IP; the supported approved execution route
resolved it. These are setup restrictions, not observed source defects.

Reusable `install_script` and `start_skill` are saved in the cloud environment
draft; saving is not publication or proof of restoration in a fresh task. No
new-task restoration has been tested. Claude Code's entry instructions are
established; running Claude Code itself was not verified.

Git read access works. At bootstrap inspection, HTTPS CONNECT to
`api.github.com` returned proxy 403 before API authentication could be evaluated.
An additive allowed-domain requirement for `api.github.com` was saved while
preserving package-manager presets. PR creation subsequently succeeded with the
supplied authentication. No new token was requested; the failed CONNECT alone
did not establish a credential problem.

The inherited nightly/Pages CI uses JDK 17 and has not been run here. Updating CI
to the verified toolchain is a separate task. Node 24.19.0 / npm 11.9.0 installed
the existing documentation lockfile successfully with `npm ci` (144 packages).

## Next executable task

Complete the device validation checklist in `tasks/010_FLOW_MVP.md`: author and
edit/delete aliases, restart the process, hide/uninstall targets, exercise offline
launch and verify existing actions/backup behavior. Then measure the reference
workload before planning typed multi-step FLOW. Do not mix AI, Accessibility,
overlays or rebranding into that validation.

Documentation commit `f0e828bc1` was pushed to `docs/intelia-bootstrap`.
[PR #1](https://github.com/ondrejmacko-it/Intelia-Launcher/pull/1) is verified OPEN
against `main`; no automatic merge was performed. A documentation handoff update
records this delivery evidence on the same branch. Until bootstrap review/merge,
base follow-up work on this branch's reviewed commit rather than recreating its context.
