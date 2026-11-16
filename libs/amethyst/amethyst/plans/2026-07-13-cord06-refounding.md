# CORD-06 Refounding — real member removal for Concord

## Problem

Concord membership is key possession: a banned member (CORD-04 banlist) still
holds the community's `community_root`, so every client just *declines to show*
their posts — they can still decrypt everything. That is a soft removal. CORD-06
adds the hard removal: rotate the key so a removed member's key stops working for
anything sent afterwards.

The quartz crypto for the kind-3303 rekey blob (`ConcordRekey`, `RekeyBlob`)
already existed and was tested, but nothing in the app called it. This wires the
whole path — build, publish, receive, persist, UI — around a **Refounding**
(whole-community rotation), the removal that matters while Amethyst supports only
public channels (a per-channel rekey needs private channels, not built yet).

## What a Refounding does (CORD-06 §3)

1. Ban the removed members on the current Control Plane (so the compacted snapshot
   carries the ban).
2. Roll `community_root` to a fresh random 32 bytes at `rootEpoch + 1`. Public
   channels + the Control/Guestbook planes all derive from the root, so rolling it
   rotates every plane at once.
3. Republish the **compacted** Control Plane under the new root — keep only each
   entity's head edition and re-wrap its *original plaintext seal*, so the original
   authors' signatures survive re-encryption (a fresh joiner verifies the slim
   state exactly as it verified the full chain).
4. Mint per-recipient kind-3303 rekey blobs delivering the new root to every
   retained member, sealed + addressed under the **prior** root on the
   `base-rekey-pseudonym(prior_root, community_id, new_epoch)` address — which every
   current member precomputes, so they receive it live. A removed member gets no
   blob and can never derive the new root.

## Layers

- **quartz** `concord/cord06Rekey/`
  - `ConcordKeyDerivation`: `baseRekeyAddress` / `channelRekeyAddress` (the rekey
    stream addresses), `epochKeyCommitment` (`prevcommit`, CORD-02 §A.5).
  - `ConcordRekey`: signer-based `blobForSigner` / `findNewKeyWithSigner` (bunker
    accounts open a blob with one `nip44Decrypt`, no raw key).
  - `ConcordRefounding`: `compactControlPlane`, `buildBaseRekeyWraps`, `build`
    (whole refounding), `findNewRoot` (receive: verify scope/epoch/continuity, find
    my blob). `OpenedStreamEvent` now also carries the inner `seal` so compaction
    can re-wrap it. Tests in `ConcordRefoundingTest`.
- **commons**
  - `ConcordActions`: `guestbookPlane` / `nextBaseRekeyPlane`, `buildGuestbookJoin`
    / `guestbookMembers`, `buildRefounding`, `openBaseRekey`.
  - `ConcordCommunitySession`: folds the Guestbook plane into `members`
    (the recipient set), buffers inbound base-rekey wraps (`pendingBaseRekeyWraps`),
    exposes `controlPlaneWraps` for compaction, and AUTHs to + subscribes the
    Guestbook and next-epoch base-rekey planes (`streamKeys`, `subscribeAddresses`).
  - `ConcordSessionRegistry.sync`: rebuilds a session when its entry's root/epoch
    changed — the session is a pure function of its entry, so adopting a new root is
    just a persisted entry swap.
  - `ConcordSubscriptionPlanner.auxiliaryPlaneSubs`: REQs the Guestbook + next
    base-rekey planes for every joined community.
- **amethyst**
  - `Account`: announces a Guestbook JOIN on create/join (`announceConcordGuestbookJoin`)
    so members are visible to a future rotator; `refoundConcordCommunity` (owner /
    BAN-holder) bans + rolls + publishes + persists; `drainConcordRekeys` (revision
    tick) adopts an inbound rotation from an authorized rotator; `adoptConcordRoot`
    persists the new root (prior root kept as a `HeldRoot`) and re-seeds the new
    epoch's Guestbook, guarded against double-adopt.
  - `AccountViewModel.removeConcordMember`; `ConcordMembersScreen` "Remove from
    community" action + confirm dialog, gated exactly like Ban.

## Recipient set

The rotator re-keys **Guestbook membership ∪ the privileged roster ∪ self**, minus
the removed and the already-banned. The Guestbook is best-effort/off-consensus, so
a member who joined but whose Guestbook JOIN hasn't propagated to the rotator would
be missed and locked out — the accepted trade for a serverless, key-possession
membership model. Adopting a new root re-announces the Guestbook JOIN at the new
epoch so cascading removals keep a live membership.

## Known limitations / follow-ups

- No explicit "you were removed" detection: a removed member simply stops receiving
  new content (their old-epoch keys still read history). CORD-06's "held all n
  chunks, none is mine ⇒ removed" self-eviction is not implemented.
- Per-channel rekey (single private channel) is not wired — needs private channels.
- Race convergence (two rotators, same epoch, lexicographically-lowest-key wins) is
  not implemented; single-rotator (owner/admin) refounding is the supported path.
