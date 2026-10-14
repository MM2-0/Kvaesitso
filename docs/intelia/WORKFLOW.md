# Development workflow for Codex and Claude Code

## Local release preview for the existing installation

`./gradlew :app:app:assembleDefaultRelease -Pintelia.localPreview=true` builds
release UI/code under `dev.intelia.launcher.debug` for an explicitly requested
local preview. Install with `adb install -r` and the same local signing key to
retain existing settings/FLOW/Home role. Ordinary release remains `.release`;
ordinary debug remains debuggable. The preview is not debuggable and ignores
the debug keep-awake extra. Preserve a signed rollback APK and an own backup
before previewing; never clear data or uninstall the user's current launcher.
The source bundle contains this option and the build command reproduces it.
Inherited R8 rules still disable obfuscation/optimizer transformations; the
preview removes debug/tooling overhead, not all possible release optimization.

## Minimal Home / LENS / ARC — 2026-10-14

The active Home uses the user's Android wallpaper surface, not a copied/read
wallpaper bitmap or Nothing-private API. Public API 31 cross-window blur follows
the runtime capability callback; older/disabled devices use a contrast scrim.
Appearance offers automatic blur, clear wallpaper and solid background. Native
portrait Home shows only optional clock/date and LENS. Tap opens local search;
hold reveals a user-selected shallow ARC with up to five stable saved app keys.
An unavailable/hidden app leaves its position empty. Hold alone never dispatches;
selection requires upward movement and a target hit, followed by release. Back,
Home, stop, multitouch and pointer cancellation clear the session. Accessible
actions and explicit search rows offer alternatives to the gesture.

No global overlay, Accessibility service, HALO/TRACE/AI or new dependency is
introduced. Existing public shortcuts are reused and filtered by parent/profile
and hidden state; their indexing is subscribed only while used. Test pure ARC
gesture contracts and backward-compatible preference backups with app/ui tests.
Decoded app icons use a process-local 4 MiB LRU keyed by app and package revision,
with package/icon loading on IO. The cache is bounded and needs no background job.

## Active Intelia interface — 2026-10-13

The user authorized an original native interface over the preserved foundation.
The active debug application is `dev.intelia.launcher.debug`, version 0.1.0;
its entry point is `dev.intelia.launcher.ui.InteliaHomeActivity`. Existing internal
namespaces are retained as implementation/compatibility boundaries, not product
branding. Source licenses and attribution remain intact.

Add `:app:ui:testDebugUnitTest` to the existing verification command. The
`bundleInteliaSource` prerequisite embeds the corresponding project source
snapshot as `assets/intelia-source.zip`; Legal can export it through Android's
document picker. The source bundle includes build scripts, wrapper, sources,
resources and notices, excluding caches, logs, private machine files and outputs.
New backup exports use `.intelia`; legacy backups remain readable by metadata.

For local debug QA only, opening the home with
`--ez intelia.validation.keepAwake true` temporarily keeps that activity's display
awake. This is ignored in non-debug builds, cleared on pause and cleared by an
ordinary Home intent. Finish validation with an ordinary Home/open intent without
that extra. Do not change global screen timeout/rotation/connectivity for this.

Permission/component inspection of the APK is mandatory for identity changes.
The native local slice does not expose notification/Accessibility/account-import
interfaces or require network/storage/contact/calendar permissions. Android app
discovery still uses `QUERY_ALL_PACKAGES`. Do not mistake retained source modules
for active UI features. Work/private-profile UI, widgets, multi-step FLOW and
production performance remain separate acceptance work.

## Shared context and branches

Read root `AGENTS.md`, this project's STATUS, then the active task. `CLAUDE.md`
imports the same instructions using Claude Code's `@AGENTS.md` syntax. Read only
the architecture/design sections relevant to the change. Keep durable state in
Git, not a private agent memory file or chat transcript.

The remote default branch was verified as `main` on 2026-10-12. This bootstrap
uses `docs/intelia-bootstrap`. Feature work starts from the reviewed baseline on
a focused branch such as `feat/flow-aliases`; do not reset or commit directly to
`main`. Check existing work before switching branches. Cloud tasks already have
isolated checkouts; no worktree is needed unless explicitly requested.

Default to one coordinating agent. A specialist is useful only for independent
work with a bounded deliverable and separate file ownership (for example a
read-only review after implementation). Avoid parallel edits to shared models,
gradle files, STATUS or decision records. Hand off the task ID, commit, changed
paths, decisions, exact verification and outstanding blocker in a compact note.
The coordinator integrates and validates; independent agent claims are not tests.

## Android baseline: portable prerequisites

