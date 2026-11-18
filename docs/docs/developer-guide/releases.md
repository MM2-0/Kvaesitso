# Android and Desktop releases

`version.properties` is the single source for Android versionName/versionCode,
Linux package version and JAR Implementation-Version, and the GitHub release tag.
Release version codes are committed positive integers and must increase with each
release; they are not generated from a workflow counter or date. The initial
unified release retains a code above the previously committed 2026110505.

1. Increase both values in `version.properties` and merge the reviewed PR into main.
2. Push the matching tag (`v<versionName>`) at that main commit, or dispatch
   **Release Android and Linux** on main. Do not run both for the same version.
3. `build-release.yml` validates the version, builds the ARM64, ARMv7 and x86_64 VM Android release APKs
   using the repository signing key, tests/builds Linux with JDK 21, and collects
   all artifacts before creating a draft. It publishes and marks Latest only
   after every required asset uploads successfully.

The workflow rejects mismatched/older tags, dispatches from other branches,
and already-published versions. A failed upload leaves a draft for a retry.
Missing signing secrets fail the job instead of generating a new signing key.
PR validation builds the three Android ABIs without repository secrets and tests/builds
Linux; it cannot publish. The Android profile uses class patterns and explicit
method descriptors, avoiding the invalid trailing return-type wildcard that
crashed profgen in previous release builds. Device startup performance is not
certified by this build check.

`release.yml` (the independent `v1.0.<run_number>` publisher) has been removed.
Debug and F-Droid workflows upload Actions artifacts only; Linux validation uses
JDK 21 and also uploads artifacts. Only `build-release.yml` publishes stable
application releases. The separate `sip-libs` prerelease is a native build input,
not an application release. Historical tags/releases are retained.

Download `telos-arm64-v8a.apk` for modern ARM phones or `telos-armeabi-v7a.apk`
for 32-bit ARM. The existing x86_64 VM build is `telos-x86_64.apk`. Linux users need the JAR, launcher script, and Java 21 or newer;
Linux session scripts are supplied in `telos-linux-session.tar.gz`.

Full Linux validation also checks archive paths: absolute paths and traversal
entries are rejected. Split-archive fixtures use stored data so the test actually
creates multiple volumes; ACE-root tests create the required archive fixture.

Both PR and production builds verify that the APK contains `.so` files for
exactly the selected ABI. Packaging exclusions follow `telos.abi`, including
`armeabi-v7a`; a Java-only APK cannot pass the release gate.

APK size is measured by `measure-apk-size.yml` on packaging-related PRs (debug
signed, not a merge gate). Compile-only gates that ran `compileDefaultDebugSources`
on every push (`apk-size-guard`, Visual Regression Guard, Edge-to-Edge Insets
Audit, Shizuku IPC compile) were removed; they failed for unrelated Gradle
errors and did not test size, layout, insets or Shizuku.

CodeQL uses a manual, uncached compilation for Java/Kotlin, with incremental
compilation disabled. Restoring compiled Gradle outputs would leave CodeQL
without compiler activity and fail analysis with "No source code was seen
during the build". The Actions language uses `build-mode: none` so GitHub can
build overlay databases on pull requests (`manual` cannot overlay).
`:libs:shizuku-next` applies HiddenApiRefinePlugin for `ActivityManagerHidden`
and similar stub types. `UserService` calls `UserHandle.of` and
`Context.createPackageContextAsUser` through reflection so javac never casts
`Context` to `ContextHidden` against `android.jar`. Maven `dev.rikka.shizuku:api`, `provider`, and `aidl` supply client types
(`ShizukuApiConstants`, `BinderContainer`, `IRemoteProcess`). Those vendored
sources are not compiled into `:libs:shizuku-next`, so R8 does not see the
classes twice. Release R8 uses `-dontwarn` for hidden framework types
(`ContentProviderHolder`, `ContextImpl`, `IProcessObserver`, `SELinux`,
`ServiceManager`, and others) that Shizuku-Next and `rikka.hidden.compat`
reference. Those classes exist on the device, not in `android.jar`.

## GitHub Actions runtime maintenance

All JavaScript Actions use Node.js 24 runtimes. Current supported majors are
checkout v7, setup-java v6, setup-node/setup-python/setup-go v7, Gradle setup v6,
cache v6, upload-artifact v7, download-artifact v8, CodeQL v4, stale v11,
upload-pages-artifact v5 and deploy-pages v5. The emulator runner's v2 major
already uses Node.js 24. Action major versions are independent of the installed
JDK: Android and Linux continue to build with the project's JDK 21 toolchain.

