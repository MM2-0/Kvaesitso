# DM loading: live tail + per-relay history paging

> **Status:** shipped — Live-tail + per-relay history layers exist (`WindowLoadTracker`, `RelayLoadingCursors`, `AccountGiftWrapsHistoryEoseManager`); doc marks itself authoritative-as-of-code.
> _Audited 2026-06-30._

> **Status:** authoritative as of 2026-06-05. The "Current architecture"
> section below describes the code as it actually stands. The original
> time-slice design and the round-model history are kept at the bottom under
> **Design evolution (historical)** — they are superseded and no longer match
> the code; don't trust them for how it works today.

## Problem

The DM loaders used a single subscription whose `since` floor grew as the user
scrolled (`loadMore`: 7d → 14d → 28d → …). The filter carried **only `since`,
no `until`**, so every widen re-requested the whole window and the relay
re-streamed the entire history from the new floor. Traces showed this directly:

```
[giftwrap] load summary: 589 event(s)  (14d)
[giftwrap] load summary: 1486 event(s) (28d)
[giftwrap] load summary: 2609 event(s) (56d)
```

Each step re-downloaded everything it already had plus the new slice — the
"getting all events over and over again" the owner reported. It also cascaded:
a few pixels of scroll walked the window to the 10-year backstop, because
widening pulls older *messages* but the rooms list is keyed by *conversation*,
so a handful of busy correspondents flood thousands of events without adding a
single new row, and the "scrolled near the oldest room" trigger never clears.

---

## Current architecture

### Two layers per protocol

Each DM protocol — **NIP-17** gift wraps (kind 1059) and **NIP-04** legacy DMs
(kind 4) — is split into two independent responsibilities:

1. **Live tail** — a fixed ~1-week floor with **no `until`**, open to the
   future. Never widens. New messages always arrive here. Backed by the
   **round model** (`WindowLoadTracker`): one REQ fanned to every relay, "done"
   when all settle, drives the boot spinner.
2. **History** — everything *older* than the week floor, paged **backward by
   `until`+`limit`, per relay, on demand**. Backed by the **per-relay model**
   (`RelayLoadingCursors` + `PerRelayLoadTracker`), driven by on-screen markers.

The two are disjoint in time, so re-issuing a history page never re-streams the
live tail, and consecutive history pages never re-stream each other.

| Surface | Live-tail manager (round) | History manager (per-relay) |
|---|---|---|
| Account gift wraps (NIP-17) | `AccountGiftWrapsEoseManager` | `AccountGiftWrapsHistoryEoseManager` |
| Conversation NIP-04 | `ChatroomNip04SubAssembler` | `ChatroomNip04HistorySubAssembler` |
| Rooms-list NIP-04 | `ChatroomListNip04SubAssembler` | `ChatroomListNip04HistorySubAssembler` |

Accessed from the UI via `accountViewModel.dataSources()` as
`.account.giftWrapsHistory`, `.chatroom.nip04History`,
`.chatroomList.nip04History`.

### The history paging primitive: `RelayLoadingCursors`

The time-window model can't tell "this relay is empty" from "this is a gap" — a
`since`/`until` slice that returns nothing might just be a quiet stretch above
older messages. Paging by `until`+`limit` removes that ambiguity: a relay
returns up to `limit` (**10000**) of its newest events older than the cursor,
**skipping gaps**, so an **empty page + EOSE is a gap-proof "nothing older"**.

Per relay, two cursors are kept deliberately decoupled:

- `requestedUntil` — the `until` the REQ carries. Moves **only** in `advance()`.
  Leaving it untouched on EOSE is what makes paging demand-driven: a relay that
  finished a page just **parks** at the same filter (no re-REQ) until advanced.
- `reachedUntil` — the oldest `created_at` actually delivered. Moves on EOSE.
  The in-stream markers sit here; the next page starts at `reachedUntil − 1`.

