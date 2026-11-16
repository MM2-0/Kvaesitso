# Location Foreground Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop Amethyst holding a location registration when no activity is started, register on one appropriate provider instead of four, and make `location.ms` measure real listening time.

**Architecture:** A three-state gate over *permission × foreground* inside `LocationState` replaces the permission-only gate, so the registration is released whenever the app is backgrounded. `LocationFlow` selects one provider from an ordered ladder instead of shotgunning `allProviders`, and owns the `onListening` hook so accounting cannot start without a live registration. A `RefCountedSession` serialises the meter's refcount with the transition it drives.

**Tech Stack:** Kotlin, kotlinx.coroutines Flow/StateFlow, `android.location.LocationManager` (no Play Services), JUnit 4 + MockK + kotlinx-coroutines-test.

**Design spec:** `amethyst/plans/2026-07-29-location-foreground-gate.md` — read it before starting. This plan implements it; where the two disagree, the spec is wrong and should be corrected.

## Global Constraints

- Module is `amethyst` (Android only). Package root `com.vitorpamplona.amethyst`.
- `minSdk = 26`, `targetSdk = 37`, `compileSdk = 37` (`gradle/libs.versions.toml:12-14`).
- Every new `.kt` file starts with the MIT licence header — copy it verbatim from `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationState.kt:1-20`, changing nothing.
- Logging uses `com.vitorpamplona.quartz.utils.Log`, never `android.util.Log`. Use the lambda overload (`Log.d("Tag") { "msg $x" }`) when the message interpolates **and there is no throwable**. When a throwable must be logged, use the three-argument form `Log.w("Tag", "message", e)` — the lambda overloads take no `Throwable` (`quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/utils/Log.kt:74-79`), so "converting" such a call to a lambda silently discards the stack trace. That is the exact anti-pattern the repo's `find-non-lambda-logs` skill flags.
- Never pass `--no-verify` to `git commit`. The pre-commit hook runs `./gradlew spotlessCheck` and nothing else — no compilation — so it passes even at the two points in this plan where the module is temporarily red.
- No new third-party dependencies. All libraries used here are already declared.
- Never write a fully-qualified class name inline in a function body — add an `import`.
- Run `./gradlew spotlessApply` before every commit.
- Do not push and do not open a PR. Commit locally only.
- Unit tests live in `amethyst/src/test/java/...`, run with `./gradlew :amethyst:testPlayDebugUnitTest`.

## Naming contract

These names are used across tasks. Use them exactly.

| Name | Defined in | Signature |
|---|---|---|
| `RefCountedSession` | Task 2 | `class RefCountedSession(setSessionActive: (Boolean) -> Unit)`, method `fun setActive(active: Boolean)` |
| `LocationProviderLadder.chooseProviders` | Task 3 | `fun chooseProviders(sdkInt: Int, hasFine: Boolean, exists: (String) -> Boolean): List<String>` |
| `LocationFlow` | Task 4 | `class LocationFlow(locationManager: LocationManager, sdkInt: Int = Build.VERSION.SDK_INT, hasFine: Boolean = false)`, method `fun get(minTimeMs: Long, minDistanceM: Float, onListening: ((Boolean) -> Unit)? = null): Flow<Location>` |
| `LocationState` | Task 5 | `class LocationState(context: Context, scope: CoroutineScope, isForeground: StateFlow<Boolean>, onListening: ((Boolean) -> Unit)? = null, locationSource: (Long, Float) -> Flow<Location> = …)` |
| `LocationState.COARSE_MIN_TIME` | Task 5 | `const val = 60_000L` |
| `LocationState.COARSE_MIN_DISTANCE` | Task 5 | `const val = 500.0f` |
| `LocationState.PRECISE_MIN_TIME` | Task 5 | `const val = 10_000L` |
| `LocationState.PRECISE_MIN_DISTANCE` | Task 5 | `const val = 100.0f` |
| `LocationState.BACKGROUND_GRACE_MS` | Task 5 | `const val = 5_000L` |

`LocationState.MIN_TIME` and `LocationState.MIN_DISTANCE` are **deleted** in Task 5. Their only readers are `LocationState.kt:80`, `:119` and `LocationFlow.kt:29-30,42-43`, all rewritten by Tasks 4–5.

## File structure

| File | Task | Responsibility |
|---|---|---|
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSession.kt` | 2 | Serialise a refcount with the boolean transition it drives |
| `amethyst/src/test/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSessionTest.kt` | 2 | ↑ |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadder.kt` | 3 | Pure provider-selection policy |
| `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadderTest.kt` | 3 | ↑ |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt` | 4 | Own the OS registration and the paired listening hook |
| `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationFlowTest.kt` | 4 | ↑ |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationState.kt` | 5 | Gate on permission × foreground; expose the two geohash StateFlows |
| `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationStateTest.kt` | 5 | ↑ |
| `amethyst/src/main/java/com/vitorpamplona/amethyst/AppModules.kt` | 6 | Wire the foreground signal and the refcounted meter |

---

### Task 1: Verify Hypothesis H1 on an API 26 emulator

The spec's §H1 predicts that today's `allProviders` loop throws `SecurityException` on `gps`, `passive` and `fused` below API 31, because those required `ACCESS_FINE_LOCATION` before Android 12 and Amethyst holds coarse only. If true, location is **already broken** on Android 8–11 and this change fixes it — which belongs in the PR description. If false, the PR must not claim it.

This task changes no production code. It is first because the spec says to verify before implementing, and because the answer decides one paragraph of the PR.

**Files:**
- Modify (temporarily, reverted in Step 5): `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt:55`

**Interfaces:**
- Consumes: nothing
- Produces: a written observation recorded in Step 6; no code

- [ ] **Step 1: Boot the API 26 emulator**

```bash
emulator -avd Medium_Phone_API_26_8_ -no-snapshot-load &
adb wait-for-device
adb shell getprop ro.build.version.sdk
```

Expected: prints `26`.

If the AVD fails to boot, stop and report it. Do not substitute a different API level without saying so — the claim is specifically about API 26–30.

- [ ] **Step 2: Add a temporary log of the provider order**

