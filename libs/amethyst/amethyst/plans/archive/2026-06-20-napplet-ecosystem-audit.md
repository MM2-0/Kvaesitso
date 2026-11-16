# Napplet implementation audit vs the upstream SDK / demo runtimes

> **Status:** shipped — Audit doc; the wire-compatibility gaps it identified were subsequently closed (shell handshake, keys, upload now in the broker/codec).
> _Audited 2026-06-30._

**Date:** 2026-06-20
**Sources:** `github.com/napplet/naps` (NAP specs), `github.com/napplet/web`
(`@napplet/shim` SDK), `github.com/kehto/web` + `kehto.github.io/web/playground`
(reference runtime). Audited against our branch `claude/awesome-pasteur-xwiwad`.

## TL;DR

Our shell is a **correct and security-hardened NIP-5A/5D renderer + capability
broker** — process isolation, verified-blob serving, default-deny CSP,
capability-aware + signer-aware consent, foreground-only, permissions UI. Those
are *shell-quality* properties the demo runtimes don't even specify, and we're
ahead there.

**But it is not wire-compatible with the napplet ecosystem.** Real napplets are
built against `@napplet/shim`, which exposes a **namespaced** `window.napplet.*`
and a **`{type:"domain.action", id}`** postMessage envelope. We inject a **flat**
`window.napplet.*` and use a **`{id, payload:{op}}`** envelope. A napplet from the
kehto playground calling `window.napplet.relay.publish(...)` or
`window.napplet.shell.supports("relay")` hits `undefined` on our shell. So today:
**0 real ecosystem napplets run as-is.**

## Reference: the upstream surface

`window.napplet` namespaces (from `@napplet/web`):

| Namespace | Methods (upstream) |
|---|---|
| `shell` | `supports(domain)` — **required of every runtime** |
| `identity` | `getPublicKey()`, `onChanged(handler)` |
| `keys` | signing / NIP-04 / NIP-44 (separate domain from `identity`) |
| `relay` | `subscribe(filters)` (live), `publish(event)`, `query(filters)`, `publishEncrypted` |
| `storage` | `get(key)`, `set(key, value)`, `remove(key)` |
| `inc` | `emit(type, payload)`, `on(type, handler)` (inter-napplet) |
| `resource` | `bytes(url)`, `bytesAsObjectURL(url)` — fetch https/blossom/nostr/data |
| `value` | shell-mediated value transfer + zaps (depends on `relay`) |
| `upload` | shell-mediated file/blob upload (Blossom; depends on `relay`) |
| `intent` | invoke a napplet by archetype |
| `theme`, `notify`, `media`, `config`, `outbox`, `ifc`, `cvm` | further domains |

**Envelope:** `{ type: "<domain>.<action>", id, ...payload }` →
result `{ type: "<domain>.<action>.result", id, ok, ... }`.
**Sandbox:** `iframe sandbox="allow-scripts"` only (no `allow-same-origin`) — **we
match this exactly.**

## Coverage scorecard (our broker vs upstream domains)

| Domain | Status | Notes |
|---|---|---|
| `shell` | ✗ **missing** | `supports()` is foundational; without it well-behaved napplets bail early |
| `identity` | ◐ | `getPublicKey` ✓; `onChanged` ✗ (no push) |
| `keys` (sign/nip04/nip44) | ◐ | implemented, but lumped under our `IDENTITY` capability, not a `keys` domain |
| `relay` | ◐ | `publish` ✓, `query` ✓ (now live); `subscribe` (live) ✗, `publishEncrypted` ✗ |
| `storage` | ✓ | get/set/remove — shape matches (wire differs) |
| `value` | ◐ | we do `payInvoice` via NWC; upstream `value` is zaps/value-transfer (depends on relay) — different method shape |
| `resource` | ✗ | no `bytes(url)`; we only serve manifest subresources |
| `upload` | ✗ | no Blossom upload |
| `inc` | ✗ | deferred (see inter-applet plan) |
| `intent` | ✗ | deferred |
| `theme` `notify` `media` `config` `outbox` `ifc` `cvm` | ✗ | not modeled |

Our extra `NET` domain has **no upstream equivalent** — upstream fetching is
`resource`. Our `fromNapDomain` recognizes `identity/relay/value/storage/net`;
everything else (`shell`, `resource`, `upload`, `inc`, `intent`, `keys`, …) maps
to *unknown → denied*. So a manifest `requires: ["relay","shell","resource"]`
would have two of three flagged unknown — and `shell` is mandatory.

## The two interop blockers

1. **Envelope.** Real napplets send `{type:"relay.publish", id, event}` and await
   `{type:"relay.publish.result", id, ok}`. We expect `{id, payload:{op:"publish"}}`
   and reply `{id, response:{type:"published"}}`. Incompatible.
