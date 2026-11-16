# AGENTS.md

**Single source of truth for every AI coding agent** working in this repository
(Claude, Codex, Copilot, Cursor, Devin, ChatGPT, Gemini, Grok, and any other
assistant).

Read this file only. Do not create or keep parallel instruction files
(`CLAUDE.md`, `.github/copilot-instructions.md`, `GEMINI.md`, `.cursorrules`,
`.cursor/rules/`, `CODEX.md`, `.windsurfrules`, Aider rule files, or extra
`*-instructions.md`). Put every shared rule here so all agents stay in sync.

## Project

Telos — a search-focused, free and open source Android launcher (package `de.mm20.launcher2`). Kotlin/Gradle multi-module Android project. Licensed under GPL-3.0, except `plugins/sdk` and `core/shared` which are Apache-2.0.

## Build & common commands

- Build environment: JDK 21 toolchain (see `gradle/gradle-daemon-jvm.properties`), Gradle 9.9.0 via wrapper.
- Build variants are a combination of a build type (`debug`, `release`, `nightly`) and a product flavor (`default`, `fdroid`), e.g. `assembleDefaultDebug`, `assembleFdroidRelease`.
- Build a debug APK: `./gradlew assembleDefaultDebug`
- Install to a connected device/emulator: `./gradlew installDefaultDebug`
- Run all unit tests: `./gradlew test`
- Run tests for a single module: `./gradlew :core:base:test` (module paths mirror the directory structure, e.g. `:data:locations:test`)
- Run a single test class: `./gradlew :core:base:test --tests "de.mm20.launcher2.OpeningScheduleTest"`
- Lint is non-fatal (`lint.abortOnError = false` in `app/app/build.gradle.kts`).
- Unit tests exist in `core/ktx`, `core/base`, `data/locations`, `services/comms`, `services/store` and `data/store`; most other modules have no test suite.
- CI (`.github/workflows/build-nightly.yml`) builds `assembleDefaultNightly` nightly.
- Automated Daily Dependency Check: Scheduled GitHub Action (`.github/workflows/daily-dependency-check.yml`) executes daily at 06:00 UTC and updates `.github/daily-dependency-check.json`. That stamp is not the vulnerability list.

## Dependabot alerts (mandatory, high priority)

Every coding agent (Grok, Claude, Codex, Copilot, Devin, ChatGPT) must treat open GitHub Dependabot alerts as work to do, not as optional reading.

1. At the start of a session, and whenever you can call the GitHub API, list **open** Dependabot alerts for `koukomin/Telos-Launcher`. GraphQL `vulnerabilityAlerts(states: OPEN)` or REST `/repos/koukomin/Telos-Launcher/dependabot/alerts`. Do not stop at `.github/daily-dependency-check.json`.
2. Fix them **automatically** in this session, ordered **CRITICAL → HIGH → MODERATE → LOW**. CRITICAL and HIGH go before unrelated feature work. MODERATE and LOW go in the same change when you already touch the catalog, Gradle pins, or `docs/package-lock.json`.
3. Direct versions in `gradle/libs.versions.toml` can already be patched while transitives stay old. Check the SBOM / resolved graph. Pin transitives in the root `allprojects { configurations.configureEach { resolutionStrategy.eachDependency { … } } }` block, including root Dokka generator/publication resolvers, and keep Bouncy Castle / Jackson / Guava / jsoup on one version. App modules that still `useVersion` Bouncy Castle must use `libs.versions.bouncycastle`. Run `./gradlew -I tools/validation/security-dependencies.init.gradle verifyDokkaSecurityDependencies --no-configuration-cache` before submitting the dependency graph.
4. Docs site (`docs/package.json`): bump VitePress/Vue and `overrides` for vite, esbuild, postcss, nanoid, rollup, immutable, then regenerate `docs/package-lock.json`. Run `npm audit` in `docs/` until remaining issues are explained.
5. Do not dismiss an alert without writing why it is a false positive. Do not upgrade a GPL-incompatible library. Do not merge or release unless the user asked.
6. Update `readme.md`, `docs/docs/developer-guide/dependabot.md`, and `THIRD_PARTY_NOTICES.md` when a library version or license changes. Push to the working PR branch.

