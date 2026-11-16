# Location: foreground-gate the listener, trim the request, fix the meter

Date: 2026-07-29
Module: `amethyst`
Origin: Finding 1 of `2026-07-29-resource-report-1.13.0-analysis.md` (1.13.0-PLAY,
Pixel 9a / Android 17)
Revision: 2 — incorporates spec review of 2026-07-29

## Context

The 1.13.0 resource report showed **7.13 h of location listening against 9.1
seconds of app foreground**, and the accompanying analysis called it the leading
suspect for that day's 11 pp of background battery drain. Root cause given: 30
`SharingStarted.Eagerly` top-nav filter states on the account scope
(`Account.kt:843-933`), one of which is always `TopFilter.AroundMe` because
`AccountSettings.kt:256` ships that as the Products default. The `AroundMe`
branch collects `locationFlow()`, and an `Eagerly`-shared subscriber holds
`LocationState`'s `WhileSubscribed(5000)` open for the life of the process.

That chain is real. **The battery conclusion drawn from it is not**, and this
spec is written against the measurement rather than the inference.

### What the device reports

`amethyst/src/main/AndroidManifest.xml:80` declares **only**
`ACCESS_COARSE_LOCATION` — no `ACCESS_FINE_LOCATION`, no
`ACCESS_BACKGROUND_LOCATION` — and no service declares
`foregroundServiceType="location"` (the declared types are `mediaPlayback`,
`microphone`, `camera`, `phoneCall`, `shortService`, `dataSync`, `specialUse`).
`targetSdk = 37`.

`adb shell dumpsys location`, Pixel 9a, 21 d 7 h of uptime. **These are the
`com.vitorpamplona.amethyst` rows** of the per-provider *Historical Aggregate
Location Provider Data* block — not system-wide totals:

| provider | registration held | **active** | **foreground** | fixes |
|---|---|---|---|---|
| passive | 9 d 14 h 20 m | 8 h 26 m 14 s | 8 h 14 m 50 s | 107 |
| network | 9 d 14 h 20 m | 8 h 26 m 13 s | 8 h 14 m 49 s | 95 |
| fused | 9 d 14 h 20 m | 8 h 26 m 12 s | 8 h 14 m 48 s | 95 |
| gps | 9 d 14 h 20 m | 8 h 26 m 11 s | 8 h 14 m 47 s | 98 |

Roughly **11 minutes of background-active location in three weeks**, and about
100 delivered fixes per provider. With the app backgrounded at the time of the
dump: `gps provider: service: ProviderRequest[OFF]`, `gps_hardware:
mStarted=false`.

The cleanest assumption-free comparison: the ledger's **two-day** `location.ms`
total (3.57 h + 7.13 h = 10.70 h) **exceeds the OS's three-week active total**
(8 h 26 m) by 27 %. `location.ms` is not measuring what its label implies.

**One figure does not reconcile, and is left open.** Registration-held is
9 d 14 h over 21 d 7 h ≈ 10.8 h/day, whereas `location.ms` averages 5.35 h/day
across the two ledger days. If `location.ms` measured subscription existence
these should agree; they are ~2× apart. Candidates: segments open at process
death are lost (the pre-flush hook does not run on a kill, and the report shows
6 process starts across the two days); the ledger covers 2 days while dumpsys
spans 21 with different usage. Not investigated. It does not affect the
conclusion below, which rests on `location.ms` versus OS-*active* time, not
versus registration-held.

### How far the "no background location" claim generalises

This matters because the "no behaviour change" argument rests on it, so it is
scoped rather than asserted universally:

- **API 29+ (Android 10 and up):** `ACCESS_BACKGROUND_LOCATION` gates background
  access, and for `targetSdk ≥ 29` an FGS additionally needs
  `foregroundServiceType="location"`. Amethyst has neither, so background
  registrations are suspended. This is the case the Pixel 9a measurement above
  covers.
- **API 26–28 (Android 8–9):** `ACCESS_BACKGROUND_LOCATION` does not exist.
  Amethyst *can* receive background location there, throttled by the platform to
  a few updates per hour. The gate is a genuine, if small, improvement on these
  releases rather than a no-op.

So "structural" applies to API 29+; on 26–28 the change has real effect.

### What is actually wrong

1. **The meter lies.** `location.ms` measures how long a *subscription* existed,
   not how long anything listened. That is what made Finding 1 read as the
   top-priority battery bug.
2. **The request is 4× redundant.** `LocationFlow.kt:55` iterates
   `locationManager.allProviders` and calls `requestLocationUpdates` on each —
   passive, network, fused **and** gps (the last tagged `HIGH_ACCURACY`) — every
   one at `@+10s0ms, minUpdateDistance=100.0`, to produce a **5 km** geohash.
   This burns during the 8 h 14 m the app genuinely is foreground.
3. **The subscription is held for 45 % of device uptime** doing nothing, because
   `Eagerly` never lets go.
4. **Every user is exposed**, since Products defaults to `AroundMe` and needs no
   opt-in.
5. **Location may be entirely broken on Android 8–11** — see Hypothesis H1.

## Hypothesis H1 — location is dead below API 31 (unverified)

