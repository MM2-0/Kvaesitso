# Verified architecture and extension points

Inspected on 2026-10-12 at baseline commit
`3d18f3056dce2276569aa335343175ad2ca6700e`. Source paths below are repository-relative.
This is a map of existing code followed by proposed integration boundaries.

## Build and module boundaries

`settings.gradle.kts` includes 51 Android Gradle modules. The project uses Kotlin,
Jetpack Compose, coroutines/Flow and Koin. Android Gradle Plugin 9.3.1 uses built-in
Kotlin; the version catalog also pins Kotlin plugins and libraries to 2.4.10.
Gradle wrapper 9.5.0 is checksum-pinned. Android compile SDK is 37 with minor API
level 1, target SDK 37, minimum SDK 26. Java/Kotlin bytecode targets are 11;
the verified build runs with a full JDK 21, not a JRE.

| Area | Existing responsibility and boundary |
| --- | --- |
| `app/app` | APK, manifest, application initialization, flavor/build variants and signing. `LauncherApplication.kt` registers Koin modules. |
| `app/ui` | Compose launcher, view models, search, settings, home scaffold, widgets, themes and transitions. |
| `core/base` | Shared launcher contracts: `Searchable`, `SavableSearchable`, `Application`, `SearchableRepository`, filters, result scoring and fuzzy matching; shared resources. |
| `core/preferences`, `core/profiles`, `core/permissions` | Persisted launcher settings, profile lifecycle and permission coordination. Settings migrations are existing compatibility boundaries. |
| Other `core/*` | Compatibility, Kotlin extensions, localization, crash reporting, device pose and plugin-shared contracts (`core/shared`). |
| `data/*` | Feature repositories/providers: applications, shortcuts, contacts, calendar, files, calculator, conversion, currencies, weather, websites, Wikipedia, locations, widgets, themes and plugins; database and saved-searchable metadata. |
| `services/*` | Cross-feature orchestration: search, icons, badges, accounts, backup, widgets, favorites, tags, plugins, music, global actions and feed. |
| `libs/*`, `plugins/sdk` | Bundled libraries and the external plugin SDK. Their licensing differs by module. |

Dependencies are explicit in each module's `build.gradle.kts`. Do not infer that
every `data` module may depend on every other one. Recheck the touched graph before
adding a project dependency; avoid cycles and moving feature logic into `core/base`.

The installed build selected SDK build-tools 36.0.0; 37.0.0 is also installed in
the onboarding environment. Default/fdroid flavors and debug/release/nightly build
types exist. Current default debug ID is `de.mm20.launcher2.debug`; version is
1.41.0. Intelia branding and package migration have not been implemented.

## Search and execution

1. `app/ui/.../ui/launcher/search/SearchVM.kt` cancels its prior query job, invokes
   `SearchService`, observes results, applies hidden-item filtering and ranks
   savable items using text scores and stored weights. Blank queries use all-apps
   results. Query collection follows the view-model lifecycle.
2. `services/search/.../search/SearchService.kt` combines repository result streams
   inside a supervisor scope. It passes `filters.allowNetwork` to relevant
   providers, incorporates custom labels and invokes `SearchActionService` for
   nonblank queries. `SearchResults.searchActions` is already an action channel.
3. `data/search-actions/.../searchactions/SearchActionService.kt` classifies text
   and builds actions from saved builders. Built-ins include calls, messages,
   timers, alarms, URLs and web search. Existing actions are not FLOW workflows.
4. `SearchActionBuilder.from/toDatabaseEntity` maps builders to the existing
   `SearchActionEntity` columns. `SearchActionDao` returns records in position
   order and transactionally replaces the list. `SearchActionRepository` also
   exports/restores those fields through existing backup code.
5. `app/ui/.../ui/launcher/searchbar/SearchBarActions.kt` renders action chips and
   invokes `SearchAction.start(context)` only on a click. The existing editor is
   `ui/settings/searchactions/EditSearchActionSheet*`.

Important distinction: `AppSearchAction.start` constructs `Intent.ACTION_SEARCH`
and passes the query to another app. It is not an ordinary launcher app launch.
`data/applications/.../applications/LauncherApp.kt` implements
`Application.launch(context, options)` through `LauncherApps.startMainActivity`,
including the Android user and error handling. `AppRepository.findMany()` exposes
installed apps; stable app keys encode activity and, for non-main profiles, user
serial. `AppRepository.findOne(packageName, user)` is package-based: it is not an
exact activity-key lookup. Avoid fallback to a different activity/profile.