## Architecture

The codebase is split into ~4 dozen Gradle modules under four top-level groups, from highest to lowest level:

- **`:app`** — `:app:app` is almost empty, just the `Application` class (`LauncherApplication`, in `app/app/src/main/java/de/mm20/launcher2/LauncherApplication.kt`). `:app:ui` contains almost the entire UI and is the only module using Jetpack Compose.
- **`:services`** — higher-level business-logic APIs, one module per feature area (`:search`, `:icons`, `:widgets`, `:favorites`, `:tags`, `:badges`, `:music`, `:accounts`, `:backup`, `:plugins`, `:global-actions`, `:feed`).
- **`:data`** — lower-level implementations, usually implementing interfaces defined in `:core:base` so that `:services` don't need to depend on `:data` directly (e.g. `:data:applications`, `:data:files`, `:data:calendar`, `:data:contacts`, `:data:websites`, `:data:wikipedia`, `:data:widgets`, `:data:database` (Room), `:data:plugins`, `:data:locations`).
- **`:core`** — foundational, mostly UI-agnostic building blocks: `:core:base` (core interfaces like `Searchable`/`SavableSearchable`, common data classes, utilities), `:core:preferences` (AndroidX Datastore), `:core:permissions`, `:core:i18n`, `:core:ktx`, `:core:compat`, `:core:profiles`, `:core:devicepose`, `:core:crashreporter`.
- **`:libs`** — standalone modules/forked third-party code with no dependency on `:core:base` (`:material-color-utilities`, `:nextcloud`, `:owncloud`, `:webdav`, `:address-formatter`).
- **`:plugins:sdk`** — public SDK (published to Maven Central as `de.mm20.launcher2:plugin-sdk`) that lets third-party apps implement search/weather/calendar plugins via content providers, without depending on internal launcher modules.

Note module naming is not perfectly consistent between directory layout and Gradle path — check `settings.gradle.kts` for the authoritative module list.

Dependency injection: **Koin**. Nearly every module has a `Module.kt` at its root defining a Koin module exposing that module's APIs to others. All per-module Koin modules are aggregated and started in `LauncherApplication.onCreate()`.

Other key libraries: Jetpack Compose + Accompanist (UI), Coil (images), KotlinX coroutines/serialization, AndroidX Room (database) and Datastore (preferences), Ktor (HTTP).

### Search architecture

Search is the core feature. `core/base` defines the common `Searchable`/`SavableSearchable` abstractions and `SearchableRepository` interface; each `:data:*` module (applications, files, contacts, calendar, calculator, websites, wikipedia, locations, unit converter, search-actions, etc.) implements search for its domain, and `:services:search` composes these into the unified search experience surfaced by `:app:ui`.

### Plugin system

External apps can extend Kvaesitso (weather, file/contact/places search, calendar) by implementing content providers using `plugins/sdk`, without needing the launcher's internal modules. `:data:plugins` holds low-level plugin APIs/access control; `:services:plugins` is the higher-level plugin management service (enable/disable/list). See `docs/docs/developer-guide/plugins/` for the plugin type contracts and `docs/docs/developer-guide/plugins/access-control.md` for the security model.

## Telos Desktop — pending phases (do not forget)

Gradle module `:desktop-linux` hosts **Telos Desktop** (Compose for Desktop JVM). Linux labwc/niri session + vault-as-`$HOME` are in tree. Windows UberJar is a portable Host Home shell; macOS Intel/ARM ships only after Linux + Windows play correctly. See `docs/docs/developer-guide/desktop-roadmap.md` and `.github/workflows/build-desktop-binaries.yml`.

If the user asks what is still pending for the Linux session stack: **none of P0–P4, Helix, Surface, or Wayland W0–W3**. Spec: `docs/docs/developer-guide/telos-wayland-shell.md`. Spec: `docs/docs/developer-guide/telos-helix.md`. Never depend `:app:app` on `:desktop-linux`. `:app:ui` may use `:ui-kmp`.

