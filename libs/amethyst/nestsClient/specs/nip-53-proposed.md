# NIP-53

## Live Activities

`draft` `optional`

Service providers want to offer live activities to the Nostr network in such a way that participants can easily logon, chat, send zaps and follow other participants. This NIP describes a framework to advertise and discover hosts of live activities, such as streaming and audio rooms.

## Format

The format uses two NIP-01 *addressable* events, plus one *replaceable* event for user preferences:

| kind  | name                            | scope                |
|-------|---------------------------------|----------------------|
| 30311 | Live Event                      | streaming            |
| 30312 | Interactive Room (Audio Space)  | nests / audio rooms  |
| 10112 | User MoQ-Server List            | nests user prefs     |

This document is a **proposed update to NIP-53** based on what the
deployed nostrnests reference (NestsUI v2 + moq-auth + moq-rs)
actually emits. Earlier versions of this NIP described kind 30312
with the `service`, `endpoint`, and `status: open|closed` tags; the
deployed reference uses `auth`, `streaming`, and `status: live|ended`
instead. Receivers MUST tolerate both tag-name spellings (canonical
vs. legacy) but publishers MUST emit the canonical names defined
below.

### Live Event (kind 30311)

Unchanged from the current NIP-53. Streaming hosts publish a
NIP-01 addressable event with the following tags:

```json
{
  "kind": 30311,
  "tags": [
    ["d", "<unique identifier>"],
    ["title", "<name of the event>"],
    ["summary", "<description>"],
    ["image", "<preview image url>"],
    ["t", "hashtag"],
    ["streaming", "<url>"],
    ["recording", "<url>"],
    ["starts", "<unix timestamp in seconds>"],
    ["ends",   "<unix timestamp in seconds>"],
    ["status", "<planned, live, ended>"],
    ["current_participants", "<number>"],
    ["total_participants", "<number>"],
    ["p", "<pubkey>", "<relay url>", "<role>", "<proof>"],
    ["relays", "<relay 1>", "<relay 2>", ...]
  ]
}
```

### Interactive Room (kind 30312)

A `kind:30312` is the audio-room (a.k.a. **nest**) counterpart of a
streaming Live Event. The event's pubkey is the host. Subscribers read
this event to learn who runs the room, where the audio plane lives,
who else is invited, and whether the room is currently live.

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
    ["starts", "<unix timestamp in seconds>"],
    ["color",  "<gradient identifier or hex>"],
    ["relays", "<wss relay 1>", "<wss relay 2>", ...],
    ["p", "<pubkey>", "<relay hint>", "host" | "admin" | "speaker"],
    ...
  ],
  "content": "",
  ...
}
```

Required tags: `d`, `title`, `status`, `streaming`, `auth`, plus a
single `p` entry naming the host.

#### `streaming` tag

Base URL of a WebTransport-capable moq-relay (the audio plane). The
relay listens on QUIC; the URL's port may accept UDP only. Clients
MUST NOT issue HTTP requests against this URL — it is the
WebTransport authority for the `:authority` pseudo-header during
session establishment.

#### `auth` tag

Base URL of the moq-auth sidecar (the JWT mint). The sidecar speaks
HTTP/1.1 or HTTP/2 over TCP/TLS — it MUST be reachable on a port
that accepts TCP (typically 443). Clients MUST NOT collapse this URL
into the `streaming` URL: the public reference deployment hosts them
on different hosts (`moq.nostrnests.com:4443` for the relay,
`moq-auth.nostrnests.com` for auth), and an HTTP request against the
QUIC-only relay port hangs without ever reaching a server.

The two URLs MAY point at the same authority for community
deployments that genuinely co-locate both services, but they remain
independent fields and clients MUST NOT assume they share an
authority.

#### `status` tag

| value     | meaning                                                            |
|-----------|--------------------------------------------------------------------|
| `live`    | Room in progress, anyone may join. Auth sidecar mints listener tokens. |
| `private` | Room in progress with an out-of-band allowlist. Auth sidecar replies `403` to non-allowlisted requesters. |
| `ended`   | Room is over; audio plane SHOULD be torn down server-side.         |
| `planned` | Room is scheduled to start at the `starts` unix-second timestamp.  |

Receivers MUST also accept `open` (synonym for `live`) and `closed`
(synonym for `ended`) on read for back-compat with earlier drafts.
Publishers MUST emit only the canonical names.

#### `p` tag (participant)

`["p", <pubkey hex>, <relay hint>, <role>, <proof>]`

`role` is one of `host`, `admin`, `speaker`. Role flips re-publish
the kind-30312 event. `proof` is reserved for invitee
acknowledgements; absent today.

#### `relays` tag

A SINGLE tag whose first element is the literal string `"relays"`
and whose remaining elements are wss URLs. Implementers MUST NOT
emit one `["relays", url]` tag per relay.

#### Authentication & WebTransport handshake

To open the audio plane, a peer mints a JWT against the auth sidecar
using NIP-98 and then opens a WebTransport CONNECT against the relay
carrying the JWT in the URL.

##### Step 1 — token request

```
POST <auth>/auth
Authorization: Nostr <base64(NIP-98 kind:27235 event JSON)>
Content-Type:  application/json; charset=utf-8

