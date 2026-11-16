# moq-lite Lite-03 compliance audit

> **Status:** shipped — audit found no wire-incompatibilities; the nine spec tightenings + Lite-04 codec/ALPN it recommended all shipped end-to-end (per the completion plan).
> _Audited 2026-06-30._

**Date:** 2026-05-09
**Branch:** `claude/audit-moq-lite-compliance-NSuPk`
**Scope:** `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/` plus
the audio glue under `…/audio/` that drives moq-lite. The IETF
`draft-ietf-moq-transport-17` reference impl in `…/moq/` (no `lite/`)
is OUT of scope — it stays as a unit-test target only. The QUIC layer
(`:quic`) is OUT of scope; recently audited and locked.

## TL;DR

The moq-lite Lite-03 implementation is **wire-compatible** with
kixelated's reference relay (moq-rs / moq-lite-03). All on-the-wire
codec primitives — control type discriminators, AnnouncePlease,
Announce, Subscribe (incl. the `Option<u64>` off-by-one and `Duration`
millis-as-varint conventions), SubscribeOk/Drop (with the
type-outside-size-prefix framing peculiar to Lite-03), GroupHeader,
Probe — match the reference Rust impl byte-for-byte. The known gaps
are all spec-loose / future-fragile (🟡 / 🟦), not wire-incompatible
(🔴).

**This audit shipped nine fixes** (M1, M2, M3, M4, M5, L1, L2, L3,
L5) and **closed M6** (verified via WebFetch that Goaway has no
body in the spec, so our existing handler is canonical). **L4**
is subsumed by the M1 fix. M2/M3 originally deferred under the
"`:quic` is locked" constraint; after a merge from `main` brought
in the full RFC-compliant `:quic` layer, the user lifted the
constraint and M2/M3 landed end-to-end. L1/L2/L3 originally
parked as 🟦 non-fixes; the user re-prioritised them and all
three landed with regression tests. **Every gap from the matrix
is now resolved (✅) or explicitly closed.** No 🔴, 🟡, or 🟦
items remain open.

## Methodology

1. Walked every Kotlin file under `moq/lite/` and traced data flow
   speaker → relay → listener.
2. Cross-referenced each on-wire message + control byte against
   kixelated's reference Rust impl at
   `https://github.com/kixelated/moq/tree/main/rs/moq-lite/src/`,
   reading specifically:
   - `coding/decode.rs`, `coding/encode.rs`, `coding/varint.rs`
     (primitive codings — confirmed `Option<u64>` is `0=None /
     n=Some(n-1)`, `Duration` is `millis-as-varint`, `bool` is 1
     byte 0/1).
   - `lite/stream.rs` (ControlType + DataType discriminator codes).
   - `lite/announce.rs` (AnnouncePlease + Announce body shape).
   - `lite/subscribe.rs` (Subscribe + SubscribeResponse Lite-03
     framing).
   - `lite/publisher.rs` (`Publisher::serve_group` priority handle,
     uni-stream open + group header).
   - `lite/subscriber.rs` (Probe direction, group decode loop,
     unsubscribe = FIN).
   - `lite/probe.rs` (Probe body: `bitrate u62` only on Lite-03).
