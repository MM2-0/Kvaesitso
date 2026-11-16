# EGGs — Extensible Gossip Guidelines

Wire-protocol specs for nostrnests-style audio rooms. Each EGG defines one
self-contained capability that a client or relay can implement. Two compliant
peers that implement the same set of EGGs round-trip without further
coordination.

Specs intentionally mirror the [Nostr NIP](https://github.com/nostr-protocol/nips)
and [Blossom BUD](https://github.com/hzrd149/blossom) formats: ASCII Markdown,
RFC-2119 keywords (MUST / SHOULD / MAY), one capability per file.

## Index

| #  | File           | Topic                                     | Category   |
|----|----------------|-------------------------------------------|------------|
| 01 | [EGG-01](./EGG-01.md) | Room event (`kind:30312`)         | required   |
| 02 | [EGG-02](./EGG-02.md) | Auth & WebTransport handshake      | required   |
| 03 | [EGG-03](./EGG-03.md) | Audio plane (moq-lite)             | required   |
| 04 | [EGG-04](./EGG-04.md) | Presence (`kind:10312`)            | required   |
| 05 | [EGG-05](./EGG-05.md) | In-room chat (`kind:1311`)         | optional   |
| 06 | [EGG-06](./EGG-06.md) | Reactions (`kind:7`)               | optional   |
| 07 | [EGG-07](./EGG-07.md) | Roles & moderation (`kind:4312`)   | optional   |
| 08 | [EGG-08](./EGG-08.md) | Scheduling (`status=planned`)      | optional   |
| 09 | [EGG-09](./EGG-09.md) | User server list (`kind:10112`)    | optional   |
| 10 | [EGG-10](./EGG-10.md) | Theming (`c`/`f`/`bg`)             | decorative |
| 11 | [EGG-11](./EGG-11.md) | Recording (`recording` tag)        | decorative |
| 12 | [EGG-12](./EGG-12.md) | Catalog track (`catalog.json`)     | optional   |

## Conformance levels

A peer claims **Listener compliance** when it implements EGG-01, EGG-02, EGG-03
and at least the read side of EGG-04.

A peer claims **Speaker compliance** when it implements all of the above plus
the publish side of EGG-03 and the write side of EGG-04.

A peer claims **Host compliance** when it implements Speaker compliance plus
EGG-07 and the write side of EGG-08 (if scheduled rooms are exposed in its UI).

EGG-05 through EGG-12 are independently optional. Lacking any of them MUST NOT
break interop on the EGGs a peer does implement.

## Versioning

Each spec carries a `status` line at the top: `draft` (subject to change),
`accepted` (frozen except for clarifications), or `replaced-by: EGG-XX`
(superseded). Breaking changes ship as a new EGG number; existing numbers are
never re-purposed.

## Naming

EGG = Extensible Gossip Guideline. The acronym is intentional: nostrnests
serves nests; nests hold eggs.

## Conventions

Rules in this section apply across every EGG. They are normative.

1. **Hex strings.** Every pubkey, event id, signature, and other hex value
   referenced in any EGG MUST be **lowercase**, exactly the spec's expected
   length (64 chars for pubkey/event id, 128 chars for sig), with NO `0x`
   prefix and NO whitespace. Receivers MUST reject mixed-case or padded hex
   rather than silently lowercasing — an inconsistent hex on the wire is a
   bug at the source.
2. **Nostr foundations.** Unless an EGG says otherwise, every event referenced
   here is a NIP-01 event (id = sha256 of the canonical serialization;
   schnorr signature over `id`). Receivers MUST verify `id` and `sig` before
   acting on the event. Replaceable / addressable / ephemeral semantics
   follow NIP-01.
3. **`a`-tag form.** Every `["a", ...]` tag in these specs uses the
   addressable form `<kind>:<author pubkey hex>:<d-tag>`. The optional 3rd
   element is a relay hint URL.
4. **Created-at tie-break.** When two events share the same `(kind, pubkey,
   d)` AND the same `created_at`, receivers MUST keep the one with the
   lexicographically SMALLEST event id and discard the other (matches NIP-01
   replaceable-event tie-break behavior).
5. **JSON.** All HTTP bodies and event content are UTF-8. JSON producers MUST
   NOT include a BOM. JSON parsers MUST tolerate trailing whitespace.
6. **Time.** All timestamps are unsigned unix seconds (base-10 ASCII when
   carried as a tag value, integer when carried in JSON). Sub-second
   precision is out of scope.

## Joining sequence

A normative end-to-end walkthrough for "I have a `kind:30312` event in hand,
get me to first audio frame". Every step references the EGG that defines it.

```
┌──────────────────────────────────────────────────────────────────────┐
│ 1. Parse room event (EGG-01)                                         │
│    - Verify NIP-01 id + sig                                          │
│    - Read `service`, `endpoint`, `relays`, `status`, `p` tags        │
│    - Reject if status != "open" / "private"                          │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 2. Subscribe to chat / presence relays (EGG-04, EGG-05, EGG-06)      │
│    Filter: { "#a": ["30312:<host>:<d>"], "kinds":[1311,7,4312] }     │
│    Filter: { "#a": ["30312:<host>:<d>"], "kinds":[10312] }           │
│    Relays = `relays` tag ∪ host's NIP-65 outbox                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 3. Mint moq-auth JWT (EGG-02)                                        │
│    POST <service>/auth                                               │
│    Authorization: Nostr <base64(NIP-98 event)>                       │
│    body: { "namespace": "nests/30312:<host>:<d>",                    │
│            "publish": <true if speaker, else false> }                │
│    ← 200 { "token": "<es256 jwt>" }                                  │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 4. Open WebTransport session (EGG-02)                                │
│    CONNECT :path = /<namespace>?jwt=<token>                          │
│    :authority = host:port from `endpoint`                            │
│    Run moq-lite Setup handshake on the bidi control stream           │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 5. Discover speakers (EGG-03)                                        │
│    Send moq-lite AnnouncePlease prefix=""                            │
│    Each Announce { suffix=<speaker pubkey hex>, status=Active }      │
│    is one live speaker.                                              │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 6. Subscribe per speaker (EGG-03)                                    │
│    Subscribe { broadcast=<pubkey hex>, track="audio/data" }          │
│    Each Group → unidirectional QUIC stream                           │
│    Each Frame → exactly one 20 ms Opus packet                        │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 7. Publish own presence (EGG-04)                                     │
│    kind:10312 with `a` = "30312:<host>:<d>", flags, fixed d-tag      │
│    Re-publish every 30 s (with ±5 s jitter) until leaving            │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
        (speaker only)
┌──────────────────────────────────────────────────────────────────────┐
│ 8. Speaker — publish own audio (EGG-03)                              │
│    Send moq-lite Announce { suffix=<own pubkey hex>, status=Active } │
│    Open one unidirectional QUIC stream per Group, write Frames       │
└──────────────────────────────────────────────────────────────────────┘
```

Token lifetime is 600 s. Re-mint a fresh token (steps 3 + 4) before the
old one expires. Relays MUST close sessions within 30 s past `exp`.

## Hosting a new room

A normative walkthrough for "I am a host; create a fresh room and start
broadcasting". Symmetrical to the joining sequence above but starts one
step earlier — the host has to bring the `kind:30312` into existence
before anyone (including themselves) can authenticate against it.

```
┌──────────────────────────────────────────────────────────────────────┐
│ 1. Pick service + endpoint (EGG-01, EGG-09)                          │
│    Default: FIRST entry of host's own kind:10112 (EGG-09)            │
│    Both MUST be https://; http:// is rejected (EGG-09 rule 1)        │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 2. Compose kind:30312 (EGG-01)                                       │
│    - status = "open" (or "planned" + starts tag, EGG-08)             │
│    - d = fresh room id, charset [A-Za-z0-9._-] (EGG-01 rule 9)       │
│    - first p-tag = ["p", <own pubkey>, <relay hint>, "host"]         │
│    - relays / image / summary / theme tags as desired                │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 3. Sign + publish to relays (EGG-01 rule 10)                         │
│    Target set = `relays` tag ∪ host's NIP-65 outbox                  │
│    The auth sidecar's inbound relay pool MUST overlap this set,      │
│    otherwise step 4 will 410 `unknown_room` indefinitely.            │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 4. Mint publish JWT (EGG-02)                                         │
│    POST <service>/auth, body = {namespace, "publish": true}          │
│    NIP-98 Authorization signed by host pubkey.                       │
│    On 410 `unknown_room`: relay-propagation lag — retry up to 3      │
│    times with backoff (1s / 2s / 4s) before surfacing.               │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 5. Open WebTransport + run moq-lite Setup (EGG-02 step 3)            │
│    Same as listener flow.                                            │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 6. Announce own broadcast (EGG-03)                                   │
│    Announce { suffix=<own pubkey hex>, status=Active, hops=0 }       │
│    Then AnnouncePlease prefix="" to also receive other speakers.     │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 7. Publish own presence (EGG-04)                                     │
│    publishing="1", onstage="1", muted="0"; heartbeat per EGG-04.     │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────────────┐
│ 8. Stream Opus (EGG-03)                                              │
│    One unidirectional QUIC stream per Group, one Opus packet per     │
│    Frame, 20 ms cadence.                                             │
└──────────────────────────────────────────────────────────────────────┘
```

Ongoing host duties:

- **Add a speaker:** re-publish kind:30312 with the new `["p", _, _,
  "speaker"]` tag (EGG-07).
- **Promote to admin / demote / remove:** re-publish kind:30312 with
  updated role markers (EGG-07).
- **Kick:** sign and broadcast a kind:4312 ephemeral (EGG-07).
- **Edit metadata** (rename, image, theme): re-publish kind:30312 with
  higher `created_at` (EGG-01 rule 1).
- **Close the room:** re-publish kind:30312 with `status="closed"`
  (EGG-01 rule 6). The audio plane SHOULD be torn down server-side.
- **Publish recording** after close: re-publish closed event with
  `["recording", url]` tag (EGG-11).

## Filing changes

Edit a single EGG per pull request. Include a wire-format example showing the
delta. If a change crosses two specs, file two PRs and reference each from the
other.
