# Concord epoch-walking message backfill

**Date:** 2026-07-17
**Module:** `commons` (drivers), with `quartz` primitives (present) + `amethyst` wiring
**Status:** design / not started

## Problem

A Concord community that has been **refounded** (CORD-06 §3 — hard removal
rotates `community_root` and bumps the epoch) loses all of its pre-refounding
chat history from the client's view. Every channel message is encrypted under a
plane stream key derived from the *root at the epoch it was authored under*
(`publicChannel(community_root, channel_id, rootEpoch)`), and the message carries
an `["epoch", n]` tag so cross-epoch replay is rejected
(`ChannelChat.isBoundTo`). The client only ever derives, subscribes to, AUTHs
as, and decrypts the plane at the **single current** `entry.rootEpoch`. So after
a refounding the feed shows only messages authored since that refounding, then
reports **"All caught up"** — which is literally correct *for the current
epoch*, while months of history sit on the same relays under the previous
epochs' stream keys, unfetched.

### Reproduced live (2026-07-17, `amy` vs the real Soapbox Community)

Joined the Armada invite and probed `relay.ditto.pub` + `relay.dreamith.to`:

| Fact | Value |
|---|---|
| `rootEpoch` (from invite bundle) | **2** — refounded ≥ once |
| nostrhub @ epoch 2 | 2 messages, both authored **2026-07-17** |
| general @ epoch 2 | 62 messages, **oldest 2026-07-16** |
| ditto @ epoch 2 | 4 messages, **oldest 2026-07-16** |
| agora @ epoch 2 | 3 messages, **oldest 2026-07-16** |

Every channel's history begins on 2026-07-16 (the refounding date). The app's
"only 2 messages in nostrhub" reproduces exactly through the CLI. Not a relay,
AUTH, or paging-cap issue — a structural epoch gap.

## Key finding: prior roots are already retained but never consumed

The hard part — keeping the old keys — **is already done**:

- `ConcordCommunityListEntry.heldRoots: List<HeldRoot(epoch, key)>`
  (`quartz/.../cord02Community/ConcordCommunityList.kt:31,60`, wire field
  `held_roots`). `HeldRoot`'s own KDoc: *"A past root key for a specific epoch,
  kept so historical channel keys stay derivable."*
- `Account.adoptConcordRoot` (`amethyst/.../model/Account.kt:2513`) appends the
  outgoing `(rootEpoch, root)` to `heldRoots` on **every** rotation — both the
  refounder path (`refoundConcordCommunity`) and the receive path
  (`drainConcordRekeys` → `openBaseRekey`). It is persisted to the account's
  Concord list event.

But a repo-wide grep confirms **no code in the fetch / fold / subscribe path
ever reads `heldRoots`.** `ConcordCommunitySession`, `ConcordSessionRegistry`,
`ConcordSubscriptionPlanner`, and the amethyst filter assemblers all derive
planes solely from `entry.rootEpoch` / `entry.root`. The retained roots are dead
data. **This plan is almost entirely about consuming `heldRoots` on the read
side** — the retention infrastructure the feature needs already exists.

### Scope boundary — who this helps

- **Members present across the refoundings** (have populated `heldRoots`): can
  fully backfill. Primary target.
- **Fresh joiners via an invite** (empty `heldRoots`; the invite bundle carries
  only the current root): **cryptographically cannot** decrypt prior-epoch
  history — they were never given those roots, and the relays gate each epoch's
  kind-1059 behind AUTH-as-that-epoch's-stream-key. This is by design (a
  refounding is meant to sever access) and is **out of scope**. We must not try
  to work around it by stuffing old roots into invites — that would hand a
  brand-new member the keys a removal was meant to deny. If cross-refounding
  history for new joiners is ever wanted, it's a *protocol* change (Armada would
  re-publish compacted history under the new root), tracked separately.

## Design

`ConcordCommunitySession` is documented as "a pure function of its entry" and is
rebuilt wholesale on rotation (`ConcordSessionRegistry.sync` replaces it when
`root`/`rootEpoch` changes). Two ways to add historical epochs:

- **(A) Generalize the session to be multi-epoch** — derive a plane key set per
  `(epoch, root)` in `{current} ∪ heldRoots`, index buffered wraps and decrypt
  attempts across all of them.
