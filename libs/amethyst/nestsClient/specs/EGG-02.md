# EGG-02: Auth & WebTransport handshake

`status: draft`
`requires: EGG-01, NIP-98`
`category: required`

## Summary

To open the audio plane (EGG-03), a peer first proves identity to a per-room
auth sidecar (moq-auth) using a NIP-98 HTTP signature, receives a short-lived
JWT, then opens a WebTransport session against the moq-relay carrying the JWT
in the URL.

Two separate URLs are involved: the `auth` tag from EGG-01 is the auth
sidecar (HTTP/1.1 or HTTP/2 over TCP); the `streaming` tag is the moq-relay
(WebTransport over QUIC). The deployed nostrnests reference puts these on
different hosts — see EGG-01 §4 for why they cannot be collapsed.

## Wire format

### Step 1 — token request

```
POST <auth>/auth
Authorization: Nostr <base64(NIP-98 kind:27235 event JSON)>
Content-Type:  application/json; charset=utf-8

{ "namespace": "nests/<kind>:<host_pubkey_hex>:<room_d>",
  "publish":   <boolean> }
```

`<kind>` is `30312` for nests audio rooms (EGG-01).

The `<auth>` URL is the EGG-01 `auth` tag value with any trailing `/`
stripped, then `/auth` appended literally. (The same path component is
used regardless of how the host is named — `<auth>` here is just the
sidecar base URL, so the full request line is `POST <auth-base>/auth`.)

The body is a single-line UTF-8 JSON object — the server hashes the **exact
bytes sent** to compare against NIP-98's `payload` tag, so producers MUST
NOT pretty-print or re-order keys after signing.

The `<host_pubkey_hex>` is the host's pubkey, lowercase hex. The `<room_d>`
is the EGG-01 `d` tag (which by EGG-01 rule 9 cannot contain `:`, so the
namespace is unambiguous without escaping).

#### NIP-98 event shape

The signed kind 27235 event MUST carry exactly these tags (NIP-98 §1):

```json
{
  "kind": 27235,
  "pubkey": "<requester pubkey hex>",
  "created_at": <unix seconds>,
  "tags": [
    ["u",       "<auth>/auth"],      // exact request URL, scheme included
    ["method",  "POST"],
    ["payload", "<sha256 of request body, lowercase hex>"]
  ],
  "content": "",
  "id":  "<...>",
  "sig": "<...>"
}
```

The event JSON is then serialized (NIP-01 canonical form), encoded as
**RFC 4648 standard Base64** (NOT base64url; pad with `=`), prefixed with
`Nostr ` (literal, including the trailing space), and placed in the
`Authorization` header.

### Step 2 — token response

```
HTTP/1.1 200 OK
Content-Type: application/json

{ "token": "<jwt>" }
```

The JWT carries (at minimum):

| claim   | meaning                                                       |
|---------|---------------------------------------------------------------|
| `root`  | The exact `namespace` echoed back. Authorisation is scoped here. |
| `get`   | Read-allowed sub-paths; for nests this is `[""]` (any).       |
| `put`   | Publish-allowed sub-paths. For listeners: `[]`. For speakers: `[<own pubkey hex>]`. |
| `iat`   | Unix seconds; issuance.                                       |
| `exp`   | Unix seconds; expiry. MUST be `iat + 600` for nests today.    |

#### JWT signing

The JWT MUST be signed with `alg: "ES256"` (ECDSA on the NIST P-256 curve,
RFC 7518 §3.4). The auth sidecar MUST publish its public verification keys
as a JWKS at:

```
GET <auth>/.well-known/jwks.json
```

The response is an `application/json` body shaped per RFC 7517:

```json
{
  "keys": [
    {
      "kty": "EC", "crv": "P-256", "alg": "ES256",
      "use": "sig", "kid": "<key id>",
      "x": "<base64url x>", "y": "<base64url y>"
    }
  ]
}
```

The relay (EGG-03 streaming endpoint) MUST verify inbound JWTs against this JWKS.
Relays SHOULD cache the JWKS for at most 5 minutes so a key rotation
propagates without requiring a relay restart. A relay that cannot reach
the JWKS endpoint MUST refuse new sessions rather than fall through to
"trust the unverified token".

### Step 3 — WebTransport CONNECT

```
:method  = CONNECT
:protocol = webtransport
:scheme  = https
:authority = <host:port from `streaming`>
:path    = /<namespace>?jwt=<token>
```

