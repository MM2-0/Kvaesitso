# Browser surfaces → PWA parity review

Status: **implemented** (phases 0–5, see §7), decisions recorded in §6. Not yet verified on a device. Scope: every surface that renders a
plain web client — the **embedded** bottom-bar tab and the **external** full-screen browser —
plus their shared top pull-down "pill" and bottom console "pill". The nsite/napplet hosts
reuse the same chrome and are covered where the change is shared.

Benchmark: an installed PWA in Chrome for Android (WebAPK, `display: standalone` /
`minimal-ui`), including the Custom-Tab-style bar Chrome shows when a PWA navigates out of
its scope.

## 1. Surfaces today

| Surface | Code | Process / rendering | Chrome |
|---|---|---|---|
| **Embedded web tab** (bottom-bar favorite) | `WebAppScreen` → `EmbeddedWebAppController` → `NappletBrowserService` | `:napplet` WebView streamed via SurfaceControlViewHost (API 30+) | Compose `TopControlSheet` + `BottomConsoleSheet`, drawn by `EmbeddedTabLayer` |
| **External browser** (Browser tab launcher, "Open full") | `BrowserScreen` → `FavoriteAppLauncher.launchUrl` → `NappletBrowserActivity` | `:napplet` WebView hosted directly; own task (`documentLaunchMode=intoExisting`) | native-View `NappletControlSheet` + `NappletConsolePanel` |
| Embedded / full nsite + napplet | `NostrAppScreen` / `NappletHostService` / `NappletHostActivity` | same split, verified-blob shell | same two chrome implementations, `isSandbox = true` |

So there are **two implementations of the same chrome** (Compose + plain Views), and they have
drifted.

### Top pill — current contents

| Row | Embedded (Compose) | External (Views) |
|---|---|---|
| Header | icon + host title | emoji 🌐/🛡 + **launch-time** title (never updates) |
| Address | — | **editable** URL field + 🔒/🧅/🌐 glyph |
| Tor | switch, `Lock` icon, `favorite_app_network_*` strings | switch, `ic_tor` icon, `napplet_net_*` strings |
| Reload | ✓ | ✓ (glyph `↻`) |
| Access info | sandbox only | sandbox only (glyph `ⓘ`) |
| Manage permissions | ✓ (navigates in-app) | ✓ (glyph `⚙`, IPC → broker) |
| Open full screen | ✓ | — (no way back to the tab) |
| Favorite | ✓, reflects registry live | ✓, but **resets to "Add" on every navigation** |
| Console | switch | switch (glyph `>`) |

### Bottom pill

Console only: hidden until the top-pill Console switch turns it on, then a grabber + log panel
(max 40 % height in Compose, a fixed 220 dp in Views). Neither surface has a bottom
navigation/actions pill.

## 2. What a Chrome PWA gives the user

**Window / OS integration**
- Its own launcher icon (Add to Home screen / Install), its own task in Recents with the
  **app's name, icon and `theme_color`**, and a splash screen built from the manifest.
- Status bar (and nav bar) tinted with `theme_color` / `<meta name="theme-color">`, updated
  live when the page changes it.
- `display_override` / `display` modes: `standalone` (no UI), `minimal-ui` (back + reload),
  `fullscreen`.
- Launch handling: `start_url`, `scope`, `launch_handler`; deep links into the scope open the
  PWA; app shortcuts (manifest `shortcuts`, shown on launcher long-press); `share_target`
  (appears in the Android share sheet).

**Navigation**
- System back = history back; at the root, back leaves the app.
- **Out-of-scope** navigation (e.g. an OAuth provider) opens a Custom-Tab-like bar showing the
  **origin + security state + ✕ close**, and returns to the app when closed.
- `target=_blank` / `window.open()` open a new Chrome tab (or a popup window for OAuth) and
  `window.opener`/`postMessage` works.
- Non-web schemes (`mailto:`, `tel:`, `intent:`, `geo:`, `nostr:`…) hand off to apps.

**App menu** (minimal-ui ⋮, or the out-of-scope bar ⋮) — top icon row
`Forward · Reload/Stop`, then: Share…, Copy link, Open in Chrome, Find in page, Desktop site,
Zoom/text size, Page info (connection, certificate, cookies, site settings / permissions),
Clear site data, App info.