Privacy is split across provider, profile and UI layers. A new action must enforce
its own target visibility: the existing action channel is not automatically
filtered like the app result list. Do not leak locked private-space app labels or
turn an alias into a bypass for hidden apps.

## Home and gestures

`app/ui/.../ui/launcher/SharedLauncherActivity.kt` assembles scaffold configuration.
`LauncherScaffoldVM.kt` maps stored gesture settings and launch keys to state.
`ui/launcher/scaffold/LauncherScaffold.kt` owns directional swipes, tap/long-press
handling, thresholds, drag state, home/secondary pages and animation controllers.
`ScaffoldComponent` supplies lifecycle hooks, IME behavior, background and scroll
contracts; existing components include clock/home, widgets, search and feed.

`core/preferences/.../preferences/LauncherSettingsData.kt` defines `GestureAction`
and stored gestures. `ui/settings/gestures/*` edits them. System actions already
exist in `services/global-actions`, including an optional
`LauncherAccessibilityService`; this inherited capability is not authorization
to add Accessibility automation. The existing `OrbitClock.kt` is a clock style,
not an implementation of Intelia ORBIT.

## Proposed extensions (not present in the baseline)

| Feature | Preferred seam | Keep outside this seam |
| --- | --- | --- |
| FLOW v0 | A distinct local-alias builder/action in `data/search-actions`, existing entity serialization/editor/chips, and profile-aware `Application.launch`. | AI, generic intent scripting, executor loops and network requests. |
| Later FLOW workflows | Pure typed step definitions and planning/execution contracts, with adapters to existing launch and panel capabilities. Add a module only when the actual dependency graph warrants it. | Compose state in the domain model; implicit waits or claims about third-party UI success. |
| ORBIT | Launcher-local recognition/state near the existing scaffold, persisted configuration in preferences, mapped to explicit actions. | Global hooks, new overlays and changes to Android system navigation. |
| MORPH | Existing home components, widget rendering and theme/preferences state while preserving scaffold navigation contracts. | A second launcher activity or duplicate home/search state machines. |
| POCKET | A future temporary `ScaffoldComponent` with explicit lifetime and cancellation. | Persistent background services or collecting context without consent. |

## Licenses and inherited fixes

The root README/contribution policy specifies GPL-3.0-or-later, backed by
`LICENSE.txt`. `plugins/sdk` and `core/shared` have Apache-2.0 licenses;
material-color-utilities contains an Apache-2.0 license, address-formatter MIT.
Preserve the bundled license resources and individual source notices (including
third-party Apache/BSD/MIT/font notices). This is a verified inventory, not a
complete dependency-license audit or permission to relicense modules.

Keep both recent commits in ancestry: `556883681` fixes input/error crashes and
database migration hazards; `3d18f3056` closes streams/clients, reuses the OSM
provider, improves thumbnail memory use and makes a user-serial cache concurrent.
Feature tasks must not undo those resource, migration and cancellation safeguards.

## Implemented FLOW v0 extension

The bootstrap map above describes the inherited baseline. On `feat/flow-aliases`,
`data/search-actions/.../searchactions/flow/` contains the pure alias codec,
validator/matcher, guarded executor, record operations and Android target adapter.
`FlowAliasActionBuilder` is the `flow_alias` discriminator in existing entity
serialization. `SearchActionService` combines alias records with current eligible
targets; `SearchBarActions` dispatches through the suspend `activate` hook in a
Compose coroutine. Existing synchronous actions use the default adapter.

`FlowAliasRuntime` depends on existing `AppRepository` and saved-searchable
visibility. It filters personal-profile/exact activity targets, queries hidden
app keys without a count cutoff, resolves again for activation and dispatch, and
never falls back by package. `FlowAliasRecords` updates raw records without losing
legacy/unknown payloads; the shared repository serializes writes. No migration
is needed because existing fields already store the versioned payload.

`FlowAliasSheet` adds authoring/repair/delete to the current Search Actions settings
route, separate from the sealed generic intent editor. `SearchVM` excludes alias
results from best-match fallback. Unit tests cover the pure contracts; Android
Room persistence, real backup/restore and UI/device behavior are still unverified.
