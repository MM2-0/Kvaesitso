# Embedded napplet / nsite / browser tabs — final architecture

> **Status:** shipped — `EmbeddedTabLayer` and the `:nappletHost` module exist; embedded warm-tab + browser render paths are implemented (PR #3348).
> _Audited 2026-06-30._

**Date:** 2026-06-24
**Status:** Implemented on `claude/webview-menu-custom-url-ikrgbz` (PR #3348); needs on-device verification.
**Supersedes the rendering half of** `amethyst/plans/2026-06-19-napplet-sandbox-host.md`
(full-screen-only `NappletHostActivity` in `amethyst/androidMain`). The trust
model is unchanged and still governed by
`amethyst/plans/2026-06-22-napplet-nsite-security.md`.

## What changed since the sandbox-host plan

The 2026-06-19 design rendered a napplet/nsite **only** full-screen, in its own
`:napplet` Activity/task. This branch adds:

1. A second, **embedded** render path: warm bottom-bar "favorite" tabs that live
   inside the main Amethyst window, swapped in place with no relaunch — built on
   a cross-process `SurfaceControlViewHost` surface.
2. A **browser** host: the same keyless `:napplet` sandbox now also renders an
   arbitrary user-typed URL (not just a verified-blob nsite/napplet), as both a
   full-screen activity and an embedded tab.
3. A working **soft keyboard** inside the embedded surface (the cross-process
   surface window cannot itself be an IME target).
4. The sandbox runtime extracted into its **own `:nappletHost` module** (depends
   only on `:commons` + `:quartz`; cannot import `Amethyst`/`LocalCache`/`Account`).
5. **Per-site Tor / open-web** routing memory, and a startup **preloader** that
   warms every bottom-bar favorite so the first tap is instant.

The keyless-sandbox guarantee is preserved throughout: keys live only in the main
process, every NIP-07 / `window.napplet` call is brokered + consent-gated, and the
embedded surface is z-ordered **below** the client window.

## Two render paths, one sandbox

| | Full-screen | Embedded tab |
|---|---|---|
| Container | `NappletHostActivity` / `NappletBrowserActivity` (`:napplet` task) | `SandboxedSdkView` in `EmbeddedTabLayer` (main window) |
| Surface | the Activity's own window | `SurfaceControlViewHost` shipped to the host `SandboxedSdkView` |
| Chrome (pull-down) | `NappletControlSheet` (native Android `View`s) | `TopControlSheet` (Compose) |
| Soft keyboard | the Activity's own window (native) | `RemoteImeView` proxy in the **main** window |
| Lifecycle | one session per task | many warm sessions, parked off-screen |

Both paths run the **same** `NappletHostService` / `NappletBrowserService` and the
same `shell.html` + `shim.js`; only the host/embedding differs.

## Persistent warm-surface layer (embedded)

`EmbeddedTabLayer` is mounted once in the app shell, below the drawer/dialogs. It
renders **every** warm session's `SandboxedSdkView` and keeps it attached:

- `EmbeddedTabHost` (object) owns the set of warm `sessions`, the active id, and
  the reserved `contentBounds`. `setActive(id)` returns a token; `clearActiveIfOwner(token)`
  only clears if the caller still owns it (so a fast tab A→B→A swap can't have B's
  teardown clear A's just-set active id). `retainOnly(ids)` warm-keeps the
  bottom-bar favorites (+ the momentarily-active tab) and evicts the rest.
- The active session is offset over the current tab's bounds; **inactive warm tabs
  keep the same size and are merely shifted ~10 000 dp off-screen** (never resized
  to 1 dp). Parking by translation rather than resize is what avoids the ~1 s black
  flash on every tab switch (a resize forces a surface re-render).
- The surface is z-below, so Compose draws over it — that's how `TopControlSheet`
  sits on top. Each surface is wrapped in `EmbeddedSurfaceTouchHolder` so a scroll
  gesture isn't stolen by a host-side ancestor (the cross-process WebView can't
  `requestDisallowInterceptTouchEvent` for itself).

`EmbeddedTabFactory` builds/acquires the per-app controller (`EmbeddedBrowserController`
/ `EmbeddedNappletController`, both `EmbeddedSurfaceController` + `EmbeddedImeBridge`)
and gates preloading on Tor (won't warm a Tor-routed site over clearnet while Tor is
still connecting).

## Per-session services (the multi-tab refactor)

`NappletHostService` / `NappletBrowserService` are **single shared instances** (same
bind `Intent`); per-tab state lives in a `NappletTab` / `BrowserTab` map keyed by the
client-stamped `KEY_SESSION_ID` (`NappletEmbedContract` / `NappletBrowserContract`).
Each tab carries its **own** reply `Messenger`, so broker responses + pushes route to
the right tab (the broker echoes `replyTo`). Critical correctness rules baked in:

- `contentServer` is `@Volatile` (read on the WebView worker thread in
  `shouldInterceptRequest`, written on main).
- broker-reply delivery drops a reply whose tab was replaced (`tabs[id] !== tab`)
  and wraps `postMessage` in `runCatching` (a torn-down WebView can't crash the relay).
- session close / `onDestroy` tears down **that tab's** content server + WebView only
  (an earlier bug destroyed sibling tabs' WebViews → "open A, switch to B, back to A =
  black").

## Embedded soft keyboard (IME proxy)

The embedded surface is a `SurfaceControlViewHost` window: in z-below mode it forwards
touch but **cannot be an IME target**, so a focused field would get no keyboard. The fix
mirrors Flutter's `TextInputPlugin`:

- `RemoteImeView` — an invisible, focusable `EditText` in the **main** app window takes
  the keyboard. A real local `Editable` is the source of truth (the platform handles
  composing regions, suggestions, autofill); edits are **coalesced across batch edits**
  and the whole **editing state** (text + selection + composing) is shipped — not
  individual ops. It flushes **synchronously** at the outermost `endBatchEdit` so a
  compose-then-commit in one frame preserves the composing region.
- `shim.js` IME agent (gated on `window.__nappletImeProxy`) applies that state to the
  focused field and synthesizes the matching DOM `input`/composition events so web
  frameworks react as if typed natively. It is surrogate-pair-safe (emoji / CJK-supplement
  never split into a lone surrogate) and supports `contenteditable` via Range-mapped char
  offsets (in-place replacement, not a `textContent` overwrite), with selection-echo dedup.
- `EmbeddedTabLayer` shrinks the active surface to clear the keyboard using the **snapped**
  `WindowInsets.imeAnimationTarget` (not the per-frame animated `ime`), so the expensive
  cross-process surface resize happens once, not every animation frame.

State + ops cross over `MSG_IME_EVENT` / `MSG_IME_OP` (`EmbeddedImeBridge`). Full-screen
hosts set neither IME flag (they have a native keyboard).

## Per-site network routing

`WebUrlNetworkRegistry` (keyed by site host) and `NappletNetworkRegistry` (keyed by
`author:identifier`) remember whether a site routes through **Tor** (default) or the
**open web** — some servers reject Tor exits, so a user can opt one out and it must
survive relaunch. Both hydrate from DataStore asynchronously and now expose
`awaitReady()`; the preloader awaits hydration **before** its first routing decision so a
cold start can't route an open-web-pinned site through Tor. Live in the **main** process
only; the keyless sandbox never reads them (the launcher stamps the choice into the
launch intent).

## The two control-sheet twins

`TopControlSheet` (Compose, `:amethyst`, drawn in the main process over the z-below
surface) and `NappletControlSheet` (hand-built Android `View`s, `:nappletHost`, in the
keyless sandbox process) are deliberate twins: the sandbox module is Compose-free and
can't depend on `:amethyst`, so the composable can't be shared. They are kept visually
identical by hand — same 10 dp row rhythm, same muted-icon + framework `Switch` Tor row.
Both are a top-center pull-down (collapsed = a small grabber out of the corner where a
site puts its own avatar/menu): route over Tor, reload, "what it can access" (sandboxed
apps), open full screen.

## Bottom bar

Built-ins and favorites are one ordered list (`BottomBarEntry`, polymorphic with
`@SerialName("builtIn")` / `@SerialName("favorite")`). `UISharedPreferences.decodeBottomBarItems`
migrates the old persisted discriminator (the kotlinx default fully-qualified class name)
to the short names, falling back to `DefaultBottomBarEntries` — fixing the one-time bottom
bar reset the `@SerialName` change would otherwise have caused.

The **Browser** launcher (`BrowserScreen`) is an omnibox + the shared `FavoriteAppsGrid`;
each opened URL lands in its own full-screen `NappletBrowserActivity`. When the Browser is
reached from the drawer (pushed) rather than as a bottom-bar tab, its omnibox shows a back
arrow — the standard `nav.canPop()` rule (mirrors `NappletsTopBar`).

## File map (final)

**`:nappletHost`** (`com.vitorpamplona.amethyst.napplethost`, `:napplet` process):
`NappletHostService` / `NappletBrowserService` (per-session WebView hosts),
`NappletHostActivity` / `NappletBrowserActivity` (full-screen),
`NappletHostUiAdapter` / `NappletBrowserUiAdapter` (`SandboxedUiAdapter` for the embedded
surface), `NappletControlSheet` (native pull-down), `NappletContentServer` +
`NappletBlobHttp` + `NappletBlobCache` + `NappletBlobPrefetcher` (verified-blob serving,
Tor-routed, content-addressed), `NappletEmbedContract` / `NappletBrowserContract` /
`NappletHostContract` (Messenger wire keys), `NappletIpc`, `NappletKeyActions`,
`NappletWebViewInsets`.

**`:amethyst` embed** (`ui/screen/loggedIn/embed`): `EmbeddedTabLayer`, `EmbeddedTabHost`,
`EmbeddedTabFactory`, `EmbeddedTabChrome`, `EmbeddedSurfaceController`,
`EmbeddedSurfaceTouchHolder`, `EmbeddedImeBridge`, `RemoteImeView`, `TopControlSheet`,
`EmbeddedTabPreloader`, `TorToggleButton`.

**`:amethyst` main-process napplet** (`napplet/`): `NappletBrokerService`,
`NappletLauncher`, `NappletLaunchRegistry`, `WebUrlNetworkRegistry`,
`NappletNetworkRegistry`, `SandboxForegroundHold`, consent (`NappletConsent*`), gateways,
DataStore stores.

**`:amethyst` launchers/screens**: `BrowserScreen`, `FavoriteWebAppScreen`,
`FavoriteNappletScreen`, `FavoriteAppsScreen` + `FavoriteAppsGrid`.

**`:commons`**: `shell.html`, `shim.js` (`composeResources/files/napplet/`),
`FavoriteApp`/`FavoriteAppIcon`, `BottomBarEntry`.

## Residual / on-device verification

- All embedded-surface behavior (IME, scroll-gesture claiming, warm-keep across swaps, the
  Tor shrink) needs emulator/device verification — `SurfaceControlViewHost` + the IME proxy
  can't be unit-tested.
- The IME proxy runs a host-window `EditText`; it ships only editing **state** to the page,
  never to any key material — the keyless-sandbox boundary is unaffected.
- Launch-token lifecycle, coarse persistent grants, and the `resource.bytes` exfil channel
  remain as tracked in the 2026-06-22 security review.