2. **API surface.** Upstream is namespaced (`relay.publish`, `identity.getPublicKey`,
   `keys.signEvent`, `shell.supports`, `resource.bytes`). Ours is flat
   (`getPublicKey`, `signEvent`, `publish`, `queryEvents`, `payInvoice`) + a
   namespaced `storage`. Only `storage` lines up.

Both live in our **edge layer** (the injected JS shim + `NappletProtocolJson` +
`NappletIpc`) and the capability enum — *not* in the security core (broker,
ledger, process model), which is dialect-agnostic. So aligning is an edge rewrite,
not an architecture change.

## What we have that the demos don't

- Separate-OS-process sandbox (`:napplet`), not just an iframe.
- Verified-blob serving (manifest-authoritative, per-blob sha256) with default-deny
  CSP (`connect-src 'none'`) and Tor-routed fetch.
- Capability **declaration enforcement** (manifest `requires` gate), **per-use**
  payment consent, **signer-aware** identity deferral, **foreground-only** execution.
- A persisted permission ledger + a permissions-management UI.

These are real-shell concerns the reference runtimes leave to the implementer; we
should keep them.

## Recommended path to ecosystem compatibility (priority order)

1. **Adopt the upstream envelope** `{type:"domain.action", id}` / `…​.result` in the
   shim + `NappletProtocolJson` + `NappletIpc`. (Unblocks everything.)
2. **Re-shape the injected shim** to the namespaced `window.napplet.*` and add
   **`shell.supports(domain)`** (cheap; derive from the granted capability set).
3. **Split capabilities** to match domains: `keys` (sign/nip04/nip44) distinct from
   `identity` (getPublicKey/onChanged); rename `NET`→`resource`; add `SHELL`,
   `UPLOAD`, `VALUE` semantics (zap-by-target, not raw invoice).
4. **Add the push channel**: `relay.subscribe` (live events), `identity.onChanged`,
   later `inc.on` — the host already holds a `JavaScriptReplyProxy` we can push
   unsolicited `…event` messages through.
5. **`resource.bytes`** (consented fetch of https/blossom/nostr/data) and
   **`upload`** (Blossom), both brokered + consent-gated.
6. Lower priority / app-specific: `theme`, `notify`, `media`, `config`, `outbox`,
   `intent`, `inc`, `ifc`, `cvm`.

## Update (2026-06-20): ecosystem alignment landed

Acted on #1–#5. The dialect mismatch is resolved:

- **Envelope** is now `{type:"<domain>.<action>", id}` → `{type:"…​.result", id, ok, …}`,
  matching upstream (codec + host shuttle + shim rewritten; round-trip unit-tested).
- **Namespaced `window.napplet.*`** shim: `shell.supports`, `identity.getPublicKey`
  (+`onChanged` stub), `keys.{signEvent,nip04*,nip44*}`, `relay.{publish,query,subscribe}`,
  `storage.{get,set,remove}`, `value.payInvoice`, `resource.{bytes,bytesAsObjectURL}`,
  `upload.blob`. The applet's own SDK-targeted code now runs unchanged.
- **`shell.supports(domain)`** implemented (no consent; reflects declared+brokered domains).
- **Capabilities split** to the domain model: `SHELL`, `IDENTITY`, `KEYS`, `RELAY`,
  `STORAGE`, `VALUE`, `RESOURCE`, `UPLOAD` (was `IDENTITY/RELAY/WALLET/STORAGE/NET`).
- **`resource.bytes`** implemented for `https`/`data` (broker-fetched, Tor-routed,
  consent-gated); `blossom:`/`nostr:` are a follow-up.

## Update (2026-06-21): return shapes verified against `@napplet/nap@0.15.0`

Pulled the canonical message types (`@napplet/nap` `*/types.d.ts` + `value-types`) and corrected
the result field names — several were wrong guesses. The authoritative wire:

| Method | Request `type` | Result field(s) |
|---|---|---|
| `identity.getPublicKey` | `identity.getPublicKey` | `pubkey: string` |
| `identity.getProfile` | `identity.getProfile` | `profile: ProfileData \| null` |
| `identity.getRelays` | `identity.getRelays` | `relays: Record<url, {read,write}>` |
| `identity.getFollows`/`getMutes`/`getBlocked` | same | `pubkeys: string[]` |
| `identity.getList` | `identity.getList` | `entries: string[]` |
| `identity.getZaps` / `getBadges` | same | `zaps[]` / `badges[]` |
| `storage.getItem/setItem/removeItem/keys` | **`storage.get`/`set`/`remove`/`keys`** | `value` / — / — / `keys: string[]` |
| `relay.publish` / `publishEncrypted` | same | template in the **`event`** field; result `{ok, event, eventId}` |
| `relay.query` | `relay.query` | `events: NostrEvent[]` |
| `resource.bytes` | `resource.bytes` | `blob: Blob`, `mime: string` |