Through Android 11, AOSP's `getMinimumPermissionForProvider` required
`ACCESS_FINE_LOCATION` for the `gps`, `passive` and `fused` providers; only
`network` accepted `ACCESS_COARSE_LOCATION`. Approximate-location, which lets a
coarse-only app request any provider and receive a fuzzed result, is an Android
12 (API 31) change.

Amethyst holds coarse only. So on API 26–30 today's `allProviders` loop should
throw `SecurityException` on three of the four providers. Two consequences:

- The throw escapes the `callbackFlow` builder → `.catch` in `LocationState` →
  `LackPermission`. Location would be **non-functional** on Android 8–11.
- The builder aborting means `awaitClose` never runs, so `removeUpdates` is
  never called and any registration made before the throw **leaks** for the life
  of the process.

**The leak is iteration-order dependent, and may not occur at all.**
`getLastKnownLocation` is permission-checked per provider too, and
`LocationFlow.kt:56-68` calls it *before* `requestLocationUpdates` on each
iteration. If a fine-only provider comes first in `allProviders` — `passive`
does, in the common AOSP ordering — the throw lands before any registration
exists: dead, but not leaking. A leak requires `network` to precede a fine-only
provider.

`@SuppressLint("MissingPermission")` at `LocationFlow.kt:40` suppresses the lint
warning, not the runtime check, so this would not have been caught statically.

**Not reproduced.** Verify before implementing, on the existing
`Medium_Phone_API_26_8_` AVD: grant coarse only, open a screen that subscribes,
and capture **both** the `SecurityException` *and* the actual
`locationManager.allProviders` order (log it). The PR should claim only what that
run observed — "location is dead on Android 8–11" and "registrations leak" are
separate claims and the second may not hold.

The design below is written to be correct either way (§B excludes
permission-incompatible providers by API level, and catches `SecurityException`
per provider). If H1 holds, this change also **fixes location on Android 8–11**,
which should be called out in the PR.

### H1 verification result (2026-07-30, Pixel 9a, API 37)

Partial. The API-level claim could **not** be tested on this hardware: API 31+
grants coarse-only apps access to every provider, so no `SecurityException` can
appear regardless of whether H1 is true. Only an API ≤ 30 image can settle it.

What *was* settled is the ordering, which decides the leak sub-claim.
`dumpsys location` recent-events shows the same iteration order on every
registration cycle across two days, all four sharing one registration id
(`88A8E679`), confirming a single `LocationFlow` subscription:

```
07-30 07:09:58.282: passive provider +registration .../88A8E679
07-30 07:09:58.291: network provider +registration .../88A8E679
07-30 07:09:58.293: fused  provider +registration .../88A8E679
07-30 07:09:58.299: gps    provider +registration .../88A8E679
```

`allProviders` yields **passive first**, and `passive` is one of the fine-only
providers below API 31. So on Android 8–11 the throw would land on the first
iteration, before any registration exists:

- "location is dead on Android 8–11" — **still unverified**, needs API ≤ 30.
- "registrations leak" — **disproved for this ordering**. Dead, but not leaking.

The PR must not claim the leak. Caveat: the ordering is observed on API 37 and
`getAllProviders()` could order differently on API 26.

**Unrelated but decisive "before" datum, same session:** with
`mWakefulness=Dozing` (screen off, device dozing) and `MainActivity` sitting in
`mLastPausedActivity`, Amethyst held **four** live registrations at
`@+10s0ms / minUpdateDistance=100.0`. That is the state §A's gate exists to
eliminate, captured on the owner's daily-driver device rather than an emulator.

## Goals

- Release the location registration whenever no activity is started.
- Register on one appropriate, permission-compatible provider at an interval
  matched to the precision actually needed.
- Make `location.ms` reflect real listening time, correctly, under concurrency.
- No user-visible behaviour change to the "Around Me" feed or geohash chats.

## Non-goals

- Changing the Products `AroundMe` default (`AccountSettings.kt:256`). With the
  gate in place its cost is bounded to foreground use. Worth revisiting
  separately as a product decision.
- Requesting `ACCESS_FINE_LOCATION` or `ACCESS_BACKGROUND_LOCATION`.
- Findings 2–6 of the source analysis. Finding 2 (the relay reconnect storm,
  1.65 GB/day at a 75 % dial-failure rate) is the more likely explanation for
  the background battery drain and should be taken next.

## Design

### A. The gate

`LocationState` gains an `isForeground: StateFlow<Boolean>` parameter, wired in
`AppModules.kt:251` from the existing `foregroundTracker` (`AppModules.kt:333`,
registered at `Amethyst.kt:122`). `locationManager` is `by lazy`, so
initialisation order is safe.

Today's `hasLocationPermission.transformLatest { … }` becomes a three-state gate
over *permission × foreground*, applied identically to `geohashStateFlow` and
`preciseGeohashStateFlow`:

| gate state | behaviour |
|---|---|
| no permission | emit `LackPermission` (unchanged) |
| permitted, foreground | **R1**: emit `Loading` *only if* no `Success` is cached; then `emitAll(locationSource(…))` |
| permitted, backgrounded | emit nothing; the registration is released and the `StateFlow` retains its last value |

