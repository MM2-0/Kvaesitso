---
title: "One UI: the whole app moves to commonsUI, Android and Desktop become shims"
type: refactor
status: in-progress
date: 2026-09-27
owner: commons
consumers: amethyst, desktopApp, commonsUI
---

# One UI for Android and Desktop

## The decision (maintainer, 2026-09-27)

Android now ships on laptops, so Amethyst Android needs a desktop-class UI. Rather than
maintain two desktop UIs, there will be one:

- **`commonsUI` holds the whole app UI.** Every screen, the navigation host, and the
  navigation chrome at every size (bottom bar, rail, permanent drawer, docked panels).
- **`amethyst/` becomes an Android shim**: Activities, services, notifications, media3,
  camera, WebView, Health Connect, Keystore/DataStore actuals, flavours. Nothing that is not
  Android itself.
- **A new `desktopApp` becomes a JVM shim** that runs the same `commonsUI` app, so Desktop
  looks exactly like Android on a laptop: Window, tray, menu bar, keyring, file pickers.
  It **replaces** the current `desktopApp`, which keeps shipping until the new one reaches
  parity, then goes.

This supersedes the "screens and navigation stay platform-native" rule that
`.claude/CLAUDE.md`, `commons/ARCHITECTURE.md`, the `kotlin-multiplatform` /
`compose-expert` / `desktop-expert` skills and the sweep tracker's STAY list used to state.
Those were updated alongside this plan.

## What this changes in the running migration

The tracker ([2026-08-30-commons-migration-sweep.md](2026-08-30-commons-migration-sweep.md))
stays the log of what moved. These of its conclusions no longer hold:

| Old conclusion | Now |
|---|---|
| `Account` is a god object: "decompose, don't move". `AccountViewModel`: "shrink, don't move". | **Move both** to `commons`. They were only to be decomposed because Desktop was going to keep its own; the shared screens now need the same `Account` on both platforms. Narrow interfaces can still be carved out later, for tests and the CLI, but they are not a prerequisite. |
| STAY: `*Screen.kt`, `*TopBar.kt`, `AppNavigation`, `INav`/`Route`/`RouteMaker`, drawer/bottom bar, `LoggedInPage`, `loggedOff/`, `settings/` screens | **All move** to `commonsUI` (`RouteMaker` to `commons`, it has no Compose). |
| Wave 2 part B: turn `DesktopLocalCache` into a facade over `EventCache` | **Dropped.** The new desktop app uses `LocalCache` and `Account` directly. The old `desktopApp` keeps its fork until it is retired. |
| "Desktop phase": merge Desktop's `ToggleableTimeAgoText`, `TimeAgoFormatter`, `ChatBubbleLayout` forks onto the shared ones | **Dropped** for the same reason. |
| `jdk.localedata` in Desktop's packaged runtime (+~28 MB) is "a packaging call" | **Required** by the new desktop app: the shared date formatters need the CLDR data. |

## Where we start from

The Android app already contains the laptop UI. `ScreenLayoutSpec` (now in
`commonsUI/…/ui/layouts/ScreenLayout.kt`) picks one of three navigation tiers from the window
size alone (bottom bar below 600dp, rail, permanent drawer when wide, landscape and at least
600dp tall), docks the notification panel from 1200dp, and caps every destination to a 600dp
reading column (`CappedScreenContent`). A JVM window can feed it `widthDp`/`heightDp` and
get the same answer. Still app-side and part of the move: `AppNavigation.kt` (1,370 lines),
`AppNavigationRail.kt`, the permanent drawer in `AccountSwitcherAndLeftDrawerLayout.kt`,
`MessagesTwoPane.kt`, and `MainActivity.kt` (493 lines, the part that is not Activity
plumbing).

## Prerequisites the move surfaced