Install a full JDK 21, Android SDK platform `android-37.1`, build-tools 36.0.0 and
platform-tools. Use the checked-in Gradle wrapper 9.5.0 and its checksum. The
onboarding instance additionally has build-tools 37.0.0 and command-line tools
23.0. Android Studio is optional for command-line builds, useful for device work.
Minimum runtime API is 26; target API is 37. No release keystore or publishing
credential is needed for default debug development.

From the checkout root, with `JAVA_HOME` and `ANDROID_HOME` pointing to those tools:

```sh
bash ./gradlew :app:app:assembleDefaultDebug \
  :core:base:testDebugUnitTest :data:locations:testDebugUnitTest \
  --console=plain --max-workers=4
```

Use a suitable worker limit for the actual machine. The cloud baseline has four
CPU cores and 32 GiB memory; the repository configures a 4 GiB Gradle heap. An
offline run is useful once the relevant artifacts are cached, not a substitute
for dependency installation. Preserve the exit code when redirecting logs.

Output: `app/app/build/outputs/apk/default/debug/app-default-debug.apk`.
JUnit XML: `core/base/build/test-results/testDebugUnitTest/TEST-*.xml` and
`data/locations/build/test-results/testDebugUnitTest/TEST-*.xml`.
Gradle may report tests UP-TO-DATE. To execute only those test tasks again without
rebuilding all dependencies:

```sh
bash ./gradlew :core:base:testDebugUnitTest --rerun \
  :data:locations:testDebugUnitTest --rerun --console=plain --max-workers=4
```

Inspect fresh reports. Report passed/failed/skipped counts; do not count a skipped
test as passed. The known ignored even/odd-week test is documented in STATUS.

## Current cloud activation

These paths describe this environment only; do not hardcode them into application
source or assume they exist in another checkout. The saved environment install
script prepares them, and the saved start instructions activate them:

```sh
export JAVA_HOME=/workspace/.cache/intelia/jdk-21.0.12.1+1
export GRADLE_USER_HOME=/workspace/.cache/intelia/gradle
export ANDROID_HOME=/workspace/.cache/intelia/android-sdk
export ANDROID_USER_HOME=/workspace/.cache/intelia/android-user
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/23.0/bin:$PATH"
cd /workspace/Intelia-Launcher
javac -version
bash ./gradlew --version
```

The machine initially had only a JRE and read-only home directories. Writable
Gradle/Android caches and full Temurin JDK 21.0.12.1+1 resolved those setup issues.
Gradle's user properties use the platform HTTP/HTTPS proxy and verified system
CA store at `/etc/ssl/certs/java/cacerts`. Refresh proxy host/port from the running
environment when necessary; never dump proxy credentials or disable TLS checks.

In a sandbox that denies Gradle's local sockets, use the supported execution
approval mechanism; a wildcard-IP/file-lock failure does not prove an Android
source defect. The onboarding helpers live outside the checkout and are not a
portable replacement for these explicit instructions. Live daemons do not survive
environment publication; no application server needs to be restarted.

## Documentation and optional checks

The existing docs use VitePress. From `docs`, install without changing the lock:

```sh
npm ci
npm run docs:build
```

Cloud npm cache may be placed under `/workspace/.cache/intelia/npm` when home is
read-only. The verified Node/npm versions are recorded in STATUS. The inherited
Pages workflow first generates API references with `bash ./gradlew dokkaGenerateHtml`
and copies `build/dokka/html` to `docs/public/reference`; use that sequence if the
site reports missing API links. Keep generated assets ignored and never suppress
dead-link checking to make a build pass. Markdown files outside the VitePress root
(AGENTS, CLAUDE and tasks) also need a relative-link review.

Device/emulator smoke tests and performance checks are separate from JVM tests.
No device test has been performed in the cloud baseline; `/dev/kvm` is absent.
For a future device check, document installing the APK, choosing Intelia as home,
searching, launching an app, navigating back and verifying the task-specific
failure path. Do not claim unattended device execution or create localhost links.

## CI, commits and PRs

The inherited nightly workflow uses JDK 17, `assembleDefaultNightly` and release
signing secrets. The Pages workflow also uses JDK 17 and invokes system `gradle`
for Dokka. These workflows are not the locally verified JDK 21/wrapper debug
baseline; this bootstrap neither fixes them nor claims their CI jobs passed.
There is no observed PR debug/test gate in the checked-in workflows.

Review `git diff --check`, changed paths and test evidence before committing. A
bootstrap commit contains only root instructions, `docs/intelia/*` and `tasks/*`.
Feature commits stay focused. PRs state resulting behavior, scope and exact
validation, including device checks that were not run. Push without force, open a
reviewable PR against `main` and never merge automatically.

Use supplied HTTPS Git authentication. A successful `git ls-remote` does not
establish GitHub API permissions: PR creation needs `api.github.com` access and
usable API authentication. Check the operation before requesting new credentials.
Enter any required values securely through platform settings, never chat or Git.