Documentation builds select `lts/*` with `check-latest: true` for the installed
Node.js version. Actions use their maintained major tags to receive compatible
patch/minor updates. `.github/dependabot.yml` checks GitHub Actions, Gradle and the docs npm lockfile
daily at 06:00 Europe/Athens, including weekends and major updates, and groups
each ecosystem into a PR for CI. Agents also fetch open **Dependabot security
alerts** and apply patched versions (CRITICAL then HIGH) without waiting for
that schedule; see [Dependabot](/docs/developer-guide/dependabot).
On forks, enable **Settings > Advanced Security > Dependabot version updates**
once; committing a config alone does not activate version updates on a fork.

GitHub's managed **Automatic Dependency Submission (Gradle)** is the single
dependency graph writer. `dependency-graph.yml` now verifies resolved dependency
security in the host and included build, without submitting a second snapshot.
It runs for dependency/build-definition changes and remains manually dispatchable.

The runtime compatibility workflow exercises Java, Node, Python, Go, Gradle,
cache, artifact upload/download and Pages artifact packaging, and builds the docs.
It never deploys Pages or publishes a release. Gradle v6 explicitly selects
`cache-provider: basic`, its open-source cache provider; it does not select the
commercial enhanced caching component introduced in v6.

Upstream references: [setup-java](https://github.com/actions/setup-java),
[Gradle Actions](https://github.com/gradle/actions), and
[Dependabot Actions updates](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/auto-update-actions).

## Avoiding duplicate and obsolete runs

`build-release.yml` is the single stable publisher. The unused auto-changelog
workflow and duplicate Linux desktop workflow were removed. Release notes are
created by `tools/release-notes.sh`. The retained desktop workflow keeps Linux
ARM64 artifacts on tags; x64 Linux and Windows release artifacts come from the
publisher. Desktop pull-request/manual checks still cover both Linux
architectures and Windows, including the former Linux session-script checks.

Heavy PR workflows group concurrency by PR number. Closing or merging a PR
cancels its older checks through a skipped `closed` run. Publication stays
serialized and is never cancelled by a PR update. Before upload, the publisher
checks that the remote tag still names the built commit and that current main
contains it.

`cancel-obsolete-ci.yml` also cancels checks for already closed PRs and older
ancestor commits on the default branch, including managed dependency-submission
backlogs. It reads trusted default-branch code, never a PR head or its artifacts.
Current main, release tags, manual runs, scheduled runs and unknown/diverged
history are preserved. Historical logs and verification evidence are retained.
Run the selection regression checks with
`python3 tools/validation/test_ci_run_selection.py`.

Full Android compilations use one in-process compiler with a bounded heap so
the two Kotlin versions cannot each start an additional large compiler daemon.
The nightly JDK matches the required JDK 21. Unique APK, AAB, F-Droid, emulator,
CodeQL and native checks remain separate.

The Windows VM archive is staged from published launcher output, its toolchain,
setup scripts and the x86_64 APK. It excludes repository metadata and installer
caches. Required executables must pass version checks before packaging. The ISO
is downloaded separately during user setup; `-SkipIso` keeps the release archive
from bundling the multi-gigabyte guest image. A Windows build/packaging result
does not certify an actual BlissOS guest boot on a physical host.

## SQLite startup regression

The production `AppDatabase.onOpen` callback must execute `PRAGMA mmap_size = N`
as a query and consume/close its cursor. Even the assignment form returns a row;
Android `execSQL()` rejects it with `unknown error (code 0 SQLITE_OK)`. This crash
was reproduced on an Android 37.1 emulator before the fix. The instrumentation
test opens the real production database callback, checks seeded search actions,
writes a record, closes/reopens the helper, and verifies the record remains.
No schema migration or database reset is part of this fix.

Local verification on a fresh Android 37.1 x86_64 emulator: the unchanged code
failed with the exact `SQLiteException: unknown error (code 0 SQLITE_OK)` at the
mmap statement in `AppDatabase.onOpen`; after the cursor fix,
`DatabaseStartupTest.productionDatabaseOpensAndReopensWithoutLosingData` passed.
The CI startup instrumentation matrix also covers API 26 and API 35.
