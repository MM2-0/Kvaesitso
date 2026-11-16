# EGG-04: Presence (`kind:10312`)

`status: draft`
`requires: EGG-01`
`category: required`

## Summary

Each participant in a room periodically broadcasts a `kind:10312` event
declaring their state: which room they are in, whether their hand is raised,
whether they are muted, whether they are actively pushing audio, and whether
they currently hold a stage seat.

Presence is the source of truth for the audience-side participant grid, the
hand-raise queue, the listener counter, and the home-screen "live" bubble.

## Wire format

`kind:10312` is a NIP-01 *replaceable* event with a fixed `d`-tag (one
presence-state per pubkey, regardless of how many rooms a user joins; see
"Behavior" below).

```json
{
  "kind": 10312,
  "pubkey": "<participant pubkey hex>",
  "tags": [
    ["a", "30312:<host pubkey hex>:<room d>", "<relay hint>"],
    ["hand",       "0" | "1"],
    ["muted",      "0" | "1"],
    ["publishing", "0" | "1"],
    ["onstage",    "0" | "1"],
    ["alt", "Room Presence tag"]
  ],
  "content": "",
  ...
}
```

The `a` tag is REQUIRED and points at the room defined in EGG-01.

## Behavior

1. A participant MUST emit a `kind:10312` event:
   - on entering the room,
   - every 30 s ± 5 s of jitter while in the room,
   - immediately when any flag (`hand`, `muted`, `publishing`, `onstage`)
     changes,
   - one final time on leaving, with `publishing="0"` and `onstage="0"`.
   The ±5 s jitter is REQUIRED. Synchronised heartbeats from a
   thousand-listener room would otherwise trigger relay bursts every 30 s.
2. Receivers MUST treat a participant as departed if no presence has been
   observed in `> 6 minutes`. (One missed heartbeat plus a 5 minute
   tolerance window.)
3. The `hand` flag is the participant's request-to-speak. Hosts and admins
   read it to populate a queue (EGG-07).
4. The `muted` flag is set ONLY by speakers who are also publishing. Pure
   audience members SHOULD omit it.
5. The `publishing` flag means "I currently have an open `audio/data`
   broadcast on the relay" (EGG-03). It MUST be `0` whenever the speaker
   has stopped publishing, regardless of mute state.
6. The `onstage` flag means "I currently hold a speaker slot in the room's
   `p`-tag list (EGG-01) AND I have not voluntarily stepped off". A
   speaker who steps off the stage without losing the role tag MUST emit
   `onstage="0"`.
7. The `d`-tag MUST be the FIXED string `nests-room-presence` (chosen so
   that one presence event represents a participant's CURRENT room across
   all clients). A participant MUST NOT be in two rooms simultaneously.
8. Every flag MUST be present in every emitted event. Receivers MAY assume
   omitted flags default to `0`, but emitters that omit are non-conformant.
9. Hosts and clients displaying the participant grid MUST hide presences
   from `kind:10312` events whose `created_at` is older than the staleness
   window in rule (2).
10. The flag values `"0"` and `"1"` are **strings**, not JSON booleans —
    they appear inside a Nostr tag array which is `[string, string, ...]`
    by NIP-01. Parsers MUST accept strings only; integers, booleans, or
    any other JSON type for these positions MUST be treated as malformed.
11. **Single-room rule.** Because the `d`-tag is fixed (rule 7), a
    participant has exactly one current presence event, scoped to one
    room. Receivers MUST keep only the most recent `kind:10312` per
    pubkey (replaceable-event semantics) and apply NIP-01 tie-break on
    identical `created_at`. A buggy peer that publishes presences for
    two rooms in rapid succession is presumed to be in whichever room
    the most recent presence references.

## Example

```json
{
  "kind": 10312,
  "pubkey": "def...co",
  "created_at": 1714003231,
  "tags": [
    ["a", "30312:abchost:office-hours-2026-04", "wss://relay.example"],
    ["hand", "0"],
    ["muted", "0"],
    ["publishing", "1"],
    ["onstage", "1"],
    ["alt", "Room Presence tag"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

EGG-07 (moderation) reads `hand` to drive the host's promote queue.
EGG-03 (audio plane) is the source of truth for whether a speaker IS
broadcasting; presence's `publishing` flag is a Nostr-side reflection that
clients use as a hint when they cannot subscribe to the audio plane (e.g.
listeners on weak networks rendering the home bubble).

The `kind:10312` shape here intentionally mirrors `kind:10311` from
NIP-53 streaming so that a generic Nostr client can render audience
indicators for both protocols without code-paths.