`ProfileData` is `{ name?, displayName?, about?, picture?, banner?, nip05?, lud16?, website? }` —
note **`displayName`** (camelCase), so the shell maps kind-0 `display_name` → `displayName` rather
than dumping raw content. Corrected in code: identity reads now emit method-specific fields;
`storage.*` wire types fixed (the *function* is `getItem`, the *envelope* is `storage.get`);
`storage.keys` returns `keys`; `relay.publish`/`publishEncrypted` read the template from `event`;
`getProfile` builds a `ProfileData` object. Locked by `NappletProtocolJsonTest`.

**Transport: structured-clone objects + subscription push — landed.** `@napplet/core` posts
**structured-clone objects** (not JSON strings) via `target.postMessage(obj)` and validates
cloneability — that is how `resource.bytes` returns a real `Blob`, and why a stock napplet's
object messages were dropped before (our shell only forwarded strings). Fixed:

- **`shell.html` bridges object↔string both ways.** applet→native serializes object envelopes to
  the string the native bridge carries (requests carry no Blobs); native→applet parses the reply
  to an object and posts a **structured-clone object** (what the SDK reads via `e.data.type`), not
  a string. The injected shim accepts either form.
- **`resource.bytes` Blob.** The shell rebuilds a real `Blob` from the host's base64 `bytes`+`mime`
  before delivering, so both the SDK and our shim resolve to a `Blob`.
- **`relay.subscribe` push channel.** Subscriptions are answered with `relay.event` (one per match)
  then `relay.eose`, keyed by `subId` — no `.result`, matching the SDK. A new `MSG_PUSH` IPC frame
  lets the broker push unsolicited envelopes the host forwards verbatim; `relay.close` is a
  fire-and-forget no-op. Today this delivers the **initial snapshot then EOSE**.

Still open: a **live subscription tail** (push as events arrive, not just the snapshot) plus
`identity.onChanged`/`inc.on`; multi-`filters` queries (we use the first filter); the Blossom
`upload` gateway; and **on-device verification** — the shell/shim changes are JS and not exercised
by the JVM unit tests.

## Update (2026-06-21, even later): feed surfacing + nsite runtime hardening

- **Feed surfacing (sandbox-preserving).** Napplets gained an inline feed card (nsites already had
  one), both render via `NoteCompose`, are indexed in `LocalCache`, and a profile **"Apps & Sites"**
  tab lists a user's manifests. The cards are inert (`Text`+`Button`, no WebView); execution begins
  only on explicit tap, in the `:napplet` process.
- **SPA route fallback** — a document navigation (Accept: text/html) to a route not in the manifest
  serves the verified `index.html`; missing sub-resources still 404.
