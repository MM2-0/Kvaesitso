# NIP-29 group-chat subscriptions: split *state* (always-on) from *content* (paginated)

**Status:** proposed · **Date:** 2026-07-18 · **Module:** `amethyst` (+ `commons` model, reuses `commons`/`quartz` paging)

## Problem

NIP-29 relay-group ("RelayGroup") chat is served today by **six** overlapping
REQ assemblers, each keyed differently and each re-deriving the same two queries:

| Query shape | Emitted by (today) |
|---|---|
| Metadata `#d` (39000–39005 + pins) | Warmup, ChannelPublic (open), MyJoinedGroups (roster subset), OnRelay (directory) |
| Content `#h` (kind 9 + poll) | MyJoinedGroups (limit 50), Warmup (limit 50), ChannelPublic-open (limit 200) |
| My-own `#h` (`authors=[me]`) | ChannelFromUser — **redundant for groups** (the all-authors `#h` window already returns my messages; a group is pinned to one host relay) |
| Threads `#h` (11 + 1111) | Warmup, ThreadFeed |

Two concrete defects fall out of this shape:

1. **Slow / partial first load** (the reported bug). Content is fetched in **fixed
   windows** (limit 50 / 200) gated by a *shared per-relay* `since`
   (`RelayGroupMyJoinedGroupsSubAssembler` is keyed by `Account`, so its `since`
   collapses to one map per relay, not per group). A group joined or surfaced
   after that relay's `since` advanced never backfills; opening it waits a full
   relay round-trip.
2. **We can miss messages.** A fixed `limit=200` window has no way to reach older
   history, and no demand-driven paging: scroll up past 200 and there is nothing
   behind it. There is also a **serving-relay keying hazard** (see below) where a
   referenced group message lands in a channel the UI never reads.

Every *other* chat surface in the app already solved this with a **two-subscription
model** — an always-on live tail + an on-demand backward history pager — and there
is a reusable framework for it. Group chat is the outlier that never adopted it.

## Goal

Split group chat into the same shape every other chat uses, and delete the
duplication:

- **State** (metadata / roster / roles / pins) — small replaceable events →
  **one always-on account subscription**, gated on the NIP-29 settings toggle.
  The cache is always current; no per-screen metadata re-fetch.
- **Content** (kind 9 chat + polls) — high volume → **live tail + backward history
  pager**, exactly like NIP-04 DMs and Concord channels. Gap-proof (`RelayLoadingCursors`
  handles cache-prune rewind), demand-driven by the visible feed, reconnect-safe.

No message path that delivers a group message today may be dropped.

## Reused framework (do not reimplement)

Mapped end-to-end from the NIP-04 DM stack and the **Concord channel** stack, which
is the closest existing template (a public group channel already paged this way):

| Piece | Location | Role |
|---|---|---|
| `BackwardRelayPager(name, pageLimit, liveTailSeconds)` | `commons/.../relayClient/paging/` | single-active per-relay backward orchestrator |
| `RelayLoadingCursors` | `quartz/.../relay/client/paging/` | per-relay `until`/`reached`/`done` cursors + `rewindTo` (prune realign) — **one instance per group scope** |
| `WindowLoadTracker` + `trackingListener` | `commons/.../relayClient/paging/` | live-tail "all relays settled" indicator |
| `PagingStatus`, `RelayPagingProgress` | paging pkg / quartz | atomic display snapshot |
| `RelayReachCursor` / `RelayReachSentinels` / `RelayReachMarkers` | `commons/.../ui/feeds/RelayReachMarker.kt` | viewport-driven "load older" markers |
| `DmHistoryLoadingCard`, `RefreshingChatroomFeedView(olderBoundary, markersInGap, sentinels)` | `amethyst/.../chats/feed/ChatFeedView.kt` | shared feed hooks |
| `DmHistoryTuning.recentBoundary()` | `commons/.../model/privateChats/` | shared live-tail floor (7 days) |

**Direct templates to copy:**
`ConcordChannelHistorySubAssembler` + `ConcordChannelHistoryFilterAssembler` +
`ConcordChannelHistorySubscription` + `ConcordChannelScreen`'s
`ConcordBackfillHistoryToWindow`; and `ChatroomNip04SubAssembler` (live tail) /
`ConcordChannelFilterAssembler` (batched always-on live).

## Target architecture

Four concerns, mirroring the DM stack (rooms-list tail + per-conversation tail +
per-conversation history) plus a groups-only always-on state sub.

