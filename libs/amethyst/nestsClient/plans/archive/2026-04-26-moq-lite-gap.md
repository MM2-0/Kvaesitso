# Plan: bridge the moq-lite protocol gap

> **Status:** shipped — moq-lite Lite-03 listener + speaker landed (phases 5a-5d, `fb47a4c`→`71cf99d`); doc self-marks DONE and `moq/lite/` is the production stack.
> _Audited 2026-06-30._

**Status:** ✅ **DONE** — listener + speaker on moq-lite Lite-03 against
the real nostrnests stack. Phases 5a → 5d landed (`fb47a4c` →
`71cf99d`); follow-up cleanup + harness fixes shipped through `015b0d7`.
Default `:nestsClient:jvmTest` passes; integration tests gated by
`-DnestsInterop=true` work end-to-end against a Docker'd nostrnests
deployment.

**Origin:** discovered while writing the nostrnests interop test suite (phases 1–4).

## Discovery

`:nestsClient` implements **IETF `draft-ietf-moq-transport-17`**
(`TrackNamespace` tuples, `CLIENT_SETUP` / `SERVER_SETUP`,
`OBJECT_DATAGRAM` with `track_alias`, two-message ANNOUNCE / SUBSCRIBE
shape).

The actual nostrnests stack runs on **moq-lite** — kixelated's own MoQ
flavour, wire-incompatible with IETF MoQ-transport:

- JS client `NestsUI-v2/package.json` depends on `@moq/lite`,
  `@moq/publish`, `@moq/watch`. None implement IETF MoQ-transport.
- Rust relay `kixelated/moq-rs` is built on `rs/moq-lite/` types
  throughout.

The phase-4 wire fixes (path = `/<namespace>`, JWT in `?jwt=` query) are
on the moq-rs wire shape and let the WebTransport CONNECT succeed. But
the first MoQ-framing message we send afterwards (IETF `ClientSetup`)
is unintelligible to moq-rs's moq-lite framing.

## Wire spec — moq-lite (Lite-03)

Fully extracted from `kixelated/moq-rs/rs/moq-lite/src/` and
`@moq/lite` v0.1.7.

### Connection setup

- **ALPN advertised on the WebTransport upgrade:** prefer
  `"moq-lite-03"` (`Lite.ALPN_03`); fall back to `"moql"` (legacy
  combined ALPN that requires a SETUP exchange).
  Source: `rs/moq-lite/src/version.rs:21-26`,
  `@moq/lite/connection/connect.js:277`.
- **Lite-03 has NO setup or control message inside the WT session.**
  The WebTransport handshake itself is the handshake. Both sides go
  straight to opening per-purpose streams. `client.rs:86-101`,
  `connect.js:113-115`.
- Legacy Lite-01/02 (`"moql"`) DO open a bidi setup stream with byte
  `0x20` → `ClientSetup` (Draft-14 IETF-format), reply `0x21` →
  `ServerSetup`, then exchange `SessionInfo {bitrate: u62}` forever.
  We are targeting Lite-03 only.
- Version is **chosen entirely by ALPN.** No version, role, or setup
  parameter is exchanged inside the WT session.

### Streams + datagrams

- **No persistent control stream.** Each control "request" is a fresh
  client-initiated **bidi**, whose first byte is a varint
  `ControlType` discriminator and whose body is a size-prefixed message.
  The bidi stays open for the duration of the request/response/stream.
- `ControlType` codes (`lite/stream.rs:7-15`): `Session=0` (unused in
  Lite-03), `Announce=1`, `Subscribe=2`, `Fetch=3`, `Probe=4`. All
  encoded as QUIC varints.
- **Media flows on uni streams**, one stream per *group*. Uni-stream
  type byte: `DataType::Group = 0` (varint, in practice a literal
  `0` byte). `lite/stream.rs:32-36`, `publisher.js:209`.
- **No QUIC datagrams used for media in Lite-03.** Each group is its
  own uni stream; no datagram path.

### Announce

Two-step, on a single bidi opened by the **subscriber**:

1. **Subscriber → publisher** (or relay). Bidi opens with control byte
   `varint(1)` = `Announce`, then size-prefixed `AnnouncePlease`:

       prefix: string   (varint length + UTF-8; broadcast-name prefix
                         the subscriber cares about; empty = "everything")

   Source: `subscriber.rs:82-89`, `subscriber.js:46-47`,
   `announce.rs:64-81`.

