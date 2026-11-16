# Napplet SDK conformance audit — feature by feature

> **Status:** shipped — Conformance audit; the four breakers are marked fixed and pinned by `NappletSdkConformanceTest`.
> _Audited 2026-06-30._

**Date:** 2026-06-21
**Authoritative sources (verified, not from memory):**
`@napplet/nap@0.15.0` (`dist/<domain>/types.d.ts` — the canonical wire message types),
`@napplet/shim@0.16.0` (the SDK napplets bundle), `@napplet/core@0.15.0` (base envelope +
shell handshake). Audited against branch `claude/awesome-pasteur-xwiwad`.

Our edge layer: `NappletProtocolJson` (codec), `NappletRequest`/`NappletResponse` (commons),
`NappletHostActivity` (`SHIM_JS` + `shell.html` relay), `NappletBroker`, `NappletBrokerService`.

## Update — the four 🔴 breakers are now implemented

All four conformance breakers below are fixed (codec pinned by `NappletSdkConformanceTest`):

1. **Shell handshake** — the host answers `shell.ready` with `shell.init { capabilities:{domains,
   protocols}, services }` built from the declared domains (`NappletProtocolJson.encodeShellInit`),
   so a stock napplet's cached environment is populated and `supports()` works.
2. **Id-less messages** — `onShellMessage` no longer drops messages without an `id`: `shell.ready`
   is answered locally, and other fire-and-forget messages get a synthetic id so they reach the broker.
