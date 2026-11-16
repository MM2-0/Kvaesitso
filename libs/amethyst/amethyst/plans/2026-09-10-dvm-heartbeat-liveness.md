# DVM heartbeat liveness — only show DVMs with a fresh kind-11998 heartbeat

_Status: **implemented** (Android; desktop wiring deliberately out of scope — §8)._

## 0. The shape of the thing

Amethyst shows Data Vending Machines (DVMs) in three places: the Discover "Content" tab
(kind 31990 NIP-89 announcements advertising kind 5300), DVM feeds pinned to the top-nav
(`FavoriteAlgoFeedsOrchestrator`), and the per-DVM content-discovery screen. Today all of
these treat every announced DVM as alive, forever — a DVM that went down months ago still
renders as a usable feed.

DVM operators are now sending a **heartbeat event (kind 11998) every 300 seconds**. The
event is plain-text (`content = "Alive and kicking"`) with three tags:

- `status` — free-text status line (e.g. "My heart keeps beating like a hammer")
- `d` — the DVM's **NIP-89 DTAG**, tying the heartbeat to the announcement's address
- `expiration` — `createdAt + 300` (NIP-40), so relays drop the beat once the next one lands

Kind 11998 sits in the replaceable range (10000–19999), so relays keep only the latest beat
per author. There is no NIP for this yet — the shape above comes from the operator-side
builder and is treated as the wire contract.

The feature: **a DVM counts as alive only if its latest heartbeat is at most 900 seconds
old** (one missed 300s beat plus slack). Dead DVMs disappear from the Discover list; pinned
feeds and the detail surface show an offline state instead.

## 1. Decisions taken

1. **Approach: cache-backed heartbeats.** The heartbeat is a real event class stored through
   the standard replaceable path in `LocalCache` (newest per address, standard invalidation).
   Rejected alternatives: a side-state registry (duplicates invalidation plumbing) and
   regular-note storage (no address matching, pollutes the notes index).
2. **Scope: all three surfaces** — Discover list (hide), pinned feeds (offline state, chip
   stays), DVM detail screen (offline banner, requesting still allowed). The manage screen
   (`FavoriteAlgoFeedsListScreen`) also gets the badge.
3. **Pinned chips stay when offline** — the user pinned them deliberately; they gray out
   with an offline badge rather than vanishing, and tapping still opens the feed.
4. **Threshold: 900 seconds** (raised from the original 420 after field testing: beats arrive every 300s, and a 420s window tolerated barely one delivery hiccup, dropping live DVMs in oscillations). Exactly 900s old counts as fresh.
5. **Strict from cold start.** No grace period: the Discover list starts empty and fills
   within ~1–2s as heartbeat REQs return (same behavior as the existing 31990 load).

## 2. Event model (quartz)

New `quartz/.../nip90Dvms/dvmHeartbeat/DvmHeartbeatEvent.kt`:

- `class DvmHeartbeatEvent(...) : BaseAddressableEvent(...)`, `KIND = 11998` — the codebase
  convention for 10xxx events with real `d` tags (e.g. `StarterPackEvent`), so `dTag()` /
  `address()` / `addressTag()` come from the base. The cache address is
  `Address(11998, dvmPubkey, dTag)`, the exact mirror of the announcement's
  `Address(31990, dvmPubkey, dTag)`.
- Accessors: `status()`, and `expiration()` via the existing NIP-40 extension.
- `MAX_AGE_SECONDS = 900` and `isFreshAt(now)` live in quartz too (commons imports them).
- Registered in `EventFactory` (kind → constructor) and allowlisted in
  `EventFactoryKindRangeTest.knownDTagReaders`: the `d` tag keys the client-side address
  while relay storage stays plain-replaceable per the kind range.

## 3. Cache consumption (LocalCache)

One routing line in `LocalCache.justConsumeInnerInner`: `is DvmHeartbeatEvent ->`
`consumeBaseReplaceable(event, relay, wasVerified)`. This yields newest-per-address
replacement, relay tracking, and `LocalCacheFlow` invalidation for free. Unlisted kinds fall
into the `else` branch and are rejected, so the routing line is mandatory.

Stale beats simply sit at their address until overwritten; the age check (§4) makes them
invisible. The cache pruner removes old entries on its own schedule.

## 4. Freshness core (amethyst)

Small helper file in `amethyst/.../model/` (the threshold constant itself lives in quartz):

- `LocalCache.dvmHeartbeatOf(appDef: AppDefinitionEvent): DvmHeartbeatEvent?` — address
  lookup `Address(DvmHeartbeatEvent.KIND, appDef.pubKey, appDef.dTag())`
- `DvmHeartbeatEvent.isFreshAt(now: Long): Boolean` — `createdAt >= now - 900`
- `@Composable fun rememberDvmHeartbeatFresh(address: Address, accountViewModel: AccountViewModel): State<Boolean>` —
  as built (uniform-strict ruling): returns true while the DVM has a heartbeat at most 900s
  old; an unresolved/absent beat counts as offline (`false`) on every surface. The returned
  `State` identity is stable for the lifetime of the call site (one unconditional
  `rememberUpdatedState`), so callers may capture it across recompositions. Composable-scoped
  subscription (§5) + staleness re-check tick (§6), shared by every surface that renders
  liveness.

## 5. Subscriptions

