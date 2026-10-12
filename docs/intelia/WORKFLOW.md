# Development workflow for Codex and Claude Code

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
