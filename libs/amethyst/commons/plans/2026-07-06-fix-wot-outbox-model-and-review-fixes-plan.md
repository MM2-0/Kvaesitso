---
title: WoT fetch via outbox model + PR #3483 review fixes
type: fix
status: completed
date: 2026-07-06
origin: PR https://github.com/vitorpamplona/amethyst/pull/3483 review comments (Vitor Pamplona, davotoula)
---

# WoT fetch via outbox model + PR #3483 review fixes

## Overview

PR #3483 (branch `feat/wot-shared-index-relays`) adds Web-of-Trust badges + shared
Index Relays + `amy wot` verbs. Two reviewers flagged issues:

- **Vitor** (owner): stop broadcasting kind-0/kind-3 REQs to a static index-relay
  list. Use the outbox model: index relays discover each author's kind-10002,
  then kind-0/kind-3 REQs go to each author's declared write relays.
- **davotoula**: six correctness / perf / lifecycle bugs across
  `DesktopLocalCache`, `WoTService`, and `FeedMetadataCoordinator` — some
  Desktop-scoped, most in `commons/commonMain` so Android inherits them the
  moment WoT gets wired there.

This plan lands **both** in a single PR revision:

- The outbox-model refactor for kind-0 / kind-3 fetching (Vitor's ask).
- All six correctness/perf/lifecycle fixes (davotoula's ask).
- A sweep confirming no production default references the dying
  `relay.damus.io`.

The scope is intentionally larger than a normal review-fix cycle because the
outbox refactor changes the same seams the bug-fixes touch — separating them
would produce a churny diff.

## Problem Statement

### 1. Index-relay broadcast is architecturally wrong for kind-0 / kind-3

Current flow (this branch):

```
Login (~350 follows) ─▶ Main.kt:1315
                          └── FeedMetadataCoordinator.loadKind3Batched(follows)
                              └── REQ kinds=[3] authors=[follows chunked/100]
                                  to *every* index relay in
                                  PreferencesIndexRelays.effective()
```

Semantics:

- Every kind-3 event is fetched from index relays whether or not the author
  publishes there.
- Users who *only* publish to their own outbox (increasingly common on modern
  Nostr) return no kind-3 — WoT signal is wrong (undercount).
- Index-relay operators absorb the entire follow set's worth of REQ authors,
  even when other relays hold the data.
- The same anti-pattern exists for kind-0 profile metadata (via
  `loadMetadataBatched`) and inside `amy wot sync` (which reimplements the same
  broadcast in `WotCommand.sync`).

Vitor's directive (quoting review):

> Kind 0 and 3 must be downloaded from the outbox relay (10002, write) of each
> user. Basically, find all 10002 events via index relays (purple pages, etc),
> then parse them all to find a list of relays per author, invert the map to
> get a list of authors per relay, then use that list to download posts, kind
> 0 and contact lists from each author.

### 2. Six correctness / perf / lifecycle bugs

| # | File:line | Symptom | Severity |
|---|-----------|---------|----------|
| 1 | `DesktopLocalCache.kt:509-530` | `accountPubkey` race → self kind-3 stamped `lastContactListByAuthor` before self-check; later relay retry rejected by `createdAt <= prev`; empty follow view; `FollowAction.follow` calls `createFromScratch(...)` and **wipes real follow list** | **P0 (data loss)** |
| 2 | `commons/wot/WoTService.kt:162-190` | `handleFollowSet` sets `myFollows` before the `MAX_FOLLOWS` guard, guard doesn't return `myFollows` to empty, and `Main.kt:1561` still calls `loadKind3Batched` when over the cap — CPU/memory blow-up the PR description promised was skipped | P1 (perf regression on mega-follow accounts) |
| 3 | `commons/wot/WoTService.kt` | No `close()/dispose()` → writer coroutine + `Channel` leak on account switch; leaks compound over long sessions | P1 (leak) |
| 4 | `commons/wot/WoTService.kt:37-49, 149-160` | Doc claims "per-key subscriber isolation via `Snapshot.withMutableSnapshot`" — that's a mis-attribution. Per-key isolation is a `SnapshotStateMap` property, not a `withMutableSnapshot` property; the `withMutableSnapshot` on *every* op just batches writes. Any consumer that reads the map iteratively (size, keys) *will* invalidate on every mutation, which the comment claims won't happen. Future Android integrator will trust the comment. | P2 (misleading docs → landmine) |
| 5 | `commons/relayClient/assemblers/FeedMetadataCoordinator.kt:320-368` | `queuedKind3Pubkeys` marks pubkeys sent, never reset on failure. If every index relay times out (mobile flake / cold-start), WoT stays empty for the entire session; `loadKind3Batched` will short-circuit thereafter. | P1 (silent WoT-empty session) |
| 6 | `commons/relayClient/assemblers/FeedMetadataCoordinator.kt:274, 338` | `eoseReceived: MutableSet` written from per-relay `Dispatchers.IO` `onEose` callbacks with no sync → race can drop an EOSE, blocking on the full 5 s timeout instead of firing early. Low ceiling but pre-existing pattern that this PR duplicates. | P2 (perf / responsiveness) |

Additional owner-flagged item:
- `relay.damus.io` shutting down end of month. Confirmed: no production
  default on this branch references it. Only commonTest fixtures do — leave
  those alone (they're wire-format fixtures, not runtime relay lists).

## Proposed Solution

### Outbox refactor: two-phase discovery

Replace the single "broadcast a kind-3 REQ to all index relays" flow with a
two-phase pipeline that reuses existing Quartz infrastructure. The pipeline
lives in `commons/commonMain` so **Desktop, Android (future), and `amy`** all
share it.

```
             ┌────────────────────────────────────────────────────┐
             │ Phase 1 — kind-10002 discovery (index-relay REQ)   │
             │   inputs: pubkeys[], indexRelays[]                 │
             │   emits:  Map<HexKey, Set<NormalizedRelayUrl>>     │
             │           (author → declared write relays)         │
             │                                                    │
             │   • REQ kinds=[10002] authors=chunked-by-100        │
             │     to every index relay.                          │
             │   • Feed matching AdvertisedRelayListEvent into    │
             │     LocalCache (so future lookups skip the REQ).   │
             │   • Per-relay timeout (default 4s), NOT one global.│
             └────────────────────────────────────────────────────┘
                                    │
                                    ▼
             ┌────────────────────────────────────────────────────┐
             │ Phase 2a — RelayListRecommendationProcessor        │
             │   inputs: authorMap from Phase 1                   │
             │   emits:  Set<RelayRecommendation>                 │
             │           (relay → author set, minimal cover)      │
             │                                                    │
             │   Reuses Quartz's existing algorithm which:        │
             │   • builds relay → author set (transpose)          │
             │   • greedily picks most-popular relay, removes     │
             │     covered authors, repeats                       │
             │   • second pass to ensure ≥2-relay coverage per    │
             │     author                                         │
             │   • filters onion/localhost per config             │
             └────────────────────────────────────────────────────┘
                                    │
                                    ▼
             ┌────────────────────────────────────────────────────┐
             │ Phase 2b — per-relay kind 0 + kind 3 REQ           │
             │   For each RelayRecommendation:                    │
             │     REQ kinds=[0,3] authors=[recommendation.users] │
             │     with per-relay timeout, single subscription.   │
             │   Events flow into LocalCache via existing         │
             │   consume path.                                    │
             └────────────────────────────────────────────────────┘
                                    │
                                    ▼
             ┌────────────────────────────────────────────────────┐
             │ Phase 3 — Fallback for authors without 10002       │
             │   Authors in the input set that never returned a   │
             │   10002 fall back to the current index-relay flow  │
             │   (REQ kinds=[0,3] authors=[fallbackSet] on index  │
             │   relays). Bounded; only fires when non-empty.     │
             └────────────────────────────────────────────────────┘
                                    │
                                    ▼
                       onEose() → WoTService.markReadyOnce()
```

Global 2 s startup fallback in `Main.kt` stays as the outermost safety net.

### Bug fixes (correctness first, always)

**Fix 1 — `DesktopLocalCache` accountPubkey race.** Make `accountPubkey`
either a constructor parameter or a required init that must resolve *before*
hydration starts. Reorder `Main.kt` so `localCache.accountPubkey =
account.pubKeyHex` runs before `localRelayStore.hydrate(localCache)`. Belt +
braces: inside `consumeContactList`, do not stamp `lastContactListByAuthor`
for events where `event.pubKey == accountPubkey` unless the self path
actually accepted the event. This eliminates the "poisoned stamp" for the
future relay retry even if a caller ever forgets to bind pubkey first.

**Fix 2 — MAX_FOLLOWS guard bypass.** Two-part fix:
- In `WoTService.handleFollowSet`, when the follow set exceeds `MAX_FOLLOWS`,
  set `myFollows = emptySet()` *and* flip a `disabled: Boolean` flag. Both
  `handleKind3` and every future op must early-return on `disabled`.
- In the outbox driver's entrypoint (formerly `Main.kt:1561`), consult
  `WoTService.isDisabled` (new StateFlow) or `follows.size <=
  WoTService.MAX_FOLLOWS` before dispatching Phase 1. When over the cap:
  skip Phase 1 + 2 entirely and call `markReadyOnce()` immediately.

**Fix 3 — `WoTService.close()`.** Add:

```kotlin
private val supervisor = SupervisorJob(scope.coroutineContext[Job])
private val serviceScope = CoroutineScope(scope.coroutineContext + supervisor + writerDispatcher)

fun close() {
    ops.close()
    supervisor.cancel()
}
```

Call from account-switch (Main.kt clear path) and from `DesktopIAccount`
disposal. Add an internal `AutoCloseable` implement so callers can lean on
`use { }`.

**Fix 4 — Correct the misleading comments.** Rewrite `WoTService` KDoc to say:

> Scores are exposed via a Compose-observable `SnapshotStateMap`. Consumers
> that read a *specific key* (`scores[pubkey]`) recompose only when that key
> changes — this is `SnapshotStateMap`'s per-key observation. Consumers that
> iterate the map or read its size will recompose on any mutation.
>
> Ops are serialized through a single-writer `Channel`. Coalescing writes
> inside `Snapshot.withMutableSnapshot { }` batches state commits so a
> multi-key op emits a single Compose invalidation instead of one per key.

No behaviour change; the comment is the fix.

**Fix 5 — `queuedKind3Pubkeys` retryable.** Convert the current mark-on-send
set into mark-on-EOSE:
- Track `inFlight: MutableSet<HexKey>` for de-duplication during a single call.
- On successful EOSE (or per-relay EOSE), move pubkeys into `succeeded`
  (unchanged behaviour: skip future REQs).
- On global timeout with zero events for a pubkey, **do not** promote to
  `succeeded`; keep them retryable on the next `loadKind3Batched` /
  `loadKind3ViaOutbox` call.
- Cheap: same `Set` mechanics, just gated by outcome instead of intent.

**Fix 6 — Synchronise `eoseReceived`.** Two options; pick (b):
- (a) Wrap in `Mutex` / `synchronized` — `synchronized` needs a JVM-only
  path or an `expect/actual`.
- (b) **Use a single-writer coroutine**: replace the `MutableSet` +
  `CompletableDeferred<Unit>` handshake with a `Channel<NormalizedRelayUrl>(
  capacity = Channel.UNLIMITED)` + a launched consumer that increments a
  local counter and completes the deferred when it hits `indexRelays.size`.
  Same shape, zero shared mutable state across dispatchers. KMP-clean.

Apply the same fix to both `loadKind3Batched` and `loadMetadataBatched`
because the pattern is duplicated.

### Damus sweep

Grep of the current branch found `relay.damus.io` only in test fixtures
(`FeedDefinitionSerializerTest`, `TorRelayEvaluationTest`, `RichTextParserTest`,
`ZapSplitResolverTest`). None are production defaults. `DEFAULT_INDEX_RELAYS`
= `{nos.lol, nostr.wine, noswhere, primal.net}`;
`AmethystDefaults.DefaultIndexerRelayList` = `{purplepages, coracle, userkinds,
yabu, nostr1}`. Leave the test fixtures alone (they exercise URL parsing on
canonical example URLs — replacing them adds churn without protecting users).

Include a one-line status note in the PR description so Vitor sees "checked".

## Technical Approach

### Architecture — where each piece lives

Following the codebase-specific rule (`commons/ARCHITECTURE.md`): "protocol
in Quartz, business logic in commons, layouts in platform apps."

```
quartz/  (unchanged — reuse only)
  nip65RelayList/AdvertisedRelayListEvent.kt       — parser (existing)
  nip65RelayList/RelayListRecommendationProcessor  — transpose + cover (existing)

commons/commonMain/
  wot/WoTService.kt                                — bug fixes 2/3/4
  wot/OutboxDispatcher.kt                          — NEW (Phase 1-3 driver)
  wot/OutboxRelayLoader.kt                         — MOVED from amethyst/,
                                                     Flow<Map<relay, authors>>
  relayClient/assemblers/FeedMetadataCoordinator.kt — bug fixes 5/6 + calls
                                                     into OutboxDispatcher when
                                                     configured

desktopApp/jvmMain/
  Main.kt                                          — reorder localCache init,
                                                     call OutboxDispatcher
  cache/DesktopLocalCache.kt                       — bug fix 1
                                                   — new consumeAdvertisedRelayList
                                                     path

cli/
  commands/WotCommand.kt                           — amy wot sync via
                                                     OutboxDispatcher
```

### OutboxDispatcher API (draft)

```kotlin
// commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/wot/OutboxDispatcher.kt
class OutboxDispatcher(
    private val client: INostrClient,
    private val scope: CoroutineScope,
    private val indexRelays: () -> Set<NormalizedRelayUrl>,   // lazy — respects settings updates
    private val cache: OutboxCacheGateway,                    // interface, actual = DesktopLocalCache
    private val perRelayTimeoutMs: Long = 4_000,
) {
    data class Result(
        val kind10002Received: Int,
        val kind3Received: Int,
        val kind0Received: Int,
        val fallbackAuthors: Int,
    )

    /**
     * Fetch kind-3 and kind-0 for [authors] via each author's declared write
     * relays (NIP-65). Falls back to [indexRelays] for authors with no 10002.
     *
     * Suspending — returns after every phase EOSEs or times out. Callers
     * that need "return immediately, mark ready later" should wrap in
     * [scope.launch].
     */
    suspend fun fetchKind0And3(authors: Set<HexKey>): Result
    suspend fun fetchKind3Only(authors: Set<HexKey>): Result   // WoT-specific
}

interface OutboxCacheGateway {
    /** Returns the cached kind-10002 for [pubkey] if the local store already has one. */
    fun cachedOutbox(pubkey: HexKey): AdvertisedRelayListEvent?
    /** Called for every 10002 that comes back — cache should stash it. */
    fun onOutboxDiscovered(event: AdvertisedRelayListEvent, relay: NormalizedRelayUrl)
    /** Called for every kind-3 / kind-0 that comes back — cache should route through its consume path. */
    fun onDiscoveredEvent(event: Event, relay: NormalizedRelayUrl)
}
```

`DesktopLocalCache` implements `OutboxCacheGateway`; `amy` gets a minimal
implementation that writes into its local store.

`OutboxRelayLoader` (moved from `amethyst/`) provides the *live* Flow-form for
reactive lookups; `OutboxDispatcher` uses it internally for the "check cache
first, only REQ what's missing" fast-path.

### Reactivity for "new follow arriving"

Current code (Main.kt:1559) collects `localCache.followedUsers` and calls
`loadKind3Batched(follows)` on every change. The dedup set means the diff
(only new pubkeys) actually flows through.

Under the outbox model, the analogous flow is:

```
localCache.followedUsers.collect { follows ->
    wotService.onFollowSetChange(follows, account.pubKeyHex)
    if (wotService.isDisabled) { wotService.markReadyOnce(); return@collect }
    launch {
        val result = outboxDispatcher.fetchKind3Only(follows)   // dedup inside
        wotService.markReadyOnce()
    }
}
```

`fetchKind3Only` internally consults `inFlight` + `succeeded` and only REQs
the diff. Test scenario "user follows one new person mid-session" trivially
covered because Phase 1 for a single-element authors set is a single index-
relay REQ, and Phase 2 is one per-outbox REQ.

### `amy wot sync` under outbox

Replace the manual `Filter/chunked/ctx.drain(...)` block in
`WotCommand.sync` with:

```kotlin
val dispatcher = OutboxDispatcher(client, scope, ctx::indexRelays, AmyCacheGateway(store))
val result = dispatcher.fetchKind3Only(follows.toSet())
Output.emit("wot sync",
    "10002=${result.kind10002Received} kind3=${result.kind3Received} " +
    "fallback=${result.fallbackAuthors}")
```

The JSON schema for `--json` gains three new keys (`kind10002_received`,
`fallback_authors`, `kind3_received`) — additive, no rename.

### Concurrency & KMP concerns

- All new code targets `commonMain`. No `java.util.concurrent`, no
  `synchronized {}` (needs jvmAndroid actual). Rely on `Channel`, `Mutex`,
  `StateFlow`, and `Snapshot` — all KMP-safe.
- `Dispatchers.IO` isn't KMP either; use `Dispatchers.Default` in commonMain
  and let platform code override if needed.
- Per-relay timeouts implemented via `withTimeoutOrNull(perRelayTimeoutMs)`
  inside per-relay coroutines; overall EOSE gate uses a
  `CompletableDeferred<Unit>` that trips when either (a) all per-relay jobs
  complete or (b) the outer `withTimeoutOrNull(overallCap)` fires.

### Data flow: how discovered 10002s stop double-fetching

Every `AdvertisedRelayListEvent` received during Phase 1 goes through
`OutboxCacheGateway.onOutboxDiscovered(event, relay)` → the platform cache's
`consume` path. Next call for the same author checks `cachedOutbox(pubkey)`
before dispatching Phase 1, so we never REQ the same 10002 twice within a
session (or across sessions, if the local relay store persists the 10002 —
which it does, since kind-10002 events are indexed like any other event).

### Implementation Phases

#### Phase 1 — Bug fixes (correctness first, self-contained)

Ship-blockers, no outbox dependency, land these commits first so a revert
doesn't force rolling back the outbox refactor:

1. `fix(desktop-cache): eliminate accountPubkey race in
   consumeContactList` — reorder Main.kt so pubkey binds before hydrate;
   gate `lastContactListByAuthor` stamp inside self branch. Test:
   `DesktopLocalCacheHydrationTest` — reproduce the wipe by running
   hydration before pubkey bind, assert follow set survives relay retry.
2. `fix(wot): clear myFollows + set disabled flag when MAX_FOLLOWS exceeded`
   — plus a Main.kt short-circuit before dispatching Phase 1. Test:
   `WoTServiceTest.overCapDisablesEverything`.
3. `refactor(wot): close()/dispose() + AutoCloseable, call from
   account-switch` — Test: `WoTServiceLifecycleTest.closeCancelsWriter`.
4. `docs(wot): correct SnapshotStateMap isolation comments` — comment-only.
5. `fix(coordinator): mark queuedKind3Pubkeys only on EOSE, allow retry on
   timeout` — Test: `FeedMetadataCoordinatorTest.timeoutRetryIsAllowed`.
6. `fix(coordinator): single-writer EOSE aggregator (KMP-safe)` — Test:
   `FeedMetadataCoordinatorTest.eoseReadyUnderConcurrentCallbacks`
   using a fake client that fires EOSE from multiple dispatchers.

**Success criteria phase 1:** all six tests pass; `./gradlew :commons:jvmTest
:desktopApp:jvmTest :cli:test` green; `./gradlew spotlessApply` clean.

#### Phase 2 — Outbox scaffolding (commons)

7. `refactor(commons): move OutboxRelayLoader from amethyst/ to
   commons/commonMain` — pure code motion; leave a re-export in the amethyst
   package to avoid Android build breaks. Test: existing Android
   `OutboxRelayLoaderTest` (if any) still passes.
8. `feat(commons): OutboxDispatcher two-phase kind-0/kind-3 fetcher` —
   commonMain, plus jvmMain test that drives a fake `INostrClient` through
   Phase 1/2/3 including the fallback path.
9. `feat(commons): OutboxCacheGateway interface + DesktopLocalCache impl` —
   including a new `consumeAdvertisedRelayList(event, relay)` in
   `DesktopLocalCache` that mirrors the existing `consumeContactList` pattern.

**Success criteria phase 2:** `./gradlew :commons:jvmTest` green;
`OutboxDispatcherTest` covers "author with 10002", "author without 10002 →
fallback", "index relay times out on Phase 1", and "per-relay timeout on
Phase 2 doesn't cancel other relays".

#### Phase 3 — Cutover (Main.kt + amy)

10. `feat(desktop): route WoT kind-3 fetch through OutboxDispatcher` —
    Main.kt uses OutboxDispatcher; delete the direct `loadKind3Batched`
    call. Preserve the 2 s startup fallback for `markReadyOnce`.
11. `feat(desktop): also route stranger-avatar kind-0 through
    OutboxDispatcher` — MetadataPreloader gets a hook that prefers outbox
    when a 10002 exists for the author.
12. `feat(cli): amy wot sync via OutboxDispatcher` — rewrite the manual
    filter/drain in `WotCommand.sync`. Update its `--json` schema (additive).

**Success criteria phase 3:** manual testing sheet (Section: Test Plan)
passes end-to-end. `./gradlew test` green.

#### Phase 4 — Documentation & PR description

13. Update PR description's "Behaviour" section to reflect the outbox flow.
14. Add a top-level "Damus relay: production defaults verified clean" line
    so Vitor doesn't have to look.

## Alternative Approaches Considered

**A. Do the outbox refactor in a follow-up PR.** Rejected by user: the same
files (WoTService, FeedMetadataCoordinator, Main.kt) also need the review
fixes, so a two-PR split would double the churn in the same seams.

**B. Skip Phase 1 (10002 discovery) and read the local cache only.** Would
break for cold-start accounts with no cached 10002s. Only works for
"warm-cache" sessions, defeating Vitor's ask on first login.

**C. Adopt `AmethystDefaults.DefaultIndexerRelayList` as the new index-relay
default.** The current branch keeps `{nos.lol, nostr.wine, noswhere,
primal.net}` for continuity. Adopting the Purple Pages / Coracle / etc. set
is a user-visible behaviour change deserving its own review. Deferred to a
follow-up ticket. Documented in `PreferencesIndexRelays.kt:85-92` already;
no action here.

**D. Use `graperank` as Vitor idly mused in the follow-up comment.** Not
actionable in this PR — it's a musing about extending `amy`, not a review
change. Called out here so the item doesn't get lost, but leave for a
future ticket.

## System-Wide Impact

### Interaction Graph

```
Login
  └─ Main.kt:1541 LaunchedEffect binds localCache.accountPubkey
  └─ (Fix 1: must run BEFORE the block below)
  └─ Main.kt:893 launch(Dispatchers.IO) { localRelayStore.hydrate(localCache) }
        └─ per-event: localCache.justConsumeMyOwnEvent → consumeContactList
              └─ before fix: stamps lastContactListByAuthor with null accountPubkey
              └─ after fix: stamp only inside self-branch, or after ordering guarantee

Login (parallel)
  └─ Main.kt:1559 collect(followedUsers) →
        └─ WoTService.onFollowSetChange
              └─ ops.trySend(FollowSet) → writerLoop → handleFollowSet
                    └─ Fix 2: MAX_FOLLOWS → clear myFollows + disabled=true, return
        └─ if !disabled: OutboxDispatcher.fetchKind3Only(follows)
              └─ Phase 1: REQ 10002 on index relays
                    └─ OutboxCacheGateway.onOutboxDiscovered → DesktopLocalCache.consumeAdvertisedRelayList
              └─ Phase 2a: RelayListRecommendationProcessor.reliableRelaySetFor
              └─ Phase 2b: per-relay REQ kind=[0,3] authors=[relay's users]
                    └─ OutboxCacheGateway.onDiscoveredEvent → LocalCache.consume path
                          └─ consumeContactList (fixed) → _contactListEvents.tryEmit
                                └─ WoTService.applyKind3 → handleKind3 → updateScore
              └─ Phase 3: fallback for missing-10002 authors → index-relay REQ
        └─ WoTService.markReadyOnce → _isReady.value = true → badge composables recompose

Account switch
  └─ Main.kt:874 localCache.clear() → resets lastContactListByAuthor
  └─ Fix 3: WoTService.close() → cancels writer, drops ops channel
  └─ OutboxDispatcher scope cancels → in-flight REQs unsubscribe
```

### Error & Failure Propagation

- `client.subscribe` failure inside `OutboxDispatcher` → swallowed at the
  per-relay coroutine level, logged, moves on. The overall `withTimeoutOrNull`
  ensures the caller never blocks past its budget.
- `AdvertisedRelayListEvent.writeRelaysNorm()` returning null (author has a
  10002 but empty write list) → falls through to Phase 3 fallback.
- Cache consume path errors (e.g. corrupt event) → existing `LocalCache`
  behavior; not new.

### State Lifecycle Risks

- Between `WoTService.close()` and `OutboxDispatcher` scope cancel there's a
  small window where a pending REQ EOSE could arrive at a torn-down service.
  Mitigation: `OutboxCacheGateway.onDiscoveredEvent` and
  `WoTService.applyKind3` must be null-guarded against the "already-closed"
  state — WoTService's writerLoop naturally handles this (channel closed →
  loop exits).
- Fix 1 requires Main.kt reordering; if the reorder is done wrong and pubkey
  bind is *later* than hydration, the bug recurs silently. Test:
  `DesktopLocalCacheHydrationTest.regressionOrderingProtection`.

### API Surface Parity

`amy wot sync` and Desktop login both consume the same `OutboxDispatcher`,
so any protocol change propagates. Android is a future consumer — the
plan intentionally lives in commons/commonMain so wiring Android on top
is a Main.kt-equivalent + gateway impl.

### Integration Test Scenarios

1. Cold-start login, well-connected account (~350 follows, ~90% with 10002):
   Phase 1 completes, Phase 2 fetches only from write relays, Phase 3 kicks
   in for the ~10% no-10002 authors, WoT ready < 5 s, badges render.
2. Cold-start login, ~4000-follow account: MAX_FOLLOWS trips → dispatcher
   skipped, `markReadyOnce()` immediately, no badges, no REQ traffic.
3. Cold-start login, all index relays unreachable: overall timeout fires,
   `markReadyOnce()`; on next `followedUsers` emission, `inFlight` is empty
   (thanks to fix 5) so a retry happens.
4. Mid-session follow: single-author `fetchKind3Only({newPubkey})` uses cache
   hit if `cachedOutbox(newPubkey) != null`, else does one Phase-1 REQ.
5. Account switch: `WoTService.close()` runs; opening the same account again
   creates a fresh instance without leaking the previous writer coroutine.
6. `amy wot sync` on a headless VM with only the OS event store: writes
   10002 + kind 3 events to disk; second run of `amy wot get <hex>` returns
   the correct hydrated score.

## Acceptance Criteria

### Functional

- [ ] `DesktopLocalCache.consumeContactList` no longer stamps
      `lastContactListByAuthor` for the self path unless `accountPubkey` is
      set and the event matches. Regression test exists.
- [ ] `WoTService` exposes `isDisabled: StateFlow<Boolean>`; caller
      (Main.kt) skips OutboxDispatcher when disabled.
- [ ] `WoTService` implements `AutoCloseable`; account-switch path calls
      `close()`.
- [ ] `FeedMetadataCoordinator.loadKind3Batched` and
      `loadMetadataBatched` retry on timeout (pubkeys not promoted to
      `succeeded`).
- [ ] Both `loadKind3Batched` and `loadMetadataBatched` use single-writer
      EOSE aggregation (no `MutableSet` shared across dispatchers).
- [ ] `OutboxDispatcher.fetchKind3Only` and `fetchKind0And3` exist in
      commons/commonMain with test coverage for the four scenarios in
      "Integration Test Scenarios".
- [ ] `Main.kt` login path uses `OutboxDispatcher` for kind-3 seeding
      (WoT + follow-set metadata).
- [ ] `amy wot sync` uses `OutboxDispatcher`; `--json` output additively
      gains `kind10002_received`, `kind3_received`, `fallback_authors`.

### Non-functional

- [ ] `./gradlew test` green.
- [ ] `./gradlew spotlessApply` clean before commit.
- [ ] No production default relay list on this branch references
      `relay.damus.io` (already verified; keep it verified after refactor).
- [ ] No use of `java.util.concurrent` / JVM-only `synchronized {}` in
      `commons/commonMain/`.
- [ ] KDoc for `WoTService` accurately describes SnapshotStateMap
      per-key isolation.

### Quality Gates

- [ ] Manual regression sheet covering integration scenarios 1-6 above.
- [ ] `amy wot sync --json` sample output attached to PR description.
- [ ] Follow-list-wipe regression covered by an automated test that
      hydrates a cached kind-3 before binding pubkey and asserts nothing is
      poisoned.

## Success Metrics

- WoT badge coverage on real accounts (Vitor's expected win): jump from
  "index-relay-published authors only" to "any author with a 10002" —
  measured by running the desktop app before/after and diffing the badge
  count on a fixed follow-set.
- Zero follow-list-wipe reports in the two weeks after merge (davotoula
  finding 1 was worst-case data loss).
- Zero index-relay REQ traffic for kind-0/kind-3 authors that publish a
  10002. Measurable by wireshark on a test build.

## Dependencies & Prerequisites

- Quartz `AdvertisedRelayListEvent` + `RelayListRecommendationProcessor` —
  already exist, reused verbatim.
- `INostrClient.subscribe(subId, filters, listener)` — already exists.
- `DesktopLocalCache` needs a new `consumeAdvertisedRelayList(event, relay)`
  method — mirrors existing `consumeContactList` structure.
- `OutboxRelayLoader` — moved from `amethyst/` to `commons/commonMain`;
  Android continues to compile because it only depends on things
  already in commons/quartz.

No new third-party libraries introduced. No `libs.versions.toml` change.

## Risk Analysis & Mitigation

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Outbox refactor changes badge counts on live user accounts unexpectedly | Med | Med | Keep the Phase-3 fallback path so no author gets *worse* coverage than today. Manual A/B test on maintainer's account before merge. |
| `RelayListRecommendationProcessor.reliableRelaySetFor` picks pathologically many relays for a fragmented follow set | Low | Low | Algorithm already caps by second-pass "at least 2 relays per author" rule. Add a hard `MAX_RELAYS_PER_FETCH` (say 40) as a belt-and-braces guard. |
| Concurrent EOSE handshake rewrite introduces a new bug | Low | High | Test `FeedMetadataCoordinatorTest.eoseReadyUnderConcurrentCallbacks` with a fake client firing EOSE from three dispatchers 1000× to catch ordering assumptions. |
| Bug fix 1 (Main.kt reordering) breaks another consumer that read localCache before accountPubkey bind | Med | Med | Grep for all `localCache.accountPubkey` reads; verify none pre-date the bind. If any, thread the pubkey through as a parameter. |
| Adopting `AutoCloseable` on `WoTService` misleads callers into thinking it's `use`-scoped | Low | Low | Comment on `close()` says "call from account-switch/dispose only; instance lives for the account session". |
| `amy wot sync --json` schema change breaks downstream scripts | Med | Low | Additive fields only, no renames. Document in `cli/plans/*` if a plan exists there. |

## Resource Requirements

- One engineer, ~2-3 days including tests + manual regression.
- Test-relay access: can use `wss://nos.lol` and Purple Pages for real
  Phase 1 verification.
- Access to a mega-follow test account (>2000 follows) to verify Fix 2.
- Access to an account with a well-populated 10002 network to verify
  Phase 2 does what we think.

## Future Considerations

- Android wiring: `AndroidApp` currently doesn't wire WoTService. When it
  does, it can lean on the same `OutboxDispatcher` — expected diff is
  Main-equivalent + a `LocalCache` gateway.
- Graperank scoring (Vitor's follow-up musing): if `amy wot` grows a scoring
  strategy plugin API, `OutboxDispatcher` remains unchanged; only the
  post-fetch aggregation layer inside `WoTService` changes.
- Adopting `AmethystDefaults.DefaultIndexerRelayList`: separate ticket.
  Deserves its own review because it's a user-visible behaviour change.

## Documentation Plan

- Update this plan's status to `completed` post-merge; write a short
  "solutions" note if the accountPubkey race surprised us elsewhere.
- Update PR description "Behaviour" section to reflect outbox path.
- Update `commons/ARCHITECTURE.md` "where does my code go?" section with a
  one-line entry for `OutboxDispatcher`.

## Sources & References

### Origin

- **PR review comments:**
  https://github.com/vitorpamplona/amethyst/pull/3483#issuecomment-4892248528 (davotoula, bugs 1+2)
  https://github.com/vitorpamplona/amethyst/pull/3483#issuecomment-4892272000 (davotoula, bugs 3-6 impact on Android)
  https://github.com/vitorpamplona/amethyst/pull/3483#issuecomment-4892302009 (vitorpamplona, outbox directive)
  https://github.com/vitorpamplona/amethyst/pull/3483#issuecomment-4892686911 (vitorpamplona, Graperank musing — out of scope)

### Internal References

- Kind-10002 parser: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip65RelayList/AdvertisedRelayListEvent.kt`
- Relay-cover algorithm: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip65RelayList/RelayListRecommendationProcessor.kt`
- Existing Android outbox loader: `amethyst/src/main/java/com/vitorpamplona/amethyst/model/topNavFeeds/OutboxRelayLoader.kt`
- WoT service (bugs 2-4): `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/wot/WoTService.kt`
- Feed metadata coordinator (bugs 5-6): `commons/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/relayClient/assemblers/FeedMetadataCoordinator.kt`
- Cache race (bug 1): `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/cache/DesktopLocalCache.kt:509-530`
- Main wiring: `desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/Main.kt:1541-1568, 764-768, 863-874`
- amy WoT: `cli/src/main/kotlin/com/vitorpamplona/amethyst/cli/commands/WotCommand.kt`
- Index relay persistence: `commons/src/jvmMain/kotlin/com/vitorpamplona/amethyst/commons/relays/index/PreferencesIndexRelays.kt`
- Baseline plan the PR extended: `desktopApp/plans/2026-07-01-feat-desktop-wot-score-plan.md`

### External References

- NIP-65 (Relay List Metadata): https://github.com/nostr-protocol/nips/blob/master/65.md
- Original plan for the current PR: `docs/plans/2026-07-01-feat-wot-followups-search-badges-and-index-relays-plan.md`

### Related Work

- PR #3483 (this PR): https://github.com/vitorpamplona/amethyst/pull/3483
- Prior WoT badge PR (base for this branch): `feat/desktop-wot-score`

## Unanswered questions

- Per-relay timeout budget — 4 s picked from thin air. Real number?
- Should the fallback in Phase 3 also hit the account's own home/search
  relays, matching Android's `pickRelaysToLoadUsers` cascade? Or index-only?
- WoTService.close() called from account-switch — what's the canonical
  disposal hook on Desktop? DesktopIAccount teardown?
- amy wot sync `--json` schema — is `fallback_authors` the right name, or
  match Android naming?
- Should `OutboxDispatcher` be a per-account singleton (like WoTService) or
  short-lived per fetch? Leaning singleton for the dedup set.
- Do we want to persist the "author has no 10002" fact so we skip Phase 1
  for them on next login? Requires a small persistent map — worth it?
- Should Fix 1 (accountPubkey race) be split into its own hotfix commit
  before the outbox refactor lands, so backporters have a clean cherry-pick?
