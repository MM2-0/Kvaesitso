# Web-app naming overhaul (favorites / browser / app surfaces)

> **Status:** shipped — Renames applied — `WebAppScreen`, `NostrAppScreen`, `EmbeddedWebAppController`, `FavoriteAppsScreen` all present.
> _Audited 2026-06-30._

## Problem

The in-app "app" surfaces had colliding, sometimes inaccurate names:

- `software_apps` (NIP-89 native Android apps, an install-from-a-store flow) showed as
  **"Apps"** — colliding with the in-app favorites, which showed as **"Favorite apps"**.
- The **host screens** that render a single web client / nSite / nApplet were named
  `FavoriteWebAppScreen` / `FavoriteNappletScreen`. But they open *any* url/coordinate,
  favorited or not — "Favorite" described how they happened to be reached (a pinned
  bottom-bar tab), not what they are. And `FavoriteNappletScreen` also renders **nSites**
  (website-mode), so "Napplet" was narrower than reality.
- Three vocabularies for two model cases: model `WebUrl`/`NostrApp`, route
  `FavoriteWebApp`/`FavoriteNostrApp`, screen `FavoriteWebApp`/`FavoriteNapplet`.

## Taxonomy (decided with maintainer)

User-facing terms, now distinct:

| Concept | User-facing | What it is |
|---|---|---|
| Native app store | **App Store** | NIP-89 native Android apps you install off-device |
| Nostr web client | **Web app** | an `https://` client that runs in-app (WebView) |
| nApplet | **nApplet** | NIP-5D sandboxed JS app |
| nSite | **nSite** | NIP-5A static website |
| Pinned set | **Favorite** | a cross-cutting attribute (the star), *not* a screen |

Code axis (favorites / route / screen / embedded-controller layer): **`WebApp`** (url-based,
no nostr identity) and **`NostrApp`** (coordinate-based nSite *or* nApplet). The cross-process
sandbox infra (`napplet/`, `nappletHost/`, `NappletHostService`, `NappletEmbedContract`)
keeps **"Napplet"** — that process genuinely is the napplet host (it serves nSites in
website-mode too, but the host *is* the napplet runtime).

"Favorite" is reserved for the **grid of pinned apps** (`FavoriteAppsScreen` /
`Route.FavoriteApps`) and the star toggle — the only things that are actually about favorites.

## Renames

Routes: `FavoriteWebApp(url)` → `WebApp(url)`; `FavoriteNostrApp(coordinate)` → `NostrApp(coordinate)`.
Screens: `FavoriteWebAppScreen` → `WebAppScreen`; `FavoriteNappletScreen` → `NostrAppScreen`.
Controllers: `EmbeddedBrowserController` → `EmbeddedWebAppController`;
`EmbeddedNappletController` → `EmbeddedNostrAppController`.
Factory: `acquireBrowser`/`browserId` → `acquireWebApp`/`webAppId`;
`acquireNapplet`/`nappletId` → `acquireNostrApp`/`nostrAppId`.
Model: `FavoriteApp.WebUrl` → `FavoriteApp.WebApp`. Registry: `WebUrlNetworkRegistry` →
`WebAppNetworkRegistry`.

Strings: `software_apps` "Apps" → "App Store"; `favorite_apps_empty` reworded to name
nApplet/nSite.

## Stable (do NOT change — persistence / wire compat)

- Favorite `id` prefixes `"url:"` / `"nostr:"` (persisted dedup + bottom-bar keys).
- DataStore names `"favorite_apps"`, `"weburl_network"`; serialized type tags `"url"` / `"nostr"`.
- `napplet/` + `nappletHost/` sandbox infra names and IPC contracts.

## Follow-ups (not in this pass)

- Move `NostrAppScreen` + `EmbeddedNostrAppController` out of the `ui/...favorites/`
  package (they are no longer favorites-specific) into a host package alongside the web side.
- Recent nApplets / nSites (parallel to the browser's recent web apps), surfaced on the
  discovery screens.