{ "namespace": "nests/30312:<host_pubkey_hex>:<room_d>",
  "publish":   <boolean> }
```

`<auth>` is the kind-30312 `auth` tag value with any trailing `/`
stripped. The body is a single-line UTF-8 JSON object — the server
hashes the **exact bytes sent** to compare against NIP-98's `payload`
tag, so producers MUST NOT pretty-print or re-order keys after
signing. The NIP-98 event's `u` tag MUST be the full URL
`<auth>/auth` (scheme included).

`publish: true` requests a speaker token; `publish: false` a listener
token. The auth sidecar MUST gate `publish: true` on the requester
appearing in the kind-30312 event's `p` tag with role `host`,
`admin`, or `speaker`.

##### Step 2 — token response

```
HTTP/1.1 200 OK
Content-Type: application/json

{ "token": "<jwt>" }
```

The JWT is signed `alg: ES256`. The sidecar MUST publish its public
keys at `GET <auth>/.well-known/jwks.json` (RFC 7517). The relay
MUST verify inbound JWTs against this JWKS and SHOULD cache it for
at most 5 minutes.

JWT claims (minimum):

| claim   | meaning                                                            |
|---------|--------------------------------------------------------------------|
| `root`  | The `namespace` echoed back. Authorization is scoped here.         |
| `get`   | Read-allowed sub-paths; for nests this is `[""]` (any).            |
| `put`   | Publish-allowed sub-paths. Listener: `[]`. Speaker: `[<pubkey>]`.  |
| `iat`   | Unix seconds; issuance.                                            |
| `exp`   | Unix seconds; expiry. Recommended `iat + 600`.                     |

There is no refresh endpoint. Long sessions MUST mint a fresh token
and open a new WebTransport session before the old token expires.

##### Step 3 — WebTransport CONNECT

```
:method   = CONNECT
:protocol = webtransport
:scheme   = https
:authority = <host:port from `streaming`>
:path     = /<namespace>?jwt=<token>
```

The relay reads the JWT from the `?jwt=` query parameter and MUST
NOT trust the WebTransport authority for authorization — only the
JWT's `root` claim. A token issued for namespace A MUST NOT be
accepted on a session opened against namespace B.

Peers MUST NOT log the JWT or include it in error reports.

##### Error taxonomy

The auth sidecar MUST return `application/json` bodies of shape
`{ "error": "<slug>", "reason": "<human string>" }` on non-2xx
responses. Defined slugs:

| status | slug                | when                                                             |
|--------|---------------------|------------------------------------------------------------------|
| 400    | `bad_request`       | malformed JSON body or missing `namespace`                       |
| 400    | `bad_namespace`     | namespace does not match `nests/<kind>:<hexpubkey>:<d>`          |
| 401    | `bad_nip98`         | Authorization header missing, malformed, or `id`/`sig` invalid   |
| 401    | `wrong_url`         | NIP-98 `u` tag does not match the actual request URL             |
| 401    | `wrong_method`      | NIP-98 `method` tag is not `POST`                                |
| 401    | `wrong_payload`     | NIP-98 `payload` tag does not match sha256 of the body bytes     |
| 401    | `stale`             | NIP-98 `created_at` outside the ±60 s tolerance                  |
| 403    | `room_closed`       | room status is `ended` or `planned`                              |
| 403    | `not_invited`       | room status is `private` and requester not on allowlist          |
| 403    | `publish_forbidden` | `publish: true` requested but caller is not a speaker            |
| 410    | `unknown_room`      | no `kind:30312` known to the sidecar for `(host, d)`             |
| 429    | `rate_limited`      | per-pubkey or per-IP rate limit; `Retry-After` SHOULD be set     |
| 5xx    | `internal`          | sidecar internal error                                           |

The relay signals authorization failures through WebTransport
CONNECT response codes:

| WT status | meaning                                                                |
|-----------|------------------------------------------------------------------------|
| 200       | session established                                                    |
| 401       | JWT signature invalid, expired, or fails the JWKS check                |
| 403       | JWT `root` does not match path namespace, or `put` claim missing       |
| 404       | path namespace unknown to the relay                                    |

#### Example

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

### User MoQ-Server List (kind 10112)

A user MAY publish a list of nests servers they prefer to host on as
a NIP-01 *replaceable* event. Clients read this list to default-fill
the `streaming` and `auth` tags of a new `kind:30312` room.

Each entry is a single tag carrying **two** URLs because the
moq-relay and moq-auth sidecar live on different hosts in the
deployed reference:

```json
{
  "kind": 10112,
  "pubkey": "<user pubkey hex>",
  "tags": [
    ["server", "<https URL of moq-relay>", "<https URL of moq-auth>"],
    ["server", "<https URL of moq-relay>", "<https URL of moq-auth>"],
    ...
  ],
  "content": ""
}
```

Tag elements are positional: index 1 is the moq-relay URL (becomes
the kind-30312 `streaming` tag value), index 2 is the moq-auth URL
(becomes the kind-30312 `auth` tag value). Order of tags is
preserved by receivers; earlier entries are higher priority.

#### Back-compat

Receivers MUST accept two looser shapes from earlier deployed
clients; publishers MUST emit only the canonical 3-element form:

1. **First-element name `relay`.** Earliest NestsUI iterations
   wrote `["relay", relay, auth]`. Treat as a synonym for `server`.
2. **Auth URL omitted.** A 2-element `["server", relay]` (or
   `["relay", relay]`) carries no auth URL on the wire. Receivers
   MUST derive it from the relay URL by replacing a leading `moq.`
   host label with `moq-auth.`, or prepending `moq-auth.` when the
   relay host has no `moq.` prefix. Drop the entry if the relay URL
   is unparseable.

#### Behavior

1. Each `relay` and `auth` value MUST be a fully-qualified URL
   beginning with `https://`. Receivers MUST drop entries that are
   not well-formed HTTPS URLs.