**R1 is a requirement, not an improvement.** `AroundMeFeedFlow.convert` collapses
to `geotags = emptySet()` for anything that is not `Success`. Without R1 the gate
would make the "Around Me" feed flash empty on **every** return to foreground — a
new, frequent, user-visible regression introduced by this change. (It also fixes
the same flash on permission grant, which exists today.)

**R1 corollary: the `NoPermission` branch must not clear the cache.** Today's
code emits `LackPermission` without touching `latestLocation`
(`LocationState.kt:94-96`), and that stays. Clearing it is superficially
attractive — a revoked permission arguably should not leave a fix readable — but
consumers already see `LackPermission` from the `StateFlow`; `latestLocation` is
private and its only jobs are seeding `stateIn` and deciding whether `Loading` is
emitted. Clearing it would therefore buy no privacy and would cost an
empty-feed flash on every permission flap, which is precisely what R1 exists to
prevent. The cache is in-memory and dies with the process regardless.

The `.catch` branch **does** clear the cache, and keeps doing so. That asymmetry
looks arbitrary next to the paragraph above, so to be explicit: it is inherited,
not introduced. Both branches preserve today's behaviour exactly
(`LocationState.kt:87-91` clears on failure, `:94-96` does not clear on missing
permission). This corollary argues against *adding* a clear, not for removing
the existing one — changing it would be an unmotivated behaviour change. The
asymmetry is also defensible on its own terms: a source that failed mid-stream
says something about the fix's provenance, whereas a permission known to be
absent says nothing about a fix already taken.

**R2 — grace period on the background edge.** The gate must delay the
`foreground → background` transition by **5 s** before tearing down. Without it a
one-second app switch destroys and rebuilds the registration, including a full
`getLastKnownLocation` sweep, so a user flipping between apps pays more than the
steady state. 5 s matches the existing `WhileSubscribed(5000)` and is the same
intent. The `background → foreground` edge is **not** delayed.

Mechanism, stated because the obvious operator is the wrong one: `debounce(5000)`
delays both edges, and the duration-selector overload that would allow an
asymmetric delay is `@FlowPreview`. Use `transformLatest`, already in this file
and already opted into via `@OptIn(ExperimentalCoroutinesApi::class)`:

```kotlin
isForeground.transformLatest { fg ->
    if (!fg) delay(BACKGROUND_GRACE_MS)
    emit(fg)
}
```

`transformLatest` cancels the pending `delay` if foreground returns first, which
is exactly the stated semantics, with no preview opt-in.

**R3 — the retained-value contract.** The "emit nothing" branch is what keeps
this behaviour-neutral: `stateIn` holds the last `Success`, so the ~60
synchronous `.value` reads across the feed filters
(`HomeNewThreadFeedFilter.kt`, `VideoFeedFilter.kt`,
`DiscoverLongFormFeedFilter.kt`, …) keep seeing the last known geohash. A 5 km
cell does not meaningfully decay while backgrounded.

**R4 — memory visibility.** `latestLocation` and `latestPreciseLocation`
(`LocationState.kt:63-64`) are plain `var`s today, used only as `stateIn` initial
values. R1 promotes them to control flow, read from a different coroutine than
the `onEach` that writes them. They must become `@Volatile` (or
`MutableStateFlow`).

**Rejected alternative:** switching `FeedTopNavFilterState.flow` from `Eagerly`
to `WhileSubscribed`. Roughly 60 call sites read
`account.live*FollowLists.value` synchronously rather than collecting; under
`WhileSubscribed` those reads would silently serve a stale or initial value
whenever no collector happened to be active. That is a correctness regression,
not a battery fix.

**Rejected alternative:** gating only at the `AppModules` wiring point
(`geolocationFlow = { … }`). Smaller diff, but it leaves the raw
`geohashStateFlow` as a loaded gun for the next eager consumer, does nothing for
`preciseGeohashStateFlow`, and introduces a second `StateFlow` layer over the
same data.

### B. Request shape

`LocationFlow.get` registers on **one** provider, chosen by a ladder over
**provider existence and permission compatibility** — both static facts:

```
chooseProviders(sdkInt, hasFine, exists) -> List<String>:
  API 31+ or hasFine → [FUSED, NETWORK, GPS, PASSIVE] filtered by exists
  API < 31, coarse   → [NETWORK]              filtered by exists   (see H1)
```

It returns the **ordered candidate list**, not a single choice, because the
per-provider `SecurityException` fall-through below needs somewhere to fall to.
An empty list means no compatible provider exists.

**The ladder deliberately does not consult `isProviderEnabled`.** Today's code
registers regardless of enabled state, and such a registration goes live by
itself when the user enables location — including from the quick-settings shade
without leaving the app, which is exactly what someone does after seeing "Around
Me" empty. A guard evaluated once at subscription start would lose that, and the
foreground-transition restart does not cover the in-app path. Selecting on
existence keeps the property with no `PROVIDERS_CHANGED_ACTION` receiver. If
field reports show dead feeds on devices where the chosen provider exists but is
disabled while another is enabled, adding that receiver is the follow-up.

`requestLocationUpdates` is wrapped in a per-provider `SecurityException` catch
that falls through to the next rung, so H1 cannot abort the builder and leak
registrations regardless of how the AOSP check actually behaves.

