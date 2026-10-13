# Intelia status

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
