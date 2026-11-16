# Napplet / nsite sandbox host — design

> **Status:** shipped — Keyless sandbox host shipped: `:nappletHost` module, `NappletBrokerService`, `NappletLaunchRegistry` all present (rendering half superseded by embedded-tabs).
> _Audited 2026-06-30._

**Date:** 2026-06-19
**Status:** Core (commons) implemented + tested; Android host (`:napplet` process, WebView, broker IPC, consent, DataStore) implemented and compiling — needs on-device verification
**Companion:** `quartz/plans/2026-06-19-napplet-nip5a-resolver.md` (the bottom half — manifest parsing + verified Blossom resolution — already landed in `quartz`).

> **Update (2026-06-24):** the *rendering* model below (full-screen-only
> `NappletHostActivity`, host living in `amethyst/androidMain/.../napplet/`) has
> moved on. The sandbox runtime now lives in its own **`:nappletHost`** module and
> renders three ways — full-screen, embedded warm bottom-bar tabs, and an arbitrary-URL
> browser — with a cross-process `SurfaceControlViewHost`, a soft-keyboard IME proxy,
> and per-site Tor routing. See **`amethyst/plans/2026-06-24-napplet-embedded-tabs.md`**
> for the current architecture. The **trust model** in this doc (keyless `:napplet`
> process, brokered consent-gated capabilities, verified-blob serving) is unchanged.

## Goal

Render NIP-5A static sites (nsites) and NIP-5D napplets *inside* Amethyst, with
the applet's HTML/CSS/JS running behind a **hard trust boundary**: it must never
be able to read the user's `nsec` or any other secret, read app storage,
`LocalCache`, or other accounts' data, or sign / encrypt / publish / zap without
explicit per-applet user consent. A napplet is untrusted third-party code served
from an untrusted Blossom CDN; we treat it accordingly.

## Threat model

**Adversary:** the applet bundle (HTML/CSS/JS), authored by an untrusted party,
delivered from an untrusted CDN.

It must NOT be able to:

1. Read the `nsec` / any `NostrSigner`-held secret, or decrypted key material.
2. Read app private storage, `LocalCache`, DataStore, other accounts, or another
   applet's sandboxed storage.
3. Sign, encrypt/decrypt, publish, subscribe, or zap without explicit consent.
4. Escalate to native code, other installed apps, or the file system.
5. Reach the network directly to exfiltrate or fingerprint (network is a *brokered
   capability*, default-deny).
6. Forge content past the signed manifest (already handled by `StaticSiteResolver`:
   manifest is authority, CDN is untrusted, every blob is sha256-verified).

**Trusted:** the main Amethyst process, the broker, the consent UI, quartz
verification. **Assumption:** the Android System WebView (Chromium) renderer
sandbox is sound — and we add an OS-process boundary on top so that even a full
WebView/renderer escape lands in a process that holds no secrets.

## Why a separate OS process is the load-bearing decision

Android processes have isolated address spaces. The decrypted `privKey`, the
`KeyPair`, and the `NostrSigner` instance live **only in the main process heap**.
The applet host runs in a separate process (`android:process=":napplet"`) that:

- never constructs a `NostrSigner`, never touches `SecureKeyStorage`/Keystore,
  never holds an `Account` or `LocalCache`;
- only holds an IPC handle to *request operations*, whose **results carry no key
  material** (a signed event, a ciphertext, a pubkey — never the private key).

So even arbitrary code execution inside the WebView renderer (already its own
sandboxed process) or inside the `:napplet` app process cannot read the main
process's memory where the secret lives. This is the guarantee the same-process
approach cannot make.

## Process & component model