`LocationFlow.kt`, immediately before the `locationManager.allProviders.forEach {` line (currently line 55), insert:

```kotlin
            Log.w("LocationFlowH1") { "allProviders order = ${locationManager.allProviders}" }
```

This is the ordering evidence the spec requires: the leak consequence only occurs if `network` precedes a fine-only provider, so the PR cannot claim a leak without seeing the order.

- [ ] **Step 3: Install and grant coarse location only**

```bash
./gradlew :amethyst:installPlayDebug
adb shell pm grant com.vitorpamplona.amethyst android.permission.ACCESS_COARSE_LOCATION
adb logcat -c
```

- [ ] **Step 4: Trigger a location subscription and capture the result**

Launch the app, sign in to any account, and open the top-nav filter spinner on Home, selecting "Around Me". Then:

```bash
adb logcat -d | grep -E "LocationFlowH1|SecurityException|LocationFlow"
```

Record verbatim:
1. the `allProviders order = [...]` line
2. whether a `SecurityException` appears, and which provider it names
3. whether any `Requesting Updates` line precedes the exception

- [ ] **Step 5: Revert the temporary log**

```bash
git checkout -- amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt
git status --short
```

Expected: no changes to `LocationFlow.kt`.

- [ ] **Step 6: Record the observation in the spec**

Append to the `## Hypothesis H1` section of `amethyst/plans/2026-07-29-location-foreground-gate.md`, replacing `<...>` with what Step 4 actually showed:

```markdown
### H1 verification result (2026-07-29, Medium_Phone_API_26_8_, API 26)

- `allProviders` order: `<observed order>`
- `SecurityException`: `<yes, on provider X / no>`
- Registration attempted before the throw: `<yes / no>`

Conclusion: location is `<broken / working>` on API 26 with coarse-only
permission; registrations `<do / do not>` leak.
```

- [ ] **Step 7: If H1 is FALSE, amend the plan before continuing**

The spec says the design is "correct either way", which is true only in one
direction. Task 3 hard-codes H1's conclusion in `COARSE_ONLY_LEGACY_LADDER` and
two of its tests. If Step 4 showed **no** `SecurityException`, then coarse
permission does reach `gps`/`fused`/`passive` below API 31 on this build, and
restricting pre-31 devices to `network` would be a conclusion drawn from
evidence that contradicts it. It is safe for `geohashStateFlow` — `network` is
the right provider for a 5 km cell either way — but it needlessly denies
`preciseGeohashStateFlow` any provider capable of building-level precision on
Android 8–11.

So, **only if no `SecurityException` appeared**, make these three amendments
before starting Task 3, and say in the commit message that H1 was disproved:

1. In Task 3's implementation, delete `COARSE_ONLY_LEGACY_LADDER` and the
   `sdkInt`/`hasFine` branch. `chooseProviders` becomes
   `fun chooseProviders(exists: (String) -> Boolean): List<String> = FULL_LADDER.filter(exists)`.
2. In Task 3's test, delete `coarseOnlyBelowApi31GetsNetworkOnly` and
   `coarseOnlyBelowApi31WithNoNetworkProviderGetsNothing`, and drop the
   `sdkInt`/`hasFine` arguments from the remaining four.
3. In Task 4, drop the `sdkInt` and `hasFine` constructor parameters from
   `LocationFlow` and the corresponding arguments from its six tests.
4. In Task 4's `get`, update the call site to match amendment 1:
   `LocationProviderLadder.chooseProviders(sdkInt, hasFine) { it in providers }`
   becomes `LocationProviderLadder.chooseProviders { it in providers }`.

The per-rung `SecurityException` fall-through stays in **both** cases — it is
what makes the ladder robust to whatever the runtime check actually does, and
it is why H1 being wrong costs nothing.

- [ ] **Step 8: Commit**

```bash
git add amethyst/plans/2026-07-29-location-foreground-gate.md
git commit -m "docs(amethyst): record H1 verification result on API 26"
```

---

### Task 2: RefCountedSession

**Files:**
- Create: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSession.kt`
- Test: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSessionTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `class RefCountedSession(setSessionActive: (Boolean) -> Unit)` with `fun setActive(active: Boolean)`. Task 6 constructs it as `RefCountedSession(locationSession::setActive)`.

- [ ] **Step 1: Write the failing test**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSessionTest.kt` with the MIT header (copy `LocationState.kt:1-20` verbatim), then:

```kotlin
package com.vitorpamplona.amethyst.service.resourceusage

import org.junit.Assert.assertEquals
import org.junit.Test

class RefCountedSessionTest {
    @Test
    fun overlappingHoldersKeepTheSessionOpenAndReportOnlyTransitions() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(true) // holders 1 — inactive -> active
        session.setActive(true) // holders 2 — a second listener joins, no transition
        session.setActive(false) // holders 1 — the first one leaves, still active

        assertEquals("only the 0 -> 1 edge is a transition", listOf(true), calls)

        session.setActive(false) // holders 0 — the last one leaves

        assertEquals(listOf(true, false), calls)
    }

    @Test
    fun unmatchedReleaseDoesNotDriveTheCountNegative() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(false)
        session.setActive(false)

        assertEquals("releasing an idle session is a no-op", emptyList<Boolean>(), calls)

        // If the count had gone to -2, one acquire would leave it at -1 and
        // report inactive. It must open the session instead.
        session.setActive(true)

        assertEquals(listOf(true), calls)
    }

    @Test
    fun aSingleHolderOpensAndClosesTheSession() {
        val calls = mutableListOf<Boolean>()
        val session = RefCountedSession { calls.add(it) }

        session.setActive(true)
        session.setActive(false)

        assertEquals(listOf(true, false), calls)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*RefCountedSessionTest*'
```

Expected: FAIL — compilation error, `Unresolved reference: RefCountedSession`.

- [ ] **Step 3: Write the implementation**

Create `amethyst/src/main/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSession.kt` with the MIT header, then:

```kotlin
package com.vitorpamplona.amethyst.service.resourceusage

/**
 * Refcounts a boolean session so overlapping holders don't close each other's
 * segment. [LocationState][com.vitorpamplona.amethyst.service.location.LocationState]
 * exposes two independent location flows that can both be listening at once —
 * the "Around Me" feed plus an open geohash chat — and a bare
 * [SessionTimeIntegrator] would close the segment when either one stops.
 *
 * The count and the transition it drives are taken under one lock. An
 * [java.util.concurrent.atomic.AtomicInteger] beside an unsynchronised call is
 * not enough: two threads can leave the counter at 1 while the last
 * `setActive(false)` lands after the `setActive(true)`, latching the session
 * off with a holder still active.
 *
 * Takes the setter as a lambda rather than a [SessionTimeIntegrator] because
 * that is all it needs — and because constructing a real integrator drags in a
 * [ResourceUsageAccountant] and a store file to observe one boolean.
 *
 * Reports **transitions only**, not every call. A 1 -> 2 acquire would otherwise
 * re-enter [SessionTimeIntegrator.setActive] with the session already open,
 * splitting one segment into two. That happens to be arithmetically harmless
 * (`account()` adds each piece, and the pieces are contiguous), and it does not
 * inflate a `*.starts` counter either, because [SessionTimeIntegrator] already
 * guards its starts increment on `prev == null`. Transition-only is simply the
 * contract the name implies, and it keeps the class honest for a future caller
 * that reacts to the callback rather than integrating it.
 *
 * Releases must be paired with acquires. This class cannot tell an unpaired
 * release from a real one, so callers guarantee the pairing; see `LocationFlow`,
 * which throws rather than reaching `awaitClose` when nothing registered.
 */
class RefCountedSession(
    private val setSessionActive: (Boolean) -> Unit,
) {
    private val lock = Any()
    private var holders = 0

    fun setActive(active: Boolean) {
        synchronized(lock) {
            val wasActive = holders > 0
            holders = if (active) holders + 1 else (holders - 1).coerceAtLeast(0)
            val isActive = holders > 0
            if (isActive != wasActive) setSessionActive(isActive)
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*RefCountedSessionTest*'
```

Expected: PASS, 3 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSession.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/resourceusage/RefCountedSessionTest.kt
git commit -m "feat(amethyst): add RefCountedSession for overlapping ledger holders"
```

---

### Task 3: Provider ladder

**Files:**
- Create: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadder.kt`
- Test: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadderTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `LocationProviderLadder.chooseProviders(sdkInt: Int, hasFine: Boolean, exists: (String) -> Boolean): List<String>`. Task 4 calls it.