2. **Publisher → subscriber.** Server emits one size-prefixed
   `Announce` message per active broadcast, then keeps the bidi open
   for live updates:

       status: u8       (0 = Ended, 1 = Active)
       suffix: string   (broadcast path with `prefix` stripped;
                         normalised — `path.rs:78-99`)
       hops:   u62      (Lite-03 only; varint, relay routing depth)

   Source: `announce.rs:84-90`, `announce.rs:28-31`. `active=true` is
   sent on first publish; `active=false` on explicit unannounce.
   Disconnect is **not** an explicit Ended (see Cleanup).

### Subscribe

Subscriber opens a **fresh bidi**, control byte `varint(2)` = `Subscribe`,
then size-prefixed body:

    id          u62 varint   (subscriber-chosen, monotonic)
    broadcast   string       (varint length + UTF-8 path)
    track       string       (opaque app string —
                              "audio/data" or "catalog.json")
    priority    u8           (raw byte 0..255; NOT a varint)
    ordered     u8           (Lite-03 only; 0 / 1)
    maxLatency  varint       (Lite-03; **milliseconds**, 0 = unlimited)
    startGroup  varint       (Lite-03; 0 = "from latest",
                              else group_seq + 1)
    endGroup    varint       (Lite-03; 0 = "no end",
                              else group_seq + 1)

Source: `subscribe.rs:25-72`, `subscribe.js:87-104`,
`encode.rs:99-185`.

Reply: size-prefixed `SubscribeResponse` on the same bidi. Lite-03
prefixes a varint type: `0 = Ok`, `1 = Drop`. `SubscribeOk` body is
`(priority, ordered, maxLatency, startGroup, endGroup)` — same
five fields as Subscribe minus id/broadcast/track.

There is **no SUBSCRIBE_ERROR**. Failure = stream RESET with an
`Error::to_code()` u32. Track names are arbitrary opaque UTF-8.

### Frame / group / object delivery

For each group, the publisher opens a fresh uni stream:

    DataType (varint) = 0       (Group)
    Group header (size-prefixed):
        subscribe   u62 varint  (echoes Subscribe.id)
        sequence    u62 varint  (group sequence number)

    then a sequence of frames until the stream's FIN:
        frame_size  varint      (length in bytes)
        payload     frame_size raw bytes

Source: `publisher.rs:330-393`, `publisher.js:205-232`,
`subscriber.js:152-161`.

**No per-frame envelope beyond size.** No timestamp, no codec config,
no flags. All semantic structure (Opus packet boundaries, JSON
document) is the track's app-layer convention. End-of-group is QUIC
stream FIN.

### Cleanup / unsubscribe / unannounce

- Per-group end / track ended: QUIC **FIN** the uni stream
  (`publisher.rs:387-388`).
- Broadcast ended: send `Announce {status=0=Ended, suffix, hops}` on
  the announce bidi (`publisher.rs:206`, `publisher.js:118`).
- Unsubscribe: **FIN the subscribe bidi's send side**
  (`subscriber.rs:230`). No UNSUBSCRIBE message exists.
- Cancel a single group from the receiver: QUIC `STOP_SENDING` on the
  uni stream.
- Mid-broadcast publisher disconnect: relay either FINs/resets the
  announce bidi or emits `Announce::Ended` if graceful. Consumers
  detect via the bidi/QUIC close — there is no "publisher gone"
  message.
- Errors on any stream: `RESET_STREAM` with `Error::to_code()` (u32).

### Varint encoding

**RFC 9000 §16 QUIC varints** (2-bit length tag, 1/2/4/8 byte forms,
max value `2^62 − 1`). `coding/varint.rs:172-239`,
`stream.js:147-170`. The `stream.js` `u53` reader caps at the JS-safe
range. `priority` is a plain byte. Strings = `varint length + UTF-8`.
`bool` = 1 byte 0/1.

### Notable + non-obvious

- **Same WT session is bidirectional w.r.t. roles.** Either side can
  open an Announce or Subscribe bidi; the role is per-bidi via the
  `ControlType` byte. There is no role announcement.
- **`Path` normalisation is mandatory** on both sides — leading,
  trailing, and duplicate `/` are stripped before encoding. A wire
  path `"/foo//bar/"` is identical to `"foo/bar"`. An interop client
  that doesn't normalise will see broadcast lookups silently fail.