**When no provider can be registered** — the candidate list was empty, or every
rung threw — `LocationFlow` **throws** `SecurityException`. It cannot emit
`LackPermission`: the seam is `(Long, Float) -> Flow<Location>`, and
`LackPermission` is a `LocationState.LocationResult`, which `LocationFlow` has no
way to express. Throwing routes it through the `.catch` already present in
`LocationState` (`LocationState.kt:87-91`, `:126-130`), which sets
`latestLocation = LackPermission` and emits it — the existing, unchanged path.

Throwing covers **both** failure cases, and it subsumes R5's `registered`-flag
guard: the throw happens before the acquire, so `onListening(true)` cannot fire
without a live registration and no separate flag is needed. That is only half of
R5's pairing, though — see R5 for the release half, which the throw does **not**
cover and which needs `try`/`finally`.

**Stated decision: `LackPermission` stays conflated with "no usable provider".**
That value renders `R.string.lack_location_permissions` — "No Location
Permissions" — at `DisplayLocationObserver.kt:49` and `FeedFilterSpinner.kt:224`,
which is wrong for a coarse-only pre-31 device that has no `network` provider.
The conflation is accepted rather than introduced: if H1 holds, today's
`SecurityException` already lands in the same `.catch` and shows the same wrong
message. Adding an `Unavailable` state would ripple through four UI `when`s plus
`LocationState` (10 references across 5 files) and belongs with the H1 fix
messaging, not here. Recorded as a follow-up.

The `getLastKnownLocation` seed stays a sweep across all providers, taking the
freshest result. It requires no registration and is what makes the first geohash
appear immediately rather than after a fix.

`MIN_TIME` / `MIN_DISTANCE` split into two profiles, passed per call:

| flow | precision | interval / distance | provider set |
|---|---|---|---|
| `geohashStateFlow` | `KM_5_X_5` | 10 s / 100 m → **60 s / 500 m** | 4 → 1 |
| `preciseGeohashStateFlow` | `BUILDING` (8 chars) | 10 s / 100 m (kept) | 4 → 1 |

Both rows change: the ladder narrows the precise flow's provider set too, and
below API 31 that means `network` only, no GPS. Academic while the app holds
coarse only (see Follow-ups), but it is not "unchanged".

At 120 km/h a 5 km cell takes 2.5 minutes to cross, so 60 s / 500 m has no
observable effect on the feed.

**Rejected alternative — one shared source at the fine profile,** deriving the
coarse geohash by prefix truncation. It halves registrations and removes the need
for `RefCountedSession` entirely, but it upgrades the **common** case — the
"Around Me" feed alone, which is always on via the Products default — from
60 s/500 m to 10 s/100 m. That trades the change's main win for a rarer one.

**Rejected alternative — one shared source whose profile tracks the finest
active subscriber.** Recovers the above and is the best of the three on both
axes, but it is refcounting with the counter moved from the meter into the
request path, for a benefit bounded by how often the two flows overlap. They
overlap only while one of three composable-scoped, foreground-only screens is
open (`GeohashChatScreen`, `NewGeohashChatScreen`,
`GeohashLocationPickerDialog`). Not worth the machinery; revisit if that changes.

### C. The meter

`AppModules.kt:251` hands both flows the same non-refcounted
`SessionTimeIntegrator`, so `setActive(false)` from either closes the segment
while the other is still listening. Both can be live at once — the "Around Me"
feed plus an open geohash chat.

**R5 — the hook moves inside `LocationFlow`, and both edges are paired.** Today
`onListening(true)` is an `onStart` on the flow returned by `LocationFlow.get`,
so it fires on *collection* whether or not anything was registered — meaning a
device with no usable provider accrues `location.ms` with nothing listening,
reintroducing the exact defect this section exists to fix. The hook must instead
fire from inside the `callbackFlow`, after `requestLocationUpdates` returns
without throwing, and again on the way out.

The obvious "way out" is `awaitClose`, and that would introduce a worse bug than
it fixes — twice over. First, `awaitClose` runs on every normal
completion, including one where no rung ever registered, so it would fire an
**unpaired** `onListening(false)`. With R6 that does not merely under-count — it
decrements a holder it never acquired, stealing another flow's. Concretely:
`geohashStateFlow` registers (`holders = 1`), `preciseGeohashStateFlow` fails to
register and closes (`holders = 0`), and the session latches off while the coarse
flow is still listening. `coerceAtLeast(0)` does not help; the count never went
negative.

Second — and this is the one that survives fixing the first — `awaitClose` also
fails to run at all on some paths that *did* register. See below.

The pair therefore has to be guaranteed from **both** ends, and the two ends need
different mechanisms.

*No acquire without a registration* is §B's **throw**: if no rung registers, the
builder throws before reaching the acquire at all.

*No acquire without a release* needs `try`/`finally`, **not** `awaitClose`. This
is the subtlest point in the document, so the justification below is the one that
was **demonstrated**, not the one that sounds most obvious.