**Discover screen — all DVM heartbeats.** In
`commons/.../relayClient/discover/nip90DVMs/SubAssemblyHelper.kt`, `makeContentDVMsFilter`
unconditionally appends one filter for every top-filter variant:
`kinds = [11998], since = TimeUtils.now() - 900` — no authors, no tags, scoped to the same
relay set as the 31990 REQs. It deliberately ignores the 31990 `since`-cursor (heartbeats
are a rolling window, not a cursor stream — the cursor would miss re-opened tabs after the
beats expired). It rides the existing assembler lifecycle: subscribes on entering Discover,
closes on leaving.

**Per-surface — pinned chips, home banner, detail screen.** `rememberDvmHeartbeat` opens a
tiny composable-scoped subscription: `kinds = [11998], authors = [dvm pubkey], limit = 1,
since = now - 900`. The home top-bar chips live for the whole session, so they double as
the session-scoped watcher for pinned DVMs. Traffic is negligible (a few pinned DVMs ×
1 event / 5 min).

**Outbox fetcher (added after field testing).** The global REQ above only sees beats that
reach the *user's* discovery relays — but DVMs publish beats to their own write relays, and
relays don't gossip, so alive DVMs whose beats never overlap the user's relay set stayed
invisible (their detail screens proved the beats existed on the outbox). `DiscoveryDvmHeartbeatSubAssembler`
joins the discovery assembler group and, while Discover is composed, batches the cached
content-discovery announcements' authors per **DVM outbox relay** (`kinds = [11998],
authors = [those pubkeys], since = now - 900`, coverage-ranked and capped at 12 relays;
authors with unknown outboxes/hints rely on the global REQ as fallback). It re-issues when
the cached announcement set or the NIP-65 relay lists move.

The announcement source MUST be the **ungated cache scan**
(`LocalCache.cachedDvmAnnouncements` — every cached k=5300 announcement, newest first, capped
at 100), not the gated feed list. Sourcing from the gated list is a death spiral: a DVM
leaves the gated list the moment its beat ages out, the fetcher would stop covering it, and
no beat would ever arrive to bring it back — any transient staleness becomes a permanent
drop. The relay lookup unions the author's NIP-65 outbox with the cached relay hints for the
author (the same mix the event finder's `potentialRelaysToFindAddress` uses).

## 6. Invalidation — closing the two silent gaps

1. **A new heartbeat does not re-rank the list.** The additive feed path
   (`FeedContentState.updateFeedWith`) re-filters only the *new* notes, and a heartbeat
   note is never a list row — the affected 31990 card would not be re-evaluated. Fix: in
   `AccountFeedContentStates.updateFeedsWith`, branch on
   `newNotes.any { it.event is DvmHeartbeatEvent }` → `discoverDVMs.invalidateData()`
   (full rebuild re-runs the freshness check on every announcement); otherwise the normal
   additive path.
2. **Expiry produces no event.** A 60s timer collector in `AccountFeedContentStates`
   (alongside the existing `scope.launch { flows.collect { … } }` observers) calls
   `discoverDVMs.invalidateData()` every minute. The rebuild is a cheap scan (≤ a few
   hundred 31990s) and `refreshSuspended()` no-ops when the list is unchanged. Composables
   using `rememberDvmHeartbeatFresh` tick on a 30s cadence internally.

## 7. UI surfaces

1. **Discover "Content" tab** — `DiscoverNIP89FeedFilter.acceptApp` adds
   `dvmHeartbeatOf(noteEvent)?.isFreshAt(now) == true`. No fresh beat → card hidden.
2. **Pinned top-nav chips** — chip stays; when the heartbeat is stale or absent the chip is
   grayed out with a small offline dot appended to its label. Tapping still opens the feed.
3. **Pinned feed view** — new branch in `HomeAlgoFeedStatusBanner`: when the selected
   pinned feed's heartbeat is stale, show an offline banner above the last known content,
   shown *even when content exists* (the current banner only handles empty/error states).
   Single-feed and all-feeds variants both covered.
4. **DVM detail screen** (`DvmContentDiscoveryScreen`) — same offline banner; requesting is
   still allowed (informational, not a block).
5. **`FavoriteAlgoFeedsListScreen`** — offline badge per row so users can spot dead pins.
6. New English string resources (`dvm_offline`, `dvm_offline_banner`); translations flow
   via Crowdin.

## 8. Edge cases (accepted limitations)

- Heartbeat without a `d` tag → cache address dTag `""` → matches nothing → DVM hidden
  (strict; the wire contract always sends `d`).
- Device/DVM clock skew > 15 min → wrongly hidden (inherent to timestamp-based liveness).
- DVM beats that never reach the relays we query → shows offline (that is the feature).
- One keypair running multiple DVMs → relays keep only the latest beat per (kind, author);
  per-d-tag cache slots help only across relays. Most DVMs use one key each.
- **Desktop app: out of scope this round.** The commons subscription helper is shared-ready
  and the desktop relay assembler will pick up heartbeat REQs harmlessly (cache fills,
  nothing renders), but all UI wiring is Android-only.

## 9. Testing

- **quartz**: parse/build `DvmHeartbeatEvent` — `dTag()` override, `statusTag()`,
  `expiration()`, address assembly.
- **amethyst**: `DiscoverNIP89FeedFilter.acceptApp` matrix — no beat → reject; fresh beat →
  accept; 421s-old beat → reject. The `updateFeedsWith` heartbeat branch triggers a full
  rebuild. `isFreshAt` boundary (900s fresh, 901s stale).
- Verify with `./gradlew :quartz:test :amethyst:test`, then `./gradlew spotlessApply`.