- [ ] **Step 1: Write the failing test**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadderTest.kt` with the MIT header, then:

```kotlin
package com.vitorpamplona.amethyst.service.location

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationProviderLadderTest {
    private val all = setOf("fused", "network", "gps", "passive")

    @Test
    fun modernDevicePrefersFusedThenFallsBackInOrder() {
        assertEquals(
            listOf("fused", "network", "gps", "passive"),
            LocationProviderLadder.chooseProviders(sdkInt = 31, hasFine = false) { it in all },
        )
    }

    @Test
    fun missingProvidersAreFilteredOutButOrderIsKept() {
        val present = setOf("network", "passive")

        assertEquals(
            listOf("network", "passive"),
            LocationProviderLadder.chooseProviders(sdkInt = 37, hasFine = false) { it in present },
        )
    }

    @Test
    fun coarseOnlyBelowApi31GetsNetworkOnly() {
        // gps, passive and fused all required ACCESS_FINE_LOCATION before
        // Android 12 (see Hypothesis H1 in the design spec).
        assertEquals(
            listOf("network"),
            LocationProviderLadder.chooseProviders(sdkInt = 30, hasFine = false) { it in all },
        )
    }

    @Test
    fun fineBelowApi31GetsTheFullLadder() {
        assertEquals(
            listOf("fused", "network", "gps", "passive"),
            LocationProviderLadder.chooseProviders(sdkInt = 26, hasFine = true) { it in all },
        )
    }

    @Test
    fun coarseOnlyBelowApi31WithNoNetworkProviderGetsNothing() {
        val present = setOf("gps", "passive")

        assertEquals(
            emptyList<String>(),
            LocationProviderLadder.chooseProviders(sdkInt = 28, hasFine = false) { it in present },
        )
    }

    @Test
    fun noProvidersAtAllGetsNothing() {
        assertEquals(
            emptyList<String>(),
            LocationProviderLadder.chooseProviders(sdkInt = 37, hasFine = false) { false },
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationProviderLadderTest*'
```

Expected: FAIL — compilation error, `Unresolved reference: LocationProviderLadder`.

- [ ] **Step 3: Write the implementation**

Create `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadder.kt` with the MIT header, then:

```kotlin
package com.vitorpamplona.amethyst.service.location

import android.location.LocationManager
import android.os.Build

/**
 * Picks which location providers to try, in order.
 *
 * Deliberately selects on **provider existence**, never on
 * [LocationManager.isProviderEnabled]. A registration on a disabled provider
 * goes live by itself when the user enables location — including from the
 * quick-settings shade without leaving the app, which is exactly what someone
 * does after seeing an empty "Around Me" feed. An enabled-state guard evaluated
 * once at subscription start would lose that.
 *
 * Below API 31, `gps`, `passive` and `fused` required `ACCESS_FINE_LOCATION`;
 * only `network` accepted `ACCESS_COARSE_LOCATION`. Approximate location, which
 * lets a coarse-only app request any provider and receive a fuzzed result, is an
 * Android 12 change. Amethyst declares coarse only, so [hasFine] is always false
 * in production — it is a parameter so the function is total over the permission
 * axis and both sides of the API branch are testable, not because fine access is
 * anticipated.
 *
 * Returns the ordered candidate list rather than a single choice so the caller
 * can fall through to the next rung if a registration is refused. An empty list
 * means no compatible provider exists.
 */
object LocationProviderLadder {
    // Compile-time String constants, inlined by the compiler, so naming
    // FUSED_PROVIDER (added in API 31) is safe on older runtimes.
    private val FULL_LADDER =
        listOf(
            LocationManager.FUSED_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

    private val COARSE_ONLY_LEGACY_LADDER = listOf(LocationManager.NETWORK_PROVIDER)

    fun chooseProviders(
        sdkInt: Int,
        hasFine: Boolean,
        exists: (String) -> Boolean,
    ): List<String> {
        val ladder =
            if (sdkInt >= Build.VERSION_CODES.S || hasFine) {
                FULL_LADDER
            } else {
                COARSE_ONLY_LEGACY_LADDER
            }

        return ladder.filter(exists)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationProviderLadderTest*'
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadder.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationProviderLadderTest.kt
git commit -m "feat(amethyst): add location provider ladder"
```

---

### Task 4: LocationFlow — one provider, paired listening hook

**Files:**
- Modify (full rewrite of the class body): `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt`
- Test: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationFlowTest.kt`

**Interfaces:**
- Consumes: `LocationProviderLadder.chooseProviders` (Task 3)
- Produces: `class LocationFlow(locationManager: LocationManager, sdkInt: Int = Build.VERSION.SDK_INT, hasFine: Boolean = false)` with `fun get(minTimeMs: Long, minDistanceM: Float, onListening: ((Boolean) -> Unit)? = null): Flow<Location>`. Task 5 constructs it.

The constructor takes a `LocationManager`, **not** a `Context`, so it can be tested with a MockK stub. `LocationState` does the `getSystemService` lookup.

- [ ] **Step 1: Write the failing test**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationFlowTest.kt` with the MIT header, then:

```kotlin
package com.vitorpamplona.amethyst.service.location

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationFlowTest {
    /**
     * A LocationManager that reports [providers] and refuses [denied] with a
     * SecurityException, mimicking the pre-API-31 fine-location requirement.
     */
    private fun manager(
        providers: List<String>,
        denied: Set<String> = emptySet(),
    ): LocationManager {
        val lm = mockk<LocationManager>(relaxed = true)
        every { lm.allProviders } returns providers
        every { lm.getLastKnownLocation(any()) } returns null
        every {
            lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
        } answers {
            val provider = firstArg<String>()
            if (provider in denied) throw SecurityException("denied: $provider")
        }
        return lm
    }

    @Test
    fun firesNeitherEdgeWhenNoProviderExists() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val flow = LocationFlow(manager(providers = emptyList()), sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun firesNeitherEdgeWhenEveryRungIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused", "network"))
            val flow = LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }

            val failure = runCatching { flow.collect { } }.exceptionOrNull()

            assertTrue("expected SecurityException, got $failure", failure is SecurityException)
            assertEquals(emptyList<Boolean>(), edges)
        }

    @Test
    fun fallsThroughToTheNextRungWhenOneIsDenied() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("fused", "network"), denied = setOf("fused"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()

            assertEquals(listOf(true), edges)
            verify { lm.requestLocationUpdates("network", 60_000L, 500f, any<LocationListener>(), any()) }

            job.cancelAndJoin()
        }

    @Test
    fun pairsTheListeningEdgesAroundASuccessfulRegistration() =
        runTest {
            val edges = mutableListOf<Boolean>()
            val lm = manager(providers = listOf("network"))
            val job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { } }

            runCurrent()
            assertEquals(listOf(true), edges)

            job.cancelAndJoin()

            assertEquals(listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun releasesTheRegistrationWhenCancelledDuringTheSeed() =
        runTest {
            // `send` in the seed suspends, so a collector cancelling while the
            // getLastKnownLocation sweep is in flight unwinds the producer
            // there. Cleanup must still run, or the refcount sticks at >= 1
            // forever and the OS registration leaks.
            val edges = mutableListOf<Boolean>()
            val lm = mockk<LocationManager>(relaxed = true)
            every { lm.allProviders } returns listOf("network")

            lateinit var job: Job
            every { lm.getLastKnownLocation(any()) } answers {
                // Cancel from inside the sweep, so the subsequent send() throws.
                job.cancel()
                mockk<Location> { every { time } returns 1L }
            }

            job = launch { LocationFlow(lm, sdkInt = 30).get(60_000L, 500f) { edges.add(it) }.collect { } }
            runCurrent()
            job.join()

            assertEquals("the acquire must be released even on cancellation", listOf(true, false), edges)
            verify { lm.removeUpdates(any<LocationListener>()) }
        }

    @Test
    fun registersOnExactlyOneProvider() =
        runTest {
            val lm = manager(providers = listOf("fused", "network", "gps", "passive"))
            val job = launch { LocationFlow(lm, sdkInt = 37).get(60_000L, 500f).collect { } }

            runCurrent()

            verify(exactly = 1) {
                lm.requestLocationUpdates(any<String>(), any<Long>(), any<Float>(), any<LocationListener>(), any())
            }

            job.cancelAndJoin()
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationFlowTest*'
```

Expected: FAIL — compilation error. `LocationFlow` currently takes a `Context`, and `get` has no `onListening` parameter.

- [ ] **Step 3: Rewrite LocationFlow**

Replace everything **below** the MIT header in `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt` with:

```kotlin
package com.vitorpamplona.amethyst.service.location

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Wraps [LocationManager] update registration as a cold [Flow].
 *
 * Registers on **one** provider, chosen by [LocationProviderLadder], rather than
 * on every provider the device reports. The previous shotgun cost four
 * simultaneous registrations — passive, network, fused and gps, the last at
 * HIGH_ACCURACY — to produce a 5 km geohash.
 *
 * Takes a [LocationManager] rather than a `Context` so the registration
 * behaviour is unit-testable; the caller does the `getSystemService` lookup.
 *
 * [onListening] is fired from inside the flow, after a registration succeeds and
 * again from `awaitClose`, never as an `onStart`/`onCompletion` pair on the
 * returned flow. The distinction matters: an `onStart` fires on collection even
 * when nothing registered, so a device with no usable provider would accrue
 * location time with no location running, and — because the ledger refcounts the
 * two [LocationState] flows together — the unpaired close would steal the other
 * flow's holder.
 *
 * The pair is kept honest from both ends. The acquire cannot fire without a
 * registration, because a failure to register throws before reaching it. The
 * release cannot be skipped, because everything after the acquire runs inside a
 * `try`/`finally` rather than inside `awaitClose` — `send` suspends, so a
 * collector that cancels mid-seed would otherwise unwind past an `awaitClose`
 * that never ran.
 */
class LocationFlow(
    private val locationManager: LocationManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val hasFine: Boolean = false,
) {
    @SuppressLint("MissingPermission")
    fun get(
        minTimeMs: Long,
        minDistanceM: Float,
        onListening: ((Boolean) -> Unit)? = null,
    ): Flow<Location> =
        callbackFlow {
            val locationCallback =
                LocationListener { location ->
                    Log.d("LocationFlow") { "onLocationChanged $location" }
                    launch { send(location) }
                }

            // One binder call, reused for both the ladder filter and the seed.
            val providers = locationManager.allProviders

            val candidates = LocationProviderLadder.chooseProviders(sdkInt, hasFine) { it in providers }

            var registered: String? = null
            for (provider in candidates) {
                try {
                    locationManager.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        minDistanceM,
                        locationCallback,
                        Looper.getMainLooper(),
                    )
                    registered = provider
                    break
                } catch (e: SecurityException) {
                    Log.w("LocationFlow", "Provider $provider refused the update request", e)
                }
            }

            if (registered == null) {
                throw SecurityException("No usable location provider. Candidates: $candidates")
            }

            Log.i("LocationFlow") { "Listening on $registered every ${minTimeMs}ms / ${minDistanceM}m" }
            onListening?.invoke(true)

            // Everything after the acquire runs under try/finally, not under
            // awaitClose. `send` below suspends, so it is a cancellation point:
            // if the collector cancels while the seed is mid-flight, the
            // producer throws there and `awaitClose` is never entered. Cleanup
            // parked inside awaitClose would then never run — the registration
            // would leak and the refcount would stick at >= 1 for the life of
            // the process, so location.ms would accrue forever with nothing
            // listening. The finally covers normal close and
            // cancellation-during-send alike.
            try {
                // Seeded after registration so the no-provider path throws
                // without having emitted anything; seeding first would show the
                // consumer Success -> LackPermission on a device with no
                // compatible provider.
                freshestLastKnownLocation(providers)?.let {
                    Log.d("LocationFlow") { "Last known location is $it" }
                    send(it)
                }

                awaitClose { }
            } finally {
                Log.i("LocationFlow") { "Stopped listening on $registered" }
                locationManager.removeUpdates(locationCallback)
                onListening?.invoke(false)
            }
        }

    /**
     * The freshest cached fix across every provider. Permission-checked per
     * provider like the update request is, so each lookup is guarded — on a
     * device where a provider refuses us, the others should still seed.
     */
    @SuppressLint("MissingPermission")
    private fun freshestLastKnownLocation(providers: List<String>): Location? =
        providers
            .mapNotNull { provider ->
                try {
                    locationManager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    Log.w("LocationFlow", "No permission to read the last known location of $provider", e)
                    null
                }
            }.maxByOrNull { it.time }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationFlowTest*'
```

Expected: PASS, 6 tests.

If `releasesTheRegistrationWhenCancelledDuringTheSeed` fails to *fail* against a version without the `try`/`finally` — i.e. it passes either way — the cancellation is not reaching `send` as expected. Do not delete the test: replace the in-answer `job.cancel()` with a `trySend` on a channel the test awaits, or fall back to asserting the same invariant from `LocationStateTest` by cancelling the collector mid-gate. The invariant is what matters, not this particular provocation.

`LocationState.kt` will not compile yet — it still calls the old `LocationFlow(context)` and `MIN_TIME`. That is Task 5. If the Gradle run fails on `LocationState.kt` compilation rather than on the tests, that is expected; proceed to Task 5 and re-run both suites there.

- [ ] **Step 5: Commit**

The module is red until Task 5 lands. That does not block the commit: the pre-commit hook runs `spotlessCheck` only, which does not compile.

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationFlow.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationFlowTest.kt
git commit -m "feat(amethyst): register one location provider with a paired listening hook"
```

---

### Task 5: LocationState — the foreground gate

**Files:**
- Modify (full rewrite of the class body): `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationState.kt`
- Test: `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationStateTest.kt`

**Interfaces:**
- Consumes: `LocationFlow` (Task 4)
- Produces: `class LocationState(context, scope, isForeground, onListening, locationSource)` and the five `const val`s in the naming contract. Task 6 constructs it.

- [ ] **Step 1: Write the failing test**

Create `amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationStateTest.kt` with the MIT header, then:

```kotlin
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import android.location.Location
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationStateTest {
    private fun locationAt(
        lat: Double,
        lon: Double,
    ): Location =
        mockk<Location> {
            every { latitude } returns lat
            every { longitude } returns lon
        }

    /** Counts subscriptions and completions of the underlying location source. */
    private class SourceProbe(
        private val body: suspend FlowCollector<Location>.() -> Unit,
    ) {
        var subscriptions = 0
            private set
        var completions = 0
            private set

        val live: Int get() = subscriptions - completions

        fun source(): (Long, Float) -> Flow<Location> =
            { _, _ ->
                flow(body)
                    .onStart { subscriptions++ }
                    .onCompletion { completions++ }
            }
    }

    private fun neverEmits() = SourceProbe { awaitCancellation() }

    private fun emitsOnceThenHangs(
        lat: Double,
        lon: Double,
    ) = SourceProbe {
        emit(locationAt(lat, lon))
        awaitCancellation()
    }

    private fun stateWith(
        scope: CoroutineScope,
        foreground: MutableStateFlow<Boolean>,
        probe: SourceProbe,
    ) = LocationState(
        context = mockk<Context>(relaxed = true),
        scope = scope,
        isForeground = foreground,
        locationSource = probe.source(),
    )

    @Test
    fun doesNotListenWhileBackgrounded() =
        runTest {
            val probe = neverEmits()
            val foreground = MutableStateFlow(false)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(0, probe.subscriptions)
        }

    @Test
    fun listensOnceWhileForegrounded() =
        runTest {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(1, probe.subscriptions)
            assertEquals(1, probe.live)
        }

    @Test
    fun releasesTheSourceAfterTheGracePeriodAndKeepsTheLastFix() =
        runTest {
            val probe = emitsOnceThenHangs(56.048839, 12.721029)
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            val fixWhileForeground = state.geohashStateFlow.value
            assertTrue("expected a Success, got $fixWhileForeground", fixWhileForeground is LocationState.LocationResult.Success)

            foreground.value = false
            advanceUntilIdle()

            assertEquals("source must be released once backgrounded", 0, probe.live)
            assertEquals(
                "the last geohash must survive the release for the ~60 synchronous .value readers",
                fixWhileForeground,
                state.geohashStateFlow.value,
            )
        }

    @Test
    fun keepsListeningAcrossABackgroundEdgeShorterThanTheGracePeriod() =
        runTest {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()
            assertEquals(1, probe.subscriptions)

            foreground.value = false
            advanceTimeBy(LocationState.BACKGROUND_GRACE_MS / 2)
            foreground.value = true
            advanceUntilIdle()

            assertEquals("a brief app switch must not tear down the registration", 1, probe.subscriptions)
            assertEquals(1, probe.live)
        }

    @Test
    fun doesNotReemitLoadingWhenReturningToForegroundWithACachedFix() =
        runTest {
            val probe = emitsOnceThenHangs(56.048839, 12.721029)
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            val seen = mutableListOf<LocationState.LocationResult>()
            backgroundScope.launch { state.geohashStateFlow.collect { seen.add(it) } }
            advanceUntilIdle()

            foreground.value = false
            advanceUntilIdle()
            val afterBackground = seen.size

            foreground.value = true
            advanceUntilIdle()

            assertTrue(
                "returning to foreground must not flash Loading — AroundMeFeedFlow renders an empty feed for it. Saw: ${seen.drop(afterBackground)}",
                seen.drop(afterBackground).none { it is LocationState.LocationResult.Loading },
            )
        }

    @Test
    fun emitsLoadingOnTheFirstForegroundWhenThereIsNoCachedFix() =
        runTest {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(true)

            val seen = mutableListOf<LocationState.LocationResult>()
            backgroundScope.launch { state.geohashStateFlow.collect { seen.add(it) } }
            advanceUntilIdle()

            assertTrue("expected Loading, saw $seen", seen.any { it is LocationState.LocationResult.Loading })
        }

    @Test
    fun reportsLackPermissionRegardlessOfForeground() =
        runTest {
            val probe = neverEmits()
            val foreground = MutableStateFlow(true)
            val state = stateWith(backgroundScope, foreground, probe)
            state.setLocationPermission(false)

            backgroundScope.launch { state.geohashStateFlow.collect { } }
            advanceUntilIdle()

            assertEquals(LocationState.LocationResult.LackPermission, state.geohashStateFlow.value)
            assertEquals(0, probe.subscriptions)
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationStateTest*'
```

Expected: FAIL — compilation error. `LocationState` has no `isForeground` or `locationSource` parameter and no `BACKGROUND_GRACE_MS`.

- [ ] **Step 3: Rewrite LocationState**

Replace everything **below** the MIT header in `amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationState.kt` with:

```kotlin
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChannelLevel
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeoHash
import com.vitorpamplona.quartz.nip01Core.tags.geohash.GeohashPrecision
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

// `toGeoHash` is an extension on Location declared in LocationGeoHash.kt, same
// package, so it needs no import.

/**
 * Turns the device's location into geohashes, listening **only while the app is
 * in the foreground**.
 *
 * The gate is not an optimisation of last resort: `Account` builds 30
 * `SharingStarted.Eagerly` top-nav filter states on the account scope, and
 * `AccountSettings.defaultProductsFollowList` ships as `TopFilter.AroundMe`, so
 * without it every user with location permission holds a registration for the
 * life of the process. See `amethyst/plans/2026-07-29-location-foreground-gate.md`.
 *
 * Switching the *consumers* to `WhileSubscribed` is not an option: roughly 60
 * call sites read `account.live*FollowLists.value` synchronously rather than
 * collecting, and would silently serve a stale or initial value.
 */
class LocationState(
    context: Context,
    scope: CoroutineScope,
    private val isForeground: StateFlow<Boolean>,
    /**
     * Resource-ledger hook: true while location updates are actively
     * registered. Reaches the OS only through the default [locationSource],
     * which hands it to [LocationFlow] — a caller that overrides
     * [locationSource] (the tests do) is responsible for firing it, or not.
     */
    private val onListening: ((Boolean) -> Unit)? = null,
    private val locationSource: (Long, Float) -> Flow<Location> = { minTimeMs, minDistanceM ->
        LocationFlow(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)
            .get(minTimeMs, minDistanceM, onListening)
    },
) {
    companion object {
        /** A 5 km cell takes 2.5 minutes to cross at 120 km/h; 60s/500m is ample. */
        const val COARSE_MIN_TIME: Long = 60_000L
        const val COARSE_MIN_DISTANCE: Float = 500.0f

        /** Building-level geohashes need the tighter profile. */
        const val PRECISE_MIN_TIME: Long = 10_000L
        const val PRECISE_MIN_DISTANCE: Float = 100.0f

        /**
         * How long to keep listening after the last activity stops, so a
         * one-second app switch doesn't destroy and rebuild the registration.
         * Matches the `WhileSubscribed` window below, and is the same intent.
         */
        const val BACKGROUND_GRACE_MS: Long = 5_000L
    }

    sealed class LocationResult {
        data class Success(
            val geoHash: GeoHash,
        ) : LocationResult()

        object LackPermission : LocationResult()

        object Loading : LocationResult()
    }

    private enum class Gate { NoPermission, Paused, Listen }

    private var hasLocationPermission = MutableStateFlow(false)

    // Volatile: R1 below reads these to decide whether to emit Loading, from a
    // different coroutine than the onEach that writes them.
    @Volatile private var latestLocation: LocationResult = LocationResult.Loading

    @Volatile private var latestPreciseLocation: LocationResult = LocationResult.Loading

    fun setLocationPermission(newValue: Boolean) {
        if (newValue != hasLocationPermission.value) {
            hasLocationPermission.tryEmit(newValue)
        }
    }

    /**
     * Foreground with an asymmetric delay: leaving the foreground waits out
     * [BACKGROUND_GRACE_MS], returning to it is immediate.
     *
     * `debounce(5000)` would delay both edges, and the duration-selector
     * overload that allows an asymmetric delay is `@FlowPreview`.
     * `transformLatest` cancels the pending `delay` when foreground returns
     * first, which is exactly the semantics wanted, with no preview opt-in.
     *
     * Known and harmless: [ForegroundTracker] starts at `false`, so on a
     * process that starts backgrounded the first emission — and therefore the
     * first gate verdict, including `LackPermission` — is delayed by
     * [BACKGROUND_GRACE_MS]. Nothing renders while backgrounded, and a process
     * that starts into the foreground emits immediately, because the activity's
     * `onStart` cancels the pending delay.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val settledForeground: Flow<Boolean> =
        isForeground.transformLatest { foreground ->
            if (!foreground) delay(BACKGROUND_GRACE_MS)
            emit(foreground)
        }

    private val gate: Flow<Gate> =
        combine(hasLocationPermission, settledForeground) { permitted, foreground ->
            when {
                !permitted -> Gate.NoPermission
                foreground -> Gate.Listen
                else -> Gate.Paused
            }
        }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun geohashFlow(
        tag: String,
        charsCount: Int,
        minTimeMs: Long,
        minDistanceM: Float,
        latest: () -> LocationResult,
        setLatest: (LocationResult) -> Unit,
    ): Flow<LocationResult> =
        gate.transformLatest { state ->
            when (state) {
                // Deliberately does NOT write to the cache. Today's code emits
                // LackPermission without touching latestLocation, and wiping it
                // here would cost a Loading emission — and so an empty-feed
                // flash — on every permission flap, which is the regression R1
                // exists to prevent. Consumers already see LackPermission from
                // the StateFlow; the cache is internal and only decides whether
                // Loading is emitted.
                Gate.NoPermission -> emit(LocationResult.LackPermission)

                // Emit nothing: stateIn keeps the last value, so every
                // synchronous .value reader still sees the last known geohash
                // while the OS registration is released.
                Gate.Paused -> Unit

                Gate.Listen -> {
                    // Only when there is nothing cached. Emitting Loading on
                    // every foreground return would flash the "Around Me" feed
                    // empty, because AroundMeFeedFlow.convert maps anything
                    // that is not Success to an empty geotag set.
                    if (latest() !is LocationResult.Success) emit(LocationResult.Loading)

                    emitAll(
                        locationSource(minTimeMs, minDistanceM)
                            .map { LocationResult.Success(it.toGeoHash(charsCount)) as LocationResult }
                            .onEach { setLatest(it) }
                            .catch { e ->
                                Log.w(tag, "Exception in the flow", e)
                                setLatest(LocationResult.LackPermission)
                                emit(LocationResult.LackPermission)
                            },
                    )
                }
            }
        }

    val geohashStateFlow: StateFlow<LocationResult> by lazy {
        geohashFlow(
            tag = "GeohashStateFlow",
            charsCount = GeohashPrecision.KM_5_X_5.digits,
            minTimeMs = COARSE_MIN_TIME,
            minDistanceM = COARSE_MIN_DISTANCE,
            latest = { latestLocation },
            setLatest = { latestLocation = it },
        ).stateIn(scope, SharingStarted.WhileSubscribed(5000), latestLocation)
    }

    /**
     * Like [geohashStateFlow] but at building-level precision
     * ([GeohashChannelLevel.BUILDING] = 8 chars). Location channels truncate this
     * to every coarser level (a geohash is a prefix code), so one fix yields the
     * whole region→building ladder. Kept separate so the coarser
     * [geohashStateFlow] the "around me" feed relies on is unchanged.
     *
     * Note that Amethyst declares only `ACCESS_COARSE_LOCATION`, so Android
     * fuzzes every fix to roughly a 3 km grid and this is not in fact
     * building-level today. The profile is kept so the intent survives if the
     * app ever requests `ACCESS_FINE_LOCATION`.
     */
    val preciseGeohashStateFlow: StateFlow<LocationResult> by lazy {
        geohashFlow(
            tag = "PreciseGeohashStateFlow",
            charsCount = GeohashChannelLevel.BUILDING.chars,
            minTimeMs = PRECISE_MIN_TIME,
            minDistanceM = PRECISE_MIN_DISTANCE,
            latest = { latestPreciseLocation },
            setLatest = { latestPreciseLocation = it },
        ).stateIn(scope, SharingStarted.WhileSubscribed(5000), latestPreciseLocation)
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :amethyst:testPlayDebugUnitTest --tests '*LocationStateTest*' --tests '*LocationFlowTest*'
```

Expected: PASS, 7 + 6 tests. `AppModules.kt` still will not compile — it calls the three-argument `LocationState` constructor. That is Task 6.

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/service/location/LocationState.kt \
        amethyst/src/test/java/com/vitorpamplona/amethyst/service/location/LocationStateTest.kt
git commit -m "feat(amethyst): gate location listening on foreground"
```

`AppModules.kt` lands in Task 6, and the module compiles from there on.

---

### Task 6: Wire it up in AppModules

**Files:**
- Modify: `amethyst/src/main/java/com/vitorpamplona/amethyst/AppModules.kt` — two edits, anchored on declaration text rather than line numbers, because Step 1 shifts everything below it

**Interfaces:**
- Consumes: `RefCountedSession` (Task 2), `LocationState` (Task 5)
- Produces: nothing downstream

- [ ] **Step 1: Add the refcounted session next to the integrator**

Find the line beginning `private val locationSession = SessionTimeIntegrator(resourceUsage, UsageKeys.LOCATION_MS)` (currently line 369) and insert immediately after it:

```kotlin

    // LocationState exposes two independent flows that can both be listening at
    // once (the "Around Me" feed plus an open geohash chat). Refcounting keeps
    // either one stopping from closing the other's segment.
    private val locationRefCount = RefCountedSession(locationSession::setActive)
```

Add the import alongside the other `service.resourceusage` imports:

```kotlin
import com.vitorpamplona.amethyst.service.resourceusage.RefCountedSession
```

- [ ] **Step 2: Pass the foreground signal and the refcount into LocationState**

Find the `val locationManager by lazy` declaration (near line 249, unchanged by Step 1 since that insert was below it) and replace this exact block:

```kotlin
    // App services that should be run as soon as there are subscribers to their flows
    val locationManager by lazy {
        Log.d("AppModules", "LocationManager Init")
        LocationState(appContext, applicationIOScope, onListening = { locationSession.setActive(it) })
    }
```

with:

```kotlin
    // App services that should be run as soon as there are subscribers to their
    // flows. Location additionally releases its OS registration whenever no
    // activity is started — see the foreground gate inside LocationState.
    val locationManager by lazy {
        Log.d("AppModules", "LocationManager Init")
        LocationState(
            appContext,
            applicationIOScope,
            isForeground = foregroundTracker.isForeground,
            onListening = { locationRefCount.setActive(it) },
        )
    }
```

Both `foregroundTracker` (line 333) and `locationRefCount` (added in Step 1) are declared after `locationManager`, which is fine — `locationManager` is `by lazy`, so the references resolve on first access. `locationSession` was already referenced this way.

- [ ] **Step 3: Verify the whole module compiles, tests pass, and lint is clean**

```bash
./gradlew :amethyst:compilePlayDebugKotlin
./gradlew :amethyst:testPlayDebugUnitTest
./gradlew :amethyst:lintPlayDebug
```

Expected: BUILD SUCCESSFUL for all three.

Lint is not optional here. This change names `LocationManager.FUSED_PROVIDER` (API 31) on `minSdk = 26`, and keeps `@SuppressLint("MissingPermission")` on a rewritten method. The ladder's KDoc argues the constant is inlined by the compiler and therefore safe on older runtimes — correct, but an argument, and `NewApi` is exactly the check that settles it. If lint flags `NewApi` on `FUSED_PROVIDER`, replace the constant with the literal `"fused"` and keep the explanatory comment.

If `compilePlayDebugKotlin` reports an unresolved `MIN_TIME` or `MIN_DISTANCE`, a caller was missed — grep for it and fix.

```bash
grep -rn --include='*.kt' "MIN_TIME\|MIN_DISTANCE" amethyst/src commons/src desktopApp/src
```

Expected: only the four `COARSE_*`/`PRECISE_*` constants in `LocationState.kt` and their uses.

- [ ] **Step 4: Commit**

```bash
./gradlew spotlessApply
git add amethyst/src/main/java/com/vitorpamplona/amethyst/AppModules.kt
git commit -m "feat(amethyst): wire the location foreground gate and refcounted meter"
```

---

### Task 7: Verify the acceptance criteria on device

**Files:** none — this is measurement.

**Interfaces:**
- Consumes: the whole change
- Produces: the evidence block for the PR description

- [ ] **Step 1: Install on the Pixel 9a**

```bash
adb devices -l
./gradlew :amethyst:installPlayDebug
```

Expected: one device listed (`model:Pixel_9a`), BUILD SUCCESSFUL.

- [ ] **Step 2: Record the foreground baseline**

Open the app to Home, leave it in the foreground, then:

```bash
adb shell dumpsys location | sed -n '/Location Providers:/,/Historical/p' | grep -c "com.vitorpamplona.amethyst"
adb shell dumpsys location | sed -n '/Location Providers:/,/Historical/p' | grep -A1 "com.vitorpamplona.amethyst"
```

Expected: the count is **1** in the steady state where only the "Around Me" feed is live (at most 2 if a geohash chat is also open — the two flows are independent, which is why the ledger refcounts). Before this change it was 4.

Expected in the request line: `@+60s0ms` and `minUpdateDistance=500.0`. Before this change: `@+10s0ms` and `minUpdateDistance=100.0`.

- [ ] **Step 3: Verify the registration is released when backgrounded**

Press Home, wait more than `BACKGROUND_GRACE_MS` (5 s), then:

```bash
adb shell dumpsys location | sed -n '/Location Providers:/,/Historical/p' | grep -c "com.vitorpamplona.amethyst"
adb shell dumpsys location | grep "com.vitorpamplona.amethyst" | tail -5
```

Expected: the count is **0**, and the last recent-event lines are `-registration` entries timestamped when you pressed Home.

- [ ] **Step 4: Verify the feed does not flash empty**

Foreground the app on Home with the top-nav filter set to "Around Me". Note the geohash shown in the spinner. Press Home, wait 10 s, reopen the app.

Expected: the same geohash is displayed immediately, with no "Loading" state and no empty feed. This is R1; a flash here means the cached-`Success` check is wrong.

- [ ] **Step 5: Check the ledger invariant after a day of use**

Open the in-app Resource Usage Report and compare:

```
location.ms  ≤  app.fgms + 5000 × (number of times the app was backgrounded)
```

Both counters are driven by the same `foregroundTracker.isForeground`, so the grace period is the only expected slack. A violation much larger than that indicts `app.fgms` (Finding 4 of the source analysis suspects it of under-reporting) rather than this change.

- [ ] **Step 6: Record the evidence**

Append the observed numbers from Steps 2, 3 and 5 to the `## Acceptance criteria` section of `amethyst/plans/2026-07-29-location-foreground-gate.md` under a `### Verified` heading, then:

```bash
git add amethyst/plans/2026-07-29-location-foreground-gate.md
git commit -m "docs(amethyst): record on-device verification of the location gate"
```

---

## Self-review notes

Spec coverage: §A gate → Task 5 (R1 cached-`Success` check, R2 `settledForeground`, R3 `Gate.Paused` emitting nothing, R4 `@Volatile`). §B request shape → Tasks 3 and 4. §C meter → Tasks 2, 4 (R5 pairing via throw) and 6 (R6 wiring). H1 → Task 1. Testing → the test steps of Tasks 2–5. Acceptance criteria → Task 7.

Not covered by design, and correctly so: the two `Unavailable`-state and Products-default items are Non-goals; the `GeohashChatScreen.kt:161-163` permission latch is a Follow-up.

Tasks 4 and 5 leave the module temporarily uncompilable. That is a deliberate split — one task spanning `LocationFlow`, `LocationState` and `AppModules` would review far worse — and it costs nothing, because the pre-commit hook is `spotlessCheck` alone. Task 6 restores a green build.
