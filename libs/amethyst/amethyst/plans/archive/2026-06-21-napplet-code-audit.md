# Napplet subsystem code audit — bugs, performance, refactor, placement

> **Status:** shipped — Audit recording completed fixes (LiveSub teardown, broker caching, shim extraction); referenced files exist.
> _Audited 2026-06-30._

**Date:** 2026-06-21. Scope: the napplet/nsite subsystem (`amethyst/.../napplet/`,
`commons/.../napplet/`, `quartz/.../nip5aStaticWebsites` + `nip5dNapplets`).

## Fixed in this pass

| # | Category | Issue | Fix |
|---|---|---|---|
| 1 | 🐛 correctness | **Live subscription unsubscribed from the wrong client after an account switch** — `closeLiveSubscription`/`onDestroy` used the *current* account's client, leaking the sub on the original. | `LiveSub` holder stores the exact `INostrClient` that opened the sub; teardown uses it. |
| 2 | 🐛 correctness | **Multi-relay subscriptions emitted N `relay.eose`** (one per relay) — the SDK expects one. | An `eoseSent` latch (`compareAndSet`) emits a single `relay.eose`. |
| 3 | ⚡ perf | **Broker rebuilt on every request** (new gateways/prompt each call). | `broker()` caches per account (reference identity), rebuilt only on switch. |
| 4 | ⚡ perf | **A fresh `OkHttpClient` per blob fetch** (no connection pooling). | `blobHttpClient()` caches the client keyed by Tor port (`@Synchronized`). |
| 5 | ♻️ refactor | **105-line `SHIM_JS` string constant** in `NappletHostActivity.kt` (no highlighting, hard to edit). | Moved to `assets/napplet/shim.js`, loaded once like `shell.html`. |
| 6 | 📝 docs | Stale shim comments (`onChanged` "follow-up", `subscribe` "snapshot…follow-up"). | Rewritten to match reality (no-op onChanged; live tail). |

All compile; `commons:jvmTest` + the amethyst napplet suite stay green.

## Deferred — with rationale (not silently dropped)

- **Duplicate events across relays** — the same event id can arrive from multiple relays, so
  `relay.event` is pushed more than once. Deduping needs a per-subscription seen-id set (unbounded
  memory for long subs); napplets already dedupe by id. Left as-is; documented.
- **Background teardown of live subscriptions** — a backgrounded-but-alive napplet keeps its relay
  subscription open (the WebView is paused, but the service keeps streaming). It is *not* a
  permanent leak: the service is bind-only, so closing the napplet → `unbindService` → service
  `onDestroy` → all subs torn down. A proper pause/resume (unsubscribe on background, re-`REQ` on
  foreground) is a real optimization but needs an IPC pause/resume signal + device verification.
- **Request ordering** — requests are handled concurrently (`scope.launch` per message), so
  `storage.set` then `storage.get` aren't guaranteed in-order. Matches the SDK's async model;
  serializing would hurt throughput. Documented, not changed.
- **`runBlocking` in `shouldInterceptRequest`** — this runs on a WebView *background* worker thread
  (not the UI thread), so blocking there during a blob fetch is acceptable; WebView fans out
  resource loads across workers. Left as-is.
- **Over-flags from the sweep that aren't real:** `pendingRequests` / `bridgeReplyProxy` "races" —
  both the `WebMessageListener` callback and the reply `Handler` run on the **main looper**, so
  there is no cross-thread access. A null `bridgeReplyProxy` only drops a reply to an
  already-gone WebView (the applet is gone too) — harmless.

## Recommended moves to commons / quartz

- **quartz (protocol-only): correct as-is.** NIP-5A/5D events, `NappletManifest`,
  `StaticSiteResolver` + `StaticSitePathLookup` (`sniffContentType`), `SiteAggregateHash` are all in
  `commonMain`. No app policy leaked in.
- **commons (shared logic): mostly correct.** Broker, capability, identity, request/response,
  permissions ledger/store, and gateway interfaces are in `commonMain` — right home.
- **DONE: `NappletProtocolJson` → `commons/jvmAndroid`.** Moved to
  `commons/.../napplet/protocol/` (next to the types it marshals) so the future **desktop** host
  reuses the exact wire codec. Its tests stay in `amethyst` (JUnit4) for now and still exercise it
  via the commons dependency; converting them to `kotlin.test` and moving to commons `jvmTest` is a
  small follow-up. See `desktopApp/plans/2026-06-21-napplet-desktop-host.md`.
- **amethyst (Android-only): correctly platform-bound.** `NappletHostActivity` (WebView/process),
  `NappletBrokerService` (Service/Messenger/account), the gateway *implementations* (account,
  `BlossomUploader`, DataStore, NWC), `NappletLauncher`, consent UI, `NappletIpc`, and the screens
  all belong here.

## Lower-priority refactors (not done)

- Extract a shared Tor-aware OkHttp builder (host `buildHttpClient` vs service `blobHttpClient`
  duplicate the proxy logic) into a small util.
- `summaryFor`'s long `when` could become a per-request-type method, and `encodeResponse`'s big
  `when` is repetitive — both are readability, not correctness.