```
┌─────────────────────────────────────────┐        ┌───────────────────────────────────┐
│  Main process  (com.vitorpamplona.amethyst)│        │  Applet process  (…:napplet)       │
│                                            │        │                                    │
│  Account · NostrSigner · SecureKeyStorage  │        │  NappletHostActivity               │
│  LocalCache · NostrClient · Blossom · NWC  │        │   └─ WebView                       │
│                                            │        │       ├─ shell page (trusted,      │
│  NappletBrokerService (bound, not exported)│◀──AIDL─▶│       │   app-asset origin)        │
│   └─ commons NappletBroker                 │ Binder │       │   exposes bridge → broker  │
│       └─ NappletPermissionLedger           │  (UID  │       └─ <iframe sandbox=          │
│                                            │ checked)│           "allow-scripts">        │
│  NappletConsentActivity (consent UI)       │        │             = applet, opaque origin│
└─────────────────────────────────────────┘        └───────────────────────────────────┘
         holds secrets                                       holds NO secrets
```

### Three transport hops (each a trust step-down)

1. **applet iframe ↔ shell page** — `postMessage` with strict origin checks. The
   shell injects a tiny `window.napplet.*` client shim into the applet that wraps
   postMessage calls into promises (request-id correlation). This is the NIP-5D
   capability surface the applet codes against.