Anything between the acquire and `awaitClose` that unwinds skips cleanup parked
inside `awaitClose`, because `awaitClose` is never reached to register it. The
registration then leaks and the refcount sticks at ≥ 1 for the life of the
process, so `location.ms` accrues forever with nothing listening — this exact
defect, arrived at from the other direction, and unrecoverable once hit.

The **proven** path is the seed throwing a non-cancellation exception:
`getLastKnownLocation` is a binder call and can fail. The regression test
`releasesTheRegistrationWhenTheSeedThrows` provokes exactly this and was watched
failing against an `awaitClose`-only implementation
(`expected:<[true, false]> but was:<[true]>`).

A cancellation during the seed is *in principle* a second such path, since `send`
is a suspending call. Recorded honestly: **this one could not be reproduced.**
Two attempts during implementation both produced tests that passed against a
deliberately broken implementation, because `callbackFlow`'s channel is buffered,
so `send` returns without suspending and never observes the cancel. Do not treat
the cancellation story as the reason for the `try`/`finally`; a future reader who
tries to reproduce it, fails, and concludes the guard is unnecessary would
reintroduce the leak.

```kotlin
var registered: String? = null
for (provider in candidates) {
    try {
        locationManager.requestLocationUpdates(provider, minTimeMs, minDistanceM, callback, Looper.getMainLooper())
        registered = provider
        break
    } catch (e: SecurityException) { /* next rung */ }
}
if (registered == null) throw SecurityException("no usable location provider")

onListening?.invoke(true)                       // cannot fire without a registration
try {
    freshestLastKnownLocation(providers)?.let { send(it) }   // suspends — cancellable
    awaitClose { }                              // only to satisfy callbackFlow's contract
} finally {
    locationManager.removeUpdates(callback)
    onListening?.invoke(false)                  // cannot be skipped
}
```

`onListening?.invoke(true)` sits immediately before the `try`, with no suspension
between them, so the acquire cannot happen outside the block that guarantees its
release.

`trySend` for the seed would also close this particular hole, being
non-suspending. It is rejected because it leaves the invariant resting on nobody
adding a suspending call to that block later — vigilance rather than
impossibility, which is the standard the rest of R5 is held to.

**R6 — refcounting.** An `AtomicInteger` beside the `setActive` call is not
sufficient: two threads can leave the counter at 1 while the last
`setActive(false)` lands after the `setActive(true)`, latching the session off.
The count and the transition must move under one lock. New class in
`service/resourceusage/`:

```kotlin
class RefCountedSession(private val setSessionActive: (Boolean) -> Unit) {
    private val lock = Any()
    private var holders = 0

    fun setActive(active: Boolean) =
        synchronized(lock) {
            holders = if (active) holders + 1 else (holders - 1).coerceAtLeast(0)
            setSessionActive(holders > 0)
        }
}
```

It takes the setter as a lambda rather than a `SessionTimeIntegrator` because
that is all it needs, and because constructing a real integrator in a unit test
would drag in a `ResourceUsageAccountant`, a `ResourceUsageStore` and a temp
file to observe one boolean.

`AppModules` wires `RefCountedSession(locationSession::setActive)` and passes
`onListening = { locationRefCount.setActive(it) }`. The outer
lock serialises entry into `SessionTimeIntegrator.setActive`, whose own lock is
then nested but never acquired in the reverse order, so there is no deadlock.
`coerceAtLeast(0)` guards an unmatched release.

### What `location.ms` means after this change

Stated plainly, because the finding that opened this spec is "the meter lies"
and the next reader should not over-trust the fixed number the way the last one
over-trusted the broken one:

> `location.ms` measures **how long a location registration was held while the
> app was in the foreground**. It is not radio-on time and not an energy
> figure. A `network`-provider registration at 60 s costs close to nothing; a
> `gps` registration at 10 s costs a great deal. The counter cannot tell them
> apart.

Reading it as a battery signal requires knowing which provider was chosen —
which the ledger does not record. Recording the chosen provider as a separate
counter is a possible follow-up.

## Testing

JVM unit tests — JUnit + MockK + `kotlinx-coroutines-test`, no Robolectric,
alongside the existing `service/resourceusage/ResourceUsageLedgerTest.kt`.

**Gate.** `LocationState` gains `locationSource: (Long, Float) -> Flow<Location>`,
defaulting to `LocationFlow(context.getSystemService(…) as LocationManager)::get`
(see *Registration pairing* for why `LocationFlow` now takes the manager rather
than the `Context`). That is the seam:
`Location.toGeoHash` is `GeoHash.encode(lat, lon, chars)` from quartz — pure
Kotlin — and `unitTests.isReturnDefaultValues = true` is already set, so a
`mockk<Location>` with stubbed `latitude`/`longitude` suffices. Against a
counting fake source:

- backgrounded + permitted → source never subscribed
- foreground + permitted → subscribed exactly once
- foreground → background → subscription released after the R2 grace period,
  last `Success` still readable via `.value`
- background edge shorter than the grace period → subscription **not** torn down
- return to foreground with a cached `Success` → **no** `Loading` emission (R1)
- return to foreground with no cached fix → `Loading` first
- permission revoked → `LackPermission` regardless of foreground state