The relay reads the JWT from the `?jwt=` query string. No `Authorization`
header is used at this step.

## Behavior

1. Listening peers MUST request `publish: false`. Speaking peers MUST request
   `publish: true` AND the JWT's `put` claim MUST list the speaker's own
   pubkey hex.
2. The auth sidecar's TLS certificate MUST be a publicly-trusted chain. The
   relay's TLS chain MAY be self-signed in development; production deployments
   MUST use a public chain.
3. The auth sidecar SHOULD reject any request whose NIP-98 `created_at` is
   more than 60 s in the past or future.
4. The JWT lifetime is fixed at 600 s. There is no refresh endpoint. A client
   that needs a longer session MUST mint a fresh token and open a new
   WebTransport session before the old token expires; see the deployment-side
   commentary in `nestsClient/plans/`.
5. The relay MUST close the WebTransport session within 30 s of `exp`. A peer
   receiving an unexpected close MUST be prepared to mint a fresh token and
   reconnect.
6. The relay MUST NOT trust the WebTransport authority for authorisation —
   only the JWT's `root` claim. A token issued for namespace A MUST NOT be
   accepted on a session opened against namespace B.
7. A peer MUST NOT log the JWT or include it in error reports. The token is a
   bearer credential.

## Error taxonomy

The auth sidecar MUST use the following HTTP status codes for `POST /auth`.
Bodies are `application/json` and follow the shape
`{ "error": "<machine slug>", "reason": "<human string>" }`. Receivers
MAY ignore `reason` but MUST surface `error` to user-facing error toasts.

| status | `error` slug         | when                                                                |
|--------|----------------------|---------------------------------------------------------------------|
| 200    | —                    | success; body is `{ "token": "<jwt>" }`                              |
| 400    | `bad_request`        | malformed JSON body, missing `namespace`, unknown content-type       |
| 400    | `bad_namespace`      | namespace does not match `nests/<kind>:<hexpubkey>:<d>`              |
| 401    | `bad_nip98`          | Authorization header missing, malformed, or `id`/`sig` invalid       |
| 401    | `wrong_url`          | NIP-98 `u` tag does not match the actual request URL                 |
| 401    | `wrong_method`       | NIP-98 `method` tag is not `POST`                                    |
| 401    | `wrong_payload`      | NIP-98 `payload` tag does not match sha256 of the body bytes         |
| 401    | `stale`              | NIP-98 `created_at` outside the ±60 s tolerance                      |
| 403    | `room_closed`        | room status is `ended` (EGG-01) or `planned` (EGG-08)                |
| 403    | `not_invited`        | room status is `private` and requester is not on the allowlist       |
| 403    | `publish_forbidden`  | `publish: true` requested but caller is not a speaker per EGG-07     |
| 410    | `unknown_room`       | no `kind:30312` known to the sidecar for `(host, d)`                 |
| 429    | `rate_limited`       | per-pubkey or per-IP rate limit; `Retry-After` header SHOULD be set |
| 5xx    | `internal`           | sidecar internal error                                               |

Receivers MUST treat unknown 4xx slugs as "fatal, do not retry" and
unknown 5xx slugs as "transient, retry with exponential backoff".

The relay (EGG-03 streaming endpoint) signals authorization failures through
WebTransport CONNECT response codes, NOT through the auth-sidecar table:

| WT status | meaning                                                                |
|-----------|------------------------------------------------------------------------|
| 200       | session established                                                    |
| 401       | JWT signature invalid, expired, or fails the JWKS check                |
| 403       | JWT `root` does not match the path namespace, or `put` claim missing for a publishing peer |
| 404       | path namespace unknown to the relay                                    |

## Example

```
> POST https://moq-auth.nostrnests.com/auth
> Authorization: Nostr eyJ...kind27235...
> Content-Type: application/json
>
> {"namespace":"nests/30312:abchost:office-hours-2026-04","publish":false}

< HTTP/1.1 200 OK
< Content-Type: application/json
<
< {"token":"eyJhbGc..."}

> CONNECT :path=/nests/30312:abchost:office-hours-2026-04?jwt=eyJhbGc...
< 200 OK   (WebTransport session established)
```

## Compatibility

EGG-03 (audio plane) operates inside the WebTransport session this EGG opens.
EGG-12 (catalog) shares the same session.