Stop signals: an empty page marks the relay **`done`**. A relay returning fewer
than `limit` is treated as its own cap, **not** exhaustion. A misbehaving relay
that returns events but none older than already reached (echoing its newest
events) is also treated as the bottom, so its marker can't re-request the same
window forever. Tested in `RelayLoadingCursorsTest.kt`.

### The two completion models (and where each lives)

**Round model — `WindowLoadTracker` (live tail only).** One REQ is fanned to all
relays; the window is "done" only when **every** expected relay reaches a
terminal signal (`settled ⊇ expected`), with backstops for stragglers (idle /
silence / connect-grace / absolute cap). `loading` starts **`true`**. This is a
*barrier*: nobody moves on until the cohort answers. It is the right shape for
the one-shot fixed-window backfill the live tail does.

> Note: the silence + connect-grace backstops are gated behind `tracksReqSends`,
> and **none of the three live-tail managers pass `tracksReqSends = true`**, so
> in current use only the settle / idle / cap paths ever fire. The REQ-aware
> machinery is dormant in production — see "Things to scrutinize".

**Per-relay model — `RelayLoadingCursors` + `PerRelayLoadTracker` (all history).**
Each relay advances to its next page the instant *it* EOSEs, independent of the
others; the subscription layer diffs per relay, so re-issuing only re-REQs the
relay whose cursor moved. `loading` starts **`false`** (a `true` start would
wedge the scroll loader's `!loading` gate on first open). Fast relays race to
the bottom in back-to-back pages; slow / auth-walled relays catch up at their
own pace and **none are abandoned** — a stalled relay keeps its subscription
open and resumes when re-advanced. This removes the round model's
slowest-relay coupling, which matters most on the conversation screen where the
fan-out includes correspondents' (often auth-walled, slow) relays.

`exhausted` (per history manager) flips true when **every relay is `done` OR
`stalled`** — "nothing more reachable right now". A merely *parked* relay (more
to load, just not advancing) keeps it false.

> All three history managers (`AccountGiftWrapsHistoryEoseManager`,
> `ChatroomNip04HistorySubAssembler`, `ChatroomListNip04HistorySubAssembler`)
> were structurally the same per-relay loader, so that bookkeeping is now a
> single reusable engine — **`BackwardRelayPager`** (keyless, single-active). It
> does **not** hold the cursors: the per-relay `RelayLoadingCursors` live on the
> scope's own domain object (a `Chatroom` per conversation, a `ChatroomList` per
> account), so they share the cached messages' lifetime and survive an account
> switch. The orchestrator owns only the transient bits — in-flight + silence
> tracking, the stalled set, and the display flows — and
> `bind(cursors, scope, relaysFor)`s to whichever scope is active. Each manager
> supplies its REQ-filter builder, a `relaysFor` lookup, and the subscription
> wiring (forwards relay callbacks via `onEvent`/`onEose`/`onClosed`/`onCannotConnect`,
> re-issues filters after `advance`/`advanceAll`). The earlier round-model history
> (and the rooms-list "stall-gate") was fully removed — see Design evolution.

### What drives `advance()`: on-screen markers, off viewport visibility

History paging is demand-driven by **per-relay window-limit markers** placed in
the message stream, not by a scroll-position trigger:

- **`RelayReachCursor`** — one per (protocol, relay): its `reachedUntil` depth,
  its `RelayReachState` (`REACHING ↓` / `STALLED …` / `DONE ✓`), and the
  `advance()` that pulls *that relay's* next page. Built in the feed views from
  each history manager's `relayProgress` map (gift wraps + NIP-04 combined; a
  protocol drops out of the list once `exhausted`).