**Provider ladder.** Extracted as a pure function
`chooseProviders(sdkInt: Int, hasFine: Boolean, exists: (String) -> Boolean):
List<String>` so §B is covered rather than sitting below the seam. Cases: rungs
returned in order; missing rungs filtered out; API < 31 coarse-only yields
`[network]`; API < 31 with fine yields the full ladder; no compatible provider
yields an empty list.

`hasFine` is **always `false` in production** — the non-goals rule out ever
requesting `ACCESS_FINE_LOCATION`. It is a parameter rather than a constant so
that the function is total over the permission axis and the API < 31 branch can
be tested from both sides, not because fine access is anticipated. If that
changes, the ladder is already correct.

R5 sits below the `locationSource` seam, so a fake source never fires it. It gets
its own tests against a mocked `LocationManager` — see *Registration pairing*
below.

**Meter.** `RefCountedSession`: overlapping holders keep the session open;
balanced pairs close it; an unmatched release does not drive the count negative.

Note what this class **cannot** do: it cannot distinguish an unpaired release
from a legitimate one, so `acquire → unpaired release` closes the session even
while another holder is listening. That is precisely the R5 bug, and
`coerceAtLeast(0)` is no defence against it. **The pairing guarantee belongs to
`LocationFlow`, not here** — which is why it needs its own test below.

**Registration pairing (R5).** To make this testable rather than device-only,
`LocationFlow` takes a `LocationManager` instead of a `Context`
(`LocationFlow(context)` → `LocationFlow(locationManager)`; the caller in
`LocationState` does the `getSystemService` lookup). A `mockk<LocationManager>`
then covers:

- every rung throws `SecurityException` → the flow throws and `onListening` fires
  **neither** edge
- `chooseProviders` returns an empty list → same: throws, neither edge
- an earlier rung throws and a later one succeeds → registration falls through,
  exactly one `true`
- a successful registration → exactly one `true`, and exactly one `false` plus
  `removeUpdates` on cancellation
- **cancellation mid-seed**, while the `getLastKnownLocation` sweep is in flight
  → both edges still fire and `removeUpdates` is still called. This is the case
  the `try`/`finally` exists for; without it the test fails by hanging the
  refcount at 1 rather than by throwing, so assert on the edges, not on the
  absence of an exception.
These cover the *semantics* only — the two-thread interleaving that motivates the
lock is made unobservable by the lock itself and is not reproduced by any test
here.

## Acceptance criteria

On device, re-running the measurement above:

- **Backgrounded:** after the 5 s grace period, `adb shell dumpsys location`
  shows no `com.vitorpamplona.amethyst` entry under any provider's `listeners:`,
  and a `-registration` in the recent-events log.
- **Foregrounded:** **one registration per actively-collected flow — at most
  two**, and one in the steady state where only the "Around Me" feed is live
  (§C exists precisely because the two flows may overlap). Not four. The coarse
  registration reads `@+60s0ms` / `minUpdateDistance=500.0` rather than
  `@+10s0ms` / `100.0`.
- The historical aggregates are cumulative since boot; compare deltas across a
  foreground/background cycle, not absolute totals.
- **Invariant:** a subsequent in-app Resource Usage Report shows

  ```
  location.ms  ≤  app.fgms + 5 s × (background transitions)
  ```

  Both counters are driven by the same `foregroundTracker.isForeground` flow, so
  without R2 this would hold exactly. R2 is deliberately the error term: the
  registration really *is* live during the grace period, so counting it is the
  honest reading, and a stated fudge factor beats an invariant quietly known to
  be false. The term is not negligible — the source report shows 6 process
  starts across two days, and app switches are far more frequent than that — so
  writing `location.ms ≤ app.fgms` would guarantee that the first person to
  check it files a bug against this change.

  Second caveat: Finding 4 of the source analysis suspects `app.fgms` of
  under-reporting, so a violation beyond the grace term indicts that counter
  rather than this one.
- If H1 holds: location works on the API 26 AVD after the change and did not
  before.

### Verified on device (2026-07-30, Pixel 9a / Android 17, API 37)

Measured against the `benchmark` variant — `initWith(release)`, so R8-minified with
the shipping proguard rules, installed as `com.vitorpamplona.amethyst.benchmark`
beside the untouched Play install. The Play install was force-stopped for the
duration so its own (unfixed) registrations could not be mistaken for these.

| criterion | before | after |
|---|---|---|
| registrations, foreground | **4** (passive, network, fused, gps) | **1** (fused) |
| request profile | `@+10s0ms HIGH_ACCURACY`, `minUpdateDistance=100.0` | `@+1m0s0ms BALANCED`, `minUpdateDistance=500.0` |
| registrations, backgrounded | **4**, held while `mWakefulness=Dozing` | **0** |

Event trace for one full cycle, process alive throughout (pid 28682):

```
17:57:00.117  +registration fused …/40F5A6D7  @+1m0s0ms BALANCED, minUpdateDistance=500.0
17:57:33.894  -registration fused …/40F5A6D7          ← HOME pressed, released after the grace
17:58:05.798  +registration fused …/091EDA96  @+1m0s0ms BALANCED, minUpdateDistance=500.0
              (HOME then reopen within 2 s — no -/+ pair; 091EDA96 survives)
```

