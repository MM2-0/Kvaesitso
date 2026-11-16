# EGG-03: Audio plane (moq-lite)

`status: draft`
`requires: EGG-02`
`category: required`

## Summary

Audio is carried over [moq-lite](https://github.com/kixelated/moq) **Lite-03**
on top of the WebTransport session opened in EGG-02. Each speaker publishes
one broadcast keyed by their pubkey hex, on a single track named
`audio/data`, encoded as Opus.

This EGG defines the broadcast/track naming convention and the audio codec
parameters. It does not redefine moq-lite framing — refer to the upstream
spec for the wire layout of `Subscribe`, `Announce`, `Group`, and `Frame`
control messages.

### Normative reference for moq-lite Lite-03

The wire formats for `AnnouncePlease`, `Announce`, `Subscribe`, `Group`
header, and `Frame` are defined by the Rust reference implementation at:

```
https://github.com/kixelated/moq-rs
  rs/moq-lite/src/lite/{announce,subscribe,group,setup,session}.rs
```

Implementers MUST track Lite-03 (NOT the IETF moq-transport draft, which
is incompatible). Cross-version interop tests MUST pin a specific
`moq-rs` commit hash for reproducibility.

All path / broadcast / track strings on the moq-lite wire are
length-prefixed UTF-8. All integers are RFC 9000 §16 varints unless the
upstream codec says otherwise. Path components on this wire MUST contain
only the characters allowed in a URL path segment per RFC 3986; relays
typically reject `/` inside a single path component.

## Wire format

### Speaker → relay (publish)

A speaker MUST send a moq-lite `Announce` with:

```
prefix     = ""           (empty — relative to the JWT's `root` namespace)
suffix     = <pubkey hex>      (lowercase, 64 chars, see Conventions in README)
status     = Active            (status byte 0x01 per Lite-03)
hops       = 0
```

After the announce, the speaker opens a unidirectional QUIC stream per group,
prefixed with a moq-lite `Group` header followed by `Frame`s carrying Opus
payloads.

Each moq-lite `Frame` carries **exactly one Opus packet** (one 20 ms frame).
Producers MUST NOT pack multiple Opus packets into a single Frame, MUST NOT
prefix the payload with an Ogg page or any container, and MUST NOT carry an
explicit timestamp inside the Frame — the receive order plus the fixed 20 ms
cadence is the timing contract.

### Listener → relay (subscribe)

A listener MUST send a moq-lite `Subscribe` with:

```
broadcast  = <speaker pubkey hex>     (lowercase, 64 chars)
track      = "audio/data"
priority   = 128            (recommended)
ordered    = true
maxLatency = 0              (unlimited)
```

To learn which broadcasts exist on this session, a listener sends a
moq-lite `AnnouncePlease` with `prefix=""` once after the moq-lite Setup
handshake completes. The relay then streams `Announce` frames for every
active speaker; the listener subscribes to each per the form above.

The relay forwards each subsequent `Group` and its `Frame`s to the listener
in subscribe-id order.

### Codec

| Parameter       | Value                                 |
|-----------------|---------------------------------------|
| Codec           | Opus (RFC 6716)                       |
| Sample rate     | 48 kHz                                |
| Channels        | 1 (mono)                              |
| Frame duration  | 20 ms (960 samples)                   |
| Bit-rate target | 32 kbit/s (configurable, no upper cap)|
| VBR             | Allowed                               |

A new `Group` MUST be opened on every Opus reset (e.g. mute/unmute).

## Behavior

1. A speaker MUST emit an `Announce` with `status=Active` immediately after
   the WebTransport session reaches a usable state, and emit `status=Ended`
   when stopping the broadcast cleanly.
2. A speaker MUST cap their broadcast to one concurrent `audio/data` track
   per session. Multi-quality stacks are reserved for a future EGG.
3. Listeners MUST tolerate gaps in the group sequence (frames dropped due to
   network loss). They MUST NOT request retransmits.
4. Listeners SHOULD subscribe with `ordered=true`. Out-of-order delivery
   would re-introduce reorderable jitter the Opus decoder is not equipped to
   handle.
5. A speaker MUST encode in mono. Stereo broadcasts are reserved for a future
   EGG.
6. Listeners MUST NOT subscribe to their own broadcast (would create an audio
   loopback through the relay).
7. The relay MUST drop a publishing peer's `Announce` and any subsequent
   stream data if the JWT's `put` claim does not contain `<pubkey hex>`.
8. **Mute behavior.** Muting MUST be implemented as "stop publishing" — the
   speaker closes their currently-open Group stream and does NOT open a
   new one until unmute. Speakers MUST NOT push silence frames to fake
   continuity, and MUST NOT send `Announce status=Ended` on mute (mute is
   a transient state; ending the broadcast is reserved for leaving the
   stage). The presence event's `muted` flag (EGG-04) communicates the
   intent to listeners that lack the audio plane.
9. **Joining mid-stream.** Listeners MUST tolerate joining at any point
   inside a Group; the first received Frame is independently decodable
   (Opus is a self-synchronising codec). The first decoded sample MAY
   contain a few ms of pre-roll noise; receivers SHOULD discard the
   first decoded packet's pre-skip samples per RFC 7845 §4.2.

## Example

A two-speaker room:

```
A's session:  ANNOUNCE  prefix="" suffix="speakerA" status=Active
              GROUP     subscribe-id=…, sequence=0
              FRAME     <opus payload, 20ms>
              FRAME     <opus payload, 20ms>
              …

Listener's session:
  SUBSCRIBE  broadcast="speakerA" track="audio/data"
  ←  GROUP   sequence=0
  ←  FRAME   <opus payload, 20ms>
  ←  …
  SUBSCRIBE  broadcast="speakerB" track="audio/data"
  ←  GROUP   sequence=0
  ←  FRAME   <opus payload, 20ms>
  ←  …
```

## Compatibility

EGG-12 adds an OPTIONAL `catalog.json` track per broadcast carrying codec
metadata. EGG-12 MUST NOT change the `audio/data` track parameters defined
here.

A future "video plane" EGG MAY introduce additional tracks; their names MUST
NOT collide with `audio/data` or `catalog.json`.