2. Receivers MUST de-duplicate by exact-string match on the relay
   URL after trimming a single trailing `/`. Order is preserved
   (FIRST occurrence wins).
3. When the user opens a create-room sheet, the client SHOULD
   pre-fill the kind-30312 `streaming` and `auth` tags from the
   FIRST entry in the list. Clients MUST NOT collapse the two URLs
   into a single field.
4. Users MAY enumerate up to 64 servers; receivers MUST tolerate
   longer lists by truncating to the first 64.
5. The list is purely a defaults / discovery hint. A `kind:30312`
   event's own `streaming` / `auth` tags are authoritative for that
   specific room and override the user list at join time.

#### Example

```json
{
  "kind": 10112,
  "pubkey": "abc...host",
  "created_at": 1714003000,
  "tags": [
    ["server", "https://moq.nostrnests.com:4443", "https://moq-auth.nostrnests.com"],
    ["server", "https://relay.example.org:4443",  "https://moq-auth.example.org"]
  ],
  "content": "",
  "id": "...",
  "sig": "..."
}
```

## Use Cases

Common use cases include meeting room/conference calls, watch-together activities, audio spaces, hangouts, and game streams.

## Notes

Live Activity management events are not designed to be used by relays for filtering, so clients SHOULD use the addressable event (`<kind>:<pubkey>:<d>`) when referencing room messages.