- **§A gate** — zero registrations while backgrounded, with the process still
  alive. That is the state the change exists to create; before, four
  registrations survived screen-off and doze.
- **§B request shape** — one provider, top of the ladder (`fused`), at exactly
  `COARSE_MIN_TIME` / `COARSE_MIN_DISTANCE`. The OS tags it `(COARSE)` and
  coalesces the effective service request to `@+10m0s0ms LOW_POWER`.
- **R2 grace period** — a sub-grace app switch produced **no** teardown/rebuild
  pair, so a brief switch no longer costs a re-registration and a fresh
  `getLastKnownLocation` sweep.

**The OS aggregate after four foreground/background cycles is the headline
result**, because it is the same counter shape `location.ms` measures:

```
com.vitorpamplona.amethyst.benchmark:
  min/max interval = 60s/60s
  total/active/foreground duration = +2m33s542ms / +2m33s456ms / +2m33s531ms
  locations = 4
```

Total ≈ active ≈ foreground, all three within 90 ms — against the Play install's
`9d14h20m / 8h26m / 8h14m`, where registration was held for 45 % of uptime while
only 1.7 % was active. The four foreground windows sum to 153.6 s, matching the
aggregate exactly, so nothing is held outside them. Registration-held time now
*equals* foreground time, which is precisely what makes `location.ms` honest: the
counter measures registration lifetime, and that quantity is no longer divorced
from reality.

**A fix arrives within milliseconds of every re-registration**, which bounds the
staleness the coarser profile was feared to introduce:

```
17:57:00.117 +registration → 17:57:00.127 delivered location[1]   (10 ms)
17:58:05.798 +registration → 17:58:05.802 delivered location[1]   ( 4 ms)
17:59:09.965 +registration → 17:59:09.967 delivered location[1]   ( 2 ms)
18:00:09.206 +registration → 18:00:09.213 delivered location[1]   ( 7 ms)
```

The `fused` provider hands over its cached fix on registration, so the window in
which a returning user could act on a stale geohash is milliseconds, not the 60 s
poll interval. Caveat: that cache is warm on this device because Maps and GMS
keep it fresh; on a device with no other location consumer it could be colder,
which is what `freshestLastKnownLocation` exists to cover.

**Side-by-side A/B, same device, same instant, both clients backgrounded and
running.** The unmodified release client (1.13.1, installed via Obtainium, pid
5552) and the benchmark build of this branch (pid 28682) were sampled together:

```
com.vitorpamplona.amethyst/B7B299BE  {bg, na} (COARSE) Request[PASSIVE,     minUpdateDistance=100.0]  (inactive)
com.vitorpamplona.amethyst/B7B299BE  {bg, na} (COARSE) Request[@+10m LOW_POWER, minUpdateDistance=100.0]  (inactive)
com.vitorpamplona.amethyst/B7B299BE  {bg, na} (COARSE) Request[@+10m LOW_POWER, minUpdateDistance=100.0]  (inactive)
com.vitorpamplona.amethyst/B7B299BE  {bg, na} (COARSE) Request[@+10m LOW_POWER, minUpdateDistance=100.0]  (inactive)
                                     ← com.vitorpamplona.amethyst.benchmark: no rows at all
```

Four held registrations versus zero. Note the release client's rows are all
`{bg, na} … (inactive)`: the OS has throttled the effective interval to 10
minutes and suspended delivery, exactly as §"What the device reports" describes —
but the **registration is still held**, and registration-held time is precisely
what `location.ms` counts. That is the inflation, visible in one frame.

Naming note for anyone re-reading the numbers above: both artifacts are `play`
**flavor** builds and differ only by buildType, so "the Play install" is an
ambiguous label. The unmodified client here is the *release* build, and on this
device it came from Obtainium rather than Google Play.

### Ledger invariant confirmed (2026-07-31, benchmark client, in-app report)

The acceptance criterion `location.ms ≤ app.fgms + 5 s × transitions` now checks
out against accumulated data:

| | `location.ms` | `app.fgms` | ratio |
|---|---|---|---|
| release client 1.13.0, day 20663 (before) | 25,660,172 | 9,147 | **2,805×** |
| benchmark, day 20664 (permission granted mid-day) | 3m7.3s | 11m31.0s | 0.27× |
| benchmark, **day 20665** (granted all day) | **2m10.8s** | **1m56.9s** | **1.12×** |

Day 20665 is the clean case: `location.ms` exceeds `app.fgms` by 13.9 s, which
requires ≥ 3 background transitions to fall inside the grace allowance — met by
the report navigation plus an `am start`. Day 20664 independently reconciles with
the `dumpsys` measurement: 2m33.5s of OS registration-held + 4 × 5 s grace =
~2m53.5s predicted, 3m7.3s actual, the residual being foreground use after the
measurement ended. **The ledger and the OS now agree**, where before they were
irreconcilable (10.7 h ledger vs 8h26m OS-active over three weeks).

**Limitation:** this is not a within-package before/after. `location.ms` is
absent from days 20648–20663 because the benchmark client had location permission
*denied* until 2026-07-30; the "before" is no data, not inflated data.

### The same data closes the battery question

