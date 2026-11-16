# nSite / nApplet favorite icons

> **Status:** shipped — `quartz/.../NappletIconPath.kt` and `amethyst/.../favorites/NappletFavoriteIcon.kt` are in tree.
> _Audited 2026-06-30._

**Date:** 2026-06-26
**Status:** implemented (pending on-device verification of the blob image-load path)

## Problem

When a user favorites a plain web app and pins it to the bottom nav, the generic globe
icon is replaced by the **site's favicon**. Favorited nSites (NIP-5A) and nApplets
(NIP-5D) did not get the same treatment — they fell back to the generic grid glyph.

## Why the webapp trick doesn't carry over

The webapp favicon is **captured live** from the WebView that loads the page
(`NappletBrowserActivity.onReceivedIcon` → IPC `MSG_RECORD_ICON` →
`BrowserIconRegistry`, keyed by host). That works because, for a plain webapp, the site
**is** the WebView's main frame.

nSites/nApplets render differently: they always load inside a **cross-origin sandboxed
iframe** under a trusted shell document (`commons/.../composeResources/files/napplet/shell.html`,
`iframe.src = '__APP_ORIGIN__/'`). `WebChromeClient.onReceivedIcon` only reports the
**main frame's** favicon — i.e. the shell (`<title>Napplet</title>`, no icon), never the
applet's iframe. So the live-capture approach is structurally blind to the app's own
favicon here, and mirroring it would silently show nothing.

## Approach: derive the icon from the manifest's own bundled blobs

An nSite/nApplet ships its files as `path → sha256` (`path` tags). Its icon is almost
always one of those blobs (a conventional `/favicon.png`, `/icon.png`,
`/apple-touch-icon.png`, …). We already download + sha256-verify every manifest blob into
a shared, content-addressed cache (`NappletBlobCache` / `NappletBlobPrefetcher`,
Tor-routed). So the icon can be resolved from the manifest itself — no WebView, no iframe
problem, content-addressed and verifiable, on the same private network path as everything
else.

### Resolution priority (per favorite)

1. **Captured/bundled blob** (`iconModel`) — the conventional icon path picked from the
   manifest's blobs, loaded from the verified cache as a `file://` model.
2. **Manifest `icon` tag** (`FavoriteApp.iconUrl`) — the publisher-declared icon URL
   (already wired before this change).
3. **Type glyph** — grid (nostr app) / globe (web), already the fallback in
   `FavoriteAppIcon`.

Blob beats the `icon` URL deliberately: the blob is verified and rides the site's
Tor-routed path, whereas a remote `icon` URL would be a clearnet fetch by Coil. Both still
beat the glyph.

## Changes

- **quartz** `nip5aStaticWebsites/NappletIconPath.kt` (new) — pure, unit-tested heuristic
  that picks the best icon `PathTag` from a manifest's `path` tags (priority list of
  conventional names + a loose raster fallback; prefers shallower paths; raster formats
  over `.ico`/`.svg`). Tests in `NappletIconPathTest.kt`.
- **quartz** — `NappletManifest.iconBlob()` (covers nApplet kinds) and
  `RootSiteEvent.iconBlob()` / `NamedSiteEvent.iconBlob()` (nSite kinds) delegate to it.
- **amethyst** `favorites/NappletFavoriteIcon.kt` (new) — `rememberNappletIconModel(coordinate)`:
  re-resolves the live event from `LocalCache`, picks its icon blob, ensures it's in the
  shared cache (prefetching on demand, off the composition thread), and returns a `file://`
  Coil model. Returns null until the blob is on disk (icon appears on next recomposition).
- **amethyst** — `AppBottomBar` and `FavoriteAppsScreen` now resolve that model for
  `FavoriteApp.NostrApp` and pass it as `iconModel`, exactly as they already did with the
  captured favicon for `FavoriteApp.WebApp`.

`FavoriteApp` is unchanged (no persistence migration): the icon is resolved from the live
manifest at render time, consistent with the existing rule that a `NostrApp` favorite is
only usable while its event is resolvable in `LocalCache`.

## Follow-ups / not done

- **On-device verification** of the blob → Coil image load (the heuristic + wiring are
  verified by unit tests + compilation; the actual image render needs a device).
- **HTML `<link rel="icon">` parsing.** The heuristic matches by conventional file name.
  A future pass could fetch + parse the index blob to honor a non-conventional icon path.
- **`.svg` / `.ico` decoding.** Listed as low-priority candidates; if Coil can't decode
  them the `FavoriteAppIcon` error fallback shows the glyph, so it's harmless but not
  guaranteed to render.