2. **shell page ↔ `:napplet` native** — exactly one audited bridge,
   `WebView.addWebMessageListener` restricted to the **shell origin only** (the
   applet's opaque-origin iframe cannot reach it). The shell forwards validated
   requests.
3. **`:napplet` process ↔ main-process broker** — AIDL/`Messenger`. The broker
   checks `Binder.getCallingUid() == Process.myUid()` (reject anything not from
   our own app), consults the permission ledger, runs the operation with the real
   signer/client, returns only the result.

### WebView hardening (`:napplet`)

- Shell document served from app assets via `WebViewAssetLoader` at a fixed
  internal origin (`https://napplet.localhost/`). The applet lives in a child
  `<iframe sandbox="allow-scripts">` **without `allow-same-origin`** → unique
  opaque origin: no access to the shell DOM, cookies, `localStorage`,
  `IndexedDB`, or the bridge.
- Applet resources are served by `shouldInterceptRequest` from an **in-memory map
  of already-verified blob bytes** (resolved + sha256-checked by
  `StaticSiteResolver` *before* the WebView loads). Only manifest-declared paths
  resolve; everything else → 404. No `file://`, no `content://`.
- `WebSettings`: `allowFileAccess=false`, `allowContentAccess=false`,
  `allowFileAccessFromFileURLs=false`, `allowUniversalAccessFromFileURLs=false`,
  `setGeolocationEnabled(false)`, `mediaPlaybackRequiresUserGesture=true`,
  `safeBrowsingEnabled=true`, no insecure mixed content. `domStorageEnabled`
  only for a partitioned per-applet store (or off in v1).
- **CSP**: the shell injects `connect-src 'none'` for the applet by default — the
  applet has *no direct network*. Relay/Blossom/identity all go through the
  broker. Direct network is itself a capability (`net`) that widens `connect-src`
  to user-approved origins only.
- External navigation blocked in `shouldOverrideUrlLoading`; links open in the
  system browser only after consent.

## Capability / NAP-domain model

`NappletManifest.requires()` already yields the bare NAP domains
(`identity`, `relay`, `storage`, …; see `quartz/.../tags/RequiresTag.kt`). Map
each to a `NappletCapability` with the concrete broker operations it unlocks:

| NAP domain | Capability | Broker operations |
|---|---|---|
| `identity` | `IDENTITY` | `getPublicKey`, `signEvent`, `nip04/44 encrypt/decrypt` |
| `relay`    | `RELAY`    | `publish`, scoped `subscribe` (read) |
| `value` / `wallet` | `WALLET` | NIP-57 zap request / invoice; NWC pay (stricter, separate grant) |
| `storage`  | `STORAGE`  | per-applet sandboxed KV store, namespaced by applet identity — **never** app storage |
| `net`      | `NET`      | widen CSP `connect-src` to approved origins |
| *(unknown)* | — | **denied by default**, surfaced to the user |

**Applet identity for the ledger** = author pubkey + `d` identifier (the
addressable coordinate), *not* the blob hash, so grants survive updates. We still
record the manifest aggregate hash (`computeAggregateHash()`) so a user who picks
"ask again on code change" is re-prompted when the bundle changes.

## Permission ledger

`NappletPermissionLedger` — per-applet-identity grants, each
`NappletCapability → GrantState` (`ASK` / `ALLOW_ONCE` / `ALLOW_SESSION` /
`ALLOW_ALWAYS` / `DENY`), persisted via a `NappletPermissionStore` interface
(DataStore actual on Android). The broker consults it on every request:

- `DENY` → immediate `Denied` response.
- `ALLOW_*` → execute.
- `ASK` → suspend, launch `NappletConsentActivity` (main process), await the
  user's decision, optionally persist, then execute or deny.

Consent UI reuses the signer-prompt design
(`amethyst/plans/2026-05-25-appfunctions-signer-prompts.md`) and
`commons/.../ui/signing`.

### Capability-aware consent policy

The uniform "once / session / always / deny" is refined per capability:

- **Payments (`WALLET`)** — `requiresPerUseConsent = true`: every `payInvoice`
  re-prompts with the decoded sats amount; the dialog never offers "Always allow"
  and a stray always/session grant is downgraded to one-shot so nothing persists.
- **Identity (`IDENTITY`)** — gated by us **only when Amethyst holds the key**
  (`NostrSignerInternal`). For remote (NIP-46) / external (NIP-55) signers the
  broker defers to the signer's own per-request consent (no double-prompt), while
  still honoring a standing per-napplet `DENY` and the `requires` declaration. The
  sign prompt shows a kind + content preview.
- **Foreground-only execution** — the napplet WebView's JS/timers are paused when
  the host isn't resumed (`onPause`/`onResume`), so a backgrounded applet cannot
  fire a sign/decrypt/pay request whose prompt would surface over — and be
  confused with — Amethyst's own UI. This is the precondition that makes deferring
  identity to an external signer safe.

## Module placement

- **`quartz`** — protocol done. (Optional later: a canonical `NapDomain` constant
  set once the upstream NAP list stabilizes — an open question in the resolver
  plan.)
- **`commons/commonMain`** — new CLI-safe `napplet/` feature package:
  - `napplet/NappletCapability.kt` — NAP-domain ↔ capability mapping.
  - `napplet/protocol/` — `NappletRequest` / `NappletResponse` sealed families + JSON codec.
  - `napplet/permissions/` — `NappletPermissionLedger`, `GrantState`, `NappletPermissionStore`.
  - `napplet/NappletBroker.kt` — platform-agnostic broker: `(NostrSigner +
    relay/blossom/zap handles + ledger) → (NappletRequest → NappletResponse)`.
    The heart of the boundary; **fully unit-testable on the JVM**.
  - `napplet/ui/` — shared consent composables.
- **`amethyst/androidMain`** —
  - `NappletHostActivity` (`:napplet`) + WebView + `WebViewAssetLoader` + the
    shell HTML/JS shim asset.
  - `NappletBrokerService` (main process, bound, `exported=false`) wrapping the
    commons broker; `Binder` UID check.
  - `NappletConsentActivity` (main process) using the commons consent UI.
  - AIDL/`Messenger` plumbing; OkHttp `BlobFetcher` wiring (the resolver plan's
    named follow-up).
  - AndroidManifest: `:napplet` process declaration, the bound service, the
    consent activity.
- **`desktopApp`** — out of scope for v1 (the `:napplet` process model is
  Android-specific; desktop needs a separate child-process/WebView strategy).

## Inter-applet communication (v2)

Napplets' differentiator is talking to each other. Model: applets address each
other by napplet coordinate; `napplet.send(target, msg)` is **routed through the
broker** (shell→broker→shell) so two opaque-origin iframes never share an origin
or memory, and the user (or a manifest-declared allowlist) consents to the link.
Deferred to v2; v1 nails the single-applet boundary first.

## Testing & verification

- **commons (now, JVM):** broker decision logic per capability
  (granted/denied/ask), ledger state transitions + persistence semantics,
  protocol JSON round-trips, and security cases — unknown NAP domain denied,
  request for an undeclared path 404s without fetching, signer responses never
  contain key bytes.
- **Android (needs emulator):** instrumented tests for the WebView host, the
  opaque-origin iframe isolation, and the process boundary — flagged as on-device
  verification.

## Phasing (within the "full napplet" milestone)

1. **Core (commons, tested)** — protocol + capability model + ledger + broker. ✅ done.
2. **Android host** — `:napplet` process + WebView + verified-blob serving via
   `shouldInterceptRequest` + CSP. ✅ implemented (`NappletHostActivity`).
3. **Broker IPC + `identity` + `relay`** — Messenger broker
   (`NappletBrokerService`), `window.napplet.*` shim, consent UI
   (`NappletConsentActivity`), DataStore ledger. ✅ implemented.
4. **Capabilities beyond identity/publish:**
   - **`relay` read** — `QueryEvents`: bounded live relay fetch (`fetchAll`,
     EOSE/timeout) merged with `LocalCache`, newest-first. ✅
   - **`storage`** — per-applet sandboxed KV store (`DataStoreNappletStorage`). ✅
   - **`value`/`wallet`** — `PayInvoice` wired to the user's NWC wallet
     (`sendZapPaymentRequestFor`); consent shows the decoded sats amount; throws
     (→ `Failed`) on no-wallet/error/timeout. ✅ (needs on-device verification)
   - **`net`** — CSP widening to approved origins. ⏳
5. **Capability enforcement** — the broker refuses any request whose capability is
   not in the manifest's `requires` (passed host→broker as `declared`), before any
   consent prompt. ✅
6. **Inter-applet (NAP-INC / NAP-INTENT)** — deferred; design + prerequisites in
   `2026-06-20-napplet-inter-applet.md`. **`net`** capability and an install-style
   up-front capability grant UI also remain. ⏳

### Implemented Android components (amethyst `…/napplet/`)

| File | Process | Role |
|---|---|---|
| `NappletHostActivity` | `:napplet` | WebView host: hardened settings, opaque-origin iframe, verified-blob `shouldInterceptRequest`, CSP, `window.napplet` shim, Messenger client |
| `NappletBrokerService` | main | Bound Messenger service; runs the commons `NappletBroker` against the live account; `exported=false` + UID check |
| `NappletConsentActivity` / `NappletConsentCoordinator` | main | Capability-consent dialog + suspend bridge to the broker |
| `DataStoreNappletPermissionStore` | main | Persistent grant store (`NappletPermissionStore` actual) |
| `NappletProtocolJson` / `NappletIpc` | both | JSON codec + Messenger wire contract |
| `NappletLauncher` | caller | Packs a verified manifest into the host Intent |
| `assets/napplet/shell.html` | `:napplet` | Trusted shell page that sandboxes the applet iframe and relays messages |

### Remaining before user-facing ship

- **On-device verification (needs emulator/device):** opaque-origin iframe really
  excludes the bridge; CSP `connect-src 'none'` blocks fetch/XHR/WebSocket; a real
  napplet renders and round-trips a `getPublicKey` / `signEvent` through consent.
- ✅ **UI entry point:** a "Napplets" drawer item → `NappletsScreen` that lists
  cached napplet manifests (kinds 15129/35129) and launches the host.
- ✅ **Process isolation:** `Amethyst.onCreate` now skips `AppModules` entirely in
  the `:napplet` process, so the account/signer are never loaded there. (Previously
  `initiate()` loaded the account in every process — a real hole, now closed.)
- ✅ **Privacy:** the host routes blob fetches through the user's Tor SOCKS proxy
  when active; the port is passed in by the launcher (main process) so the sandbox
  process never touches the account-bound HTTP stack.
- ✅ **Relay discovery:** `NappletsFilterAssembler` (registered in
  `RelaySubscriptionsCoordinator`, invoked by `NappletsScreen`) REQs kinds
  15129/35129 from the user's read relays while the screen is open, so manifests
  flow into `LocalCache` for the list to render.
- **Consent UX:** reuse `commons/.../ui/signing` styling; show the manifest title
  and a per-capability rationale; batch-grant on first run.