3. **keys** — `keys.registerAction`/`keys.unregisterAction` decode and the broker acknowledges them
   (declared-gated, no consent) so `registerAction()` resolves; the shim dispatches the `keys.action`
   push. (The actual global-key binding is still a follow-up — `keys.action` isn't emitted yet.)
4. **upload** — realigned to `upload.upload { request:{ data, mimeType, filename } }` → rich
   `UploadResult { ok, uploadId, status, url, sha256, size, mimeType }`; `shell.html` inlines the
   request `Blob` as base64 so it survives the bridge; the gateway uploads via the app's
   `BlossomUploader` to the user's kind:10063 server with a signed auth event.

Also now implemented (the ◐ follow-ups):
- **Live subscription tail** — `relay.subscribe` opens a real `client.subscribe` whose listener
  streams `relay.event` (stored + live), `relay.eose`, and `relay.closed` pushes by `subId`;
  `relay.close` unsubscribes (tracked in `liveSubs`, torn down in `onDestroy`).
- **Multi-`filters`** — `relay.query`/`subscribe` honor every filter in the `filters[]` array,
  not just the first (`decodeFilterList`, gateway `query(List<Filter>)`).
- **`resource.cancel`** — accepted at the host edge as a no-op `Done`.

Still open: identity `getList`/`getZaps`/`getBadges` + `onChanged` (object shapes / list-type
semantics underspecified), the `keys.action` push (needs a host command-palette UI to *trigger*
actions — registration already conforms), the `resource` `nostr:` scheme (unspecified bytes),
`inc`/`intent`/the niche domains, and **on-device verification** of the host/shell behavior.

## Base envelope & error convention (verified)

- `NappletMessage` carries only **`type`** (`"domain.action"`). **There is no universal `id`** —
  request/response pairs add `id`; fire-and-forget and handshake/push messages have **no `id`**.
- **No universal `ok`.** Each domain picks its own: `relay.publish`/`publishEncrypted`,
  `outbox.publish`, `upload`, `intent` use `ok: boolean`; identity/storage/query results omit `ok`
  and signal success by the data field's presence + an optional `error?: string`. The SDK shim
  rejects when `error` is present and otherwise reads the domain's data field.
  - **Ours:** we set `ok` on *every* result. Harmless (the SDK reads the data field and ignores the
    extra `ok`), and our own injected shim relies on `ok`. ✅ compatible, ⚠️ non-canonical.

## Transport (verified) — two architectural gaps

1. **Structured-clone objects, not strings.** `@napplet/core` posts cloneable **objects**. Our
   `shell.html` now bridges object↔string both ways (done earlier). ✅
2. **🔴 The host drops id-less messages.** `NappletHostActivity.onShellMessage` does
   `id = optString("id").ifEmpty { return }` — so every message **without an `id`** is dropped:
   `shell.ready`, `inc.emit`, `keys.unregisterAction`. This silently breaks the shell handshake and
   all fire-and-forget messages. **Must fix** to forward/handle id-less messages.
3. **🔴 Blob-carrying requests can't cross.** `upload.upload`'s request payload contains a `Blob`
   (`data: Blob | ArrayBuffer`). Our applet→native bridge does `JSON.stringify`, which turns a Blob
   into `{}`. Real-napplet uploads lose their bytes. Needs a Blob-aware request path.

## Shell handshake (verified) — 🔴 not implemented

The SDK does **not** send a `shell.supports` message. Instead:
- napplet posts **`shell.ready`** (no payload), and
- the shell replies **once** with **`shell.init`** = `{ capabilities: { domains: string[],
  protocols: Record<string,string[]> }, services: string[] }`.
- `shell.supports(capability, protocol?)` is then answered **synchronously and locally** from that
  cached environment.

**Ours:** we implement a `shell.supports` *request* (`ShellSupports` → `Supported`) and our injected
shim calls it async. A real napplet never sends `shell.supports`; it sends `shell.ready` — which our
host **drops** (no id) — so its cached environment stays empty and `supports()` returns `false` for
everything, likely making well-behaved napplets bail early. **Highest-impact gap.**
Fix: host answers `shell.ready` with a `shell.init` carrying the declared domains.

## Per-domain conformance matrix

Legend: ✅ conformant · ◐ partial · 🔴 mismatch/missing · ➖ not modeled.

| Domain | SDK surface (wire) | Ours | Verdict |
|---|---|---|---|
| **shell** | `shell.ready`→`shell.init{capabilities,services}`; `supports()` local | `shell.supports` request→`{supported}` | 🔴 wrong model (no handshake) |
| **identity** | `getPublicKey`→`{pubkey}`; `getProfile`→`{profile}`; `getRelays`→`{relays}`; `getFollows`/`getMutes`/`getBlocked`→`{pubkeys}`; `getList`→`{entries}`; `getZaps`→`{zaps}`; `getBadges`→`{badges}`; `onChanged` push | getPublicKey ✅; profile/relays/follows/mutes/blocked ✅ (exact fields); getList/getZaps/getBadges → Unsupported; onChanged no-op | ◐ reads ✅, push/3 methods missing |
| **relay** | `publish{event}`→`{ok,event,eventId}`; `publishEncrypted{event,recipient,encryption}`; `query{filters[]}`→`{events}`; `subscribe{subId,filters,relay?}`; `close{subId}`; pushes `relay.event{subId,event,resources?}`, `relay.eose{subId}`, `relay.closed{subId,reason?}` | publish/publishEncrypted ✅ (read `event`); query ◐ (first filter only); subscribe ✅ + event/eose push ✅ (snapshot); close ✅ (no-op); `relay.closed` not emitted | ◐ strong; multi-filter + live tail + `relay.closed` open |
| **storage** | `get`→`{value}`; `set`; `remove`; `keys`→`{keys}` (512 KB quota) | ✅ exact (`get/set/remove/keys`, `value`/`keys` fields) | ✅ (quota not enforced) |
| **resource** | `bytes{url}`→`{blob,mime}`; `cancel`; https/blossom/nostr/data | bytes ✅ (shell builds Blob); https/data/blossom ✅; nostr ➖; cancel ➖ | ◐ nostr + cancel missing |
| **keys** | `registerAction{action}`→`{actionId,binding?}`; `unregisterAction{actionId}`; pushes `keys.action{actionId}`, `keys.bindings{bindings[]}` | client-side no-op stubs only; **host rejects** `keys.*` (decodes to null) | 🔴 real napplets' `registerAction` rejects |
| **upload** | `upload.upload{request:{data:Blob,mimeType?,...}}`→`{ok,uploadId,status,url?,sha256?,...}`; `upload.status` | type `upload` + `{bytes(base64),contentType}`→`{url}`; gateway null→Unsupported | 🔴 wrong type + shape + Blob transport |
| **inc** | `inc.emit`; `inc.subscribe`/`.result`; `inc.unsubscribe`; `inc.event` push; channel mode (`inc.channel.*`) | ➖ (deferred; maps to null→denied) | ➖ not modeled |
| **intent** | invoke a napplet by archetype | ➖ | ➖ |
| **theme/notify/media/config/outbox/ifc/cvm** | further domains | ➖ | ➖ |

## Inconsistencies, ranked

1. **🔴 Shell handshake missing** (`shell.ready`→`shell.init`). Real napplets get an empty
   capability environment → `supports()` false → likely bail. *Fix: host emits `shell.init`.*
2. **🔴 Id-less messages dropped** by the host. Breaks the handshake + every fire-and-forget
   (`inc.emit`, `keys.unregisterAction`). *Fix: forward/handle id-less messages.*
3. **🔴 `keys.*` rejects** for real napplets (we only stub client-side). *Fix: decode
   `keys.registerAction`/`unregisterAction`, answer a stub `{actionId}`; later wire `keys.action`.*
4. **🔴 `upload` non-conformant** (`upload` vs `upload.upload`, flat base64 vs `request:{data:Blob}}`,
   `{url}` vs rich `UploadResult`) AND the Blob can't cross our string bridge. *Fix: realign the
   wire + a Blob-aware request path when the gateway lands.*
5. **◐ `relay.query`/`subscribe` use only the first of `filters[]`.** Multi-filter napplets get
   partial results. *Fix: honor all filters.*
6. **◐ Identity `getList`/`getZaps`/`getBadges` + `onChanged`** unimplemented.
7. **◐ `resource` `nostr:` scheme + `resource.cancel`** unimplemented.
8. **◐ `relay.closed` push** not emitted; **live subscription tail** absent (snapshot only).
9. **⚠️ Non-canonical `ok` on every result** (harmless, but not how the SDK signals success for
   identity/storage/query).

## What the conformance tests assert

`NappletSdkConformanceTest` (amethyst, JVM) pins the codec to the **exact SDK wire** for the
methods we support, so a regression that drifts from `@napplet/nap` fails CI:

- **Requests:** the SDK's exact envelope (`relay.publish{event}`, `storage.get`, `identity.*`,
  `resource.bytes`, multi-`filters`) decodes to the right `NappletRequest`.
- **Results:** `encodeResponse` emits the SDK's exact field names (`pubkey`, `profile`, `relays`,
  `pubkeys`, `entries`, `value`, `keys`, `events`, `event`/`eventId`, `bytes`/`mime`).
- **Pushes:** `relay.event{subId,event}` / `relay.eose{subId}` match the SDK push shapes.
- **Gap guards:** tests that *document current behavior* for the known gaps (`shell.ready`,
  `keys.registerAction`, `upload.upload`, `inc.emit` currently decode to `null`), each annotated
  with the audit item so the day we fix them the guard flips intentionally.