- **External-link handoff** — a user-tapped off-origin http(s) link opens in the system browser
  (gesture-gated so a hostile site can't auto-redirect); the sandbox WebView never navigates away.
- **`resource.bytes` `blossom:` scheme** — `blossom:<sha256>` fetches from the user's kind:10063
  Blossom servers and verifies the hash before returning. `nostr:` stays deferred (unspecified).
- **Content-type byte-sniffing** — when a manifest path has no/unknown extension, the resolver
  sniffs magic bytes; text/markup is never sniffed, so HTML detection stays extension-driven.
  Unit-tested in quartz.
- **kind:10063 fallback** — the launcher augments the manifest's `servers` with the author's
  published Blossom list (best-effort); every blob is still sha256-verified.
- **Blob caching** — the host OkHttp client caches blobs on disk (forced-immutable, since they're
  content-addressed); the resolver re-verifies every served blob, so a stale entry can't be served.

Remaining: live subscription tail + `identity.onChanged`/`inc.on`, the Blossom `upload` gateway,
`getList`/`getZaps`/`getBadges`, the `nostr:` resource scheme, multi-`filters` queries — and
**on-device verification** of all the WebView-host behavior.

## Update (2026-06-20, later): verified against `@napplet/shim@0.16.0` and corrected

Pulled the authoritative SDK (`@napplet/shim` v0.16.0, npm/unpkg) and corrected the
implementation to its real contract. Commit `5ca44e27` had carried several wrong guesses; the
verified surface is:

| Namespace | Verified methods (v0.16.0) |
|---|---|
| `shell` | `supports(domain, protocol?)` (sync), `ready()`, `onReady(cb)`, `services` |
| `identity` | `getPublicKey()`, `onChanged(h)`, + read API (`getRelays/getProfile/getFollows/getList/getZaps/getMutes/getBlocked/getBadges`) |
| `keys` | **keyboard/command actions** — `registerAction/unregisterAction/onAction` (NOT signing) |
| `relay` | `publish(template, options?)` → signed `NostrEvent`, `publishEncrypted(template, recipient, encryption?)`, `query(filters)`, `subscribe(filters, onEvent, onEose, options?)` |
| `storage` | `getItem/setItem/removeItem/keys` (512 KB quota; `instance.*` variant) |
| `resource` | `bytes(url)` → `Blob`, `bytesAsObjectURL(url)` |
| `inc` | `emit(topic, extraTags?, content?)`, `on(topic, cb)` |

Crucial design fact, quoted: **"signing and encryption are mediated by the shell via
`relay.publish()` and `relay.publishEncrypted()`"** and *"no cryptographic dependencies — the
shim sends JSON envelope messages and the shell handles identity"*. **There is no `sign()` and no
raw nip04/44 in the napplet surface.** There is **no `value` or `upload` domain** in v0.16.0.

Corrections landed (this commit):

- **Signing model fixed (the big one).** Dropped the bogus `keys.signEvent` / `keys.nip04*` /
  `keys.nip44*` napplet ops. `relay.publish` now takes an **unsigned template** (`kind/tags/content`)
  and the broker signs it as the user and returns the signed event — exactly the upstream contract.
  Added `relay.publishEncrypted` (broker encrypts to recipient with nip44/nip04, then signs +
  publishes). The broker still defers the per-signature prompt to remote/external signers
  (`signsAsUser` + non-internal signer) and honors standing DENY.
- **`keys` re-pointed to keyboard actions** (`registerAction/unregisterAction/onAction`),
  implemented as client-side no-op stubs (not yet wired to the host keyboard) so action-using
  napplets don't crash. They never cross the broker boundary.
- **`storage` renamed** to `getItem/setItem/removeItem` and **`storage.keys`** added end-to-end
  (protocol + broker + DataStore + shim), matching upstream.
- **`resource.bytes` now returns a `Blob`** (shim builds it from `{bytes, mime}`); wire field
  renamed `contentType`→`mime`.
- **`shell.supports(domain, protocol?)`** gained the optional protocol arg; added `shell.ready()`,
  `onReady`, `services` stubs.
- **`relay.subscribe`** wired (initial matches; live tail still a follow-up).
- `value.payInvoice` and `upload.blob` are **kept as clearly-marked Amethyst-specific extensions**
  (no upstream equivalent in v0.16.0) — a real `@napplet/shim` napplet never calls them, so they
  can't conflict.

Verified off-device: `commons:jvmTest` (broker + capability + ledger) and the amethyst codec
round-trip test (`NappletProtocolJsonTest`) both green.

Still open (documented, not blocking basic napplets):
- **`upload`** — wired end-to-end (protocol/shim/capability) but the Android Blossom
  gateway is unprovided (`Unsupported`): a correct upload needs a content Uri + signed
  auth event + server selection, which needs on-device verification.
- **Live push** — `relay.subscribe` returns initial matches via `query`; a live tail and
  `identity.onChanged`/`inc.on` need a push channel over the existing reply proxy.
- **Underspecified domains** — `inc`, `intent`, `theme`, `notify`, `media`, `config`,
  `outbox`, `ifc`, `cvm` remain unknown→denied (no method spec available to build to).
- **Method-name fidelity** — ✅ resolved. All standard method names/shapes are now confirmed
  against `@napplet/shim@0.16.0` (see the later update above), not guessed.
- **Identity read API** — ✅ partly landed. `identity.getProfile` (kind-0 content), `getRelays`
  (NIP-65 read/write map), `getFollows` (kind-3 authors), `getMutes` and `getBlocked` (NIP-51
  decrypted user tags) now read from the active `Account` and return JSON, gated by the IDENTITY
  consent (and deferred to remote/external signers). `getList`/`getZaps`/`getBadges` route through
  but degrade to `Unsupported` for now; `identity.onChanged` is still a client-side no-op (needs
  the live push channel). Return shapes still want on-device verification against a real napplet.
- **On-device verification** of the whole round-trip with a real playground napplet.

Revised ecosystem-compatibility estimate: **~80%** — real request/response napplets using
identity(getPublicKey)/relay(publish/publishEncrypted/query/subscribe)/storage/resource +
`shell.supports` now run against the *verified* contract; remaining gaps are the identity read
API, keyboard-action wiring, live subscription tails, the niche domains, and device verification.

## Verdict (original assessment, pre-update)

- **As a secure NIP-5A/5D renderer + broker:** ~85% — the hard, security-critical
  parts are done and tested; gaps are on-device verification and breadth of ops.
- **As an ecosystem-compatible napplet host (runs real napplets):** ~25% — blocked
  by the envelope + namespaced-API mismatch and missing `shell`/`resource`. Until
  #1–#2 land, existing napplets won't run regardless of how solid the core is.
