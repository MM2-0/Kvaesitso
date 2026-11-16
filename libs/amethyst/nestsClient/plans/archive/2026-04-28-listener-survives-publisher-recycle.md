# Listener-survives-publisher-recycle: resolution log

> **Status:** shipped — resolution log; all three reconnecting-listener interop scenarios pass against the real moq-rs relay (fixes landed on `851045c6` + Round 2).
> _Audited 2026-06-30._

**Status:** ✅ All three reconnecting-listener interop scenarios
pass against the real moq-rs relay: happy-path, session-swap,
and listener-survives-publisher-recycle. Two further fixes landed
on top of `851045c6` to close the session-swap gap; see "Round 2"
below.

## What was broken (recap)

When a remote speaker JWT-refreshed (every 9 min via
`connectReconnectingNestsSpeaker`), any listener with a vanilla
`SubscribeHandle` open against that speaker's broadcast went silent.
The listener's session stayed Connected; the listener's
`MoqLiteSubscribeHandle.frames` `Channel.consumeAsFlow()` just sat
open waiting for frames that never arrived. moq-lite Lite-03 has no
explicit "publisher gone" message on the subscribe bidi (the relay
keeps that bidi open across publisher cycles in case a fresh
publisher takes over the suffix), so the announce stream's
`Announce(Ended)` event is the only reliable signal.

## What landed (commit `851045c6`)

**Session layer** (`MoqLiteSession.kt`):

- Lazy single shared announce-watch pump per session, opened on
  first subscribe.
- On `Announce(Ended)` for a broadcast suffix, close the matching
  `ListenerSubscription`'s frames `Channel` and remove it from the
  map.

**Wrapper layer** (`ReconnectingNestsListener.kt`):

- Inner `while (currentCoroutineContext().isActive)` loop in
  `reissuingSubscribe`. When the underlying frames flow completes
  (signalled by the session layer), re-issue subscribe against the
  same listener with a 100 ms backoff. moq-lite supports
  subscribe-before-announce so the new subscribe attaches cleanly
  when the next publisher comes up under the same suffix.

**Verified against the real moq-rs relay** (host build, external
mode): the new
`NostrNestsReconnectingListenerInteropTest.subscribe_handle_survives_publisher_recycle`
test passes — single SubscribeHandle keeps emitting frames across
multiple speaker JWT-refresh cycles. Speaker reconnect tests still
pass too.

## Round 2 — closing the session-swap gap (commit `d8ab4fd9`)

The `reconnecting_wrapper_keeps_handle_alive_across_session_swap`
test exposed two compounding issues that round 1 didn't address:

1. **Orchestrator break-on-Closed** — `if (terminal is Closed) break`
   in `ReconnectingNestsListener.kt` exited whenever the inner
   listener went Closed for ANY reason, including the test's own
   `firstListener.close()`. Removed the break: user-driven
   `reconnecting.close()` already cancels the orchestrator
   coroutine separately, so any other Closed (peer transport drop,
   recycle) is now a reconnect trigger.

2. **Publisher single-group architecture** —
   `NestMoqLiteBroadcaster` only ever called `publisher.send(opus)`,
   never `endGroup()`. The entire broadcast was one giant moq-lite
   group; a subscriber that joined mid-broadcast got nothing
   because `from-latest` subscribe semantics give the NEXT group's
   frames, and the publisher was in a never-ending group. Fixed by
   adding `publisher.endGroup()` after each send — one Opus frame
   per moq-lite group, mirroring the kixelated reference's audio
   publish path.

Three companion changes in `MoqLiteSession.kt` were needed to make
those work cleanly:

- `ensureAnnounceWatchStarted()` runs synchronously before the
  first subscribe, so the relay sees us as an audience member
  before we ask to subscribe (otherwise it returns "not found").
- `handleInboundBidi` refactored to a single long-running
  collector with the varint `typeCode` hoisted outside `collect`
  (an earlier draft re-read the body bytes as the type code on the
  second collect pass).
- `removeInboundSubscription(sub)` FINs the publisher's
  `currentGroup` when an inbound subscribe bidi closes, so the next
  send opens a fresh uni stream keyed off a live subscriber rather
  than the dead one that was first in the inboundSubs set.

The round-trip interop test's groupId assertion was updated from
`groupId == 0` to `groupId == idx` to match the new
one-group-per-frame contract.