- **`startGroup`/`endGroup` use the off-by-one trick:**
  `0 = None, n = Some(n − 1)`. Easy to get wrong.
- **`maxLatency` is in milliseconds.** Not seconds, not microseconds.
- **No head-of-line blocking across groups** (one uni stream per
  group), but no in-order guarantee across them either. The receiver
  uses `sequence` to reorder.
- **`hops`** is the only relay-routing metadata on the wire (Lite-03).
- **Probe stream (ControlType=4)** is opened by the *subscriber* but
  the publisher writes `Probe { bitrate: u62 }` size-prefixed messages
  on it — opposite direction from a normal request/response.

## Concrete wire shapes nests-side

| Wire field             | JS reference value                       |
| ---------------------- | ---------------------------------------- |
| WT URL path            | `/nests/30312:<host>:<roomId>`           |
| `?jwt=` query          | JWT (`claims.root` = the same path)      |
| `claims.put` (publish) | `[<myPubkey>]`                           |
| `claims.get`           | `[""]`                                   |
| ANNOUNCE.suffix        | `<myPubkey>` (single string)             |
| SUBSCRIBE.broadcast    | `<speakerPubkey>` (single string)        |
| SUBSCRIBE.track        | `"catalog.json"` then `"audio/data"`     |

## Implementation status (2026-04-26 PM)

**Landed (listener path complete end-to-end through `:nestsClient`):**

| Phase | Commit    | Surface                                                                                              |
| ----- | --------- | ---------------------------------------------------------------------------------------------------- |
| 5a    | `fb47a4c` | `MoqLitePath` (mandatory wire-boundary normalisation), `MoqLitePathTest`                             |
| 5b    | `fb47a4c` | `MoqLiteCodec` + every Lite-03 message type + `MoqLiteCodecTest` (round-trip + negative paths)       |
| 5c    | `4e136ca` | `MoqLiteSession.client(...)` (no SETUP), `announce`, `subscribe`, group uni-stream demux, framing helpers, `MoqLiteSessionTest` |
| 5d    | `41f4dcd` | `connectNestsListener` swap — `MoqLiteNestsListener` adapts `MoqLiteFrame` → `MoqObject` for downstream `AudioRoomPlayer` / `AudioRoomViewModel`. WT URL path = `/<namespace>?jwt=<token>`. |

**Speaker path landed (phase 5c-speaker):**

| Layer                       | Surface                                                                                                                                                                                                                                                                                                                                                          |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `WebTransportSession`       | `incomingBidiStreams()` (relay → us) + `openUniStream()` (group push) added.                                                                                                                                                                                                                                                                                     |
| `:quic` `WtPeerStreamDemux` | `StrippedWtStream` carries optional `send` + `finish` closures (driver-aware) so peer-initiated bidis can write back; demux now takes the `QuicConnectionDriver` so wakeups fire after each app-level write.                                                                                                                                                     |
| `FakeWebTransport`          | now exposes `incomingBidiStreams` + `openUniStream` directly, so production callers and tests use the same API.                                                                                                                                                                                                                                                  |
| `MoqLiteSession`            | New `publish(suffix)` returns a `MoqLitePublisherHandle`. The session lazily launches a relay→us bidi pump that dispatches by `ControlType`: `Announce` → reply `Active(suffix=ours)`; `Subscribe` → reply `SubscribeOk` and register an inbound subscription. `MoqLitePublisherHandle.send` opens a uni stream per group with `DataType=0` + group header + `varint(size)+payload` frames; `endGroup` FINs the current uni; `close` emits `Announce(Ended)` on every active announce bidi. |
| `MoqLiteNestsSpeaker`       | NestsSpeaker adapter that wraps a `MoqLiteSession` + `AudioRoomMoqLiteBroadcaster` (Opus encode + push loop, mirror of the IETF `AudioRoomBroadcaster` but driving the moq-lite publisher).                                                                                                                                                                       |
| `connectNestsSpeaker`       | now opens a `MoqLiteSession` (no SETUP) and returns `MoqLiteNestsSpeaker`.                                                                                                                                                                                                                                                                                        |

The existing integration tests (round-trip, multi-peer, fan-out,
subscribe-before-announce) drive the end-to-end public API — they
should now pass against the real Docker'd nostrnests stack when the
host runs them with `-DnestsInterop=true`.

## Implementation plan (original spec — kept for reference)

### Phase 5a — codec primitives (1 day)