- **Navigation library.** The app used `androidx.navigation:navigation-compose` 2.10.1. Its
  Gradle module metadata publishes an `androidJvm` variant and only **`jvmStubs`** for `jvm`
  (checked 2026-09-27), so it cannot run the nav host on Desktop. Rather than swap to JetBrains'
  multiplatform build of that same Navigation 2, step 7a moved to **Navigation 3**:
  `org.jetbrains.androidx.navigation3:navigation3-ui` 1.1.2 and
  `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0, both Apache-2.0,
  each publishing Android, desktop JVM and iOS variants. Navigation 3 is the current direction
  for Compose: the app owns the back stack as state, and the library only renders it. See
  step 7.
- **The app root.** `Amethyst.instance` (the `AppModules` graph) is read by 178 files, 115 of
  them under `ui/`. Shared screens cannot reach an Android `Application`. The root needs a
  commons-side interface for what screens read from it, provided once at the composition root
  (as `LocalUserFinderAccount` / `LocalEventFinder` already are), with an Android and a JVM
  implementation.
- **Android-only libraries rendered inside screens.** Each needs an expect/actual or a slot the
  shim fills: media3 (46 files), Vico charts (8), WebView (5), CameraX (4), Health Connect (4),
  ML Kit (3, `play` flavour only).
- **Current-Desktop-only features.** The current `desktopApp` (260 files, ~70k lines) has
  features Android lacks: deck columns, the article editor, highlights, scheduled-post
  screens, keyboard shortcuts, menu bar, tray. Each needs a call before the old app is
  retired: bring it into `commonsUI` for both platforms, or keep it in the new JVM shim. That
  inventory is not done yet.

## Wave 4, measured: what moves with `Account`

Measured 2026-09-27 on `main` @ `c13e496c` with a closure script (not committed; the method
is below) over `amethyst/src/{main,play,fdroid}`.

**An unconstrained closure is useless.** Following every edge from `Account.kt` reaches
1,724 of the app's 1,762 files, because a few edges leave `model/` for the app root
(`Amethyst.kt`), `ui/navigation` and `ui/screen`, and those reach everything. The useful
measure is the group reachable **inside `model/`**, plus the list of edges that leave it.
Each leaving edge is a seam to cut before the group can move.

### The group

- **75 files, 22,753 lines**: `Account.kt`, `AccountSettings.kt`, the `Account*Actions` files,
  `EventBroadcaster`, and 60-odd per-feature state holders (`nip51Lists/*`, `nip65RelayList`,
  `serverList/*`, `topNavFeeds/*`, `nip46Signer/*`, `cordn/*`, …). That is 75 of the 89 files
  in `model/`.
- **Adding `EventProcessor`** (`ui/screen/loggedIn/DecryptAndIndexProcessor.kt`, which
  `Account` constructs) brings it to 77 files, 23,850 lines, and adds no new exit edge except
  two `Amethyst.instance.notificationDispatcher` calls.
- **11 files use `java.*`** (`BigDecimal`, `ConcurrentHashMap`, `UUID`, `Base64`, `File`,
  `Locale`, and `OkHttpClient` in `CashuWalletState`). So the group lands in
  **`commons/jvmAndroid`** first and promotes to `commonMain` later, the same route
  `LocalCache` took.
- **No Compose UI** in any of them.

### Hard blockers inside the group (5 files)

| File | Blocker | Proposed cut |
|---|---|---|
| `Account.kt` | `BuildConfig.VERSION_NAME` (donation prompt, 3 sites) | constructor parameter `appVersion: String`. **Done 2026-09-27**: `AccountCacheState` takes it too and `AppModules` passes `BuildConfig.VERSION_NAME`. |
| `AccountSyncedSettingsInternal.kt` | `Resources.getSystem()` + `ConfigurationCompat` for the system language list; `DefaultBottomBarEntries` from `ui/navigation/bottombars/NavBarItem.kt` | languages: an injected `() -> List<String>` or a small expect/actual; defaults: move the default entry list to `commons/model/navigation`, beside `BottomBarEntry`. **Defaults done 2026-09-28**: `DefaultBottomBarItems` + `DefaultBottomBarEntries` are in `commons/…/model/navigation/DefaultBottomBar.kt`. **Languages done 2026-09-28**: an `expect fun getLanguagesSpokenByUser()` in `commons/util/SpokenLanguages.kt` (it is a `@Serializable` default, so it could not be injected). |
| `GeohashChatIdentityState.kt` | `androidx.core.content.edit`, `LegacySharedPreferences`, `LocalPreferences.LEGACY_WRITES_RETIRED`, `Amethyst.instance.encryptedStorage` | the legacy-prefs read/write is a migration path; put it behind a `GeohashIdentityLegacyStore` port, implemented in the app. **Done 2026-09-28** as `GeohashIdentityStore` (model/), implemented by `AndroidGeohashIdentityStore` (app root). |
| `AccountZapActions.kt` | `onError: (StringResource, String?)` with `Res.string.bolt12_*` (compose resources, which `commons` cannot see) | a typed error (sealed class) that the UI maps to a string. **Done 2026-09-28**: `Bolt12ZapFailure` (commons/model); `ZapPaymentHandler` maps it to the same strings. |
| `nip46Signer/Nip46ConsentBridge.kt` | `Res` + `loadStringRes`; `Amethyst.instance.appContext`; the app's `SignerConnectCoordinator` / `SignerConsentCoordinator` / napplet op labels | it is the Android consent-dialog bridge: leave it in the app and inject it into `Account` through an interface. **Done 2026-09-28**: `Nip46ConsentPrompter` (model/nip46Signer); the bridge and `Nip46ConsentInfoBuilder` moved to `connectedApps/consent`. |

### Edges that leave the group (14 targets)

| Target | Used for | Proposed cut |
|---|---|---|
| `Amethyst.kt` | `keyCache` (Account), `encryptedStorage` (Geohash), `appContext` (Nip46 bridge), `notificationDispatcher` (EventProcessor) | constructor parameters / ports; the notification dispatcher gets an interface. **Done 2026-09-28**: `keyCache` → `Account.encryptionKeyCache`; the notifications → `MarmotGroupNotifier`, which `NotificationDispatcher` implements; `encryptedStorage` and `appContext` left with the Geohash and Nip46 cuts. |
| `LocalPreferences.kt` | `saveToEncryptedStorage(accountSettings)` on settings change | **Done 2026-09-28** as a constructor lambda, `Account.saveSettings: suspend (AccountSettings) -> Unit`, rather than a named port. |
| `DebugUtils.kt` | `logTime` (2 sites) | **Done 2026-09-28.** `commons/util` already had an identical `logTime`; its Android `isDebug` was hard-coded `false`, so it now reads `AndroidDebugFlag.enabled`, which `Amethyst.onCreate` sets. The app copy is gone. |
| `service/MainThreadChecker.kt` | `checkNotInMainThread` (HiddenUsersState) | **Done 2026-09-28**: `LocalCache.appHost.assertNotMainThread()`. **Deleted 2026-09-29**: a debug-only Android assertion that shared code could no longer rely on; the checker, its call sites and the host hook are gone. |
| `service/location/LocationState.kt` | the `LocationResult` type in `geolocationFlow` and the around-me feed | **Done 2026-09-28**: `commons/model/location/LocationResult.kt`; 25 files repointed. |
| `service/uploads/FileHeader.kt` | the `FileHeader` data type in three send methods | **Done 2026-09-28**: `commons/service/upload/FileHeader.kt` (with `BlurhashWrapper`/`ThumbhashWrapper` moved from commonsUI, same package); `prepare` stays in the app as `FileHeader.Companion` extensions. |
| `service/relayClient/…/BuzzMembershipEoseManager.kt` | the `MembershipNotificationKinds` constant | **Done 2026-09-28**: `commons/model/buzz/BuzzMembershipKinds.kt`. |
| `ui/navigation/bottombars/NavBarItem.kt` | `DefaultBottomBarEntries` | moved to `commons` (done 2026-09-28) |
| `ui/screen/loggedIn/DecryptAndIndexProcessor.kt` | `EventProcessor`, built by `Account` | moves with the group (see above) |
| `AccountSecretsStore.kt`, `LegacySharedPreferences.kt` | Geohash identity storage | behind the Geohash port above |
| `connectedApps/consent/*Coordinator.kt`, `napplet/NostrSignerOpLabels.kt` | Nip46 consent bridge | stay in the app with the bridge |

About twenty small cuts, most of them "pass it in" or "move one declaration". None is a
redesign.

## Wave 4, measured: `AccountViewModel`

`AccountViewModel.kt` is 3,303 lines.

- **Android imports.** Its Android imports are `Context` (3 methods take one), `Toast`,
  `Uri`, `Handler`/`Looper`, `android.util.LruCache` (5 sites), `NotificationManager` and
  `ContextCompat`. It has no `R` references left.
- **`Amethyst.instance`.** It reads the app root in six places: `websocketBuilder`,
  `relayStats`, `powPublishQueue`, `localBlossomCacheProbe`, `blossomResolver` and
  `appContext`.
- **Other app files.** Outside the `Account` group it depends on 29 app files. They fall into
  three kinds:
  - **Headless, should move with it:** `RelaySubscriptionsCoordinator`, `ClinkDebitPayer`,
    `CallSessionBridge`, `NestBridge`, `MarkChatRoomsAsRead`, `RowUnread`,
    `ReloadMintViewModel`, `EventSync`, `CardFeedContentState`, `RoleBasedHttpClientBuilder`.
  - **Payment and intent handlers:** `ZapPaymentHandler`, `V4VPaymentHandler`,
    `LightningAddressResolver`, `MeltProcessor`, `ZapCustomDialog.payViaIntent`. They take an
    Android `Context` to fire payment intents; that needs a `PaymentLauncher` port.
  - **Composable files it borrows a type or constant from:** `ZapAmountCommentNotification`
    (`MultiSetCompose`), `ZapraiserStatus` (`ReactionsRow`), `NOTIFICATION_LAST_READ_KEY`
    (`NotificationScreen`). The declarations move to `commons`; the composables don't have to.
  - **Genuinely Android:** `MediaSaverToDisk`, `MarmotGroupIconUploader`,
    `dismissNotificationForEvent` (`NotificationUtils`). These go behind ports.

`AccountViewModel` moves after the `Account` group, into `commons/viewmodels` (jvmAndroid
first).

### Re-measured 2026-09-28, after `Account` moved

With `Account` in commons, `AccountViewModel` (3,334 lines) sits on a headless group of **64
app files, 11,322 lines** (the relay-subscription coordinator and its assemblers, the chat-room
read markers, the Clink/call/nest bridges, `ReloadMintViewModel`, …). Leaving that group are
**25 edges**, in four kinds:

| Kind | Edges | Cut |
|---|---|---|
| A type or constant borrowed from a composable or Android file | `NOTIFICATION_LAST_READ_KEY` (NotificationScreen), `ZapAmountCommentNotification` (MultiSetCompose), `ZapraiserStatus` (ReactionsRow), `CombinedZap` (CardFeedContentState), `sats` (ReloadMintScreen) | move the declaration to commons |
| A JVM-only file that is otherwise headless | `NwcNotificationsEoseManager`, `FollowingGeohashChatSubAssembler`, `CardFeedContentState`, `EventSync`, `AccountFeedContentStates` (a `ComponentCallbacks2` level constant) | KMP swaps, then it joins the group |
| An Android service it calls | `ZapPaymentHandler`, `V4VPaymentHandler`, `MeltProcessor`, `LightningAddressResolver`, `payViaIntent`, `dismissNotificationForEvent`, `MediaSaverToDisk`, `MarmotGroupIconUploader`, `UrlCachedPreviewer`, `powKindLabelRes`, `LocalPreferences`/`AccountInfo`, `Amethyst.instance` (6 reads in the VM, 1 in `ClinkDebitPayer`), `isDebug`, `checkNotInMainThread` | ports / constructor parameters, as for `Account` |
| Its own Android calls | `Context` parameters (payments, media save, NWC setup), `Toast` + `Handler(Looper)`, `Uri`, `android.util.LruCache` (5), `NotificationManager` via `ContextCompat` | `androidx.collection.LruCache`; the rest behind the same ports |

The preview helpers at the bottom of the file (`mockAccountViewModel`, `mockVitorAccountViewModel`)
wire app classes and stay in the app, in their own file.

### What that measurement missed, and where the VM lands

The survey above counted any `com.vitorpamplona.amethyst.commons.*` import as shared. Two kinds
of shared symbol are not usable from `commons/commonMain`, and it missed both:

- **`commonsUI`-only symbols.** The VM toasts through compose-resources strings (`Res`, 33
  imports), `ToastManager` and `loadStringRes`; the payment stack's error messages are `Res`
  strings too; `NotificationSummaryState` builds a Vico chart model. By the rule in CLAUDE.md
  ("anything that imports … `Res` belongs in `commonsUI`"), **`AccountViewModel` lands in
  `commonsUI/commonMain`**, not `commons`. That changes nothing for its callers (all
  composables, all moving to `commonsUI`) and only rules out the CLI, which has `Account`.
- **`jvmAndroid`-only symbols** in commons and quartz: the relay pagers and window trackers,
  `showAmount`, the calendar sort keys, `LnurlEndpointCache`, `OnlineChecker`,
  `GeoRelayCsvLoader`, `IRoleBasedHttpClientBuilder`.

The cuts, 2026-09-28 (each its own commit on this branch):

| Cut | How |
|---|---|
| Android services the VM read | `AccountViewModelHost` (commons): memory pressure, Blossom-cache probe, PoW failures, relay stats, crawl socket builder, saved accounts, tray-notification dismissal, the wallet-app hand-off, the LNURL transport and money-op relay routing. `AndroidAccountViewModelHost` implements it over `AppModules`; previews pass a no-op. |
| Android-only actions | `saveMediaToGallery`, `uploadMarmotGroupIcon`, `urlPreview`, `checkVideoIsOnline` became app-side extensions with the same call shape. |
| `Context` threaded through payments | Never read; removed from the VM, the zap/V4V/melt handlers and the resolver. |
| OkHttp in the zap stack | `LnurlHttpTransport` port; the resolver on kotlinx JSON with Jackson-lenient readers; a scripted-transport test. |
| JVM collections, locks, dates, BigDecimal | commons `ConcurrentSet` (+`contains`/`isEmpty`/`snapshot`), `KmpLock`, quartz `ConcurrentMap`/`PlatformLock`, `LocalClock.epochDayCounter()`, `SearchDate`, quartz `BigDecimal.toDoubleValue()`, `expect fun showAmount` (DecimalFormat stays the JVM actual; a test pins the common port to it). |
| App singletons | `ClinkDebitPayer` takes `MoneyOpRelayRouting`; NIP-11 reads go through `LocalCacheHost.relayInfo`; `GeohashRelays` gets its loader installed at startup; `OnlineStatusCache` split from `OnlineChecker`. |
| `IRoleBasedHttpClientBuilder` | An `expect interface` in commonMain; the JVM actual is a typealias to the OkHttp interface, so every caller is unchanged. |

**Re-measured after the cuts:** the group reachable from `AccountViewModel` is **127 files,
22.4k lines, with no edges leaving it**. By what they import, **33 files (10.2k lines) go to
`commonsUI`** (the VM, `AccountFeedContentStates`, the payment stack, `TopNavFilterState`,
`NotificationSummaryState`, the account filter assemblers that hold the feed states, the nest
assemblers) and **94 (12.2k lines) to `commons`** (feed filters, the chat/relay-group/hashtag
assemblers, `EventSync`, …). Packages are renamed on the way:
`ui.screen.loggedIn` → `commons.viewmodels`, `ui.screen.loggedIn.X` → `commons.X`,
`ui.screen`/`ui.dal` → `commons.feeds`, `service.relayClient.X` → `commons.relayClient.X`,
`service.X` → `commons.service.X`.

## Sequence

1. **Docs** (this plan, and the rule changes in CLAUDE.md, both ARCHITECTURE files, three
   skills and the tracker). Done 2026-09-27.
2. **Cut the `Account` group's seams**, one small PR each, in the app, with no move yet. Every
   cut is a behaviour-preserving refactor that compiles and tests on its own:
   - the `BuildConfig` parameter (done);
   - `DefaultBottomBarEntries` (done);
   - `MembershipNotificationKinds` (done);
   - `logTime` (done);
   - `checkNotInMainThread` (done);
   - the `LocationResult` and `FileHeader` types (done);
   - the settings saver and the media key cache (done, as constructor parameters);
   - the typed zap error (done);
   - the language list (done);
   - the Geohash identity store, the Nip46 consent prompter and the Marmot notifier (done).

   **Result, re-measured 2026-09-28:** the group reachable from `Account.kt` inside `model/`
   (plus `EventProcessor`) is 77 files, 23,474 lines, with **no hard blockers, no edges leaving
   it and no `commonsUI`-only symbols**. It is ready for step 3.
3. **Move the group** (77 files) to `commons/jvmAndroid` in one PR. Desktop keeps its
   `DesktopIAccount` until the old app is retired; `IAccount` stays as the port it already is.

   **Done 2026-09-28, to `commonMain` rather than `jvmAndroid`** (maintainer's call), and with the
   packages renamed to `com.vitorpamplona.amethyst.commons.model.*`. Getting there took more than
   the seam cuts:
   - JVM-only APIs in the group swapped for KMP ones: `BigDecimal` (quartz), `UUID`
     (`kotlin.uuid`), `Base64` (`kotlin.io.encoding`), concurrent maps and sets (quartz
     `ConcurrentMap`, commons `ConcurrentSet` + new `snapshot()`), `Locale` (an expect).
   - Cashu rewritten as KMP: quartz `MintHttpTransport` under `MintHttpClient` and
     `CashuMintOperations` (OkHttp is the jvmAndroid implementation, wired in `AccountCacheState`
     and the CLI); `CashuWalletOps`, `CashuWalletReader`, `CashuMintDirectoryState` to commonMain.
   - cordn's file stores and `EncryptedAppendLog` rewritten on okio, with a golden test pinning
     the on-disk bytes from the old `java.io` code.
   - Desktop's own `Nip65RelayListState`, `BlossomServerListState` and
     `Nip65RelayListRepository` moved into `desktopApp`, freeing those names for the app's.
   - Two blockers that arrived from `main` meanwhile were injected: the Android Keystore cordn
     cipher, and the `marmotQuic` stream transport.
   - `NestsServerListState` moved too (a line-split fully-qualified name hid it from the survey).

   The app's `model/` keeps 13 Android-bound files (`AccountCacheState`, preferences, Tor, …).
   `IAccount` and `DesktopIAccount` are unchanged.
4. **`AccountViewModel`**: the same recipe, using the dependency list above.

   **Done 2026-09-28**, after the cuts listed under "What that measurement missed": the VM
   and 32 files that need compose resources, Vico or commonsUI symbols went to
   `commonsUI/commonMain`, the other 94 to `commons/commonMain`, with the package renames
   given there. The app keeps `AndroidAccountViewModelHost`, the Android-only extensions in
   `AccountViewModelAndroidActions.kt`, and the previews. iOS compiles are unverified in this
   container (unrelated pre-existing failures); CI checks them.
5. **The shared composables and their helpers** (sized in the tracker's 2026-09-27 section):
   - `RouteMaker`;
   - drop the `accountViewModel` overloads of the `observe*` helpers;
   - `DisappearingScaffold`'s immersive-scrolling read goes onto `DisplaySettings`;
   - then `UserProfilePicture`, `UsernameDisplay`, `Loaders`, `RichTextViewer`,
     `NoteCompose`.

   Once `AccountViewModel` is in `commons`, these move without retyping.

   **Progress 2026-09-28.** With the VM shared, re-measured closures were far smaller than the
   tracker's counts:
   - **Moved to `commonsUI`:** `RouteMaker`; `UserProfilePicture`, `UsernameDisplay` and
     `Loaders` (`commons.ui.note`, with `LoadUser`); `RobohashAsyncImage` (jvmAndroid →
     commonMain).
   - **The `accountViewModel` overloads were moved, not dropped.** That covers
     `DisappearingScaffold` and the per-note, per-user and user-finder `observe*` helpers,
     which now sit in `commonsUI` beside the shared versions they delegate to
     (`Account*.kt` files). They read the account and data sources off the VM, so they work
     under Activity roots that provide no composition locals. Dropping them would rewrite
     ~176 call sites, and stays a later cleanup.
   - **Promoted to commonMain:** `CachedRichTextParser` and `CachedAsciiDocToMarkdown`.
     Their only JVM tie was `ConcurrentLruCache`, which moved in step 4.
   - **Left: `RichTextViewer` and `NoteCompose`.** They depend on each other (embedded notes
     ↔ rich text) and reach the Android media stack, so they need a design, not a move:
     - **Media:** media3 video, the zoomable image viewer, GIF video, LaTeX (JLatexMath),
       the OSM map.
     - **Intents and share sheets:** Blossom URIs, torrents, chat links, napplets.
     - **Old JVM formatting:** `SimpleDateFormat`/`NumberFormat` in a dozen note types.
     - **Translations:** the flavour-only `TranslatableRichTextViewer`.

     The likely shape is renderer slots provided through a CompositionLocal at the Android
     root (and later the Desktop one) for the media and intents, plus KMP swaps for the
     formatting. RichTextViewer's own closure is 32 files with 41 exits; NoteCompose's is
     144 files with 81.

   **Progress 2026-09-28, later.** The shape held: slots at the root, KMP swaps below them.
   - **`RichTextViewer` moved** over `RichTextPlatform` (`LocalRichTextPlatform`): the
     platform's segment renderer, its markdown renderer, and the secret-message body. The
     quoted-note card is `LocalInlineQuoteRenderer`.
   - **Translation** is `TranslationPlatform` (`LocalTranslationPlatform`, commonsUI). The
     shared `TranslatableRichTextViewer` asks it; Play installs ML Kit and F-Droid the no-op.
   - **Platform verbs** became small commonsUI expect/actuals instead of Android calls in
     note code: `rememberTextSharer()` (the `ACTION_SEND` chooser), `rememberShortNotice()`
     (a toast), `rememberBlossomUriOpener()` (on `LocalUriHandler`),
     `argbPixelsToImageBitmap`. Plain `VIEW` intents became `LocalUriHandler.openUri`.
   - **Formatting:** `formatDateTime(epochMillis, DateTimeStyle, DateTimeStyle)`,
     `DecimalPatternFormatter`, `PlatformNumberFormatter`, `phonePrefersMiles()` and the
     quartz `BigDecimal` (+ `parseBigDecimalOrNull`) replace `java.text` / `java.math` in
     the note types. `UrlInfoItem` resolves OpenGraph URLs through `resolveHttpUrl`, and
     NIP-95 local media is an okio `Path` served by `Nip95BlobStore.path(id)`.
   - **The rest of the note card** goes through `NotePlatform` (`LocalNotePlatform`): the
     zoomable viewer and video/GIF players, link previews, the map and reverse geocoding,
     the note types built on a platform engine (audio players, chess, the git browser,
     meeting rooms, napplets/nsites), the reactions/zap row and the post editor. Shared
     code calls same-named shims in `commons.ui.note.platform`, so call sites only changed
     imports. Each of those pieces can later get a shared implementation and leave the slot.
   - **`NoteCompose` moved.** Its whole closure, 176 files (the card, `ui/note/types`,
     `elements`, `nip22Comments`, the channel/DM headers it embeds, and the slot itself),
     is now `commonsUI/commonMain` under `commons.ui.*` / `commons.relayClient.*`, and
     compiles for Android, Desktop and iOS. `AndroidNotePlatform` stays in the app and is
     installed in `AmethystTheme`. What remains app-side is what the slots name: the media
     players, the map, the platform-engine note types, the reactions/zap row and the editor.
6. **Screens**, feature by feature, into `commonsUI`.

   **Measured 2026-09-28** (after NoteCompose moved), with the same closure method: of 252
   `*Screen.kt` files, 23 had no Android-only exit, 45 had one. The exits most screens share:
   `Amethyst.instance` (109 screens), `LoadRelayInfo`/`Nip11CachedRetriever` (73),
   `NappletFavoriteIcon` (65), `ConcordCommunityImage` (64), `PrefetchFeedMedia` (48), the
   gallery picker (38), the app's `StringResourceCache` (35), `FeedFilterSpinner` (32) and
   direct `ReactionsRow` imports (30). Most `Amethyst.instance` reads already have an
   `AccountViewModel` equivalent (`httpClientBuilder`, `account.encryptionKeyCache`,
   `host.relayStats`, `account.client`, `LocalCache`); the rest need an app-services port.
   - **Wave 1 moved:** the 23 zero-exit screens with their closures, 43 files: buzz forum/
     persona/new-DM, contact lists, emoji-pack selection, interest sets, geocache hunts,
     Concord invite/members, relay-group threads, the import-follow-list flow, poll results,
     badges, podcast authoring and four settings screens. `viewModel()` became the shared
     `rememberViewModel` (with a factory overload).
   - **Wave 2 cuts:**
     - The NIP-11 cache is now commonMain behind a `Nip11Fetcher`, served by
       `LocalCacheHost.nip11Cache`.
     - `Amethyst.instance` reads that have an `AccountViewModel` equivalent now use it.
     - More files use the shared `stringRes`/`painterRes`, and six drawables are Compose
       resources.
     - `PrefetchFeedMedia` is common: Coil's `SingletonImageLoader`, `ConcurrentSet`, and
       link previews warmed through `NotePlatform.warmUrlPreview`.
     - The shared date formatter replaced `LocalizedDateTimeFormat` in four screens.
     - An `AppPlatform` slot (`LocalAppPlatform`) holds the bottom bar, the "around me"
       location label and the geohash location picker. `AndroidAppPlatform` is installed in
       the theme.
   - **Wave 2 moved:** 37 more screens with their closures, 105 files. That covers the
     bookmark, pinned-note, draft, people-list, emoji-pack and interest-set lists; the Cashu
     mint screens; relay and relay-group members; communities; Event Sync; the vanish
     screens; and the feed filter spinner with the feed views they share. `ViewModelProvider.Factory`
     implementations now override the multiplatform `create(KClass, CreationExtras)`.
   - **Wave 3 cuts:**
     - An `AppServices` port (`commons/service`) holds the app-wide stores screens read:
       favorites, browser history and favicons, napplet and signer permissions, Tor
       settings. It is installed as `LocalAppServices`; `AccountViewModelHost` gained the
       Tor relay evaluation and the NIP-42 auth state.
     - `AppPlatform` gained the camera QR scanner, an `AppLauncher` (favorite apps and web
       links) and the Concord/napplet/manifest/favicon image models.
     - The QR drawer is shared. Encoding sits behind `encodeQrMatrix`: zxing core on
       JVM/Android, nothing yet on iOS. `KeepScreenBrightAndAwake` is expect/actual.
     - `uriToRoute` left `MainActivity`. `ReactionsRow` and `LoadCityName` are imported
       through the note-platform shims. Settings rows left `AppSettingsScreen`.
       `formatGrouped` replaced `NumberFormat`.
   - **Wave 3 moved:** 15 screens, 47 files. That covers the browser, favorite apps,
     connected apps, bottom-bar settings, public chats, relay info, relay-group browse, the
     Buzz boards and invite, Concord home, backup conflicts, show-QR and software-app
     detail. `AppBottomBar` moved with them.
   - **Main-thread checker deleted:** it was a debug-only Android assertion. Once the cache
     and feeds were shared, it could only fire through a host hook that is a no-op
     everywhere else.
   - **Wave 4 cuts:**
     - `@SuppressLint` became `@Suppress` app-wide.
     - `formatHistoryReachDate` is expect/actual.
     - quartz's `ConcurrentSet`/`ConcurrentMap` replaced `java.util.concurrent` in the Buzz
       view models.
     - Settings painter icons are `DrawableResource`s.
     - Share buttons use `rememberTextSharer`.
     - `AppPlatform.isCastingAvailable` and `supportsEmbeddedAppTabs` replace
       `BuildConfig` and an API-level check.
     - More of the synchronous-tier strings now have Compose copies.
   - **Wave 4 moved:** 28 screens, 66 files: hashtag, relay and geohash feeds; people lists
     and follow packs; the hidden/blocked/muted/spam settings; the Cordn hub, key-package,
     link and coordinator screens; relay auth, privacy, home tabs, security filters, profile
     UI, drawer and video-player settings; Cashu wallet, NWC and CLINK setup; the Concord
     channel list and invite links; the attestation screen and the redirect loader.
   - **Wave 5 cuts:**
     - `PlatformBackHandler` wraps AndroidX's `BackHandler` on Android and Compose
       Multiplatform's `ui-backhandler` on desktop and iOS.
     - `rememberLongNotice` joins `rememberShortNotice`.
     - The wallet screens use `formatGrouped` and the shared clipboard extensions.
     - A shared `formatMonthDayTime` builds its label from `DateSkeletonFormatter` and the
       time-of-day formatter.
     - `LocalCacheHost.usageCounter` feeds the resource-usage ledger.
     - The flavour-specific legal settings section is `AppPlatform.legalSettingsCategory`.
   - **Wave 5 moved:** 11 screens, 23 files: the wallet detail, send, receive and
     transaction screens; on-chain transactions; the Buzz canvas, agent console and DM
     list; badge awarding; old bookmarks; and the settings index.
   - **Wave 6 cuts:**
     - The gallery picker is shared. `SelectedMedia` holds an `expect abstract class
       MediaUri`, which is `actual typealias MediaUri = android.net.Uri` on Android, so the
       upload code reading `media.uri` is unchanged. The picker launchers are
       expect/actual; desktop and iOS report a cancel for now.
     - Other expect/actuals: `JavaSerializable`, `imageContentReceiver` (image paste) and
       `SetDialogToEdgeToEdge`.
     - The editor's URL highlighting uses quartz's `UrlDetector`.
     - `MediaMimeTypes` moved to commons.
   - **Wave 6 moved:** the NIP-46 signer screen. The other cuts clear blockers that sit
     behind the upload stack.
   - **Upload port (2026-09-29):**
     - The upload model is in `commons.service.uploads`: `UploadOrchestrator`,
       `MultiOrchestrator`, `UploadingState`, `CompressorQuality`, `MediaCompressorResult`
       and the `MediaUploader` port.
     - Errors are an `UploadError` enum. commonsUI maps it to a string (`errorResource`).
     - The Android pipeline (compression, metadata stripping, encryption, the
       NIP-95/NIP-96/Blossom uploaders) stays in the app as `AndroidMediaUploader`. It is
       reached through `AccountViewModelHost.mediaUploader`.
     - Composer view models take a `MediaUploader` instead of a `Context`.
     - Five view models had their own copy of strip, compress and upload for a single
       image. They now call the shared `uploadToDefaultServer`.
     - Moved: the profile editor, the emoji pack screen and the emoji pack metadata screen
       (7 files).
   - **Wave 7 (2026-09-29):**
     - The upload preview, the file picker and the single-document picker are shared.
     - A shared wallet-app launcher, edge-to-edge dialog properties, `CashuWalletDiscovery`
       in commonMain, the Namecoin resolver on `AppServices`, and `ReusableZapButton` as a
       slot.
     - Moved: the wallet screen, the discovery tabs, badges, communities, music playlists,
       Concord create/edit, the Cashu wizard, podcast show/trailer, geocache log and the
       visualizer settings. 136 of 252 screens shared.
   - **Player seam (2026-09-30):** the media3 player stays in the app, like WebView. Shared
     UI reaches it only through `NotePlatform` slots.
     - Twenty-three screens used to reach it through side doors: direct calls to
       `EditPostView`, `ZoomableContentView`, the audio/music/voice renderers and
       `VideoViewInner`. They now call the slots, plus a new `FullscreenVideoView` slot.
     - No screen reaches `service/playback` now.
     - Moved: the thread screen, bookmark groups, the URL feed and relay-group metadata.
     - **Later:** sharing the player *chrome* (voice/music/podcast rows, controls, waveform)
       needs a `PlaybackController` port: state flows plus play/pause/seek/mute, wrapping the
       pooled media3 `MediaController` on Android. Do it when Desktop gets a player, and pick
       that engine through the license gate. VLCJ's README says "GPL, version 3 or later",
       with no linking exception (a commercial license is sold separately), so it is a stop.
   - **Wave 8 (2026-09-30):** the composers.
     - Ports: `FileSharer` (the relay ZIP export), `TakePicture`/`TakeVideo`,
       `rememberCoarseLocationPermission`, `SharedMediaResolver` + `OnIncomingShare` (SEND
       intents), `DeviceLocation` (on `AccountViewModelHost` and `AppServices`),
       `AltTextSuggester`, `BlossomServerFinder`, `mediaUriOfFile`, `availableProcessors`,
       the language names, `LocalClock.utcOffsetSeconds` and `rememberIs24HourClock`.
     - `MediaUploader` gained `remoteFileHeader` (imeta for pasted links) and `displayName`.
       The host gained `scheduledPostStore`, `anonymizeVoice` and `createWritingAssistant`.
     - Voice recordings are okio `Path`s. `commons` exposes okio as `api`.
     - `WalletAppLauncher` opens any payment URI; BOLT-11 and BOLT-12 are extensions.
     - Toasts go through `rememberShortNotice`/`rememberLongNotice`.
     - Moved: every note composer and `ShortNotePostViewModel`; the public, ephemeral,
       geohash, DM, Marmot, Concord, Minichat and relay-group chats; the media feeds; the
       relay settings; the metadata editors; podcasts; Bolt12 offers; the vanish and
       language settings. 180 of 252 screens shared.
   - **Wave 9 (2026-09-30):** `AppServices` gained `appStores`, `torBootstrapped` and
     `cachedPlaceName`; the host gained the backed-up-keys flag. `rememberDeviceAuthenticator`
     wraps BiometricPrompt with the keyguard fallback. `CalendarTimeFormat` is shared on
     `DateSkeletonFormatter`. Moved: search, the long-form composer, the calendar collection
     editor, NWC setup and the zap-amount settings. 185 of 252 screens shared.
   - **Wave 10 (2026-09-30):** Home, Discover, the nest lobby and the Marmot group editor
     first; then the date pickers on `LocalClock` (poll and zap-poll deadlines, schedule-at
     with its presets), `FileSharer.shareTextFile` (the .ics export), `PhoneCalendar` (the
     system event composer), `relativeTimeSpan` (`DateUtils` / `NSRelativeDateTimeFormatter`),
     `rememberWindowViewModel` + `LocalWindowViewModelStoreOwner` (state shared across
     destinations: the chess lobby and board, the Cordn group draft), `isHlsMedia` in
     `commons.video`, `ChessEventBroadcaster` in commonMain and `supportsWritingAssistant` on
     the host. `geo:` links go through `LocalUriHandler`. Moved: the note and poll composers,
     voice reply, the calendar collections and event detail, the geocache detail and editor,
     the live-activity channel, chess, Cordn group creation, the QR share and the compose
     settings. 204 of 252 screens shared.
   - **Wave 11 (2026-10-04):** Profile and settings. `AppServices` gained `namecoinSettings`,
     `namecoinClients` (a port over the JVM-only ElectrumX and Core RPC clients), `otsSettings`
     and `nip46ClientStore`. `AppPlatform` gained `rememberAppLanguages` (Android reads
     `locales_config.xml`) and `SpeakNameButton` (text-to-speech). `OtsSettings` and its store are
     in commonMain, on quartz's new `BitcoinExplorerUrls`; `RpcProbeResult` is too.
     `deviceDescription()` is an expect in commons. Copy-to-clipboard goes through the shared
     `Clipboard.setText`; the brand icons for identity claims are Compose resources. Moved: the
     profile (60 files: header, tabs, payment sheet), the app, NIP-46, Namecoin and OTS settings.
     210 of 252 screens shared.
   - **Wave 12 (2026-10-06):** Messages, Notifications, Calendars and the mechanical screens.
     `HorizontalTwoPane` is an expect (Android keeps Accompanist's fold-aware `TwoPane`; JVM and
     iOS split a `Row`), and the Messages pane picks single or two panes with the shell's own
     600/840 dp breakpoints (`widthClassOf`). `AppPlatform` gained `logDebugState`,
     `appVersionName`, `releaseNotesId` and `NotificationProviderPrompt`; the chart theme left the
     app's `Theme.kt`. The calendar views run on `SearchDate` instead of `java.time`, with
     `DateSkeletonFormatter` for month and weekday names. `AccountViewModelHost` gained
     `probeAudioFile` (Android's `MediaMetadataRetriever`) for the music and podcast uploaders,
     and `requestScheduledPostCatchUp`. The Blossom health probe is an expect over the HTTP
     client; NIP-86 calls go through a `Nip86Executor` port. Web bookmark previews use the note
     slot's `loadUrlPreview`. Moved: the Messages list, Notifications (cards, summary chart,
     donation cards), Calendars (day, week, month), the music and podcast uploaders, the media
     server lists, relay management, web bookmarks and scheduled posts. 220 of 252 screens
     shared.
   - **Wave 13 (2026-10-06):** Notification and calendar-reminder settings, Blossom import and
     the blob manager. `AppPlatform` gained `NotificationDeliverySettings` (push provider,
     background service, per-account participation, battery optimization) and
     `NotificationCategorySettings` (Android's channels); the shared screen keeps the display
     toggles. `AppServices` gained `calendarReminderSettings`, `setCalendarRemindersScheduled`,
     `blossomClient(server)` and `blossomMirrorQueue`. `BlossomBlobClient` is a commonMain
     interface that the OkHttp `BlossomClient` implements, and its two exceptions moved with it;
     the mirror queue, payment handler, prompt ledger and in-flight invoice set are in commons
     (on `ConcurrentSet` instead of `synchronized`). The blob viewer's pinch-zoom (`zoomable`, already
     an app dependency, Apache-2.0, Compose Multiplatform) is now a commonsUI dependency. 224 of
     252 screens shared.
   - **Wave 14 (2026-10-06):** `ReactionsRow`, the row under every note. Moving it took four
     seams: `AppPlatform.payToApps`, a `PayToAppProbe` port (Android asks the package manager
     which installed app opens a `payto` type); the wallet hand-off through the shared
     `WalletAppLauncher` instead of `payViaIntent` with a `Context`; a `RecordAudioBox` note slot
     for the voice-reply button; and notices instead of `Toast`. `RailCapabilityResolver.peek`
     takes the probe's answer as a lambda, so `RailCapability` (and its tests) moved to commons.
     Moved with it: `ZapCustomDialog`, the nutzap, on-chain, BOLT12 and relay galleries,
     `ReusableZapButton`, `QuickZapAmountRow` and `PaymentTargetsDialog`. The `ReactionsRow`,
     `LikeReaction`, `ZapReaction`, `ReusableZapButton` and `QuickZapAmountRow` note slots are
     gone: shared code calls the composables directly, so Desktop draws the real row. The
     follow-pack feed, which only waited on it, moved too. 225 of 252 screens shared.
   - **Wave 15 (2026-10-07):** One blocker each. `AppPlatform` gained `rememberCallStarter`
     (Android asks for the call permissions, then opens `CallActivity`; null elsewhere, so the
     chat room hides its call buttons), `GeocacheMapTab` (osmdroid) and `DetectedWorkoutCarousel`
     (Health Connect). `AppServices` gained `cordnBlobStore`. Buzz invite minting is the expect
     `mintBuzzInviteUrl` over the HTTP client (OkHttp on JVM/Android, unsupported on iOS for now),
     and the Buzz import collects channel ids in a `ConcurrentSet`. Moved: the DM chat room, the
     geocaches screen, the napplets list, the new-workout form, the Cordn migration screen and the
     relay-group channel list with its Buzz import and invite menu. 231 of 252 screens shared.
   - **Next:** 21 screens remain, and what blocks them is the platform itself:
     - The Nests activity and PiP.
     - The call screens.
     - The WebView browser and napplet launcher.
     - Health Connect's fitness screen.
     - The Cordn backup file flow.
     - The git repository browser, the QR image scan, the new-calendar-event form and the
       Nests list (two to four blockers each).
     - Share-as-image (bitmaps).
     - ~~`AccountSessionManager` (login and sign-up)~~: done in 7c.
     - The HLS video uploader (LightCompressor).
     Each needs a slot or a port of its own, so step 7 started alongside them.
7. **Navigation**:
   - **7a, Navigation 3 on Android (2026-10-06).** `NavHostController` is gone.
     - `NavBackStacks` (`navs/NavBackStacks.kt`) is the back stack, as snapshot state.
       - `stack` is what is on screen.
       - `savedTabs` holds the root of every tab the user left.
       - Entries carry ids, a `tabRoute` (the bar route a tab root was opened as) and a `drawerRoot` flag.
       - It is saved through kotlinx-serialization, so `Route` is now `@Serializable` itself.
     - `Nav` implements `INav` over it with unchanged semantics: the push guard, drawer
       screens keeping the bar, tab switches that drop pushes above the tab root and never
       restore onto Home, and the keyboard settle.
     - `NavDisplay` renders `stack`. Every entry in `stack + savedTabs` keeps its saveable state
       and ViewModelStore, which replaces Navigation 2's `saveState` / `restoreState`.
       - Saved tabs are keyed by their full route, so two pinned web apps no longer share one
         saved entry (the sibling-tab bug Navigation 2's per-destination saving caused).
     - The 261 builder lines in `AppNavigation` keep their names and register into a
       `NavDestinations` table. Push, pop and predictive-back transitions come from one place,
       which retires the `PopFamilies` workaround.
     - The rail, the drawer layout, screen time and the intent router read `nav.currentRoute`.
     - The R8 keep rules for enum route arguments are gone with Navigation 2.
   - **7b, in progress (2026-10-07).** The navigation core is shared:
     - `NavBackStacks` is in commons `model/navigation`.
     - `Nav`, `rememberNav` and `ImeSettler` are in commonsUI `ui/navigation/navs`.
     - The destination table, builders, transitions and the `NavDisplay` host
       (`NavigationHost`) are in commonsUI `ui/navigation/host`.
     - 238 of the 261 destinations register from commonsUI (`sharedDestinations`). The app
       registers only the 23 whose screens are still app-only.
     - The shell is shared too:
       - `AppShellLayout` (commonsUI `ui/navigation/shell`) handles the modal drawer, rail and
         docked-drawer tiers, the docked notification panel and the account-switcher sheet.
       - `AppNavigationRail` and `NotificationSidePanel` live in commonsUI.
       - `DrawerContent` is in commonsUI `ui/navigation/drawer`. Its app hooks come through
         `AppServices`: section-collapse prefs, scheduled posts and a display-only Tor status.
         The debug flag and the flavour come through `AppPlatform`.
     - Still in the app, waiting on other work:
       - The 23 destinations whose screens are app-only move with their screens.
       - The `AppNavigation` root (intents, screen time, the overlay hosts and the embedded-tab
         layer) is step 8's Android shim.
   - **7c, the login session (2026-10-07).**
     - `AccountSessionManager` and `AccountInfo` are in commons `account/`. The manager reaches
       the platform through three ports:
       - `AccountSessionStore`: the saved-login roster (`LocalPreferences`).
       - `AccountCache`: the live `Account`s (`AccountCacheState`).
       - `AccountSessionHooks`: the call and audio-room teardown, launcher shortcuts, the
         publish-pipeline purge and the Cashu counters.
     - The login and sign-up screens, their view models, the key field, the Tor setup and its
       dialog, the add-account dialog and the account-switcher sheet are in commonsUI.
     - Android fills five `AppPlatform` slots for them:
       - whether the build asks for the terms of use (Play does);
       - the terms checkbox;
       - the NIP-55 signer-app button;
       - keys from `nostr:` links while logged off;
       - toasts.
     - `AccountScreen` and `LoggedInPage` followed in 8a.
8. **The app root port** and the new JVM shim. Then the Desktop feature inventory, and
   retiring the old `desktopApp`.
   - **8a, the shared app root (2026-10-07).** `AmethystApp(sessionManager, root)` in commonsUI
     `ui/app` is the whole app below the window:
     - the login, loading and logged-in crossfade;
     - the per-account ViewModel store (`AccountScopedViewModelStore`, expect/actual);
     - the account's `AccountViewModel` and its always-on subscriptions and preloads;
     - the navigation shell with `sharedDestinations`, the first-route hand-off and the root
       dialogs (errors, notify requests, crash reports, broadcast and Blossom progress, relay AUTH).
     - Platforms fill an `AppRoot`: how to build the `AccountViewModel`, their own destinations,
       app and logged-in effects, a shell overlay, the edge-swipe guard, navigation effects and
       a screen-time hook. Android's is `AndroidAppRoot`: intents and shares, call screens, the
       resource-usage alert, embedded tabs, push registration and the NIP-55 launcher.
     - `AuthCoordinator` moved to commons; `AppServices` gained `authCoordinator`,
       `powPublishQueue` and `takeCrashReport()`.
   - **8b, the desktop shim (2026-10-07).** `desktopApp/…/desktop/app/` renders `AmethystApp` in
     a desktop window (then `runOneUi`; `run` since 8d).
     - Account storage is shared: `AccountSettingsStores`/`AccountSettingsSource` (the settings
       records) and `StoreAccountSessionStore` (roster, records, secrets, keys in the OS keyring
       through `PrivateKeyVault`) are in commons; Android keeps only its legacy fallback on top.
       `AccountCacheState` moved too, with the signer app, Marmot QUIC, Keystore and Buzz stores
       injected.
     - The graph classes moved from `amethyst` to commons: the relay and cache connectors, the
       NOTIFY coordinator, `RoleBasedHttpClientBuilder` (usage meter injected), the Tor-state
       connector, the PoW restorer, the BUD-10 resolver/probe/HEAD cache, and the Namecoin
       clients and resolver (`NamecoinServices`). `BlossomCordnBlobStore` is the platform-free
       Cordn transport.
     - `DesktopAppModules` mirrors Android's `AppModules` minus what only a phone has
       (connectivity callbacks, the battery ledger, foreground services, push). `DesktopAppRoot`,
       `DesktopAppServices`, `DesktopAppPlatform` and `DesktopAccountViewModelHost` fill the ports.
     - Its files live in `~/.amethyst/app` (or `-Damethyst.dataDir`); keys share the legacy app's
       keyring entries, which are keyed by npub the same way.
     - Verified under Xvfb: the shared login screen, a read-only login, the permanent drawer with
       the home feed from live relays and the docked notifications, and the login surviving a restart.
     - Left for 8c (see below): note media and link previews (`NotePlatform` is `None`), uploads,
       NIP-46 bunker login, OS notifications, menu bar and shortcuts, file pickers, toasts,
       language list, location, the local Blossom cache probe, and moving the legacy app's
       accounts (`accounts.json.enc`) over.
   - **8c, the platform pieces (2026-10-08).**
     - Note media: `DesktopNotePlatform` (Coil images and GIFs, the legacy media player for video
       and audio, the lightbox) and `DesktopRichTextPlatform`. The rich-text segment renderer is
       shared now (`DefaultRichTextSegmentRenderer`; Android subclasses it for LaTeX, payment
       cards, Blossom previews and secret emoji), and so are `ImageGallery`, `RelayGroupCard` and
       the link-preview loader.
     - NIP-46 login (`bunker://` in the key field, a `nostrconnect://` code to scan), kept in the
       account's encrypted secrets and built into a `NostrSignerRemote` by `AccountCacheState`.
       Offered where `AppPlatform.supportsRemoteSignerLogin`: desktop yes, Android no (it is a
       bunker, never a bunker's client).
     - `LegacyDesktopAccountImport`: the legacy app's logins move over on the first start; every
       start opens the consolidated keyring vault the legacy app created. Verified with a real
       Secret Service keyring: an nsec saved by the legacy app logs the new app in.
     - A key the keyring refuses no longer fails the login; the window says it was not kept.
     - Uploads through the JVM Blossom pipeline (`DesktopMediaUploader`), the system file dialog
       for `GallerySelect`, OS notifications (`DesktopNotifications` over the legacy
       auto-dispatcher, now cache-agnostic) with their settings in the shared notification
       screen, snackbar toasts, the File/Go/Help menu bar, relays re-dialled after sleep, and
       scheduled posts (legacy store, in-app scheduler, OS timer).
   - **8d, the switch (2026-10-08).** `./gradlew :desktopApp:run` and the packaged app start the
     shared UI (`AmethystDesktopKt`, which also takes over `--publish-scheduled`, the macOS menu
     bar settings and the dock icon); `jdk.localedata` joined the packaged runtime. The maintainer
     chose to keep the legacy app runnable (`./gradlew :desktopApp:runLegacy`) rather than delete
     it, until its desktop-only features move to `commonsUI`:

     Each row was decided on 2026-10-09 in
     [2026-10-09-legacy-desktop-retirement.md](2026-10-09-legacy-desktop-retirement.md); this table
     is the original placement guess.

     | Legacy-only feature | Where it should land |
     |---|---|
     | Deck: multi-column layout and workspaces | A wide-window mode of the shared shell |
     | Custom feed builder | `commonsUI` feeds (Android gets it too) |
     | Spotlight and advanced search | The shared search screen |
     | Messages privacy lock | Shared settings over `commons/privacylock` |
     | Hashtag-spam filters, WoT badge | Shared moderation settings, note header |
     | Live-now bar, relay metrics | Shared live and relay screens |
     | Upload compression preview, GIF picker | The shared composer |

     Platform gaps still open on the new desktop: napplets and nsites (no
     sandboxed web view), location, the local Blossom cache probe, the language list, Tor's
     first-run splash. When the table is empty, delete everything outside `desktop/app/` that
     `desktop/app/` does not reach, and the `runLegacy` task.

9. **What was left in `amethyst/`** (2026-10-08: 476 files, 102k lines, then 418 files, 89k
   lines). Dead copies left behind by earlier moves went, and so did the screens that only a
   few Android calls kept in the app:

   - Git repository browser, music playlist sheet, calendar event editor, the audio-room list,
     create/edit sheets and room panels, key backup, Cordn backup, the Cordn group chat.
   - New ports for them: `jvmDestinations()` (shared screens that need the JVM, registered by
     Android and Desktop), `LocalClock.zoneId/atTime/secondOfDay`, `HideFromScreenshots()`,
     `Clipboard.setSensitiveText()`, `rememberFileBytesAccess()`, `NotePlatform.RenderAudioPlayer`
     (audio that belongs to no `Note`), and `MediaUploader.mimeType/stripMetadata/readBytes` for
     callers that encrypt and upload the bytes themselves. `CordnMediaService` moved to
     `commons/jvmAndroid` on the shared `BlossomClient`.
   - The NIP-96 client is shared (`commonsUI/jvmAndroid`), and Desktop uploads to NIP-96 and
     NIP-95 servers.
   - Desktop's date-skeleton formatter now puts the letters in the JDK's canonical order:
     Android's `getBestDateTimePattern` takes "EEEMMMd", `ofLocalizedPattern` threw on it, which
     broke the calendar cards on Desktop.

   What stays, and why: notification system settings (push provider, battery optimisation, channels) and
   resource usage (Android process stats), the audio room's own activity, full screen, action
   bar and foreground-service lifecycle, and the Android platform itself (services, media3,
   CameraX, WebView napplet host, osmdroid maps, Tor, notifications).

Steps 2–5 can interleave. Step 5's helpers can start before 3–4 if they take `Account` /
`AccountViewModel` unchanged and only move later.

## Method (so the numbers can be re-run)

The closure script indexes every public top-level declaration in
`amethyst/src/{main,play,fdroid}`. For each file it follows edges of four kinds:

- explicit imports;
- wildcard imports;
- inline fully-qualified names;
- **same-package references**, which need no import. This is the undercount the tracker's
  header warns about.

A same-package lowercase name reached through a `.` counts only when it is an extension. The
lexer blanks strings but keeps `${…}` templates. A file is marked blocked by any of:

- `android.*`, `com.google.*`, or a non-KMP `androidx.*`;
- `R`/`BuildConfig`;
- a library `commons` lacks.

Symbols from `commons.*` that actually live in `commonsUI` (`Res`, `loadStringRes`) are
checked separately. The first pass missed them. Known over-count: same-package token matching
can link a file to a same-named declaration it does not use; the edges above were checked by
hand.