1. **`RelayGroupStateSubAssembler`** — *always-on*, account-keyed.
   Roster `#d` (39000/39001/39002/39003/39005) batched one filter per host relay
   across the joined set. Keeps `since` (tiny replaceable events; reconnect just
   re-confirms). Mounted at `LoggedInPage` (like `AccountFilterAssemblerSubscription`),
   gated on `ChatFeedType.NIP29`. **This is today's `RelayGroupMyJoinedGroups` roster
   path, promoted to always-on and stripped of content.**

2. **`RelayGroupPreviewTailSubAssembler`** — *always-on*, account-keyed, batched.
   Content `#h` (kind 9 + poll) across **all** `liveRelayGroupList` group ids,
   `since = recentBoundary()`, **no per-group limit** (a time floor bounds it, so it
   batches into one filter per relay). `WindowLoadTracker`. Drives Messages-list
   previews and keeps joined groups' recent chat live app-wide. **Replaces
   `RelayGroupMyJoinedGroups` content path (A).** Batching + time-floor `since`
   eliminates both the per-group-`since` bug and the reconnect re-download.

3. **`RelayGroupChatTailSubAssembler`** — per-open-`GroupId`, live tail for the
   *currently open* group: content `#h` (9 + poll), `since = recentBoundary()`, host
   relay. Covers recent + live updates for **any** open group, **including non-joined**
   groups opened by link (which the batched preview tail — joined-only — doesn't cover).
   Mirrors the DM per-conversation live tail.

4. **`RelayGroupChatHistorySubAssembler`** — per-open-`GroupId`, `BackwardRelayPager`
   (`liveTailSeconds` = 7d floor; the tails cover above it), cursors on
   `RelayGroupChannel.history`, content `#h` (9 + poll, **all authors**) `until`+`limit`
   on the host relay. Demand-driven by the feed markers; eager `advanceAll()` backfill
   to a window target on open. **Replaces ChannelPublic-open content (C) and
   ChannelFromUser (D).** All-authors, so it re-materializes my own history too.

`ChannelFeedFilter` is unchanged — it reads `channel.notes`, so every path that fills
the cache surfaces. (It has **no `limit()`**, so it already renders whatever is cached.)

## Message-coverage proof (can't-miss-messages checklist)

Every current content-delivery path and what covers it after:

| Path (today) | Kinds / scope | After |
|---|---|---|
| **A** MyJoined content (50) | 9,poll `#h` joined | **Preview tail (batched `#h`, since=window)** for previews + **chat tail** when open |
| **B** Warmup content (50) | 9,poll,11,1111 `#h` card | **KEEP** — non-joined cards/discovery aren't in the joined tail (screen-dependent, per design) |
| **C** ChannelPublic-open content (200) | 9,poll `#h` open | **Chat tail (recent) + history pager (older, gap-proof)** |
| **D** ChannelFromUser (`authors=me`) | 9,poll `#h` me | **History pager (all-authors) + tail + optimistic-send attach + host echo** → redundant |
| **E** ThreadFeed | 11,1111 `#h` | **KEEP** (Threads screen; separate `threadNotes` feed). Pager adoption is a follow-up. |
| **F** Notifications | 7,9,1111,1068,… `#h`+`#p=me` | **KEEP** — always-on, p-tags-me; unchanged bonus |
| §3 by-id (`filterMissingEvents`) | ids | **KEEP** — quotes/replies/mentions; **+ serving-relay fix below** |
| §3 pinned by-id backfill | ids `filterMetadataToRelayGroup` | **KEEP** (host relay; older-than-window pins) |
| §3 replies/reactions `#e/#q` | 1111 etc. | **KEEP** — comments never attach to timeline (by design) |

**Serving-relay keying hazard (real, pre-existing — fix as part of "can't miss").**
`attachToRelayGroupIfScoped` keys the channel by `GroupId(groupId, servingRelay)`.
All subscriptions here are host-pinned, so they're safe. But `filterMissingEvents`
can deliver a referenced group message from a **non-host** relay, filing it under a
different channel object than the host-keyed one the UI reads → cached but invisible.
Fix: when attaching a group-scoped content event, if exactly one existing
`RelayGroupChannel` carries that `groupId` (the joined/host one), attach there
instead of minting a `(groupId, servingRelay)` channel — reusing the existing
`singleOrNull`-by-groupId resolution already used for the `relay == null` optimistic
branch. Ambiguous ids (the relay-wide `_` group joined on several relays) keep
serving-relay keying.

## File-by-file changes

**New (`commons` model):**
- `RelayGroupChannel`: add `val history = RelayLoadingCursors()` (mirror `ConcordChannel.history`).

**New (`amethyst` datasource, per templates):**
- `RelayGroupStateFilterAssembler` (+ SubAssembler) — always-on roster.
- `RelayGroupPreviewTailFilterAssembler` (+ SubAssembler) — batched preview tail.
- `RelayGroupChatTailFilterAssembler` (+ SubAssembler) — per-open live tail.
- `RelayGroupChatHistoryFilterAssembler` (+ SubAssembler) — per-open history pager.
- Subscription composables for each (`*Subscription`), copying the Concord ones.

**Modified:**
- `RelaySubscriptionsCoordinator`: register the four new assemblers; drop the retired ones (see below).
- `LoggedInPage`: mount `RelayGroupStateSubscription` + `RelayGroupPreviewTailSubscription` (always-on, gated).
- `RelayGroupChannelView`: mount chat-tail + history subscriptions; wire
  `RefreshingChatroomFeedView(olderBoundary, markersInGap, sentinels)` + a
  `BackfillHistoryToWindow` (copy `ConcordBackfillHistoryToWindow`).
- `MessagesSinglePane`/`MessagesTwoPane`: drop `RelayGroupMyJoinedGroupsSubscription`
  (its roster role moves to the always-on state sub; previews come from the tail).
- `LocalCache.attachToRelayGroupIfScoped`: host-relay normalization (serving-relay fix).
- `AccountViewModel.dataSources()`: expose the four new assemblers; remove retired handles.

**Retired:**
- `RelayGroupMyJoinedGroupsFilterAssembler` **content path** → deleted; the file's
  roster role becomes `RelayGroupStateFilterAssembler` (rename/replace).
- `ChannelPublicFilterSubAssembler` **`RelayGroupChannel` branch** (`filterMessagesToRelayGroup`
  + `filterMetadataToRelayGroup`) → removed; metadata now always-on, content now tail+pager.
  (Keep `filterMetadataToRelayGroup`'s **pinned-id backfill** — re-home it on the chat-tail or a
  small pin sub so older-than-window pins still resolve.)
- `ChannelFromUserFilterSubAssembler` **`RelayGroupChannel` branch** (`filterMyMessagesToRelayGroup`) → removed.
- Keep `RelayGroupWarmup*` (non-joined cards), `RelayGroupsOnRelay*` (directory),
  `RelayGroupsDiscovery*` (discover feed), `RelayGroupThreadFeed*` (threads), and the
  notifications path unchanged.

## Rollout order (additive first, retire last — never a window where messages drop)

1. **Additive, no removals:** add `RelayGroupChannel.history`; add the four new
   assemblers + subscriptions + coordinator/dataSources handles + `LoggedInPage`
   and `RelayGroupChannelView` wiring. New content now flows through tail+pager
   **alongside** the old A/C/D (harmless dedup by id). Compile + smoke.
2. **Serving-relay normalization** in `LocalCache` (independent correctness fix).
3. **Retire** A-content, C-relay-group-branch, D-relay-group-branch; move roster to
   the always-on state sub; drop the Messages-pane `MyJoinedGroups` mount. Compile.
4. Re-home the pinned-id backfill; delete now-dead code; `spotlessApply`; full suite.

## Edge cases

- **Quiet group** (newest message older than the 7-day tail): won't appear in the
  preview tail; its Messages row falls back to cached / placeholder (same as NIP-04).
  Opening it → the history pager's eager backfill loads it. Optional: a one-shot
  newest-1 per quiet joined group in the state sub's initial snapshot.
- **Non-joined open group:** covered by the per-open chat tail + history pager
  (both per-`GroupId`, no joined-list dependency).
- **Reconnect:** tails carry `since=recentBoundary()` (time floor, shared-safe,
  incremental); history is `until`-based (position, not reconnect-sensitive);
  `FiltersChanged` already ignores `since`-only changes → no full replay.
- **Threads:** unchanged this pass; a follow-up can point `RelayGroupThreadFeed` at
  a second `BackwardRelayPager` on `RelayGroupChannel` for kind 11/1111.

## Testing

- Unit: preview-tail filter batches one `#h` filter per relay with
  `since=recentBoundary()` and no per-group limit; history filter emits only for
  armed relays at their `requestedUntil`; state filter emits roster `#d` per relay.
- Cursor behavior is already covered by `RelayLoadingCursors` tests (reused).
- Manual (amy / device): join a group after session start → open → history backfills;
  scroll up past the window → older pages load; reconnect → no full re-download;
  quote a group message from a non-host relay → it appears in the group.