- `MoqLiteVarint` — RFC 9000 varint reader/writer (we already have
  `Varint` in `:quic`; reuse).
- `MoqLitePath` — string with `normalize()` (strip leading/trailing/
  duplicate `/`) + `join(prefix, suffix)`.
- `MoqLiteWriter` / `MoqLiteReader` — varint, length-prefixed string,
  size-prefixed message envelope.

### Phase 5b — message codec (1–2 days)

- `MoqLiteAnnouncePlease(prefix: String)`
- `MoqLiteAnnounce(status: AnnounceStatus, suffix: String, hops: Long)`
- `MoqLiteSubscribe(id, broadcast, track, priority, ordered,
  maxLatency, startGroup, endGroup)`
- `MoqLiteSubscribeOk(priority, ordered, maxLatency, startGroup,
  endGroup)`
- `MoqLiteSubscribeDrop(...)` (decode-only)
- `MoqLiteGroupHeader(subscribeId, sequence)`
- `MoqLiteFrame(payload)`
- Round-trip tests against hand-rolled byte sequences — exactly the
  pattern `MoqCodecTest` uses today.

### Phase 5c — session layer (3–5 days)

- `MoqLiteSession` parallel to `MoqSession`:
  - `client(transport, scope)` — no SETUP step; just spawn pumps.
  - `announce(suffix)` — subscriber-side: opens bidi with
    ControlType=Announce + AnnouncePlease(prefix=""), returns a flow
    of incoming `Announce` updates.
  - `publish(suffix)` — publisher-side: opens an uni-stream-per-group
    pump under our broadcast.
  - `subscribe(broadcast, track)` — opens bidi with
    ControlType=Subscribe + body, awaits `SubscribeOk`, returns a
    `MoqLiteSubscribeHandle` whose `frames` flow yields each frame
    grouped by sequence.
  - `close()` — close all streams.
- Path normalisation applied automatically at every wire boundary.
- ALPN wired through `:quic`'s WT factory: `"moq-lite-03"`.

### Phase 5d — production wiring (1 day)

- Replace `MoqSession.client(...)` calls in `connectNestsListener` /
  `connectNestsSpeaker` with `MoqLiteSession.client(...)`.
- `subscribeSpeaker(pubkey)` becomes:
  - subscribe `(broadcast=pubkey, track="catalog.json")` — discover
    metadata
  - subscribe `(broadcast=pubkey, track="audio/data")` — receive
    Opus frames
- Speaker side: announce `(suffix=ourPubkey)`, publish `audio/data`
  frames as one group per session (or rotate groups periodically).
- Drop the `TrackNamespace` plumbing on the nests side — it doesn't
  apply to moq-lite.

### Phase 5e — integration tests (existing)

- The `-DnestsInterop=true` round-trip + multi-peer tests should now
  pass against the real Docker'd moq-rs. No test rewrites needed; the
  tests drive `connectNestsSpeaker` / `connectNestsListener` and
  `listener.subscribeSpeaker(pubkey)` — purely through the public API.

## Decision points still open

- **Keep IETF MoQ-transport code (option A) vs delete (option B)?**
  Recommend **A** — keeps `MoqSession` reachable for any future IETF
  target, and the unit-test suite is genuinely useful as a reference
  implementation. The IETF code is ~1.5k LOC of well-tested codec
  that costs ~nothing to keep around.
- **Where does `MoqLiteSession` live?** Same package
  (`com.vitorpamplona.nestsclient.moq`) under a `lite/` subpackage —
  matches the upstream `rs/moq-lite/` layout.

## When picking up

This doc captures the wire spec used during implementation. For the
shipped surface, start from:

- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/moq/lite/`
  — `MoqLiteCodec`, `MoqLiteSession`, `MoqLitePath`, `MoqLiteFraming`.
- `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/`
  `MoqLiteNestsListener.kt` / `MoqLiteNestsSpeaker.kt`.
- `nestsClient/src/jvmTest/.../interop/` — Docker-driven interop tests
  (auth, round-trip, multi-peer, endpoint smoke).

For raw protocol reference:

- `~/.cache/amethyst-nests-interop/nests/NestsUI-v2/node_modules/@moq/lite/`
  — JS reference once the harness has run once.
- `kixelated/moq-rs/rs/moq-lite/src/{lite,coding,client,version,path}.rs`
  — canonical Rust implementation.
- `moq-auth/src/index.ts:160-166` — `claims.put = [pubkey]` rule.