**Web platform APIs that "just work"**
- JS dialogs (`alert`/`confirm`/`prompt`, `beforeunload`), labelled with the origin.
- Permission prompts: camera/mic (`getUserMedia`), geolocation, notifications + Push,
  clipboard read, MIDI, protected media — with a per-origin site-settings page.
- Downloads (`<a download>`, `Content-Disposition`, blob: URLs) to the Downloads folder.
- HTML fullscreen (`requestFullscreen`, the video player's fullscreen button), screen
  orientation lock, Wake Lock.
- Web Share (`navigator.share`) and Share Target, Badging API, Contact Picker, File System
  Access (open/save pickers), `<input type=file capture>`.
- Long-press context menu on links/images (open in new tab, copy link, share, download
  image), text selection toolbar with Share/Translate/Search.
- Pull-to-refresh (unless the page sets `overscroll-behavior`).
- Service worker offline support, a proper offline/error page with Retry.

## 3. Gap analysis

Legend: ✅ parity · ⚠️ partial · ❌ missing · 🔒 intentionally blocked (sandbox/Tor/keyless — keep it or gate it behind consent).

| Capability | Embedded | External | Notes |
|---|---|---|---|
| Own task/Recents entry | n/a (it's a tab) | ⚠️ | Own task exists, but Recents shows the Amethyst label/icon — no `setTaskDescription(title, favicon, themeColor)`. |
| Add to Home screen / install | ❌ | ❌ | No `ShortcutManagerCompat.requestPinShortcut`. The launcher intent must route through the main process (the `:napplet` activity isn't exported). |
| Theme-color system bars | ❌ | ❌ | External pads the window with the app's `colorBackground`; `theme-color` is never read. |
| Page title in chrome | ⚠️ host only | ❌ fixed at launch | Neither listens to `onReceivedTitle`. |
| System back = history back | ✅ | ✅ | |
| Forward | ❌ | ❌ | Chrome's menu icon row starts with Forward. |
| Stop loading | ❌ | ❌ | Reload stays Reload while loading. |
| Out-of-scope bar (origin + ✕ close) | ❌ | ❌ | Navigating off-site silently replaces the app. There's no concept of the app's "scope" or "home". |
| `target=_blank` / `window.open` | ❌ | ❌ | `setSupportMultipleWindows(false)` + `javaScriptCanOpenWindowsAutomatically=false`: `_blank` loads in place, `window.open()` returns `null`, so OAuth popups break. |
| Non-http schemes | ⚠️ | ⚠️ | Handed to `ACTION_VIEW` on a gesture, but `intent:` URIs aren't parsed (`Intent.parseUri` + `browser_fallback_url`), so they fail. |
| JS dialogs | ❌ | ❌ | **Confirmed from source:** the framework `JsDialogHelper` only shows a dialog when `webView.context is Activity`. The embed runs on a Service context, and the external browser's WebView uses `nightThemedContext()` (a `ContextThemeWrapper` over `createConfigurationContext`, not an Activity) whenever the theme is DARK/LIGHT, which `FavoriteAppLauncher` always resolves it to. So `confirm()` returns `false` and `prompt()` returns `null` in both. |
| Permission prompts (camera/mic) | ❌ | ❌ | `onPermissionRequest` isn't overridden, so the default denies. Video calls, QR scanners and voice notes on the web all fail. |
| Geolocation | ❌ | ❌ | `setGeolocationEnabled(false)`. To be offered per origin behind consent, on Tor and open web alike (§6). |
| Notifications / Push | ❌ | ❌ | Not available in WebView at all. Only a bridge polyfill could provide it; out of scope for v1. |
| Downloads | ❌ | ❌ | No `DownloadListener`, so download links do nothing. |
| HTML fullscreen video | ❌ | ❌ | `onShowCustomView` isn't implemented, so the fullscreen button is dead. |
| Web Share (`navigator.share`) | ❌ | ❌ | Not in WebView. Can be polyfilled through the existing document-start shim to an `ACTION_SEND` chooser. |
| Share page / Copy link | ❌ | ❌ | Not in either top pill. |
| Open in external browser | ❌ | ❌ | Useful escape hatch (Google sign-in blocks WebView user agents). |
| Find in page | ❌ | ❌ | `WebView.findAllAsync` / `findNext`. |
| Desktop site | ❌ | ❌ | UA override + `useWideViewPort`. |
| Text zoom | ❌ | ❌ | `settings.textZoom`. |
| Page info / site settings | ⚠️ | ⚠️ | "Manage permissions" covers NIP-07 grants only. No connection/cert state, no clear-site-data. |
| Long-press link/image menu | ❌ | ❌ | External can use `hitTestResult` in `onCreateContextMenu`. For the embed, the tap forwarder would need a long-press path. |
| Pull-to-refresh | ❌ | ❌ | Optional. Should respect the page's overscroll (only fire at `scrollY == 0`). |
| Error / offline page | ✅ overlay + Retry | ⚠️ | External only logs to the console and shows the raw WebView error page. |
| Renderer crash recovery | ❌ | ❌ | No `onRenderProcessGone`. A renderer crash in `:napplet` kills every WebView in that process, including every warm tab. |
| File input | ✅ | ✅ | Recently added. |
| NIP-07 `window.nostr` | ✅ | ✅ | Amethyst-only advantage. Keep it. |
| Tor per host | ✅ | ✅ | Amethyst-only advantage. |

### Bugs found along the way (fix regardless of the redesign)

1. **"Open full screen" opens the launch URL, not the current page.** In `WebAppScreen` it
   calls `FavoriteAppLauncher.launchUrl(context, url)` with the original `url`, not
   `currentUrl`.
2. **External favorite state is wrong after any navigation.** `NappletControlSheet.updateUrl`
   forces `isFavorite = false`, so an already-pinned site shows "Add to favorites" and tapping
   it sends a toggle that *removes* the pin. The broker knows the truth: it should push the
   state back (or the toggle should be an explicit add/remove, not a blind flip).
3. **External header title never updates.** It shows the launch host even after navigating to
   another site. That's misleading when combined with NIP-07 prompts.
4. **The two sheets disagree.** They use different icons (emoji vs Material symbols, `Lock` vs
   `ic_tor`), different strings for the same row, a different row order, and only one side has
   "Open full" and the address field. There's also a doc contradiction: `BrowserScreen` says "a
   running app never carries an editable address bar", but `NappletControlSheet` renders one
   in the external browser.

## 4. Proposal

### 4.1 One chrome spec, two renderers

Promote `EmbeddedTabChrome` into a surface-neutral **`WebChromeSpec`** in `commons`: title,
origin, security state (https / http / onion-via-Tor / sandbox), canGoBack/Forward,
isLoading, isFavorite, theme color, and a list of typed actions. Both renderers draw from
it with the same order, icons (Material Symbols font; the Views side can draw the same glyphs
from `material_symbols_outlined.ttf` with a `Typeface`) and strings. Add a unit test that pins
the row order and visibility for each surface type (web / nsite / napplet × embed / full).

### 4.2 Top pill (Chrome-style app menu)

Keep the top-center grabber (it stays out of the site's avatar corner and can't be spoofed
by the page). Expanded:

```
┌──────────────────────────────────────────────┐
│ [favicon] Page title                     [✕] │  ✕ only in external = close task
│ 🔒 example.com  · via Tor                    │  read-only origin chip; tap → Page info
├──────────────────────────────────────────────┤
│   ←     →     ↻/✕     ★      ⇪               │  back · forward · reload/stop · favorite · share
├──────────────────────────────────────────────┤
│ ⧉  Copy link                                 │
│ ✎  Edit address                              │  rare: reveals the editable URL field
│ ⬈  Open in browser app                       │  external browser escape hatch
│ ⛶  Open full screen   /   ⤓ Return to tab    │  embed ↔ external
│ ⌂  Add to Home screen                        │
│ 🔍  Find in page                              │
│ 🖥  Desktop site                        [ ]   │
│ 🧅  Route over Tor                      [●]   │
│ ⚙  Site settings & permissions               │  NIP-07 grants + camera/mic/location + clear data
│ >_ Console (N)                          [ ]   │  under a "Developer" divider
└──────────────────────────────────────────────┘
```

- **Hide the editable address field by default** (decision 1). A PWA never shows one. The
  origin chip is read-only (tap = page info, long-press = copy). An **Edit address** row, in
  both the embed and the external browser, swaps the chip for the editable field with
  the URL pre-selected; Go navigates and collapses it back to the chip. Expected to be used
  rarely; the Browser tab launcher stays the main place to type a URL.
- Header title comes from `onReceivedTitle`, falling back to the host.
- The icon row mirrors Chrome's top row. Reload turns into Stop while `isLoading`.
- A **scope indicator**: when the current origin differs from the app's start origin, tint the
  origin chip and show a "Back to <app>" action (the in-app version of Chrome's out-of-scope
  bar).

### 4.3 Bottom pill

- Keep it **developer-only** (Console), but move the toggle under a *Developer* divider in the
  top pill, and let the grabber show an error-count badge so the user knows why to open it.
- Unify height: 40 % of the surface in both renderers (Views currently hard-code 220 dp).
- **Find-in-page** reuses the bottom slot: a bar with the query field, `n/m`, ↑ ↓ and ✕ docked
  where the console grabber sits (Chrome puts find-in-page at the top, but the top edge here
  belongs to the grabber, and the bottom avoids covering the page's own header). Only one
  bottom panel is shown at a time.
- Out-of-scope navigation can also surface a slim bottom "← Back to <app>" pill. Pick either
  this or the chip in 4.2 after trying both on a device.

### 4.4 Behaviours (WebView plumbing)

Fix these in `NappletBrowserActivity`, `NappletBrowserService`, and where applicable the
nsite/napplet hosts (sandbox profile permitting).

1. **JS dialogs:** implement `onJsAlert`/`onJsConfirm`/`onJsPrompt`/`onJsBeforeUnload` ourselves.
   - External: show an Activity-owned dialog titled "*origin* says" (the framework helper
     can't, because the WebView's themed context isn't an Activity).
   - Embed: IPC to the main process and show a Compose dialog over the tab.
   - Sandboxed napplets keep today's auto-cancel.
2. **Popups / `_blank` — follow Chrome** (decision 2): enable `setSupportMultipleWindows(true)`
   and handle `onCreateWindow`, from both the embed and the external browser.
   - Every new window (a `_blank` link or a user-gesture `window.open`) opens as a **new
     full-screen Amethyst browser window** (a new `NappletBrowserActivity` task), the way
     Chrome opens a new tab.
   - `opener` must survive for OAuth popups. The child WebView is created in
     `onCreateWindow` (same `:napplet` process as both parents) and handed to the new
     activity through an in-process registry keyed by a one-shot token passed in the
     intent. `window.close()` from the child (`onCloseWindow`) finishes that task and
     returns the user to the opener.
   - Keep auto-open without a user gesture blocked (Chrome's popup blocker).
3. **`intent:` URIs:** parse them with `Intent.parseUri(..., URI_INTENT_SCHEME)`, strip the
   component/selector, and fall back to `browser_fallback_url`. Keep the gesture requirement.
4. **Downloads:** add a `DownloadListener`. Hand off to the main process, which runs
   `DownloadManager` (through the Tor proxy when the host is on Tor, or else refuses rather
   than leaking the download). Handle `blob:`/`data:` via the shim.
5. **Permissions:** handle `onPermissionRequest` (camera/mic) and
   `onGeolocationPermissionsShowPrompt` (enable geolocation). The consent prompt runs in the
   main process (same pattern as NIP-07 consent), is stored per origin in the existing
   Connected Apps ledger, and requests the Android runtime permission from the main
   activity. Works the same on **Tor and the open web** (decision 3); nothing is
   auto-denied because of routing.
6. **Fullscreen:** implement `onShowCustomView`/`onHideCustomView`.
   - External: swap in the custom view with immersive system bars.
   - Embed: open the external browser in fullscreen, since a streamed surface can't take over
     the window.
7. **Web Share polyfill:** have the document-start shim define `navigator.share`/`canShare`
   routed over the existing bridge to an `ACTION_SEND` chooser (text/url, files later).
   Require a user activation.
8. **Theme color:** a tiny shim observer reports `<meta name="theme-color">` (and changes to
   it) over the bridge. External tints the status/nav bar padding and uses it in
   `setTaskDescription`. Embed tints the top grabber.
9. **Task description (external):** call `setTaskDescription(title, favicon, themeColor)` on
   title, icon and theme changes, so Recents looks like an installed app.
10. **Add to Home screen** (decision 4): `ShortcutManagerCompat.requestPinShortcut`, with the
    favicon (from `BrowserIconRegistry`) as the icon. The shortcut always opens the page
    **full screen in Amethyst's own browser** (`NappletBrowserActivity`), never the system
    browser, so the NIP-07 signer is there. Its intent targets an exported **main-process**
    trampoline activity (the `:napplet` activities stay unexported). The trampoline
    brings up the broker, the Tor port and the account's WebView profile, then calls
    `FavoriteAppLauncher.launchUrl` and finishes. Also add dynamic shortcuts for the top
    favorites on launcher long-press, with the same target.
11. **Renderer crash:** handle `onRenderProcessGone`. Destroy that WebView, return `true`, and
    show the error overlay with Retry (for the embed, rebuild the session on Retry). All
    WebViews in `:napplet` share one renderer, and the process is still killed if **any** of
    them leaves the callback unhandled. So `NappletBrowserActivity`, `NappletBrowserService`,
    `NappletHostActivity` and `NappletHostService` must all ship the handler in one change.
12. **Context menu (external first):** on long-press over a link or image, offer Open in new
    window, Copy link, Share link, Download image.
13. **Desktop site / text zoom:** per-host settings persisted next to `WebAppNetworkRegistry`.
14. **Pull-to-refresh (optional):** only when the page is at `scrollY == 0` and hasn't claimed
    overscroll.

### 4.5 Deliberately different from Chrome (keep)

- Keyless `:napplet` process, per-origin NIP-07 consent, per-account WebView profile, Tor
  routing per host. These are Amethyst's reason to exist and none of the above weakens them.
  Every new capability goes through the broker with the same consent + ledger pattern.
- Notifications/Push: not feasible in WebView without a service-worker bridge. Out of scope.
- Service-worker offline: WebView already supports SW. Nothing to do beyond not clearing the
  profile.

## 5. Phasing

| Phase | Contents | Size |
|---|---|---|
| **0: bugs** ✅ | §3 bugs 1–3; live page titles (`onReceivedTitle`) in both surfaces; JS dialogs in the external browser (origin-labelled, with "Block dialogs from this page") | S |
| **1: chrome unification** | `WebChromeSpec` in commons; both renderers; icon row (back/forward/reload-stop/star/share); Copy link; Open in browser app; Return to tab; address field hidden behind "Edit address"; row-order test | M |
| **2: dead web APIs** | `onRenderProcessGone` (all four WebView owners at once — see 4.4 #11), JS dialogs in the embed, `_blank`/`window.open` → new browser window, `intent:` URIs, downloads, fullscreen video, Web Share polyfill | M–L |
| **3: OS integration** | theme-color bars, `setTaskDescription`, Add to Home screen + trampoline, dynamic shortcuts | M |
| **4: permissions & page info** | camera/mic/geo consent via the broker, site settings page (grants + clear data + connection state), Find in page, Desktop site, text zoom | L |
| **5: polish** | long-press context menu, pull-to-refresh, out-of-scope indicator | M |

## 6. Decisions (2026-09-26)

1. **Address bar:** hidden by default in running apps; an "Edit address" row reveals the
   editable field. Rarely used.
2. **New windows (`_blank`, `window.open`):** follow Chrome. They open a new full-screen
   Amethyst browser window, keeping `opener` for OAuth popups.
3. **Camera / mic / location:** consent-gated per origin, and they work on **both Tor and
   the open web**. Routing never auto-denies them.
4. **Add to Home screen:** the shortcut opens the page full screen in **Amethyst's browser**
   (not the system browser), so the signer is present. It launches through a main-process
   trampoline.

## 7. Implementation (2026-09-26)

What shipped, where it lives, and what was deliberately left out.

> The two renderers described below (`TopControlSheet`, `NappletControlSheet` and their find, console and
> dialog views) were later replaced by one set of Compose components. See
> `2026-09-26-browser-ui-review.md` §5.

**Shared layout.** `commons/…/browser/BrowserChrome.kt` decides which actions the top pill shows, and in
what order, for every surface (web / nsite / napplet × embedded / full screen), plus the security badge,
the scope check, text-zoom steps, the desktop user agent and the theme-colour parser. It is covered by
`BrowserChromeTest`. `nappletHost/…/BrowserChromeLabels.kt` maps each action to one Material Symbol and
one label (shared strings in `commons` Android resources). Both renderers draw from these two:

- `TopControlSheet` (Compose, embedded tabs).
- `NappletControlSheet` (plain Views, full screen). It loads the same Material Symbols font from the
  `commonsUI` assets, so the icons match. Three glyphs were added to the subset font: `FormatSize`,
  `DesktopWindows` and `AddToHomeScreen`.

**Top pill.** Header: security icon, page title, and `host · connection`. Tapping it opens page info;
long-pressing copies the link. The full-screen header also has ✕. Below it:
- The icon row: back · forward · reload/stop · star (filled when pinned) · share.
- Menu rows: back to app, copy link, edit address, find in page, text size, desktop site, add to Home
  screen, open in another browser, open full screen.
- Privacy: Tor, what it can access, site settings.
- Developer: console.

**Bottom pill.** Console as before; find in page docks in the same slot, one panel at a time.

**Web platform** (`BrowserWebTools`, `BrowserDownloads`, `BrowserPopups`, `BrowserExtrasScript`,
`BrowserJsDialogs`; used by both `NappletBrowserActivity` and `NappletBrowserService`):
- JS dialogs: native in full screen; relayed to Compose for the embed.
- `_blank` / `window.open` open as a new full-screen window, with `opener` kept through a parked popup
  WebView.
- `intent:` URIs are parsed, hardened, and fall back to `browser_fallback_url`.
- Downloads follow the page's route (Tor through SOCKS, remote DNS), carry the page's cookies, and land
  in Downloads. `blob:` and `data:` downloads come through the page script.
- HTML fullscreen: the whole window in full screen; inside the surface for the embed.
- `navigator.share` polyfill (text/url).
- Renderer-crash recovery in all four WebView owners.

**OS integration.**
- `theme-color` tints the full-screen window's system-bar areas.
- `setTaskDescription` sets the title, favicon and colour in Recents.
- Add to Home screen (`WebShortcuts` + `WebShortcutActivity`, main process, waits for Tor).
- Dynamic launcher shortcuts for the first four web favorites.

**Permissions and page info.**
- Camera, microphone and location go through a per-origin prompt. Answers are kept in
  `WebSitePermissionRegistry` (main process, same on Tor and open web), then Android's runtime permission
  is requested.
- The Connected Apps detail screen lists and resets those answers.
- Page info shows the connection, Tor and certificate, with Clear site data (this profile only).

**Left out, and why.**
- **Pull-to-refresh:** WebView exposes no overscroll signal, so on pages that scroll an inner element
  (most SPAs) `scrollY == 0` is always true. A pull there would reload mid-scroll.
- **Long-press menu in the embedded tab:** the SurfaceControlViewHost surface doesn't deliver long-press
  context menus. It is available in the full-screen browser.
- **Theme colour in the embedded tab:** the tab owns no system bars. The script's message is ignored
  there.
- **Find / text size for embedded nsites and napplets:** that host has no IPC for them yet. The rows are
  hidden via `BrowserChrome.State.hasFind` / `hasTextSize`. The full-screen nsite/napplet host has both.
- **Notifications / Push, file sharing through Web Share:** unchanged, see §4.5.

**Needs on-device verification.**
- Popup `opener` handoff across activities.
- Renderer-crash recovery. Trigger it with `chrome://crash` in a debug build, or by killing the renderer.
- Downloads over Tor.
- The pinned-shortcut cold start with Tor enabled.