- **`RelayReachSentinels`** — the load *driver*, **hoisted above the
  `LazyColumn`** (via `ChatFeedView`'s `sentinels` slot). Each non-done limit
  gets one stable effect (keyed by `protocol:url`) that watches `listState` and
  fires `advance()` when its gap is among the **currently visible rows** AND
  either it just scrolled into view OR its `reachedUntil` moved (a page landed —
  keep paging while visible). Driving off **viewport visibility** instead of row
  composition is deliberate: an earlier version placed the sentinel *inside* the
  hosting row, so any feed reorder (a live DM, a slow relay dribbling a page)
  tore the effect down and re-fired `advance()` on a static screen — re-arming
  stalled relays into a silence-watchdog storm. (commit `0394ec2a`)
- **`RelayReachMarkers` / `RelayReachMarker`** — pure UI (via the
  `markersInGap` slot): the "Relay sync: ✓ 8 · ↓ 1" divider at each relay's
  reached depth. Can be re-placed on every reorder without triggering paging.
- **`BootstrapHistoryWhenEmpty`** — when the feed is genuinely `Empty` (the live
  tail came back empty for a thread/list whose newest message is older than a
  week) there are no rows to host markers, so this steps every relay one page at
  a time (debounced 1200ms, gated per loader on `!loading && !exhausted`) until
  messages appear and the markers take over, or the protocol exhausts.

### NIP-04 per-relay filter scoping (`Nip04DmRelayRouting`)

A conversation's NIP-04 filters previously named the whole participant set on
every relay, so a relay belonging to one correspondent was asked about all of
them, and the `from-me` leg (`authors:[me]`) was sent to correspondents' inbox
relays — which auth-walled relays reject outright ("all authors must be
authenticated"), stalling the load.

`Nip04DmRelayRouting` (in `FilterNip04DMs.kt`) is now two **per-relay key maps**
(`relay → which keys to name there`), built from the outbox model:

- **to me** (`#p:[me]`) — my inbox carries the whole group; each correspondent's
  outbox carries only that correspondent.
- **from me** (`authors:[me]`) — my outbox carries the whole group; each
  correspondent's inbox carries only that correspondent.

So a relay only ever sees the keys that actually own it. The **conversation**
history manager scopes its REQ to the armed relays' key sets this way; the
**rooms-list** and **gift-wrap** history managers query only the account's *own*
relays (home outbox `from-me` + DM inbox `to-me`, via `filterNip04DMsFromMe` /
`filterNip04DMsToMe` and `filterGiftWrapsToPubkey`), which is why their fan-out
stays fast and reachable.

### Status card terminal states (`DmHistoryLoadingCard`)

One card per protocol at its oldest-loaded boundary. While paging it shows the
protocol tag, "N relays" being asked, and the reach-back date; it is tappable
into a per-relay popup (`DmHistoryRelayDialog`) listing every relay with
`✓` done / `…` stalled / `↓` reaching and how far back each paged.

Because `exhausted` conflates `done` and `stalled`, the terminal state is split
on `stalledCount` so it can't overclaim (commit `813110cc`):

- **caught up** (every relay `done`, `stalledCount == 0`) → "All caught up",
  lingers ~2.2s then collapses.
- **incomplete** (≥1 stalled) → "Some relays didn't respond · N unreachable",
  error-coloured `…`, **stays put** (no collapse), tappable to see which.

### Reply placeholder (`LoadingReplyNote`)

A reply whose target message hasn't been paged in yet isn't *missing*, it's
older than the loaded window (and for gift wraps the rumor id isn't even
queryable — only the outer 1059 wrap is). Instead of the generic `BlankNote`
("post not found"), `LoadingReplyNote` actively walks the relevant protocol's
history backward (kicking `advanceAll` each time a page settles) until the
target decrypts (the surrounding `WatchNoteEvent` crossfades the real message in
and disposes this) or the protocol exhausts. Its terminal state mirrors the
card: "Couldn't find this message" + an honest subtitle ("N relays unreachable ·
tap to see which" when stalled, "Searched every relay · tap to see" when
genuinely done), tappable into the same per-relay popup. Wired via
`ChatMessageCompose.RenderReply` → `WatchNoteEvent(onBlank = …)`, with the pager
chosen by the parent event's protocol (`DmReplyProtocol.NIP17` / `NIP04`).

### Diagnostics

Everything logs under one tag, **`DMPagination`** (debug builds):
`DmRelayDiagnosticsLogger` folds the per-relay connection timeline (REQ sent,
connect/disconnect, CLOSED/NOTICE/OK-fail) into it; `DmRelayLog` prints the
"relays by source" breakdown (NIP-65 in/out, DM list, private storage, local)
per subscription so an unexpected relay can be traced to the list it leaks in
from; and each assembler logs its milestones (paging start, a relay reaching
the bottom / stalling with the reason, the "window settled" summary of
done-vs-still-trying, each marker fire).

### Related fix: Tor guard-sample self-heal

`TorService` gained a `noUsableGuards()` check that, on init, inspects Arti's
persisted `guards.json` and wipes the on-disk state if a non-empty guard set has
**zero** usable guards (all `disabled` / `unlisted`). This recovers the
long-standing "can't connect to Tor → relays permanently unreachable" wedge
(Arti disables guards past a 0.7 indeterminate-failure ratio, never re-enables
them, and can't replenish once the 60-slot sample is full). Orthogonal to
pagination, but it lived here because unreachable relays were part of the same
"DM history stuck / relays never answer" symptom this branch chased.

---

## Component map (vs `origin/main`)

**Paging primitives (quartz, `nip01Core/relay/client/paging/`, `commonMain`)** —
the pure, protocol-level paging *state*; iOS-clean, reusable by any KMP target.
- `RelayLoadingCursors.kt` — per-relay `until`+`limit` cursor state + pinned floor;
  held on the scope's domain object. *(+ `RelayLoadingCursorsTest` in amethyst; the
  `until`+`limit` wire contract is covered by `UntilLimitPagingRelayTest` against
  the quartz `jvmAndroidTest` geode relay)*
- `RelayPagingProgress.kt` — `(reachedUntil, done, stalled)` per relay.

**Paging orchestrators (commons, `relayClient/paging/`, `jvmAndroid`)** — the
StateFlow-backed, subscription-loading state holders. Moved out of amethyst **and**
out of quartz (per `commons/ARCHITECTURE.md`: the relay-subscription client +
`StateFlow` state holders live in commons) so desktop / CLI / any feed can reuse
them; `jvmAndroid` (uses `java.util.concurrent` + `@Synchronized`) → Android +
Desktop, not iOS.
- `BackwardRelayPager.kt` — the keyless single-active orchestrator the three
  history managers `bind` to. *(+ `BackwardRelayPagerTest` state-machine in commons
  `jvmTest`)*
- `PerRelayLoadTracker.kt` — per-relay in-flight tracker + silence watchdog.
- `WindowLoadTracker.kt` — round/barrier completion tracker (live tail). *(+ `WindowLoadTrackerIdleTest` in amethyst)*

**Diagnostics (amethyst)** — `service/relayClient/eoseManagers/DmRelayLog.kt`,
`service/relayClient/diagnostics/DmRelayDiagnosticsLogger.kt` — the `DMPagination` logs.

**Managers / assemblers**
- `AccountGiftWrapsEoseManager.kt` (live tail) + `AccountGiftWrapsHistoryEoseManager.kt` (new, history).
- `ChatroomNip04SubAssembler.kt` (live tail) + `ChatroomNip04HistorySubAssembler.kt` (new, history).
- `ChatroomListNip04SubAssembler.kt` (live tail) + `ChatroomListNip04HistorySubAssembler.kt` (new, history).
- `FilterNip04DMs.kt` (per-relay `Nip04DmRelayRouting`, live + history builders), `FilterNip04DMsFromMe/ToMe.kt`, `FilterGiftWrapsToPubkey.kt` — `until`/`limit` added.
- `AccountFilterAssembler`, `ChatroomFilterAssembler`, `ChatroomListFilterAssembler` — wire the new managers.

**Shared UI (commons, `commons/ui/feeds/`)** — extracted from amethyst so Android +
Desktop (and any per-relay feed) render the same widgets; CMP `composeResources`
strings, no app-theme / `java.time` deps.
- `RelayReachMarker.kt` — `RelayReachCursor` + sentinels (the hoisted, visibility-driven
  paging driver) + markers (pure UI) + `RelayReachMarker`/`RelayReachState`.
- `DmHistoryLoadingCard.kt` — the boundary status card + per-relay tap dialog +
  `historySubtitle`/`incompleteSubtitle`. Takes a `formatReachDate: (epochSeconds) -> String`
  so each platform supplies its locale date formatter.

**Android UI (`amethyst/ui/screen/loggedIn/chats/`)**
- `feed/LoadingReplyNote.kt` — history-walking reply placeholder (uses the shared subtitle helpers/dialog).
- `feed/HistoryDateFormat.kt` — `formatHistoryReachDate`, the Android locale formatter passed into the shared card.
- `feed/ChatFeedView.kt` — `markersInGap` + `sentinels` slots.
- `feed/ChatMessageCompose.kt` — reply `onBlank` wiring.
- `privateDM/ChatroomView.kt`, `rooms/feed/ChatroomListFeedView.kt` — assemble cards/markers/sentinels, `BootstrapHistoryWhenEmpty`.
- `res/values/strings.xml` — `chats_reply_*` (the card's `chats_history_*` now live in commons).

---

## Things to scrutinize (review notes)

1. **`exhausted` conflates `done` + `stalled`** at the manager level. The cards
   now distinguish them via `stalledCount`, but other consumers (the scroll
   `!loading` gates, `LoadingReplyNote`'s advance loop) treat stalled as
   terminal. Intentional (don't hammer dead relays), but confirm it's desired.
2. **`PerRelayLoadTracker.lastActivityMs` is global, not per-relay** — once the
   chatty relays finish, a legitimately-slow relay gets the full 15s silence
   window and can be marked stalled mid-delivery of a 10000-event page.
3. **"All caught up" can still be technically-true-but-misleading** when
   `stalledCount == 0` yet a chat's messages live on a relay *not in the
   account's NIP-17 inbox list* — an outbox-coverage gap the card can't detect.
4. **`WindowLoadTracker`'s REQ-aware backstops are dormant** in production
   (no live-tail manager sets `tracksReqSends`). Either the live tail should
   adopt them or the round model could be slimmer for its current role.
5. **`PAGE_LIMIT = 10000`** caps per-request volume but a single page can still
   be a large payload on a dense relay.

---

## Design evolution (historical — superseded, do not trust for current behavior)

These sections describe earlier iterations, kept for context. The code has
moved past all of them.

### v1 — time-slice history (superseded by `RelayLoadingCursors`)

History was first loaded in bounded `since`+`until` **time slices**
(`TimeWindowPagination`, now deleted): `loadMore` fetched only the new band
`[newFloor, previousFloor]`, with a NIP-17 ±2-day wrapper-timestamp margin on
the slice `since` for gift wraps. This bounded re-downloads but still couldn't
tell an empty relay from a gap (an empty slice might sit above older messages),
so the only stop was a 10-year `maxLookback`, and a wide late slice could pull a
20k-event firehose. Replaced by per-relay `until`+`limit` paging.

### v2 — round-model history + rooms-list "stall-gate" (both removed)

History paging once used the **round model** (`WindowLoadTracker`): each
`loadMore` issued one page to all active relays and waited for the slowest to
settle before the next — pacing every relay at the slowest one. The rooms list
additionally had a **stall-gate**: an auto-fill loop that widened only while it
brought in new private rooms, stopping once a widen added none (to avoid the
conversation-keyed cascade).

Both are gone. All history paging is now per-relay independent
(`PerRelayLoadTracker`), and the rooms list pages to exhaustion off marker
visibility like the conversation (commit `98fb8720` dropped the stall-gate;
`60b8629a` / `9f0ecd54` moved gift-wrap and rooms-list history onto the per-relay
model). `WindowLoadTracker` survives **only** as the live-tail completion
barrier. An earlier revision of this doc ("Update 3") still claimed rooms-list
and gift-wrap history used the round model — that is no longer true.