- **(B) Keep the current-epoch session as-is (live + write) and attach
  read-only "historical epoch readers"** — one lightweight derivation set per
  held root, contributing subscribe addresses + AUTH keys + decrypt attempts,
  but never used for writing/moderation/rekey.

**Recommend (B).** Writes, moderation, control-plane folding, rekey adoption,
and Guestbook all must stay strictly on the current epoch — mixing historical
roots into those paths risks authoring under a stale key or re-folding a
superseded control plane. A read-only historical layer keeps the blast radius to
message fetch/decrypt. The channel id is **epoch-invariant** (`ConcordChannelKeys`
KDoc: "stays constant across visibility conversions and epoch rotations"), so a
decrypted historical message lands in the *same* channel feed as current ones —
no feed-merge logic needed; `LocalCache`/the gatherer keys on channel id.

### New concept: `EpochPlaneSet`

A small value type: for one `(epoch, root)`, derive the control-plane key and,
given the current folded channel-id list, the per-channel `publicChannel` keys.
The current epoch already computes this inline in `ConcordCommunitySession`;
factor the derivation into a reusable helper so current + historical share it.

> Channel *membership* comes from folding the **current** control plane (channels
> aren't re-listed per epoch). We assume the channel-id set is stable across the
> covered epochs (channels created after an old epoch simply have no messages
> there → empty, harmless). Deleted/renamed channels: the id persists, so old
> messages still decrypt. Private channels: use `entry.privateChannels`
> (`PrivateChannelKey` already carries a per-`epoch` key) instead of the root.

## Component-by-component changes

### 1. `quartz` — none required (primitives already exist)
`ConcordActions.publicChannel/controlPlane/channelRumors/channelMessages` and
`ChannelChat.isBoundTo` already take an explicit `epoch`. Reuse verbatim.

### 2. `commons/.../model/concord/ConcordCommunitySession.kt`
- Build historical `EpochPlaneSet`s from `entry.heldRoots` (bounded — see
  §Bounding) alongside the existing current-epoch derivation.
- `channelAddresses()` → also emit each historical channel plane pubkey so the
  planner subscribes to them.
- `streamKeys()` → include historical control + channel `GroupKey`s so the
  NIP-42 AUTH set authenticates as each prior epoch's stream key (this is what
  unlocks the gated relays for old wraps). Keep the aux (Guestbook / next-rekey)
  isolation rule intact.
- `ingest(wrap)` → currently matches a wrap by its plane address against the
  current-epoch address map. Extend the address→(channelId, key, **epoch**) map
  to include historical entries; on match, decrypt with that epoch's key and
  validate `isBoundTo(rumor, channelId, thatEpoch)`. Emit the rumor exactly as
  today (same `onRumor` sink → same channel feed).
- Historical wraps feed `observedAuthors` too — a nice side effect: the member
  roster harvest gets the full-history posters for free.
- Do **not** route historical wraps into `refold()` (control plane),
  `refoldGuestbook()`, or rekey buffers — read path only.

### 3. `commons/.../model/concord/ConcordSessionRegistry.kt`
`subscribeAddresses()` already unions `session.channelAddresses()`; once the
session emits historical addresses it flows through unchanged. Verify no other
call site assumes one-address-per-channel.

### 4. `commons/.../actions/ConcordSubscriptionPlanner.kt`
`channelPlaneSubs()` derives `publicChannel(root, channelId, entry.rootEpoch)`.
Generalize to emit a plane sub per `(channel, epoch)` across the covered epoch
set, collapsed into the existing `{kinds:[1059,21059], authors:[…]}` batching in
`relayBasedFilters()`. The historical subs can be one-shot (no live tail needed —
old epochs are frozen), so consider a bounded `until`-less REQ that EOSEs rather
than a standing subscription, to cap connection cost.

