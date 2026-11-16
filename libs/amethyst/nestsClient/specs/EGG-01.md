# EGG-01: Room event (`kind:30312`)

`status: draft`
`requires: NIP-01`
`category: required`

## Summary

A nests audio room is a NIP-53 addressable event of `kind:30312`. The author
is the host. Subscribers read this event to learn who runs the room, where the
audio plane lives, who else is invited, and whether the room is currently live.

Every other EGG layers on top of this event.

## Wire format

```json
{
  "kind": 30312,
  "pubkey": "<host pubkey hex>",
  "tags": [
    ["d", "<room id>"],
    ["title", "<room name>"],
    ["summary", "<one-line description>"],
    ["image", "<optional cover image URL>"],
    ["status", "live" | "private" | "ended" | "planned"],
    ["streaming", "<https URL of moq-relay WebTransport>"],
    ["auth",      "<https URL of moq-auth sidecar>"],
    ["relays", "<wss relay 1>", "<wss relay 2>", ...],   // ONE tag, multiple values
    ["p", "<pubkey>", "<relay hint>", "host" | "admin" | "speaker"],
    ...
  ],
  "content": "",
  ...
}
```

The `d` tag is the room id; `(pubkey, kind, d)` is the addressable identity.

The `relays` tag is encoded as a SINGLE tag whose first element is `"relays"`
and whose remaining elements are wss URLs. Implementers MUST NOT emit one
`["relays", url]` tag per relay; receivers MUST treat such input as a
malformed event and MAY repair it by concatenating the values, but
publishers that emit it are non-conformant.

### Tag-name back-compat

Earlier drafts of this EGG used `room` for the room name, `service` for the
auth URL, `endpoint` for the relay URL, and `open` / `closed` for the live /
ended states. Those names are still emitted by some legacy clients. Receivers
MUST accept all of:

| Canonical (this EGG) | Legacy (older drafts)        |
|----------------------|------------------------------|
| `title`              | `room`                       |
| `streaming`          | `endpoint`                   |
| `auth`               | `service`                    |
| `status: live`       | `status: open`               |
| `status: ended`      | `status: closed`             |

When a single event carries both spellings (e.g. `streaming` AND `endpoint`),
the canonical name wins; the legacy name is treated as a duplicate. Publishers
MUST emit only the canonical names.

## Behavior

1. Hosts MUST emit exactly one `kind:30312` event per room. Updating the room
   (rename, status change, role grants) MUST re-publish the same `d` tag with
   a higher `created_at`.
2. The `title`, `status`, `streaming`, and `auth` tags MUST be present. A
   client receiving an event without all four MUST treat the room as
   un-joinable.
3. The `auth` value MUST be the base URL of an EGG-02 auth sidecar (do
   not include the `/auth` suffix). Receivers MUST normalize the value by
   stripping a single trailing `/` before constructing sub-paths. The auth
   sidecar speaks HTTP/1.1 or HTTP/2 over TCP/TLS — it MUST be reachable on
   a port that accepts TCP (typically 443).
4. The `streaming` value MUST be the base URL of a WebTransport-capable
   moq-relay implementing EGG-03. Because WebTransport runs over QUIC, the
   `streaming` host's port may listen on UDP only — clients MUST NOT use
   it for the JWT-mint POST. The two URLs are separate fields precisely so
   they can live on different hosts/ports (the public nostrnests deployment
   does: `streaming = https://moq.nostrnests.com:4443`,
   `auth = https://moq-auth.nostrnests.com`).
5. The `p` tag MUST list the host as the first participant. Additional
   `p` tags grant roles (EGG-07).
6. Status semantics:
   - `live` — room is in progress, anyone can join. The auth sidecar MUST
     mint a listener token to any well-formed NIP-98 request.
   - `private` — room is live, but the auth sidecar applies an out-of-band
     allowlist. The allowlist mechanism is implementation-defined; this
     spec only mandates that a non-allowlisted requester receives `403`
     (see EGG-02 error taxonomy). Clients without a path to acquire access
     MUST render `private` rooms as un-joinable rather than attempt to
     connect blind.
   - `ended` — room is over, audio plane SHOULD be torn down server-side.
   - `planned` — room hasn't started; see EGG-08.
7. A host MAY treat a room as auto-ended after 8 h of `created_at` staleness
   even if the published status is still `live`/`private`. Receivers SHOULD do
   the same to avoid stale rooms hanging at the top of the live UI.
8. An empty `content` field is REQUIRED. Future EGGs MAY define structured
   content; until then, peers MUST ignore non-empty content rather than
   rejecting the event.
9. The `d` tag MUST contain only characters from `[A-Za-z0-9._-]`. Colons,
   slashes, whitespace, and percent-encoded sequences are forbidden because
   the `d` is interpolated unescaped into the moq-auth namespace
   `nests/<kind>:<host pubkey hex>:<d>` (EGG-02). Receivers MUST reject
   events whose `d` violates this charset.
10. **Relay discovery.** Hosts SHOULD publish the `kind:30312` event to (a)
    every relay listed in the event's own `relays` tag, AND (b) their
    NIP-65 (`kind:10002`) write relays. Receivers SHOULD subscribe to the
    same union to track room metadata changes (rename, status flip,
    role grants).
11. **Tie-break on identical `created_at`.** When two `kind:30312` events
    share `(pubkey, d)` AND `created_at`, receivers MUST keep the event
    with the lexicographically SMALLEST `id` and discard the other (per
    NIP-01 replaceable-event tie-break).
12. **Publish-before-mint ordering.** A peer MUST NOT request a JWT
    (EGG-02) for a room before that room's `kind:30312` event has
    propagated to relays the auth sidecar can read. The auth sidecar
    looks up the most-recent `kind:30312` by `(pubkey, kind, d)` to
    validate the room exists and to enforce status-based gates
    (EGG-08 rule 5). For hosts: this means publishing the freshly-
    composed event BEFORE minting their own `publish: true` token.
    For listeners: this means waiting until the event is visible on
    the listener's own relay set (an unknown room cannot be joined).
    Auth sidecars that cannot find the room MUST return HTTP 410
    `unknown_room` (EGG-02 error taxonomy); peers SHOULD retry with
    1 s / 2 s / 4 s exponential backoff before surfacing the error.
13. **Streaming / auth selection (host-side guidance).** When
    composing a new room, hosts SHOULD pre-fill `streaming` and
    `auth` from the FIRST entry of their own `kind:10112` user
    server list (EGG-09 — each entry already carries the pair).
    When the host has no `kind:10112`, the client MAY ship a
    built-in default pair but MUST allow user override. Both
    `streaming` and `auth` MUST be `https://` URLs; `http://` MUST
    be rejected at compose time (mirrors EGG-09 rule 1). The two
    URLs MAY point at the same host:port for community deployments
    that genuinely co-locate both services, but they are independent
    fields and clients MUST NOT assume they share an authority.

## Example

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "created_at": 1714003200,
  "tags": [
    ["d", "office-hours-2026-04"],
    ["title", "Office Hours"],
    ["summary", "Weekly Q&A"],
    ["status", "live"],
    ["streaming", "https://moq.nostrnests.com:4443"],
    ["auth",      "https://moq-auth.nostrnests.com"],
    ["p", "abc...host", "wss://relay.example", "host"],
    ["p", "def...co",   "wss://relay.example", "speaker"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

Peers without EGG-01 cannot interpret any other EGG. EGG-04, EGG-05, EGG-06,
EGG-07, and EGG-12 reference rooms by the `(kind, pubkey, d)` tuple defined
here.