3. Verified the team's prior `2026-04-26-moq-lite-gap.md` against
   live source — every claimed item still holds (see "Verified
   working" below).
4. Built the gap matrix (see "Gap matrix" below).
5. Shipped fixes for the two 🟡 items closest to being 🔴 — see
   "Fixes shipped" at bottom.
6. Ran `:nestsClient:jvmTest` after each fix — green throughout.

## What's verified working

The shipped Lite-03 surface holds up against the reference Rust impl
at the byte / control-flow level:

| Surface                                                                                                                              | Verdict | Evidence (file:line) |
| ------------------------------------------------------------------------------------------------------------------------------------ | ------- | -------------------- |
| ALPN `"moq-lite-03"` advertised as the only sub-protocol                                                                             | ✅       | `MoqLiteAlpn.kt:47`, `QuicWebTransportFactory.kt:88-113` |
| No SETUP / no in-band session message (Lite-03 = WT handshake IS the handshake)                                                      | ✅       | `MoqLiteSession.kt:1374-1378` (`client(...)` returns immediately, no SETUP coroutine) |
| `ControlType` codes — `Session=0, Announce=1, Subscribe=2, Fetch=3, Probe=4, Goaway=5`                                               | ✅       | `MoqLiteControlCodes.kt:39-65` (all five recognised + dispatched in `handleInboundBidi`) |
| `DataType::Group=0` on uni-stream open                                                                                               | ✅       | `MoqLiteControlCodes.kt:74-85`, `MoqLiteSession.kt:1047` |
| Path normalisation (strip leading/trailing `/`, collapse runs)                                                                       | ✅       | `MoqLitePath.kt:44-99` (matches `rs/moq-lite/src/path.rs::Path::new`) |
| Path normalisation applied at every wire boundary                                                                                    | ✅       | `MoqLiteCodec.kt:54, 70, 92` (encode side); `:60, 81, 105` (decode side) |
| AnnouncePlease body shape — `prefix: string`                                                                                         | ✅       | `MoqLiteCodec.kt:52-63` |
| Announce body shape — `status u8, suffix string, hops u62 varint`                                                                    | ✅       | `MoqLiteCodec.kt:67-85`; status enum `Ended=0, Active=1` matches `rs/moq-lite/src/lite/announce.rs:84-90` |
| Subscribe body — 8 fields: `id u62, broadcast string, track string, priority u8, ordered u8, maxLatency varint, startGroup, endGroup` | ✅       | `MoqLiteCodec.kt:89-100` |
| `Option<u64>` off-by-one collapse: `0=None, n=Some(n-1)`                                                                             | ✅       | `MoqLiteCodec.kt:286-288` matches `rs/moq-lite/src/coding/decode.rs::impl Decode for Option<u64>` |
| `Duration` encoded as millis-as-varint                                                                                               | ✅       | `MoqLiteCodec.kt:96` matches `rs/moq-lite/src/coding/encode.rs::impl Encode for std::time::Duration` |
| SubscribeResponse framing: type discriminator OUTSIDE size prefix (Lite-03+)                                                          | ✅       | `MoqLiteCodec.kt:152-167` matches `rs/moq-lite/src/lite/subscribe.rs::SubscribeResponse::encode` Lite-03 arm |
| SubscribeOk body — 5 fields (priority, ordered, maxLatency, startGroup, endGroup) without id/broadcast/track                         | ✅       | `MoqLiteCodec.kt:127-135` |
| SubscribeDrop body — `errorCode varint + reasonPhrase string`                                                                         | ✅       | `MoqLiteCodec.kt:137-142` |
| Group uni-stream layout: `DataType=0` + size-prefixed `(subscribeId, sequence)` + size-prefixed frame loop until FIN                | ✅       | `MoqLiteSession.kt:1021-1050` (publisher), `:594-668` (listener `drainOneGroup`) |
| Probe body — `bitrate u62 varint` only (rtt is Lite-04+)                                                                              | ✅       | `MoqLiteCodec.kt:262-266` (encode), `:247-251` (decode) |
| Probe direction — subscriber opens bidi, publisher writes Probe messages                                                             | ✅       | `MoqLiteSession.kt:925-944` (publisher arm of `handleInboundBidi`) |
| Subscribe id allocation — per-session, monotonic, varint                                                                             | ✅       | `MoqLiteSession.kt:83, 246-251` (incremented under `state` mutex) |
| Group sequence allocation — per-publisher, monotonic, varint, hot-swap-aware (`startSequence` parameter)                              | ✅       | `MoqLiteSession.kt:716-746, 1129-1132, 1306-1329` |
| Per-group end = QUIC FIN                                                                                                              | ✅       | `MoqLiteSession.kt:1264-1271` (`endGroup`) |
| Broadcast ended = `Announce(Ended)` BEFORE FIN (correct ordering)                                                                    | ✅       | `MoqLiteSession.kt:1273-1304` (`PublisherStateImpl.close`) |
| Unsubscribe = FIN subscribe bidi's send side (no UNSUBSCRIBE message)                                                                | ✅       | `MoqLiteSession.kt:670-675` |
| Reliable QUIC streams for groups (NOT bestEffort; matches kixelated)                                                                 | ✅       | `MoqLiteSession.kt:1024-1039` + comment block explaining the prior bestEffort regression |
| Stream priority hint — newer groups (higher sequence) drain first under congestion                                                   | ✅       | `MoqLiteSession.kt:1040-1046`; sequence-as-priority is monotonically increasing, same direction as kixelated's `PriorityHandle.insert(track.priority, sequence)` |
| Auth — purely WebTransport-CONNECT-time (JWT in `?jwt=` query); moq-lite layer touches no claims                                     | ✅       | `MoqLiteSession.kt` has zero references to JWT / claims / auth |
| Reconnect — no moq-lite-layer message; transport drop ⇒ open new WT session and resubscribe                                          | ✅       | Reconnect lives in `connectReconnectingNestsListener` / `…NestsSpeaker` (transport-layer concern) |
| Goaway recognised + FIN'd cleanly (no body decode today; relay migration not implemented)                                            | ✅       | `MoqLiteControlCodes.kt:50-58`, `MoqLiteSession.kt:973-990` |
| Catalog publisher emits one frame per inbound subscriber via `setOnNewSubscriber` hook (matches hang.live consumer expectations)     | ✅       | `MoqLitePublisherHandle.kt:91-114`, `MoqLiteNestsSpeaker.kt:160-181` |
| `hang` "legacy" container: each frame is `varint(timestamp_us) + raw_opus_packet`                                                    | ✅       | `NestMoqLiteBroadcaster.kt:212-345` (encode), `MoqLiteNestsListener.kt:279-285` (decode strip) |

The team's `2026-04-26-moq-lite-gap.md` (✅ DONE banner) holds — every
phase claim is still accurate against the live source.

## Gap matrix

Severity legend (matches the prior QUIC audit):
- 🔴 **High** — wire-incompatible with the reference relay; audio
  doesn't work.
- 🟡 **Medium** — works against the reference relay today but
  violates spec; future relay versions may break.
- 🟦 **Low** — diagnostic / observability gap; no functional impact.

| #  | Spec § / Behavior                                                                  | Severity | Gap                                                                                                                                                                                                                                                                                                                                                                | Evidence (file:line)                                                  | Status |
| -- | --------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | ------ |
| H0 | (none — no wire-incompatible items found)                                          | 🔴       | —                                                                                                                                                                                                                                                                                                                                                                  | —                                                                     | n/a    |
| M1 | `Publisher::serve_group` priority parity                                           | 🟡 → ✅   | We assigned each new group `priority = sequence (i32 cast)`, ignoring `track.priority` and never re-prioritising in flight. kixelated computes `let priority = priority.insert(track.priority, sequence); stream.set_priority(priority.current())` (per `rs/moq-lite/src/lite/publisher.rs::serve_group`). For our single Opus track at 1 group/sec the difference was unobservable (newer-first ordering still held). | `MoqLiteSession.kt:openGroupStream` (pre-fix)                         | **fixed in this audit** — see `Fix #3` |
| M2 | `STOP_SENDING` for single-group cancel                                             | 🟡 → ✅   | The Lite-03 spec lets a receiver cancel a specific group via `STOP_SENDING` on its uni stream. Pre-fix we closed the consumer-facing frames channel only — the publisher's uni stream stayed open until natural FIN, wasting bandwidth on frames the listener would discard plus relay queue pressure on the per-subscriber forward pipeline. The `:quic` `QuicStream.stopSending(errorCode)` API existed but wasn't exposed through `WebTransportReadStream`. Now plumbed end-to-end: extended `WebTransportReadStream` with `stopSending(code)`, plumbed through `:quic`'s `StrippedWtStream`, and `MoqLiteSession.drainOneGroup` fires `stopSending(SUBSCRIPTION_GONE)` once on the first frame that observes `sub == null`. | `MoqLiteSession.kt:drainOneGroup` (pre-fix), `WebTransportSession.kt` (read interface)                            | **fixed in this audit** — see `Fix #5` |
| M3 | `RESET_STREAM` with `Error::to_code()`                                             | 🟡 → ✅   | Pre-fix all error / cancel paths FIN'd gracefully via `runCatching { bidi.finish() }`. Lite-03 spec says errors on any stream are conveyed by `RESET_STREAM(application_error_code u32)`. The `SubscribeDrop` reply paths now write the body and then `RESET_STREAM(errorCode)` instead of `finish()`, so a peer watching only the QUIC layer (no body decode) can distinguish typed rejection from graceful "publisher gone" FIN. Plumbing extended `WebTransportWriteStream` with `reset(code)` and routed through both QUIC-backed adapters + `:quic`'s `StrippedWtStream`. | `MoqLiteSession.kt:handleInboundBidi` Drop replies (pre-fix)                                                      | **fixed in this audit** — see `Fix #5` |
| M4 | AnnouncePlease prefix-mismatch falls back to full suffix                           | 🟡 → ✅   | When the relay opened an Announce bidi with `prefix="X"`, our publisher emitted `Active(suffix=ourFullSuffix)` even when our suffix didn't start with `X`. The relay would observe an Active update for a broadcast it didn't ask about. In production the relay always asks for `prefix=""`, so this never bit empirically — but it's a spec violation. | `MoqLiteSession.kt:841-852` (pre-fix)                                 | **fixed in this audit** — see `Fix #1` |
| M5 | Inbound Subscribe doesn't validate broadcast field                                 | 🟡 → ✅   | When the relay opened a Subscribe bidi, we matched on `track` only, never checking `sub.broadcast == publisher.suffix`. A relay (or peer) could subscribe to broadcast `"otherPubkey"` on our connection and we'd happily route OUR audio to them. The production relay routes correctly, so this never bit empirically — but it's a spec violation. | `MoqLiteSession.kt:861-898` (pre-fix)                                 | **fixed in this audit** — see `Fix #2` |
| M6 | Goaway body decoding + migration handler                                           | 🟡 → ✅   | We recognise `ControlType::Goaway = 5` and FIN cleanly. WebFetched the kixelated reference (`rs/moq-lite/src/lite/{stream,client}.rs`): **Goaway has no body schema in moq-lite Lite-03** — it's a single ControlType byte with no payload, not even a migration URL. Our existing handler (recognise, log, FIN) is the canonical implementation; "no body decode" was never a gap. | `MoqLiteSession.kt:973-990`, `MoqLiteControlCodes.kt:50-58`           | **closed in this audit** — see `M6 closure` |
| L1 | Lite-04 ALPN constant defined but codec is Lite-03 only                            | 🟦 → ✅   | Pre-fix `MoqLiteAlpn.LITE_04` was a documented constant but the codec was hard-wired to Lite-03 — advertising Lite-04 would have caused the very first Announce to desync. Now full Lite-04 support: codec is version-aware via a new `MoqLiteVersion` enum, all three differing fields (Announce.hops as `OriginList`, AnnouncePlease.excludeHop, Probe.rtt) are encoded / decoded correctly, ALPN negotiation surfaces the server's pick via `WebTransportSession.negotiatedSubProtocol`, and `connectNestsListener` / `connectNestsSpeaker` advertise both versions and use whichever the relay echoes. | `MoqLiteCodec.kt`, `MoqLiteSession.kt`, `MoqLiteAlpn.kt`, `QuicWebTransportFactory.kt`, `WebTransportSession.kt`, `NestsConnect.kt` | **fixed in this audit** — see `Fix #8` |
| L2 | SubscribeOk always echoes `null/null` for startGroup/endGroup                      | 🟦 → ✅   | Per spec the publisher MAY narrow the subscriber's requested group bounds. Pre-fix we echoed `null/null` and lost the diagnostic "which group am I about to start sending?" signal. Now the publisher narrows `startGroup` to its `nextSequence` — useful for hot-swap continuations where the seeded `startSequence` is non-zero. `endGroup` stays null (live broadcast, no end). | `MoqLiteSession.kt` Subscribe accept arm                                | **fixed in this audit** — see `Fix #6` |
| L3 | No subscriber-driven Probe API                                                     | 🟦 → ✅   | Pre-fix the publisher-side Probe handler existed but no subscriber-side counterpart did, leaving the protocol surface incomplete. New `MoqLiteSession.probe()` opens a Probe bidi (writes `ControlType=4`) and returns a `MoqLiteProbeHandle` whose `updates` flow yields each `MoqLiteProbe` the publisher pushes. Mirrors kixelated's `Subscriber::run_probe_stream`. No production consumer (audio rooms are fixed-rate Opus, no ABR), but the API completes the surface for diagnostic tools. | `MoqLiteSession.kt` `probe()` (new), `MoqLiteHandles.kt` `MoqLiteProbeHandle` (new) | **fixed in this audit** — see `Fix #7` |
| L4 | Stream-priority overflow guard is a saturating cast                                | 🟦       | Pre-fix, `setPriority(sequence.coerceAtMost(Int.MAX_VALUE).toInt())` saturated at `Int.MAX_VALUE` (≈ 71 yrs at 1 grp/sec). After the M1 fix, sequence saturates at the 23-bit boundary (≈ 97 days within a single track) but the per-track high byte keeps cross-track ordering intact. Defensive only; production sessions cycle on JWT refresh every 9 min. | `MoqLiteSession.kt:openGroupStream` (post-M1)                         | closed (subsumed by M1 fix) |
| L5 | Inbound bidi pump captures `publishersSnapshot` at bidi-arrival time               | 🟦 → ✅   | If a new publisher was added to the session (`session.publish(track="…")`) AFTER an inbound bidi opened, the dispatcher couldn't see it. In practice both nests publishers (`audio/data` and `catalog.json`) register before any subscriber arrives, so this never bit. Tightened anyway: snapshot is now read at first-byte dispatch time. | `MoqLiteSession.kt:781-790` (pre-fix)                                 | **fixed in this audit** — see `Fix #4` |

## Specifically checked items (per audit-prompt request)

- **Stream priorities + the "stream cliff":** the priority assignment
  drains newer groups first (sequence is monotonically increasing,
  higher value = higher priority per `:quic`'s `QuicStream.priority`
  contract). The stream cliff investigation (`2026-05-01-quic-stream-
  cliff-investigation.md`) traced the production cliff to TWO
  separate root causes, neither of which was a moq-lite priority
  violation: (a) `:quic` not emitting `MAX_STREAMS_UNI` (real bug,
  fixed in `:quic`); (b) the production relay's per-subscriber
  forward-stream-rate ceiling (≈40 streams/sec) being lower than our
  `framesPerGroup=1` push rate of 50 streams/sec. The mitigation
  (`framesPerGroup=50` in production / `=5` in interop tests) is a
  cadence tuning, not a spec violation. Verified: under the current
  default the listener and speaker both stream cleanly for
  multi-minute sessions in production logs.
- **Group lifetime / stream retirement:** out of scope per audit
  prompt (`:quic` is locked). At the moq-lite layer we're confident
  groups are short-lived (one per `framesPerGroup * 20ms` =
  `framesPerGroup * 0.02s`); each ends in QUIC FIN, and the
  per-publisher `currentGroup` reference is dropped on every
  `endGroup` so the GC root chain doesn't pin retired streams.
- **Subscribe / Announce flow:** verified end-to-end — see
  `MoqLiteSessionTest.subscribe_writes_request_and_returns_handle_on_ok`
  + `…publisher_acks_subscribe_and_pushes_group_data_on_uni_stream` +
  `…publisher_replies_to_announcePlease_with_active_announce`.
  Wire-shape matches the byte-level reference in
  `2026-04-26-moq-lite-gap.md`.
- **Authentication:** moq-lite Lite-03 has no auth message inside the
  WT session — auth is purely the WT-CONNECT JWT in `?jwt=` query.
  Verified: `MoqLiteSession.kt` has zero auth references; all auth
  lives in `OkHttpNestsClient.mintToken` and the WT factory's URL
  construction.
- **Reconnect:** moq-lite has no reconnect message; on transport
  drop the `connectReconnectingNests*` wrapper opens a new WT
  session, mints a new moq-lite session, and re-issues subscribes
  (`ReconnectingNestsListener.kt:317-465`). Speaker side hot-swaps
  the publisher reference inside a long-running broadcaster
  (`NestMoqLiteBroadcaster.swapPublisher`), continuing the group
  sequence across the swap so kixelated/hang's `Container.Consumer.#run`
  doesn't drop our post-swap groups.
- **Best-effort vs reliable:** verified reliable. The previous
  `bestEffort=true` shape was reverted (see comment block at
  `MoqLiteSession.kt:1024-1039`); kixelated's reference uses
  reliable QUIC streams in `Publisher::serve_group`. Match.

## What's deliberately deferred

Nothing. Every gap surfaced by the audit is now resolved. The
remaining open items are external follow-ups (not moq-lite gaps):

  - **`kixelated/moq` feature request** — file an issue
    describing the production stream-cliff symptom (relay's
    per-subscriber forward queue starvation under sustained
    push) and propose: (a) per-deployment tuning of the
    unbounded `FuturesUnordered` task pool, and (b) a deadline
    on `serve_group()`'s `open_uni().await` derived from the
    active subscriber's smallest `max_latency`. This is a
    feature request against the upstream relay, not a defect in
    our code.

## Closed in this audit (no fix needed)

### M6 closure — Goaway has no body in moq-lite Lite-03

A second WebFetch pass against
`https://raw.githubusercontent.com/kixelated/moq/main/rs/moq-lite/src/lite/stream.rs`
and `…/client.rs` confirmed: **`ControlType::Goaway = 5` is a bare
discriminator with no payload schema in moq-lite Lite-03.** No URL,
no preferred-relay address, no error code — it's just the byte. The
"no body decode" comment in our existing handler
(`MoqLiteSession.kt:973-990`) was never a gap; there was nothing to
decode. The wrapper-layer `connectReconnectingNests*` already absorbs
the eventual hard disconnect, and a Goaway with no body has nothing
more to say than "I'm shutting down, expect FIN." Our recognise + log
+ FIN behavior is the canonical implementation.

If a future Lite-04+ revision adds a Goaway body (URL, etc.) we'd
need to decode it — that becomes part of the L1 Lite-04 codec work.

## Fixes shipped in this audit

### Fix #1 — Don't emit Active for non-matching AnnouncePlease prefix (M4)

When the relay opened an Announce bidi with a `prefix` that didn't
match our broadcast suffix, we'd fall through `MoqLitePath.stripPrefix`'s
null result and emit `Active(suffix=ourFullSuffix, hops=0)` anyway,
falsely advertising our broadcast under a prefix the subscriber didn't
ask for. The reference Rust implementation
(`rs/moq-lite/src/lite/announce.rs::Producer`) only emits Active
updates for broadcasts whose path starts with the requested prefix.

Production never hit this because the relay's announce bidi to us
always asks for `prefix=""` — but a future relay version (or a
direct peer-to-peer subscriber asking about a specific room) would
see ghost broadcasts under their requested prefix.

The fix: when `MoqLitePath.stripPrefix(please.prefix, ourSuffix)`
returns null, FIN the announce bidi without writing any Announce.
Subscriber sees a clean end-of-flow.

Regression test:
`MoqLiteSessionTest.publisher_skips_announce_when_announce_please_prefix_does_not_match`.

### Fix #2 — Reject inbound Subscribe whose broadcast doesn't match our suffix (M5)

When the relay opened a Subscribe bidi, we matched the requested
`track` against our publisher set but never checked
`sub.broadcast == ourSuffix`. A relay (or peer) could open a
Subscribe with broadcast `"otherPubkey"` on our connection and we'd
route OUR audio to them.

Production never hit this because moq-rs's relay routes Subscribe
messages to the specific publisher whose broadcast matches the
requested path — but a buggy or malicious peer connecting to us
directly would have been able to siphon our audio under any
broadcast name.

The fix: in the Subscribe arm of `handleInboundBidi`, check
`sub.broadcast == publisher.suffix` (after path normalisation —
both inputs already are, but defensive normalize on the SUBSCRIBE
side too in case a peer skipped it). If mismatch, reply
`SubscribeDrop(errorCode=BROADCAST_DOES_NOT_EXIST,
reason="<requested> not published on this session (we publish
<ours>)")` and FIN.

Regression test:
`MoqLiteSessionTest.publisher_replies_subscribeDrop_when_broadcast_does_not_match`.

### Fix #3 — Pack trackPriority + sequence into stream priority (M1)

`Publisher::serve_group` in kixelated's reference
(`rs/moq-lite/src/lite/publisher.rs`) calls
`priority.insert(track.priority, sequence)` and feeds the resulting
position into `stream.set_priority`. The `PriorityHandle` orders
streams first by `track.priority u8` (higher track = drains ahead
under congestion), then by group `sequence` within a track (newer =
drains ahead).

Pre-fix we passed raw `sequence.toInt()` directly to
`uni.setPriority`, ignoring the per-track byte. For our single-Opus-
track production case this was unobservable — newer-first ordering
held by sequence monotonicity — but a future multi-track broadcast
(audio + companion catalog / status track) would have starved the
lower-rate track the moment audio's outbound queue got congested.

The fix: add `trackPriority: Int = DEFAULT_TRACK_PRIORITY` parameter
to `MoqLiteSession.publish()`; store on `PublisherStateImpl`; bit-pack
in `openGroupStream` as
`((trackPriority and 0xFF) shl 23) or (sequence.toInt() and 0x007F_FFFF)`.

Wire layout:
  - bit 31      : 0 (reserved as the sign bit — required because
    `QuicConnectionWriter.sortedByDescending { priority }` uses
    signed `Int.compareTo`; a negative value would sort BELOW
    every positive priority and invert the intent).
  - bits 30..23 : trackPriority u8 (0..255).
  - bits 22..0  : sequence low 23 bits.

`DEFAULT_TRACK_PRIORITY = 0x80` matches the existing subscriber-side
`DEFAULT_PRIORITY` midpoint. The 23-bit sequence window is ample
(≈ 97 days at 1 grp/sec, beyond which all newer groups within a
single track tie — but still beat older groups of any LOWER-priority
track via the high byte).

Test seam: `FakeWebTransport.openUniStream` now records the most-
recent `setPriority` value via a shared `AtomicInteger` cell that the
peer-side `FakeReadStream` exposes as `lastSetPriority`. Lets the
new regression test verify the bit-pack formula on the actual
peer-side read stream rather than peeking into private state.

Regression test:
`MoqLiteSessionTest.publisher_packs_trackPriority_and_sequence_into_setPriority_value`.

### Fix #5 — Plumb RESET_STREAM + STOP_SENDING through WebTransport (M2 + M3)

The two items shipped together because the plumbing is shared. After
the merge from `main` brought in the full RFC-compliant `:quic`
layer (commits `9f7f6a9e..6f32975c`), the user lifted the
"`:quic` is locked" constraint that originally deferred M2/M3.

**Plumbing** (new commit `feat(quic,nestsclient): plumb RESET_STREAM
+ STOP_SENDING through WebTransport`):
  - `:quic`'s `StrippedWtStream` gains optional `reset` /
    `stopSending` closures, wired in `WtPeerStreamDemux.emitStripped`
    through `QuicStream.resetStream(code)` /
    `QuicStream.stopSending(code)` + `driver.wakeup()`.
  - `WebTransportReadStream.stopSending(code)` and
    `WebTransportWriteStream.reset(code)` added to the public
    interface — both suspending, both first-call-wins.
  - All adapters (`QuicBidiStreamAdapter`,
    `QuicUniWriteStreamAdapter`, `StrippedWtBidiStreamAdapter`,
    `StrippedWtReadStreamAdapter`) route directly to the underlying
    QUIC stream API.
  - `FakeWebTransport`'s `FakeBidiStream`, `FakeReadStream`,
    `ChannelWriteStream` (now public for test access) record reset
    / stopSending codes in shared `AtomicLong` cells with sentinel
    `NO_CODE = Long.MIN_VALUE`. New properties `lastResetCode`,
    `lastStopSendingCode`, `lastPeerResetCode`,
    `lastPeerStopSendingCode`, `peerStopSendingCode` give tests
    typed-error introspection without mock magic.

**M3 (publisher Drop reply uses RESET_STREAM)** (`fix(nestsclient):
RESET_STREAM with typed code after SubscribeDrop body`):
  - In `handleInboundBidi`, both Drop reply paths
    (`BROADCAST_DOES_NOT_EXIST`, `TRACK_DOES_NOT_EXIST`) now write
    the Drop body and then `bidi.reset(errorCode)` instead of
    `bidi.finish()`.
  - The errorCode matches the body's `errorCode` field, so a peer
    that decodes the Drop sees the same number as one that only
    sees the RESET_STREAM frame at the QUIC layer.
  - Strengthened the existing M5 + Drop tests to additionally
    assert `subBidi.lastPeerResetCode == errorCode` so the
    typed-error contract is locked.

**M2 (listener stopSending on dead group)** (`fix(nestsclient):
STOP_SENDING on group uni when subscription already canceled`):
  - In `drainOneGroup`, the first frame that observes `sub ==
    null` (subscription already canceled) fires
    `stream.stopSending(MoqLiteStreamCancelCode.SUBSCRIPTION_GONE)`
    once, latched, so the publisher abandons in-flight retransmits
    instead of wasting bandwidth.
  - New error-code constant
    `MoqLiteStreamCancelCode.SUBSCRIPTION_GONE = 0x10L` lives in
    `MoqLiteMessages.kt` next to `MoqLiteSubscribeDropCode`.
  - Practical impact in production: the relay's per-subscriber
    forward pipeline (already documented in the stream-cliff
    investigation) keeps queueing groups for slow / canceled
    subscribers; an early `stopSending` at the listener side
    relieves the relay's queue earlier, complementing the
    `framesPerGroup` mitigation.

Regression tests:
  - `MoqLiteSessionTest.publisher_replies_subscribeDrop_when_broadcast_does_not_match`
    — additional `lastPeerResetCode` assertion (M3).
  - `MoqLiteSessionTest.publisher_replies_subscribeDrop_when_track_is_not_published`
    — additional `lastPeerResetCode` assertion (M3).
  - `MoqLiteSessionTest.listener_stopSending_group_uni_when_subscription_already_canceled`
    — new test; verifies `peerStopSendingCode` lands the correct
    cancel code (M2).

### Fix #4 — Refresh publisher list per-dispatch on inbound bidi (L5)

`handleInboundBidi` previously snapshotted the publisher list at the
TOP of the function (= bidi-arrival time) and used that snapshot for
the rest of the bidi's lifetime. A publisher registered between
bidi-open and the first inbound chunk would miss the dispatcher's
view, even though the publisher was already live in
`activePublishers` by the time we needed to dispatch.

Practical impact today: zero — both nests publishers register from
`MoqLiteNestsSpeaker.startBroadcasting` before the relay's SUBSCRIBE
bidi for either track lands. The ~few-ms gap is below the network
round-trip floor.

But the contract is narrower if we read the list at first-byte time:
we then see every publisher that was registered up to the moment we
needed to make a routing decision. Move the publishers fetch from
the function-top to inside the `if (!dispatched)` block, after the
control-type byte has been read. On empty publisher list (the case
we previously short-circuited at function-top), we now FIN cleanly so
the peer's wait resolves instead of hanging on an idle bidi.

No new regression test — exercised end-to-end by the existing
publisher tests, which now pass against the freshness-aware
dispatcher.

### Fix #6 — SubscribeOk narrows startGroup to publisher.nextSequence (L2)

When accepting a SUBSCRIBE, the publisher MAY narrow the
subscriber's `startGroup` / `endGroup` request bounds per spec.
Pre-fix we always echoed `null/null`, which lost the diagnostic
"which group am I about to start sending?" information —
particularly useful for hot-swap continuations where
[MoqLitePublisherHandle.nextSequence] is non-zero (the seeded
`startSequence` from the previous moq-lite session).

The fix narrows `startGroup` to `targetPublisher.nextSequence`.
The subscriber decodes this as "the next group on this
subscription will be sequence N" and can log / surface the
join-point. `endGroup` stays null because live audio rooms have
no end in sight; the subscriber's request bound is honoured
implicitly when the publisher closes.

Regression test:
`MoqLiteSessionTest.publisher_subscribeOk_narrows_startGroup_to_next_sequence`.

### Fix #7 — Subscriber-driven Probe API (L3)

`MoqLiteSession.probe()` mirrors kixelated's
`Subscriber::run_probe_stream`
(`rs/moq-lite/src/lite/subscriber.rs`): opens a bidi, writes
`ControlType::Probe` (varint 4), and returns a
`MoqLiteProbeHandle` whose `updates` flow yields each
size-prefixed `MoqLiteProbe` message the publisher pushes. The
handle's `close()` FINs the bidi and cancels the pump.

`updates` is a `MutableSharedFlow(replay=8)` so a collector that
attaches after the publisher's first emit doesn't miss it
(matches the same shape the announce-watch uses).

No production consumer for the API today — Amethyst's nests
listener doesn't run ABR on a fixed-rate Opus encoder. The API
exists to round out the moq-lite Lite-03/04 surface: a
diagnostic tool can now read a publisher's bitrate without
subscribing to its data track.

Regression test:
`MoqLiteSessionTest.subscriber_probe_writes_control_type_and_decodes_publisher_bitrate`.

### Fix #8 — Version-aware Lite-03/04 codec + ALPN negotiation (L1)

Pre-fix the codec was hard-wired to Lite-03; advertising the
documented `MoqLiteAlpn.LITE_04` constant would have caused the
very first Announce to desync because Lite-04 reshapes three
fields. Now full Lite-04 support, gated on a runtime version
discriminator selected by ALPN negotiation.

Wire diff verified via WebFetch against
`kixelated/moq` `main` (2026-05-09) at
`rs/moq-lite/src/lite/{announce,subscribe,probe}.rs` and
`rs/moq-lite/src/model/origin.rs`:

  - `Announce.hops`: Lite-03 = single varint count (the spec
    fills with `Origin::UNKNOWN` placeholders on decode);
    Lite-04 = `varint(count) + count × varint(originId)` (the
    `OriginList`). MAX_HOPS = 32.
  - `AnnouncePlease.excludeHop`: Lite-03 absent; Lite-04 single
    varint after `prefix`. Sentinel `0` = no exclusion.
  - `Probe.rtt`: Lite-03 absent; Lite-04 single varint after
    `bitrate`. Sentinel `0` = unknown (decoded as null).
    Outgoing `Some(0)` clamped to `Some(1)` to avoid colliding
    with the sentinel — mirrors kixelated's `encode_msg` clamp.

Surface added:

  - `MoqLiteVersion` enum (LITE_03, LITE_04) with `fromAlpn`.
  - All affected `MoqLiteCodec` methods take `version:
    MoqLiteVersion = LITE_03`.
  - `MoqLiteSession.client(transport, scope, version)` carries
    the version through every codec invocation.
  - Data classes evolved: `MoqLiteAnnouncePlease.excludeHop`
    (default 0L), `MoqLiteAnnounce.hops` (`Long` →
    `List<Long>`), `MoqLiteProbe.rtt` (`Long?`, default null).
  - `WebTransportSession.negotiatedSubProtocol: String?` exposes
    the server's `wt-protocol` selection.
    `QuicWebTransportFactory` parses the response HEADERS,
    extracts the SF-string from `wt-protocol`, and threads it
    into `QuicWebTransportSession`.
    `FakeWebTransport.pair(negotiatedSubProtocol = …)` lets
    tests drive the same path.
  - Default advertised list now `[moq-lite-04, moq-lite-03]`.
    Lite-04 first to match kixelated's preference; servers that
    don't support it fall back to Lite-03 cleanly.
  - `connectNestsListener` / `connectNestsSpeaker` resolve the
    version via
    `resolveMoqLiteVersion(webTransport.negotiatedSubProtocol)`
    and pass it to `MoqLiteSession.client(...)`. Falls back to
    Lite-03 when the server doesn't echo `wt-protocol` (older
    deployments) or echoes an unrecognised value (forward-compat).