### 5. `amethyst/.../concord/datasource/` filter assemblers
- `ConcordChannelHistoryFilterAssembler` (`BackwardRelayPager`) — today pages one
  plane pubkey (`session.channelPlaneAddress(channelId)`, current epoch). The
  pager must **step to the previous epoch's plane pubkey when the current epoch
  is exhausted** rather than declaring `PagingStatus.exhausted`. Options:
  (a) page all epoch planes concurrently and only report exhausted when every
  epoch's relays are done; (b) sequential — walk newest→oldest epoch. (a) is
  simpler to reason about with the existing per-(uniqueId,relay) EOSE tracking;
  (b) gives cleaner "load older" UX. Prefer (a).
- `ConcordChannelFilterAssembler` (live tail) — historical planes need no live
  tail; only the current epoch keeps a standing sub.
- **"All caught up"** (`ConcordChannelScreen.kt:199` on `historyStatus.exhausted`)
  becomes correct once exhaustion means "all covered epochs drained," not "the
  current epoch drained."

### 6. AUTH — register historical stream keys
`ConcordSessionManager.streamAuthSecretsFor(relay)` derives from
`session.streamKeys()`; once that includes historical keys, `AuthCoordinator`
signs one kind-22242 per prior-epoch stream key and the gated relays serve the
old wraps. Watch the `RelayAuthStatus` LruCache size (widened 10→200 for the
current-epoch multi-identity work) — N epochs × M channels can exceed 200; size
it to `epochs × (channels + 1)` with headroom.

### 7. `cli` — diagnostics (`ConcordChannelCommands.read`) — **DONE**
`StoredCommunity` has no `heldRoots`, and a fresh `amy concord join` can't obtain
them — so amy can't self-serve a member's history. Landed:
- `amy concord read <community> <channel> --epoch <n> --root <hex>` — derives the
  Chat Plane at an explicitly supplied `(epoch, root)` and drains it (both flags
  default to the stored current epoch/root; channel-id resolution stays on the
  current epoch since ids are epoch-invariant). Output now also emits `epoch` and
  the derived `plane` pubkey. Verified: explicit `--epoch 2 --root <current>`
  reproduces the stored plane pubkey byte-for-byte; each epoch derives a distinct
  plane; a non-hex `--root` errors `bad_args`/exit 2. Confirms old-epoch wraps can
  be probed once a prior root is known.
- **DONE:** `StoredCommunity.heldRoots` + `amy concord import` — fetches this
  account's own encrypted kind-13302 `ConcordCommunityListEvent`, decrypts it
  with the account signer, and upserts every community **including its
  `heldRoots`** (the prior-epoch access roots Amethyst persists in that same
  published event via `Account.adoptConcordRoot`). `read --epoch <n>` then
  auto-resolves the root for that epoch from the stored `heldRoots` (explicit
  `--root` still wins). So a member who lived through the Refoundings can:
  `amy concord import` → `amy concord read <community> <channel> --epoch <n>`
  and reach pre-refounding history without knowing the raw prior roots. A fresh
  account simply imports empty `heldRoots` (nothing to recover — the expected
  cryptographic wall). Import + decrypt are **read-only** (no publish).

## Bounding (cost control)

Each covered epoch multiplies the subscription/AUTH footprint by
`(channels + 1)` stream keys. Bound it:
- **Config:** `CONCORD_BACKFILL_EPOCHS` (default: all held — the list is small in
  practice; refoundings are rare). If a community is refounded often, cap to the
  N most recent held epochs.
- **Time window:** the member-roster harvest already bounds to
  `now − 90d` (`ConcordMemberHarvest`, `CONCORD_MEMBER_HARVEST_WINDOW_SECS`).
  The *interactive* channel backfill should be user-driven (paged on scroll, no
  `since` floor) so a member can reach the true beginning; the *background*
  harvest keeps its window.
- Historical planes are frozen → prefer one-shot EOSE REQs over standing subs to
  avoid holding N× subscriptions open forever.

## Testing

- **quartz** — none new (primitives unchanged); existing epoch/`isBoundTo` tests
  cover the binding.
