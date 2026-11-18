# Dependabot alerts

Coding agents must fetch open Dependabot alerts and apply the patched versions
without waiting for a separate user request.

## Priority

1. **CRITICAL**
2. **HIGH**
3. **MODERATE**
4. **LOW**

CRITICAL and HIGH run before unrelated feature work in the same session.

## Where to read them

- GitHub GraphQL: `repository.vulnerabilityAlerts(states: OPEN)`
- REST: `/repos/koukomin/Telos-Launcher/dependabot/alerts`
- `.github/daily-dependency-check.json` only records that the daily job ran. It
  is not the alert list.

## How to fix

**Gradle / Maven.** Many alerts point at `settings.gradle.kts` because GitHub
attributes plugin-classpath transitives (Dokka, AGP, Kotlin) to that file.
Pin those on `gradle.beforeProject { buildscript.configurations }` in
`settings.gradle.kts` (Guava `-jre` on the plugin classpath). Jackson core/databind may be 2.22.4; pin `jackson-annotations` to `2.22`
(there is no 2.22.4 annotations artifact). Do not `useVersion` the core patch
onto annotations. Pin app graphs in
the root `allprojects { configurations.configureEach { resolutionStrategy.eachDependency } }`
block (Guava `-android` there). Keep one Bouncy Castle version
(`libs.versions.bouncycastle`, currently 1.86). App `useVersion` calls must use
that catalog entry. `.github/workflows/dependency-graph.yml` submits the
resolved Gradle graph so Dependabot can close alerts after the pins land.

**Docs (npm).** VitePress pulls vite, esbuild, postcss, nanoid, rollup and Vue.
Bump `docs/package.json` and set `overrides`, then regenerate
`docs/package-lock.json`. `npm audit` in `docs/` must be clean or each leftover
must be explained.

Do not dismiss an alert without a written false-positive. Do not take a
GPL-incompatible upgrade. Do not merge or release unless the user asked.

Dependabot version updates (`.github/dependabot.yml`) cover GitHub Actions,
Gradle and the docs npm lockfile on a daily Europe/Athens schedule.

## Verify dependency upgrades

Guava is pinned to **33.8.0** and jsoup to **1.24.1**. The app uses Guava's
`-android` artifact; Gradle's plugin classpath uses `-jre`. Update both the
`telosPluginCvePins` list and its `eachDependency` rules in `settings.gradle.kts`,
as well as the catalog entries. Leaving an old resolution rule behind silently
undoes a dependency upgrade. App modules should reference `libs.guava.android`
instead of declaring a separate literal version.

The root rule also selects the Android artifact for Android compile/runtime
classpaths even when a transitive dependency requests `-jre`. JVM modules and
compiler/tool classpaths retain `-jre`. Verify the selected artifact in
`dependencyInsight`; the declaration alone does not establish which artifact won.

The root project must be included: Dokka's generator and publication resolvers
are separate from both app dependencies and the Gradle plugin classpath. Alert
**#57 / CVE-2026-84939 / GHSA-27j2-h3m2-8237** was reproduced with FreeMarker
2.3.32 in those resolvers despite a 2.3.35 catalog pin. Applying the rules to
`allprojects` also patches the root Jackson and jsoup dependencies. The
dependency-submission workflow now runs a regression check against the actual
Dokka graph before submitting the resolved graph. It fails if vulnerable
versions remain, resolution fails, or the expected packages are not checked.

Inspect the resolved versions, not just Dependabot's manifest diff:

```sh
./gradlew :app:app:dependencyInsight --dependency com.google.guava:guava --configuration defaultDebugRuntimeClasspath
./gradlew :app:app:dependencyInsight --dependency org.jsoup:jsoup --configuration defaultDebugRuntimeClasspath
./gradlew buildEnvironment
./gradlew -I tools/validation/security-dependencies.init.gradle verifyDokkaSecurityDependencies --no-configuration-cache
./gradlew :core:base:testDebugUnitTest :services:app-management:testDebugUnitTest
npm ci --prefix docs
npm audit --prefix docs
npm run docs:build --prefix docs
```

A successful docs audit does not establish that GitHub's Dependabot, code
scanning, or code quality alerts are closed. Read those reports separately with
authorized GitHub access and recheck their status after the PR's checks finish.
The daily dependency stamp and a successful CodeQL workflow are not substitutes
for reading the alert list.

The [PR #80 audit record](https://github.com/koukomin/Telos-Launcher/blob/main/.github/dependencies/security-audit-pr80.json)
records the GitHub Advisory Database revision, the 18 reproduced advisory
matches (1 critical, 8 high, 9 moderate), and the corrected graph's zero matches
across 649 resolved module versions. This comparison excludes two JitPack
modules whose downloads were blocked in the cloud environment; it does not
claim a complete APK build or a completed CodeQL/Code Quality scan. The audit
does not dismiss GitHub alerts.

## Built-in Shizuku pairing crypto

Pairing builds `io.github.vvb2060.ndk:boringssl:20251124` (non-LTO prefab) for the pinned
NDK. The packaged headers and pinned source carry Apache-2.0 notices; the Maven POM's
older OpenSSL/ISC license metadata is stale. Source revision and full license/author notices
are recorded in `THIRD_PARTY_NOTICES.md` and APK assets. Do not replace it with the older
20250114 artifact without checking its different licensing.

Certificate construction uses `bcpkix-jdk18on` at `libs.versions.bouncycastle`, with the
existing root resolution pin keeping Bouncy Castle modules at one version. The existing
Jackson/jsoup/FreeMarker security pins are retained. The pairing regression workflow checks
both ARM native payloads and compiles the actual UI; physical-device evidence is recorded in
the [pairing audit](./shizuku-pairing.md).

## Embedded Aether build (2026-11-18)

The included Amethyst build has its own dependency catalog. Its direct jsoup
and Jackson versions are aligned to Telos (1.24.1 and 2.23.0), so independently
building the library does not fall back to the upstream snapshot's older parser
versions. The host substitutes Quartz's exact JitPack revision and Tink Android
to avoid two crypto implementations on the APK classpath. Both build paths need
validation; an included build does not inherit the host resolution strategy.

The open-alert API returned Forbidden in this cloud session. This is an access
limitation, not evidence that there are zero open alerts. The known FreeMarker
alert remains covered by the resolved Dokka graph check and the 2.3.35 pin.

The local resolved Dokka security task passed on 2026-11-18: four sensitive
packages verified across four root resolvers. `npm audit --package-lock-only`
reported eight high entries, all propagated from **GHSA-fc5q-39xc-vjpf**
(picomatch repeated extglob ReDoS, affected through 4.0.7). The npm registry's
latest version is still 4.0.7 and audit reports no fix. The docs dependencies use
repository-defined build/watch globs; the published static site does not execute
picomatch or accept visitor-provided glob expressions. No global major override
or audit suppression is added. This advisory remains open until a patched
compatible release is available; eight entries are not eight independent flaws.