## Python tooling (do not scatter scripts)

Keep Python under `tools/` (and `docs/scripts/` / `.github/scripts/` for docs/CI only). Supported trees: `tools/i18n/`, `tools/translations/`, `tools/validation/`, `tools/performance/`, plus release helpers `tools/release-*.py`. **Do not** leave one-shot `patch_*.py` / `fix_*.py` in the repository root — edit sources directly or add a maintained tool under `tools/` with a README.

## Documentation

Docs live in `docs/` (a VitePress site, published as GitHub Pages at https://koukomin.github.io/Telos-Launcher/ and historically at https://telos.mm20.de). Every user-visible or architectural change must update, **in the same change**:

- `readme.md` (feature lists, based-on table, status text);
- the matching pages under `docs/docs/` (user guide and developer guide);
- `THIRD_PARTY_NOTICES.md` when a library or license changes.

Check `docs/docs/developer-guide/` before structural changes. Do not describe anything that is not implemented.

## Contributing conventions

- For bug fixes and smaller enhancements, PRs can be sent directly.
- For bigger new features, an issue should be opened first to discuss design before implementation.
- All contributed code must be GPL-3.0-or-later licensed (except contributions to `plugins/sdk` / `core/shared`, which are Apache-2.0).

## Performance, memory and APK size rules

Telos runs as the home screen, so every agent (Devin, Copilot, ChatGPT, Claude) working on this repo follows these rules. Fix real problems; do not refactor working code only to match a checklist.

**Main thread**
- No disk, network, database, `ContentResolver` or call-log work on the main thread. Use `suspend` functions with `withContext(Dispatchers.IO)` (or `Default` for CPU work), and collect `Flow`s instead of reading them once.
- No new `runBlocking` in app code. Inside a coroutine, call `first()` directly. Outside one, launch in a scope (`containedScope(...)` from `core:base`, `lifecycleScope`, `viewModelScope`) and come back to `Dispatchers.Main` to touch the UI.
- `SharedPreferences`: use `apply()`. `commit()` is only for writes that must be on disk before the process can die (crash-recovery state such as `VirtualAppGuard`), and never on the main thread.
- Do not start heavy work in `Application.onCreate`. Create engines, decoders and services on first use.
- Battery: no GPS/network location loops on the home screen. Search location and weather use coarse, cached updates. Periodic WorkManager jobs require `setRequiresBatteryNotLow(true)` unless the user asked for a live check. Do not poll CallScreening or the dialer blocklist; it stays event-driven when enabled. Do not add wake locks or foreground services for launcher idle. Sensor listeners unregister when the lifecycle is below STARTED. Never trade home-screen frame time for extra battery savings.

**Compose**
- Wrap derived values in `remember(keys)` or `derivedStateOf`. No sorting, filtering, parsing, `Regex(...)`, `SimpleDateFormat(...)` or object allocation directly in a composable body or in `drawBehind`/`onDraw`.
- Long or unbounded lists use `LazyColumn`/`LazyRow`/`LazyVerticalGrid` with stable `key`s.
- Pass lambdas or `State` down instead of rapidly changing values (scroll offsets, animations) so only the reader recomposes.

**Memory**
- Never keep an `Activity`, `View` or composable-scoped `Context` in a static field, `object`, singleton or long-lived scope. Use `context.applicationContext` there.
- Close `Cursor`s, streams and `ParcelFileDescriptor`s with `use { }`. Recycle or cache bitmaps through the existing caches (`FileThumbs`, Coil); decode them at the size shown.
- Compile `Regex` once (top-level or class-level `val`), not per call or per loop iteration.
- Prefer primitive types (`Int`, `Long`, `IntArray`) in hot loops; avoid boxing collections there.

**APK size**
- Before adding a dependency, check if an existing one already does the job. Pin shared libraries (Bouncy Castle, OkHttp, Kotlin) to one version.
- Keep rules must be narrow: keep only what is loaded by reflection or JNI, never `-keep class some.library.** { *; }` for a whole library without a reason written next to it.
- Large assets (fonts, models, lookup tables) need a reason. Exclude unused library data files in `packaging.resources.excludes`.
- A new language needs its code added to `localeFilters` in `app/app/build.gradle.kts`, or its translations will not be packaged.
- Native libraries stay split per ABI (`-Ptelos.abi`). Do not add x86 or universal builds.
- Release and nightly builds keep R8 minification and resource shrinking on.

## Keeping README, docs and Pages up to date

`readme.md` is the public face of the project. The VitePress tree in `docs/` is the GitHub Pages site. Whenever you add or change a user-visible feature, or you port, adapt or take design from another project, update **readme, docs and Pages in the same commit**:

- add the feature to the matching list under "Features" (move it out of "Planned" if it was there);
- update the matching user-guide page under `docs/docs/user-guide/` and, for internals, `docs/docs/developer-guide/`;
- add or update the project in the "Projects Telos is based on" table with its link and license
  (check the license is GPL-3.0 compatible before copying code) and keep its copyright notice in
  `THIRD_PARTY_NOTICES.md`;
- do not describe anything that is not implemented.

## Translations

Every new or changed user-visible string must be translated into **all** supported languages **in the same change**. English goes in `core/i18n/src/main/res/values/` first. Greek (`values-el`) is the first locale after English. Then fill every other `values-*` folder. Never leave a language with an English fallback for a string you added.

Supported: Greek, German, Spanish, French, Italian, Portuguese (pt, pt-BR), Russian, Turkish, Dutch, Polish, Czech, Slovak, Slovenian, Croatian, Bulgarian, Romanian, Hungarian, Danish, Swedish, Finnish, Estonian, Latvian, Lithuanian, Irish, Maltese, Ukrainian, Norwegian, Chinese (zh-CN, zh-TW), Japanese, Korean, Arabic, Hindi, Bengali.

Use `tools/i18n/` (see its README): `extract.py` for missing keys, `build.py` to write `strings_telos_translations.xml`. Keep placeholders (`%1$s`, `%d`, XML tags) unchanged.

Do not duplicate translated text. For common labels (Cancel, Save, Settings, Search, Close) reuse existing keys (`hc_cancel`, `hc_save`, `action_settings`, …) or alias with `@string/hc_cancel`. Do not add a second copy of the same English (or Greek) sentence.

## User workflow preferences

- Add necessary UI translations for every change, for all supported languages, with Greek as the priority alongside English.
- Update README, docs and Pages source content when the behavior changes.
- Push development changes to the working PR branch. Do not merge or create a release without the user explicitly asking.

## UI design rules for every agent

Read `docs/docs/developer-guide/virtual-app-surfaces.md` before changing launcher,
widget or Telos app chrome. Preserve the shared static/video wallpaper blur and
single root backing. Only root background/surface roles may be transparent;
`surfaceContainer` must retain an opaque tonal color for popup menus. Navigation
drawers explicitly use that container so underlying text cannot show through.
Never use a cleared `surface` as an icon foreground: use paired Material roles
(e.g. primaryContainer/onPrimaryContainer) for playback controls, including TV.
Home clock, resting transparent search and dock page dots have no extra plate.
Files workspace tabs get their own horizontally scrolling row, with actions in a
separate row; preserve user font scale and minimum touch targets. Dialer Recents
search shows live call matches first and matching contacts afterwards in one lazy
list, retaining hidden-contact rules and call confirmation. Reuse translated
labels. Verify bright/dark wallpaper, video wallpaper, Greek text, large fonts,
narrow screens, menu/drawer overlays and playing/paused states. Clearly state
when build or device validation could not be performed.


## Restart and update continuity

New Store profiles default to automatic non-Play updates. Preserve an existing
explicit opt-out. The global auto-install switch is a master switch; per-app
false, pinning, tracking-only and background exclusion must still win. Never
automatically take over an install managed by com.android.vending. Session
installers still require Android confirmation; do not promise silent updates
without a supported privileged installer. Defer launcher self-updates while an
alarm is scheduled, without blocking unrelated app updates.

Use PersistentUiState for small navigation choices and PersistNavigation for
virtual-app route stacks in standalone and embedded hosts. Do not persist
passwords, confirmation dialogs or pending destructive actions. Keep snapshots
bounded, load/serialize them off the main thread, and retain the route-name R8
rule. Files owns its existing workspace persistence. Alarm definitions and Network
intent/configuration are durable independently of screen navigation. Never claim
crash/update recovery is device-verified based solely on reading the code.

Virtual-app task redirects must finish and remove a redirect-only Settings root on both onCreate and onNewIntent. Do not retain explicitly finished virtual-app document tasks in Recents. Keep unfinished-task/process recovery separate from explicit close; no lifecycle callback should relaunch a closed app.

Media playback controls must respond to taps across the full player surface, including portrait letterbox regions. Keep system bars synchronized with controls visibility instead of hiding them on each recomposition. Do not pause TV merely on app switching with background playback enabled. Every media session needs its own notification ID, including local and isolated video; preserve stop/pause and user opt-outs after background-policy migration.

Stremio playback preferences use ordered primary/secondary audio and subtitle languages; empty primary subtitles disables text tracks and automatic subtitle lookup. Scope these preferences to Stremio launches, including the isolated player process. The 1/2/5/10/20 GB choice is an LRU HTTP cache per player process, not a total torrent staging quota. Never describe it as a cap on all torrent disk usage. Network/speed controls reuse the shared TorrentSession settings rather than create a second torrent engine.

Subtitle selection uses a searchable, scrollable bottom sheet with explicit selected/off state and fixed file/online actions; keep it usable in landscape and with large fonts. Stremio autoplay is opt-in, uses only catalog-provided episode IDs and the same add-on/transport, with one bounded request on actual playback end. Never autoplay external-browser streams or random files from a multi-file torrent. Resolution/size/screener preferences only filter metadata, never alter playback: preserve unknown metadata and fail open for manual browsing, but stop automatic continuation if no candidate matches.

## Theme contract for every Telos app

The wallpaper blur belongs to the single shared root. Material container roles
(lowest/low/normal/high/highest) inside virtual apps are opaque tonal surfaces;
never apply another per-card alpha or use transparent surface as a text/icon
color. Clear only root alpha, retaining its RGB tone for legacy opacity restoration.
ListPreference, AlertDialog and other pickers over glass use `virtualAppPopup()`
(`surfaceContainer` at alpha 1). Preference rows use opaque `surfaceContainerLow`.
Settings → Appearance (theme, shapes, transparency, dynamic-color source) must stay
as frosted as other Telos surfaces; do not leave dialogs on the cleared root `surface`. Any scoped MaterialTheme must also provide LocalContentColor from its
own onSurface. Radio's default appearance inherits the launcher palette. Its explicit appearance
override, station bar, popups and mini players must obey the same foreground/background pairing. Full-screen camera,
video and screen-light content may intentionally own black or illuminated pixels.
Keep zoom and utility actions compact in landscape without shrinking touch targets.

Every new virtual app must be registered in SettingsDeepLinkContract.virtualAppTaskId,
SettingsActivity task-domain mapping, its static Android activity-alias metadata,
TelosAppLabels and TelosAppIcons, and must ship the three Rondo drawables from
`docs/ICON_DESIGN.md`. A manifest
label alone cannot represent several document tasks. Test launcher, notifications,
rotation, process recreation and explicit close independently. Biometric screens
reattach their callbacks to the retained AndroidX prompt after rotation; do not
start another prompt from every new composition or persist an unlocked flag to disk.

### Mandatory Cyber-Tech UI & Theming Guidelines (all coding agents)

All Telos virtual apps, mini players, and launcher controls must adhere to the Cyber-Tech design language:
- **Glowing Tech Borders**: Cards and container items must use `Modifier.telosTechCard(borderColor, shape, borderWidth, alpha)` with neon accent highlights (Neon Green `#00E676`, Neon Cyan `#00F0FF`, Neon Magenta `#FF007F`, Neon Orange `#FF9100`).
- **Telemetry & Dot Matrix**: Header labels, status HUDs, and technical metrics must use `TelosDotMatrixText` and `TelosRadialGauge` / `CyberGauges` for visual hierarchy and high-tech telemetry readouts.
- **Command HUD (mandatory for list apps)**: Search, filters and bulk actions (update all, pause all, refresh) use `TelosCommandHud` / `TelosHudIcon` in `app/ui/.../TelosCommandHud.kt`. Search is an icon until opened. Filters are 48dp icons with `contentDescription` and long-press `Tooltip` labels. Counts use badges. Status (last check, scanning) uses `TelosDotMatrixText` under the strip. Do not add a full-width search field plus a Filters button plus an Update-all button as three stacked rows. Categories that are names may be 1–2 letter `TelosTechPill`s with tooltip of the full name. Apply this in Store, Downloads, Freeze, ART Optimizer, and any new list/filter dashboard. Keep 48dp targets and font scale.
- **Morphe-Style Tech Pills**: Destination tabs and short named modes still use `TelosTechPill`. Filter *rows* of many options belong on the command HUD, not a second chip row.
- **Mini Player Surface**: The mini player across Telos Media, Radio, Music, TV, and Stremio must feature `telosTechCard` translucent glass borders, neon playback status indicators, marquee titles, and instant full-surface touch controls.
- **Opaque Popups Over Glass**: All dialogs, pickers, and popups over glass must use `virtualAppPopup()` (`surfaceContainer` at alpha 1) with opaque background tones to prevent background text bleed.

Do not reintroduce an embedded terminal or bootstrap downloader. Termux is an external Store recommendation; its app, prefix and sessions belong to com.termux. Keep any external launch integration separate from Telos virtual-app registration.

HWUI property values are skiavk/skiagl; the process chooses its pipeline once.
Restoring the property after launcher startup does not retroactively switch its
already-created renderer. Explain app-process restart rather than promise that
Activity.recreate or restarting System UI switches every app. Keep restart notices
on by default with an explicit preference to hide them. Never restart system UI
or kill unrelated apps automatically.

Saved Wi-Fi credentials require explicit reveal and device authentication. Do not
persist, log or copy them to telemetry. Associate keys with their own network block,
never fall back to the first saved password for an unknown current SSID. Clear
revealed values on backgrounding. Shizuku availability does not guarantee that an
OEM permits reading its Wi-Fi store; show a failure rather than fabricated data.
## Mandatory Telos UI and icon design contract (all coding agents)

These instructions are binding for any AI coding assistant or contributor working in this repository. Read `AGENTS.md` and `docs/ICON_DESIGN.md` before editing any Telos virtual app, Dialer, launcher icon, UI resource or icon pipeline. Treat these as acceptance criteria, not suggestions. Do not claim implementation from documentation alone.

### Dialer: large contact photograph

- Incoming, outgoing and in-call screens must show a prominently large contact photograph, inspired by the visual prominence in Google Phone, without copying proprietary assets.
- Use a responsive edge-to-edge photo region where appropriate, preserving clear caller identity, number, call status, controls, accessibility and safe areas. Account for compact screens, tablets, landscape, large font sizes, cutouts and gesture/navigation insets.
- Support contact photo fallback to a polished initials/avatar design; never stretch low-resolution images or obscure critical actions.
- Keep the existing call-state logic, telecom permissions, accessibility labels and functional call controls intact. Add UI previews and tests for photo/no-photo, light/dark, incoming/outgoing/active and large text.

### Rondo icon pack (mandatory on every new virtual app)

A new Telos app is incomplete until its Rondo icon exists. Read
`docs/ICON_DESIGN.md` and copy **Phone / Files / Messages**, not Store or
Weather.

- Disc: `pathData="M24,2A22,22 0 1,0 24,46A22,22 0 1,0 24,2Z"` with one unused
  saturated Material colour.
- Long shadow: `#20000000` and `pathData="M14,14 L32,32 L26,44 L10,28 Z"`.
- White filled 24 dp Material glyph in a group `translate 9,9` `scale 1.25`.
- Ship three vectors in `core/base/src/main/res/drawable/`:
  `ic_app_<slug>_full.xml` (48 dp tile), `ic_app_<slug>_fg.xml` (108 dp white
  glyph), `ic_glyph_<slug>.xml` (24 dp). Same `pathData` on the glyph.
- Register `TelosAppIcons`, `VirtualAppAppearance.glyph` (fg, never `_full` as
  a notification small icon), labels, deep link, manifest `android:icon`, Store
  catalog, user-guide and this catalog, in the same change.
- No gloss, gradients, brand logos, baked squircles, or Material Icons
  Extended. Honor a user-selected icon pack on launcher surfaces.

### Delivery gates

- Implement and verify behavior in code, not only in screenshots or prose. Do not mark tasks complete without a passing build and relevant tests or an explicit explanation of what could not be tested.
- Preserve existing launcher functionality and avoid unrelated UI changes.
- Submit changes in a PR; do not merge or release unless explicitly instructed.

## Mandatory UI design for all AI coding agents

Before modifying virtual apps, Dialer, icons or app surfaces, read `docs/ICON_DESIGN.md` and `docs/docs/developer-guide/rondo-icons.md` and comply with their acceptance criteria. A new virtual app always includes the three Rondo drawables in the same change. Dialer must show a large contact photo (Google Phone-inspired prominence, no copied proprietary assets) on incoming, outgoing and active calls; provide initials fallback, responsive layout, safe controls, large-text accessibility and tests. Keep user-selected icon packs and custom icons on launcher-controlled surfaces. Update translations, README and docs with implemented changes. Never claim completion without build and visual QA.

## UI consistency and adaptation (mandatory)

Read [the adaptive UI contract](docs/docs/developer-guide/adaptive-ui.md) before UI changes and
keep [validation evidence](docs/docs/developer-guide/ui-validation.md) accurate.

- Reuse `LauncherTheme` semantic colors/typography/shapes, `UiSpacing`, shared preference/search/
  navigation components and `LauncherCard` composited foregrounds. Preserve explicit theme,
  transparency and wallpaper-readability settings; do not introduce an independent palette.
- Decide layout from actual available width **and height**, after padding/insets; use
  `AdaptiveLayoutPolicy` for existing shared space decisions. Never infer content fit from
  orientation, screen/device class or system display metrics alone.
- Never clamp/replace system fontScale or shrink essential text to fit. Let text wrap and heights
  grow; reduce columns, collapse panes or scroll. Keep important values/actions reachable.
- Apply/consume Scaffold, cutout/system-bar and IME insets once. Maintain 48dp targets, labeled
  toggle/tab semantics, disabled behavior and non-color-only state. Keep navigation/drafts/scroll
  in existing saveable state/ViewModels across layout changes; do not reset them on resize.
- Validate compact/medium/expanded and short landscape at font 0.85 (where supported), 1.0, 1.3,
  2.0 plus enlarged display size, both themes, wallpaper contrast and IME. Add behavioral regression
  coverage for changed policies/components. Record commands, commit, screenshots and untested
  states. Compilation/previews/static tests are not device or visual verification; do not label
  an unreviewed screen or the entire UI phase complete.

- Keep min-SDK compatibility explicit: use existing AndroidX compat calls or SDK guards, never
  suppress a real NewApi failure. Do not let JDK 21 collection inference introduce SequencedMap
  into Android paths that support older APIs. CI screenshots must survive test-APK uninstall;
  follow the documented MediaStore collection pattern and verify artifact retrieval.

Locale gender variants inherit their base language. Do not fill a feminine or
masculine resource directory with English copies of already translated base
strings; keep only actual variant-specific translations and let Android inherit.

Notification identity is separate from Recents metadata. Use VirtualAppAppearance
for built-in notification glyphs and large icons. Media3 providers must explicitly
set their small icon after build(); retain station/album/video artwork. Never use
a colored full app tile as a status-bar small icon (Android masks it). Notification
content intents must open the relevant virtual-app route, not the launcher home.

Launcher typography stores lineHeight in em. Never call Density.toDp() on an
arbitrary TextUnit: it only accepts sp. For header/toolbar sizing, measure text
with the actual TextStyle and font scale; cover em, sp and unspecified heights in
Settings regression tests, including the real MainSettingsScreen.