- **commons unit** (`ConcordCommunitySessionTest`, `ConcordSubscriptionPlannerTest`,
  `ConcordSessionRegistryTest`):
  - Build an entry with `heldRoots = [(epoch0,rootA),(epoch1,rootB)]`, current
    epoch 2/rootC. Assert `channelAddresses()`/`streamKeys()`/planner subs emit
    a plane per `(channel, epoch)` across all three.
  - Feed the session wraps authored under each historical key; assert the rumor
    is decrypted with the *matching* epoch key, `isBoundTo` passes, and it
    reaches the `onRumor` sink; a wrap whose epoch tag ≠ its plane epoch is
    dropped.
  - Assert historical wraps do **not** enter `refold()`/control state.
- **commons paging** (`BackwardRelayPagerTest`): a multi-epoch channel reports
  `exhausted` only after every epoch's relays EOSE on an empty page.
- **Live, via `amy`**: use the new `--epoch/--root` diag against Soapbox once a
  prior Soapbox root is available (ask maintainer / capture from an account that
  lived through the refounding) → confirm epoch-1 nostrhub wraps decrypt.

## Risks / open questions

1. **Fresh joiners still see nothing pre-refounding** — inherent, documented
   above. UI could show a "History before <date> requires having been a member"
   affordance instead of a bare "All caught up," so it doesn't read as a bug.
2. **AUTH fan-out on gated relays** — N epochs × M channels AUTH events on one
   connection. Current-epoch work already accumulates multiple identities on one
   connection successfully; validate it scales (LruCache sizing, relay
   per-connection AUTH limits). Fall back to bounding epochs if a relay balks.
3. **`heldRoots` completeness** — only populated from the moment the account
   started adopting rotations. A member who joined at epoch 2 has no epoch-0/1
   roots even if present later; nothing to do — same cryptographic limit.
4. **Private channels across epochs** — `PrivateChannelKey.epoch` exists, but
   verify a private channel's key was actually re-delivered per epoch (rotated on
   revocation); if a member missed an epoch's private key, that epoch of that
   channel is unreadable (expected).
5. **Standing-sub vs one-shot for history** — decide before wiring the planner;
   affects connection budget on the audio-room-heavy relay set.

## Suggested sequence

1. ~~Factor derivation + make `ConcordCommunitySession` emit historical
   addresses/keys/decrypt~~ **DONE.** `ConcordActions.historicalChannelPlanes`
   (bounded by `MAX_BACKFILL_EPOCHS = 8`; 0 disables) + `HistoricalChannelPlane`;
   the session keeps a `historicalChannelKeysByAddress` map derived in `refold()`,
   folded into `channelAddresses()` (subscribe), `streamKeys()` (AUTH), and
   `ingest()` (decrypt with the matching epoch, `isBoundTo` per epoch). Test:
   `ConcordCommunitySessionTest.ingestsPriorEpochWrapsFromAHeldRoot`.
2. ~~Planner + AUTH wiring~~ **DONE.** `ConcordSubscriptionPlanner.channelPlaneSubs`
   appends the historical planes → the existing `ConcordChannelFilterAssembler`
   subscribes to them with no change; AUTH flows through `session.streamKeys()`.
   Test: `ConcordSubscriptionPlannerTest.channelSubsAlsoCoverPriorEpochPlanesForHeldRoots`.
   The live channel sub now pulls prior-epoch wraps into `LocalCache`, so
   pre-Refounding messages appear on channel open (bounded by relay cap / `since`).
3. ~~amethyst `BackwardRelayPager` epoch-stepping + "All caught up" semantics~~
   **DONE.** Rather than step epoch-by-epoch, the history REQ now asks for the
   **union** of the channel's plane pubkeys across every held epoch
   (`ConcordCommunitySession.channelPlaneAddressesAllEpochs`, used by
   `ConcordChannelHistoryFilterAssembler`). The relay serves them interleaved by
   `created_at`, so one backward `until` sweep walks the whole cross-Refounding
   timeline and `exhausted` ("All caught up") means every epoch is drained. Pager
   itself unchanged. No `ConcordChannelScreen` change needed.
4. ~~`amy --epoch/--root` diagnostic~~ **DONE** (§7). Cross-validated: the app now
   subscribes to the exact prior-epoch plane pubkeys `amy concord read --epoch 0`
   proved hold the older Soapbox #nostrhub messages (identical `publicChannel`
   derivation).
5. **TODO** — on-device verify on a refounded community (emulator Concord fold is
   historically flaky; verify when it cooperates).