Regression tests (all green):

  - `MoqLiteCodecTest.announcePlease_lite04_round_trips_excludeHop`
  - `MoqLiteCodecTest.announcePlease_lite03_omits_excludeHop_on_wire`
  - `MoqLiteCodecTest.announce_lite04_round_trips_full_origin_list`
  - `MoqLiteCodecTest.announce_lite03_drops_origin_ids_keeps_count`
  - `MoqLiteCodecTest.announce_decoder_rejects_oversize_hop_count`
  - `MoqLiteCodecTest.probe_lite04_round_trips_rtt`
  - `MoqLiteCodecTest.probe_lite04_clamps_some_zero_to_one_to_avoid_unknown_sentinel`
  - `MoqLiteCodecTest.probe_lite04_decodes_zero_rtt_as_null`
  - `MoqLiteCodecTest.probe_lite03_wire_omits_rtt`
  - `MoqLiteSessionTest.lite04_announce_round_trips_full_origin_list_through_session`

## Build / test verification

Baseline `:nestsClient:jvmTest` was green at audit start
(140 tests). After both fixes + regression tests, still green.
Default-mode tests run in seconds; the `-DnestsInterop=true` Docker
suite is unchanged by these fixes (they only tighten our existing
publisher-side behaviour against malformed input the production
relay never sends).

## Pointers

- Wire spec + IETF gap (DONE banner): `2026-04-26-moq-lite-gap.md`.
- Stream cliff investigation (production-fixed):
  `2026-05-01-quic-stream-cliff-investigation.md`.
- Frames-per-group reconciliation (production vs interop):
  `2026-05-07-framespergroup-reconciliation.md`.
- Audio-rooms completion plan: `2026-04-26-audio-rooms-completion.md`
  (will reference this audit in its compliance section).
- Reference Rust impl: `https://github.com/kixelated/moq/tree/main/rs/moq-lite/src/`.
