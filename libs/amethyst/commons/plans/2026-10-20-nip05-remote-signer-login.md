# Signing login with a NIP-05 address (NIP-46 discovery)

Status: implemented 2026-10-20 (Amethyst and `amy`). Not yet checked against a live provider: see
"Two discovery shapes in the wild".

Decided:
- Legacy before spec: legacy names the user's own key, and it is what is deployed.
- An address without a signer logs in read-only, as before.
- `amy login name@domain --read-only` keeps the read-only login on purpose. The app keeps it for
  platforms that can't log in to a remote signer (`AppPlatform.supportsRemoteSignerLogin`).

Found on the way: `resolveUserHexOrNull` turned an even-length address (`bob@signer.example`) into
a few bogus bytes through the key path's lenient hex fallback, so read-only NIP-05 logins could end
on a garbage key. Fixed by trying NIP-05 first and requiring 64 hex from the key path.

## Problem

Typing `bob@signer.example` in the login field resolves the NIP-05 to a pubkey and logs in **read-only**
(`AccountSessionManager.loginSync`, the `EMAIL_PATTERN` branch). A remote signer that serves that
domain can sign for the account, and NIP-46 (appendix, "Announcing remote-signer metadata") lets a
client find it from the domain's `/.well-known/nostr.json`. The user should get a signing account
from the address alone, approving on the signer's page, with no `bunker://` string to copy.

## Two discovery shapes in the wild

| | Spec (NIP-46 appendix today) | Legacy (what clients and signers use) |
|--|--|--|
| Query | `nostr.json?name=_` | `nostr.json?name=<name>` |
| Signer key | `names._` (the signer *app's* pubkey) | `names[<name>]`, used as the bunker pubkey |
| Relays | `nip46.relays: [...]` | `nip46: { "<pubkey>": [...] }` |
| Extra | `nip46.nostrconnect_url`, a template with `<nostrconnect>` | none |

The legacy shape is what nostr-tools reads in every version up to 2.25.2 (`queryBunkerProfile`:
`pubkey = names[name]`, `relays = nip46[pubkey]`), and nostr-login is built on it. The
spec shape gives the signer app, not the user, so it needs `nostrconnect://` (the user approves on
the signer's site) or a connect with no secret followed by an `auth_url` challenge.

Not verified against a live provider's document: the parser fixtures are written by hand from
nostr-tools and the spec. Capture a real `nostr.json` as a fixture once a provider is reachable.

## Flow

1. The login field gets `name@domain` (`EMAIL_PATTERN`). Fetch `nostr.json?name=<name>` through the
   existing `Nip05Client`, which uses the app's HTTP routing (Tor) and refuses redirects.
2. **Legacy:** `nip46[names[name]]` has relays → a bunker pointer `{pubkey, relays, secret: null}`.
   - Connect with no secret, through `BunkerLoginUseCase` with the auth prompts.
   - The signer answers `auth_url`; the existing `RemoteSignerAuthPrompt` dialog shows it.
   - Then `get_public_key`, then the same steps as a `bunker://` login.
3. **Spec:** otherwise fetch `?name=_`.
   - If `nostrconnect_url` is there: build our `nostrconnect://` code with the signer's relays
     (`NostrConnectLoginUseCase.generateUri`). Open the URL with the code substituted for
     `<nostrconnect>`, url-encoded, and wait for the signer's answer as the QR flow does.
   - If there is no URL: connect to `names._` on `nip46.relays` with no secret, as in step 2.
4. **Identity check (both paths):** the account the signer reports in `get_public_key` must equal
   `names[name]`. Otherwise refuse the login and say so. The user asked for `bob@domain`, not
   whoever the signer happens to sign for.
5. **No `nip46` in the document:** today's read-only login, with a line on the login screen saying
   the address has no remote signer.
6. Save it like a `nostrconnect://` login: the bunker address (signer pubkey + relays, no secret)
   and the transport key. `switch_relays` (already sent on every load) keeps the relays current
   after that.

## Where the code goes

| Piece | Module / file | New or reused |
|--|--|--|
| Parse `nip46` in both shapes into `Nip05RemoteSigner(signerPubKey, relays, nostrconnectUrl)` | `quartz/…/nip05DnsIdentifiers/Nip05Parser.kt` + a new data class | new |
| `INip05Client.loadRemoteSigner(nip05)` (both queries) | `quartz/…/nip05DnsIdentifiers/` | new, default `null` |
| Fill the `<nostrconnect>` template | `quartz/…/nip46RemoteSigner/NostrConnectURI.kt` | new helper |
| Secret-less connect | `BunkerLoginUseCase` (`secret` is already optional) | reused |
| `nostrconnect://` with given relays | `NostrConnectLoginUseCase` | reused |
| `auth_url` approval | `RemoteSignerAuthPrompts` + `RemoteSignerAuthPrompt` | reused |
| Discover → pick a path → identity check | `commons/…/domain/nip46/Nip05SignerLoginUseCase.kt` | new |
| `EMAIL_PATTERN` branch: try a signer first, fall back to read-only | `AccountSessionManager` | changed |
| Progress / "no signer, read-only" / identity-mismatch text | `commonsUI` strings + `LoginViewModel` | new strings |
| Open `nostrconnect_url` | `LocalUriHandler`, as the auth dialog does | reused |
| `amy login bob@domain` | `cli/…/commands/LoginCommand.kt` → the same use case | thin |

## Safety

- `nostrconnect_url` and every page a signer sends go through `RemoteSignerAuthPrompts.isWebPage`
  (http(s) only, no whitespace).
- Relays from the document: normalized and capped as `SwitchRelaysResponse` does; with no usable
  relay, fall back to read-only.
- The identity check in step 4 is required: a compromised or confused domain must not log the user
  in as someone else.
- The NIP-05 fetch stays on the app's HTTP client (Tor, no redirects), as `Nip05Client` already
  does.

## Out of scope

NIP-89 signer discovery (kind 31990, `k=24133`) and nostr-tools' non-standard `create_account`.

## Tests

- Parser: both shapes, `nip46` missing, malformed, relays not relay URLs, and the captured real
  fixtures.
- Use case, against an in-process signer and a fake `Nip05Fetcher`:
  - legacy secret-less connect with an `auth_url` step;
  - the spec path through `nostrconnect://`;
  - identity mismatch refused;
  - no `nip46` falls back to read-only.
- CLI: a harness case for `amy login name@domain` against `amy serve` and a local `nostr.json`.
- By hand: an account at a domain that advertises a signer, on a device.