Over days 20659–20665 on this device: **5m18s** of location listening against
**596 pp** of background battery drain (~85 pp/day). Location cannot be a
meaningful contributor at that ratio — Finding 1 is settled, and not in the
direction the original analysis assumed.

Three consumers visible in the same report, none of them location:

- `service.alwayson.ms` ≈ **23.9 h/day** (148.9 h over 7 days) — an always-on
  foreground service running essentially continuously. Largest structural
  difference from a stock client; worth confirming it is deliberately enabled.
- **Finding 2, unchanged.** 3,732 relay-hours over 7 days. Day 20664 alone:
  9,831 successful dials against 25,133 failures = **71.9 %**, matching the
  original report's 75 %.
- **Finding 4, now on cellular.** Day 20664 `net.other.mobile.bg.activems =
  52,392,613` — **14.6 h** of background mobile active time with **0 requests and
  0 bytes**. The three-moment `isForeground()` sampling, exactly as diagnosed, so
  the fg/bg split in this report still cannot be trusted.

Caveat: the benchmark client's round-the-clock always-on service makes its
battery figures non-comparable to a stock install. The relay and `net.other`
figures do match the release client's original report closely.

### Smoke test on a clean install (2026-07-31, benchmark client)

Uninstalled and reinstalled from branch HEAD so the account, ledger and
permission grant all started empty — which is what makes the first-grant and
empty-cache paths reachable. UID changed 10805 → 10806, cleanly separating the
new data.

- **R1 — no `Loading` flash on return to foreground: PASS.** Observed directly:
  granted the permission, set a feed to Around Me, backgrounded for 10 s,
  reopened — the geohash was present immediately with no blank feed. This was the
  last open acceptance criterion on the branch and the only one no test could
  cover. `dumpsys` shows why it works: the fix is delivered 1–6 ms after each
  re-registration.
- **Precise profile live and distinct: PASS.** Geohash screens produce
  `@+10s0ms BALANCED, minUpdateDistance=100.0`, and the aggregate's `min/max
  interval` moved from `60s/60s` to `10s/60s`. Both profiles are real in
  production, not just in the unit tests.
- **Refcount under concurrent flows: PASS.** The coarse registration `766C58A4`
  came up at 15:49:10 and survived **four complete precise-flow cycles**
  untouched (15:49:41–46, 15:50:47–52, 15:52:07–15:53:05, 15:53:17–22), with
  both live simultaneously during the first. Opening and closing geohash chats
  never closes the "Around Me" registration — `RefCountedSession` doing on a real
  device what `LocationLedgerCompositionTest` asserts.
- **No hang in the location picker.** Every precise registration received a fix
  within ~1 ms; none stuck open. That path does
  `preciseGeohashStateFlow.first { it is Success }`, which would hang rather than
  crash if the gate failed to yield.
- **Aggregate:** total 6m7.553s / active 6m7.401s (152 ms apart) / foreground
  5m49.441s. Foreground trails total by 18.1 s across ~7 background transitions,
  ≈ 2.6 s each — the grace period, visible at a scale where it is easy to check.

**Measurement note:** counting listeners with `grep -c` on the package name is
unreliable — the `service: ProviderRequest[…]` summary line also names the
package when it is the only requester, inflating the count by one. List the rows
and exclude that line instead. A count of zero is unambiguous either way.

Still not verified: nothing on the gate itself. The `location.ms ≤ app.fgms +
5 s × transitions` invariant was separately confirmed from accumulated ledger
data (see above); the clean install reset that counter, so it will need another
day of use to re-check at scale.

## Follow-ups (not in this change)

- **`preciseGeohashStateFlow` is not actually building-level.** With only
  `ACCESS_COARSE_LOCATION`, Android fuzzes every fix to roughly a 3 km grid, so
  the 8-char geohash and the location chat channels built on it are far coarser
  than they claim (`LocationState.kt:104-141`, `GeohashChatScreen.kt:165`,
  `NewGeohashChatScreen.kt:309`). The profile is kept intact here so the intent
  survives if the app ever requests `ACCESS_FINE_LOCATION`; whether to request
  it, or to stop advertising building-level precision, is a separate decision.
- **`GeohashChatScreen.kt:161-163` is a one-way permission latch** — it calls
  `setLocationPermission(true)` inside an `if (isGranted)` rather than passing
  the boolean, as every other caller does (`LoggedInPage.kt:144`,
  `LocationAsHash.kt:64`, `NewGeohashChatScreen.kt:285`,
  `GeohashLocationPickerDialog.kt:270`). Once set, a revoked permission is never
  reflected back into the shared `LocationState`. Small, in the blast radius,
  and cheap.
- **An `Unavailable` `LocationResult`**, distinct from `LackPermission`, so a
  device with no usable provider stops being told "No Location Permissions" when
  it has them. Ten references across five files
  (`DisplayLocationObserver.kt`, `FeedFilterSpinner.kt`, `HomeScreen.kt`,
  `NewGeohashChatScreen.kt`, `LocationState.kt`). Belongs with the H1 fix
  messaging — see the stated decision in §B.
- Recording the chosen provider as a ledger counter, so `location.ms` can be
  read as a cost signal.
- Whether Products should still default to `TopFilter.AroundMe`.
- Finding 2 — the relay reconnect storm.
