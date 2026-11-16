# Napplet / nsite security review (2026-06-22)

> **Status:** shipped — Security review recording completed hardening; `NappletLaunchRegistry` token model and per-applet origins are in tree.
> _Audited 2026-06-30._

A review of the attack surface for NIP-5A static sites (nsites) and NIP-5D napplets,
the protections in place, and the residual risks — with what was fixed in this pass
and what remains as future work.

## Trust model (what holds)

- **Keys never enter the sandbox.** The `:napplet` process holds no account/signer.
  Signing happens only in the main process (`signer` fixes `pubkey`, host clock
  prevents backdating). *"Even a full WebView/renderer escape into this process yields
  no secret."*
- **Content integrity.** Every blob is sha256-verified against the **signed** manifest
  before serving (`StaticSiteResolver`); cache is content-addressed + re-verified, so a
  poisoned/stale cache can't be served. nsites launch with **zero** capabilities.
- **Network containment.** App CSP `connect-src 'none'`; egress only via the brokered,
  consent-gated `resource.bytes`, Tor-routed. Bridge is origin-restricted + main-frame.
- **IPC binding** is `exported=false` + same-UID (`onBind` UID check). Payments always
  prompt per-use with the amount.

## Fixed in this pass

1. **Cross-napplet identity/storage spoofing (was: per-message identity).** The broker
   used to trust `author`/`identifier`/`declared` sent on **every** IPC message from the
   `:napplet` process. A WebView→native escape could forge another napplet's coordinate
   and read/act as it. Now the **main process** mints a random launch token
   (`NappletLaunchRegistry`), hands only that token to the sandbox, and the broker
   resolves it back to the trusted identity + declared set. A compromised sandbox can act
   as nothing but the napplet it was launched as (it holds only its own token).

2. **Private mute/block leak via `identity.getMutes`/`getBlocked`.** These read
   `muteList.flow` / `blockPeopleList.flow`, which contain **decrypted private** entries.
   Now they read the events' **public** tags only (`MuteListEvent.publicMutes()`,
   `FollowSetEvent.publicUsersIdSet()`).

3. **Silent "allow-always" actions + UI-redress.** Added persistent **trusted chrome**
   (a sandbox bar the applet can't draw over: shield + name + tap-to-see "what it can
   access") and a **live toast** when a granted RELAY/UPLOAD/VALUE op runs — so an
   allow-always grant can't act completely silently. Also fixes the host drawing under
   the status/navigation bars (edge-to-edge insets).

4. **Real per-applet storage origin (was: opaque sandbox → broken apps).** The applet
   ran in an `allow-scripts`-only iframe served under `napplet.local/app/`, i.e. an
   **opaque ("null") origin**. That has no `localStorage`/`IndexedDB`/service worker
   (reads throw `SecurityError`), and module scripts / asset fetches are CORS-blocked, so
   essentially every bundled SPA rendered **blank** or **crash-looped** ("cache version
   0 → reset → reload" forever, because IndexedDB never persisted the version). Each
   applet now loads on its **own** internal origin `https://<id>.napplet.local` —
   `id = sha256(author + ":" + identifier)`, truncated + letter-prefixed to a DNS label —
   with `allow-scripts allow-same-origin`, served at the **origin root** (bundlers emit
   absolute `/assets/...` URLs). A real origin restores DOM storage / IndexedDB / SW and
   makes the applet's own assets same-origin (no CORS shim needed). **Isolation is
   preserved precisely because the applet origin is distinct from the shell's:** the
   bridge stays origin-restricted to the shell (`napplet.local`), so the cross-origin
   applet still cannot reach it or read the shell DOM — it talks only via `postMessage`,
   which the shell relays. Per-applet subdomains keep applets' storage isolated from one
   another; CSP `connect-src 'none'` is unchanged; the app CSP was **tightened** to
   `'self'` (it no longer grants the shell origin).

## Residual risks / future work

- **`allow-same-origin` is safe only while the applet origin ≠ the shell/bridge origin.**
  A frame carrying *both* `allow-scripts` and `allow-same-origin` can strip its own
  sandbox **iff it is same-origin with its embedder**; here it never is (applet on a
  per-applet subdomain, shell on `napplet.local`), so it cannot. **Load-bearing
  invariant — do not break:** never serve the applet on the shell origin, and never add
  an applet origin (`*.napplet.local`) to the bridge's `addWebMessageListener` allowlist
  (kept to `setOf(ORIGIN)`). Collapsing the two origins would hand the applet the bridge.
- **Persistent client storage is now a persistence/exfil surface.** The applet keeps
  `localStorage`/`IndexedDB` across launches (origin-scoped, per applet, key-free and
  isolated from other applets). A consented applet can build durable local state and,
  combined with allow-always RESOURCE, a durable profile. Tie to the session-scoped-grant
  proposal above; consider a "clear this applet's data" affordance.
- **Per-applet origin id.** `sha256(author:identifier)` is stable per applet and
  collision-resistant; root/replaceable applets (kinds 15128/15129, no `d` tag) collapse
  to one origin per author — fine, since there's one root per author. Changing the id
  scheme later resets an applet's storage (new origin); acceptable.
- **Service workers are reachable but unwired.** With a real origin the applet *can* now
  register a service worker; the host does not yet route SW fetches
  (`ServiceWorkerControllerCompat`), so registration currently fails gracefully (a
  warning, not a crash). If SW support is wired later, SW-originated fetches MUST go
  through the same verified content server, never the network.

- **Coarse, persistent grants.** Non-payment capabilities persist as ALLOW_ALWAYS. A
  consented napplet can still, thereafter, publish as you (RELAY), read your social graph
  (IDENTITY), and make arbitrary network calls (RESOURCE) without re-prompting. The new
  chrome + toasts make this *visible*, but the model is still allow-forever. **Proposed:**
  a session-scoped grant ("Allow while open", cleared on close) in `GrantState` +
  consent dialog, defaulted for RESOURCE/RELAY; and per-origin consent for cross-origin
  `resource.bytes` https fetches.
- **`resource.bytes` as exfil channel.** Once RESOURCE is allow-always, the applet can
  encode data into arbitrary https URLs through the Tor proxy. Tor hides the IP, not the
  payload. Tie to the per-origin/session proposal above.
- **`'unsafe-inline'` in app `script-src`.** Required to inject the shim; the applet is
  the author's own (content sha256-pinned), so it's not an escalation. `connect-src
  'none'` remains the real boundary. Residual, accepted.
- **Trust pivots on the author key.** A compromised author key lets an attacker push a
  new *signed* manifest and own the app — expected Nostr trust model. Aggregate `x` hash
  is enforced when present (only *recommended* by the spec); per-path hashes always
  protect.
- **Sandbox isolation is now structural.** The `:napplet` runtime
  (`NappletHostActivity`, content server, IPC, key actions) lives in its own
  `:nappletHost` module that depends only on `:commons` + `:quartz` — so it is
  *compile-time incapable* of importing `Amethyst`/`LocalCache`/`Account`. The
  broker-side (signer, gateways, `NappletLaunchRegistry`) stays in `:amethyst`;
  the activity binds the broker service by class-name string and the two halves
  communicate only over Messenger IPC.
- **Launch-token lifecycle.** Tokens are capped (LRU, 128) rather than explicitly
  unregistered on sandbox close (the sandbox is a separate process and can't reach the
  main-process registry). A long-backgrounded napplet whose token was evicted would need
  relaunch. Acceptable; revisit if it bites.
