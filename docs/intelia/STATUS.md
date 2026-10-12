# Intelia status

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
- FLOW, ORBIT, MORPH and POCKET are product plans, not implemented features.
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

`tasks/010_FLOW_MVP.md`: persistent exact aliases to visible personal-profile
apps, surfaced through existing action chips and activated only by the user.
Use D003 and the verified integration map. Do not implement multi-step automation,
AI, Accessibility, overlays or rebranding in that task.

Documentation commit `f0e828bc1` was pushed to `docs/intelia-bootstrap`.
[PR #1](https://github.com/ondrejmacko-it/Intelia-Launcher/pull/1) is verified OPEN
against `main`; no automatic merge was performed. A documentation handoff update
records this delivery evidence on the same branch. Until bootstrap review/merge,
base follow-up work on this branch's reviewed commit rather than recreating its context.
