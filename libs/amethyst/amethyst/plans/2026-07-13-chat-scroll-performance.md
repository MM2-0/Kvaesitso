# Chat feed scroll performance — fast fling through thousands of messages

Status: plan. Owner: chat feed (`amethyst/.../ui/screen/loggedIn/chats/feed/`).

## Problem

A fling through a long chat history composes hundreds of `ChatroomMessageCompose`
rows per second. Each newly composed row currently pays for work that is either
(a) derivable off the composition path, (b) only needed for *recent* messages, or
(c) side-effectful (coroutines, relay-filter updates) and therefore multiplies
into churn under velocity. The redesign added per-row observers (group position,
reaction chips, delivery ticks) that are individually cheap but sum up at 1000+
rows.

## What is already fine (verified, don't re-litigate)

- **Rich text parse is cached**: `TranslatableRichTextViewer` →
  `CachedRichTextParser.parseText` behind `remember(content, tags)`; re-scrolling
  past a message doesn't re-parse.
- **Engagement flows are sampled**: `observeNoteReactions/Zaps/Replies` sample at
  200–500ms, and the EventFinder assembler folds all subscribed note ids into a
  small number of shared REQs (`SingleSubEoseManager`), not one REQ per note.
- **LazyColumn hygiene**: stable `key = idHex`, `contentType = kind`,
  `animateItem()` gated by performance mode.
- **Tracker fan-out fixed**: delivery state is one small StateFlow per message.

## Per-row cost inventory (composition of ONE new row during fling)

| # | Cost | Where | Class |
|---|------|-------|-------|
| 1 | 4× `dateFormatter` (ThreadLocal `SimpleDateFormat` for >24h-old messages) for group break checks, + 2 more in `NewDateOrSubjectDivisor` | `ChatGroupPosition.groupsWith`, `NewDateOrSubjectDivisor` | CPU, per row |
| 2 | 3× `collectAsStateWithLifecycle` on metadata flows | `watchChatGroupPosition` | collector churn |
| 3 | `LaunchedEffect` → `loadAndMarkAsRead(route, createdAt)` coroutine | `NormalChatNote` | 1 coroutine/row; read-marker lock churn |
| 4 | `LaunchedEffect` → `accountViewModel.decrypt(note)` for the jumbo flag | `NormalChatNote` | 1 coroutine/row, even for plaintext kinds |
| 5 | Reaction observer + zap observer subscriptions (ids added to shared relay filters; filter update per batch) | `ChatReactionChips`, `ObserveZapAmountText` | relay REQ re-issue churn under velocity |
| 6 | 2× flow collectors for delivery ticks on own messages, forever, even for long-settled history | `ChatDeliveryTicks` | collector churn |
| 7 | Per-row animation/gesture state: `animateColorAsState`, press `InteractionSource`, swipe `pointerInput`, highlight `LaunchedEffect` | `ChatBubbleLayout` | allocation, mostly unavoidable |
| 8 | Media/link previews kick Coil requests immediately | `TranslatableRichTextViewer` children | IO churn during fling |

Feed-level:

| # | Cost | Where |
|---|------|-------|
| 9 | `shouldHighlight = highlightedNoteId.value == item.idHex` reads one state in every item lambda → all visible items recompose when it changes (rare; low) | `ChatFeedLoaded` |
| 10 | `onScrollToNote` recreated when `ChatFeedLoaded` recomposes → unstable param defeats item skipping | `ChatFeedLoaded` |
| 11 | Feed invalidation rebuilds the sorted list; every reaction arriving during scroll can reorder/emit | `FeedContentState` (existing infra, sampled) |

## Plan

### Phase 0 — Measure first (do not skip)

1. **Macrobenchmark**: add a `benchmark` scenario that seeds `LocalCache` with
   5–10k synthetic `ChannelMessageEvent`s (mixed: text, links, emoji-only, a few
   with reactions/zaps) and flings the public-chat screen. Metrics:
   `frameDurationCpuMs` P50/P90/P99, jank %, `frameOverrunMs`.
2. **Composition tracing / recomposition counts** on a seeded room; compose
   compiler reports (`composables.txt`) for `ChatroomMessageCompose`,
   `NormalChatNote`, `InnerChatBubble`, `ChatReactionChips` — verify skippability
   and find unstable params (expect #10).
3. Record baseline numbers in this file before changing anything.

### Phase 1 — Kill per-row CPU and coroutines (expected biggest wins)

4. **Day-stamp grouping (#1)**: replace `dateFormatter` equality in `groupsWith`
   with an epoch-local-day integer comparison (`(createdAt + zoneOffsetSeconds) / 86400`),
   and give `NewDateOrSubjectDivisor` the same predicate so the break condition
   stays mirrored (only its *display* string needs formatting, and only when a
   divider actually renders). Zero `SimpleDateFormat` on the scroll path.
5. **Hoist read-marking (#3)**: replace the per-row `LaunchedEffect` with one
   feed-level `snapshotFlow { listState.firstVisibleItem createdAt }`-driven
   marker update (only the newest visible timestamp matters; the marker is
   monotonic). One coroutine per scroll session instead of one per row.
6. **Jumbo without a coroutine (#4)**: only launch the decrypt effect for
   encrypted kinds (`EncryptedDmEvent`, sealed rumors not yet in the decrypt
   cache). Plaintext kinds (public chats, NIP-17 rumors already unwrapped —
   the vast majority) take the synchronous path only.
7. **Retire settled delivery ticks (#6)**: once a message is fully accepted (or
   untracked and older than the tracker window), render the tick from a
   `remember`ed terminal value and drop both collectors. Only in-flight sends
   keep live flows.
8. **Group position with fewer collectors (#2)**: collect the three metadata
   flows only while any of the three notes is missing `event`/`author`
   (the common case for history is all-loaded → pure `remember`, no collectors).

### Phase 2 — Tame side-effect churn under velocity

9. **Defer new relay-filter membership while flinging (#5)**: gate
   `EventFinderFilterAssemblerSubscription` enrollment on
   `!listState.isScrollInProgress` (or debounce enrollment ~300ms): rows that
   fly past never join the reaction/zap filters; rows you settle on subscribe
   as today. Needs a small `LocalScrollSettled` composition local (provided by
   `ChatFeedLoaded`) so `ChatReactionChips`/`ChatDeliveryTicks` can wait without
   threading params. Verify with #1 measurements that filter re-issues drop.
10. **Defer media loads (#8)**: same settled-gate for the image/video preview
    composables inside chat bubbles (placeholder immediately, Coil request on
    settle). Coil cancels in-flight requests on dispose already, but not
    starting them is cheaper than cancel.
11. **Stabilize item lambdas (#10)**: `remember(items.list, listState)` around
    `onScrollToNote`; pass `shouldHighlight` via `derivedStateOf` keyed per item
    (#9) so only the highlighted row recomposes.

### Phase 3 — Structural (only if Phase 1/2 measurements demand more)

12. **Precomputed row model**: build a lightweight `ChatFeedRow(note, groupPos,
    dayStamp, isJumbo, isSystem)` list inside `FeedContentState` (background
    thread, once per feed update) so item composition becomes pure rendering.
    This subsumes #4/#8 above but is a bigger refactor of shared feed infra —
    justify with numbers first.
13. **Finer contentType**: distinguish `bubble / jumbo / system / zap-card`
    contentTypes so Lazy slot reuse doesn't rebuild structurally different rows.
14. **Prefetch tuning**: evaluate `LazyListPrefetchStrategy(nestedPrefetchItemCount)`
    for the fling case on Compose ≥1.7.

### Phase 4 — Regression guardrails

15. Wire the Phase-0 macrobenchmark into CI (or at least a documented manual
    run before releases touching the chat feed), and re-record numbers here.

## Non-goals

- Virtualizing bubble internals (Compose already skips off-screen work).
- Caching parsed rich text more aggressively (already LRU-cached).
- Changing feed sorting/invalidation infra (shared with all feeds; separate plan
  if measurements point there).
