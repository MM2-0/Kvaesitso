# Full sweep: `amethyst/` → `:commons` migration candidates

> **Direction change (2026-09-27): one UI.** Android now ships on laptops, so every
> screen and the navigation shell move to `commonsUI`, `amethyst` becomes an Android
> shim, and a new JVM `desktopApp` replaces the current one with the same UI. See
> [2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md). That
> supersedes, below: the STAY list's screens/navigation entries, "decompose `Account`,
> shrink `AccountViewModel`" (both now **move**), Wave 2 part B (dropped), and the
> "Desktop phase" merges of Desktop's own forks (dropped). This file stays the log of
> what moved; Wave 4 is planned and measured in the new plan.

> **Execution status (updated 2026-08-30, same branch):** Waves 0-1 are DONE
> on this branch — the 12 shim deletions, the 38-file relayClient batch
> (with `AccountScopedQuery` generalized to `IAccount`), the okhttp stack →
> `commons/service/http`, 37 model/service singles, the
> `IFeedTopNavFilter` → `ICacheProvider` signature fix, `TopFilter`
> extracted out of `AccountSettings.kt`, and 34 topNavFeeds files reunited
> with their commons half. All app/desktop/cli targets compile.
> Corrections found while executing are folded into the sections below;
> the biggest one: **import-graph analysis under-counts blockers** —
> same-package files use `Account`/`LocalCache`/each other *without
> imports* (extension receivers included), so several "clean" files
> (AccountMarmotActions, EventBroadcaster, ParticipantListBuilder,
> UnexpectedCrashSaver, the Blossom fetchers) are actually
> Account/LocalCache-coupled and stayed. The `ui/theme` + `ui/layouts`
> batch also does NOT move mechanically: `Theme.kt` is Android-coupled
> (Activity, UiModeManager, app font/theme prefs enums) and the layouts
> sit on app-side theme constants + `stringRes` — that whole cluster
> belongs to the strings/theme wave.
>
> **Refined `LocalCache` recipe (next big step, needs maintainer input):**
> the move-group is `LocalCache` + `AntiSpamFilter` (android LruCache →
> androidx.collection) + `CachePruner` + `CacheSearch` + `MiniFhir` +
> `OnchainZapResolver`, into commons **jvmAndroid** (which legalizes its
> `java.io.File` NIP-95 spill as-is). Seams to cut: `Amethyst.instance`
> (2 sites → injected scope), app `isDebug` (2 → settable flag),
> `checkNotInMainThread` (→ settable hook or expect), `ui.note.dateFormatter`
> (1 log line). The open design question: `CachePruner`/`CacheSearch` call
> `Account.isFollowing(...)` and read `account.hiddenUsers.flow.value.
> hiddenWordsCase` — `IAccount` already has `isHidden`/`hiddenWordsCase`
> but lacks `isFollowing`, so either `IAccount` grows it (DesktopIAccount
> must implement) or the pruner/search take a narrower ISP interface.

**Date:** 2026-08-30
**Scope:** every Kotlin source file in the `amethyst` Android module
(`amethyst/src/main`, 2,347 files), audited for "can and should move to
`:commons`", cross-checked against what `commons/` and `desktopApp/` already
contain.

## Method

1. **Static classification** of all 2,347 files by imports:
   - *Android-dirty* = imports `android.*`, `com.google.*`, or `androidx.*`
     other than the KMP-safe set (`compose`, `lifecycle`, `annotation`,
     `paging`, `collection`), or uses `androidx.navigation`.
   - *R-dirty* = imports `com.vitorpamplona.amethyst.R` or references
     `R.string`/`R.drawable`/`R.raw` (incl. via `stringRes`).
2. **Transitive closure** over the module-internal import graph: a file is
   **Tier A** iff it and everything it references inside the module are
   clean of both. **Tier B** = clean once its strings move to commons
   Compose resources.
3. **Six deep area audits** (model, service, service/relayClient+okhttp,
   ui shared components, ui/screen, napplet/connectedApps/misc) verifying
   verdicts file-by-file and hunting for desktopApp reimplementations.

## Headline numbers

| Metric | Count |
|---|---|
| Files in `amethyst/src/main` | 2,347 |
| Directly Android/R-clean | 1,324 |
| **Tier A — transitively clean, movable now** | **509** |
| Tier B — movable once their strings migrate | +21 |
| Files that genuinely touch an Android API | ~14% of `ui/` |
| ViewModels app-side vs migrated to commons | 138 vs 11 |
| Strings migrated to commons Compose resources | 152 / 4,417 |

The dominant blockers are **not Android APIs**. They are five hub types that
are themselves Android-import-free but still live in the app module, plus the
string-resource bridge:

| Blocker | Files it transitively blocks | Nature |
|---|---|---|
| `model.Account` | 254 | Android-clean god-object (184 vals, 218 funs) — decompose, don't move |
| `ui.screen.loggedIn.AccountViewModel` | 180 | genuinely Android-coupled (9 android imports, 36 `R` refs) — shrink, don't move |
| `ui.navigation.navs.INav` / `routes.Route` | 173 | platform nav — correctly stays |
| `model.LocalCache` | 115 | **Android-clean**; only 3 trivial dirty deps |
| `ui.stringRes` (`StringResourceCache`) | ~210 direct users | needs a Compose-resources twin |
| `model.TopFilter` (declared inside `AccountSettings.kt`!) | 39 | clean sealed class in the wrong file |

## The five highest-leverage edits

Each of these is small and unlocks a large batch:

1. **`service/relays/EOSE.kt` → finish the move.** 4 of its 6 declarations
   are already typealiases into `commons/relays`; `SincePerRelayMap`
   (`androidx.collection.LruCache` — KMP-safe) and `EOSEAccountKey` remain.
   Moving them **unblocks ~145 `subassemblies/` filter files at once** (97%
   of that layer is already Tier A).
2. **`IFeedTopNavFilter` signature fix.** `model/topNavFeeds/IFeedTopNavFilter.kt:34,36`
   hard-codes `cache: LocalCache` in `toPerRelayFlow`/`startValue`, poisoning
   all 23 `*TopNavFilter` implementors. Change to the existing commons
   `ICacheProvider` port → the app half of `topNavFeeds` reunites with the
   25-file half already in `commons/model/topNavFeeds/`.
3. **Move `LocalCache` (3,980 lines).** It's `object LocalCache : ILocalCache,
   ICacheProvider, Dao` with zero Android imports. Its entire dirty-dep set:
   `MainThreadChecker` (Looper → `expect` no-op on JVM), a `dateFormatter`
   log line, `Amethyst.instance` scope (inject), `BundledInsert` (commons
   `BundledUpdate` already exists), NIP-95 `java.io.File` blob spill
   (`expect` sink), and hoisting the inline `ILocalCache` interface.
   **This deletes `DesktopLocalCache.kt` (1,173 lines) — the single largest
   duplication in the repo.**
4. **Extract `TopFilter` + a `ListBackupStore` port out of `AccountSettings.kt`.**
   `AccountSettings` (1,900 lines) mixes the `backupXList`/`updateXList`
   persistence port with nav-shape types (`NavBarItem`,
   `DrawerItemVisibility`) and the `TopFilter` sealed class (line 102).
   The port + `ICacheProvider` unlock all of `model/nip51Lists` (31 files)
   and ~18 one-file `model/nipNN` packages — the commons copies of
   `Nip65RelayListState` etc. prove the recipe is purely mechanical
   (`LocalCache` → `ICacheProvider`, drop `AccountSettings`).
5. **A Compose-resources `stringRes` twin.** `ui/StringResourceCache.kt`
   defines the six `stringRes()` overloads + `painterRes` on
   `android.util.LruCache` + `R` ids. commons already has the translated
   `composeResources` pipeline (152 strings so far). This is the gate on
   ~210 otherwise-clean composables/ViewModels.

## MOVE-NOW batches (no prerequisites)

These are Tier A today; grouped as reviewable PRs. Verified per-file by the
area audits.

### Batch 1 — relay client (highest confidence, zero risk)
- `service/relayClient/eoseManagers/`: `PerUserEoseManager`,
  `PerUserAndFollowListEoseManager`, `PerUniqueIdEoseManager`,
  `AccountScopedSingleSubNoEoseCacheEoseManager` → `commons/relayClient/eoseManagers`
  (they extend commons `BaseEoseManager` already; deps `User`,
  `SincePerRelayMap` are commons or move with Batch 1).
- `reqCommand/account/` pure `filterXxx()` functions (~9 files:
  `FilterDraftsAndReportsFromKey`, `FilterBasicAccountInfoFromKeys`,
  `FilterBookmarksAndReportsFromKey`, `FilterFollowsAndMutesFromKey`,
  `FilterLastPostsFromKey`, `FilterAccountInfoAndListsFromKey`,
  `FilterNotificationsToPubkey`, `FilterCashuHistoryToPubkey`) + the
  account EOSE managers that consume them → `commons/relayClient/account`.
- `reqCommand/channel/` filters + watchers (`FilterChannelMetadata*`,
  `FilterLiveStreamUpdatesByAddress`, watcher sub-assemblers) →
  `commons/relayClient/channel`.
- `reqCommand/nwc/` (FilterNWCPaymentsFromRequests, NWCPaymentFilterAssembler,
  NWCPaymentWatcherSubAssembler) → `commons/relayClient/nip47WalletConnect`.
- `searchCommand/subassemblies/SearchPeopleByName`, `SearchPostsByText` →
  `commons/relayClient/search`. **Desktop's `SearchFilterFactory.kt:56`
  literally comments "ported from Android SearchPostsByText".**
- `chatDelivery/`, `speedLogger/` → commonMain; `diagnostics/` → jvmAndroid.
- `TorCircuitHealthTracker` → `commons/relays/health`.
- `authCommand/model` (9 of 11 files) → **merge into** the existing
  `commons/relayClient` auth types (`AuthApprovalPolicy`/`AuthApprovalRequests`
  are a thinner second implementation of the same concern — reconcile, don't
  copy). `DataStoreRelayAuthPermissionStore` stays as the Android actual.

### Batch 2 — the ~145 `ui/screen/**/subassemblies/` filter files
Pure `fun filterXByY(relay, …): List<RelayBasedFilter>` functions, 97%
Tier A, already importing `commons.relayClient.subscriptions.ExplainedFilter`.
Sole prerequisite: `SincePerRelayMap` (Batch 1). Desktop reimplements 34 of
them by hand in `desktop/subscriptions/FilterBuilders.kt` (742 lines).

### Batch 3 — okhttp stack → `commons/service/http` (jvmAndroid)
16 of 20 files in `service/okhttp/` move as-is: `IHttpClientManager`,
`DualHttpClientManager(+ForRelays)`, `OkHttpClientFactory(+ForRelays)`, all 8
interceptors, both event listeners, `OnionLocationCache`, `OkHttpDebugLogging`.
Plus `model/privacyOptions/` (4 of 5: role-based client builders,
`ProxiedSocketFactory`). okhttp3 is an established commons jvmAndroid dep
(BlossomClient, lnurl, UrlPreview) with no shared client manager today.
- `EncryptionKeyCache` needs `android.util.LruCache` → KMP cache first.
- `IsEmulator` stays (androidMain actual).
- `OkHttpWebSocket` is production-dead (only androidTest usages) and
  duplicates quartz's `BasicOkHttpWebSocket` — migrate the tests, delete.
- **Closes a real desktop gap**: `desktop/network/DesktopHttpClient.kt`
  re-implements the dual direct/SOCKS client but is missing every
  interceptor (onion-location, Blossom auth, encrypted blobs).

### Batch 4 — theme, layouts, feed shell (UI quick wins, no string work)
- `ui/theme/` (Theme.kt 780 lines, Shape.kt, Type.kt, Color.kt) →
  `commons/ui/theme`. commons' theme package is only a fragment (no
  ColorScheme/Typography/Shapes); **desktop re-declares the entire palette in
  `desktop/platform/PlatformColorScheme.kt` — two divergent Amethyst palettes
  ship today.**
- `ui/layouts/`: `NoteComposeLayout` (374), `RepostLayout`, `ScreenLayout`
  are 100% clean; `LeftPictureLayout`, `ChatHeaderLayout`,
  `SlimListItemLayout` need only content-description strings.
  `DisappearingScaffold` → SPLIT (drop the `AccountViewModel` param for a
  slot; its nested-scroll half is already in commons).
- `ui/feeds/` (12 of 14): `FeedStates`, `RefresheableBox`, `WatchScrollToTop`,
  `RememberForeverStates`, `ChannelFeedContentState`,
  `WatchLifecycleAndUpdateModel` have zero blockers;
  `FeedEmpty/Error/Loading/UserBlockedFeed` need 2-3 strings each. Desktop
  hand-rolls its own empty/error/loading states.
- `ui/components/` 20 zero-blocker files: the `Clickable*` family,
  `M3ActionDialog`, `OutlinedThinPaddingTextField`, `SlidingCarousel`,
  `AudioWaveformReadOnly`, `LatexEquation`, `FileAttachmentCard`,
  `pdf/PdfFetcher`, and `toasts/` (6 of 9 — despite the name it's a Compose
  snackbar queue, not Android Toast).
- `navigation/topbars/` chrome (`ShorterTopAppBar`, `TopBarWithBackButton`,
  `ActionTopBar`, `AmethystClickableIcon`) → `commons/ui/layouts`.

### Batch 5 — model + misc clean files
- `model/` root, 14 files: `HomeFeedType`, `VideoPostKind`, `HashtagIcon`,
  `ConcordInviteResult`, `NoteEditOverlays`, `PrivateChatroomReadState`,
  `RelayGroupContentRouting`, `MutedPublicChats`, `ParticipantListBuilder`,
  `LargeSoftCacheAddressExt`, `Dao`, `EventBroadcaster` →
  `commons/model`; `AccountMarmotActions`, `AccountRelayGroupActions` →
  `commons/actions`.
- `model/nip64Chess/ChessAction` → `commons/nip64Chess`.
- `model/nip03Timestamp` (4 of 6: OTS settings, explorer endpoints,
  verification, resolver builder) → `commons/model/nip03Timestamp`.
- `model/marmot/InMemoryMlsGroupStateStore` → `commons/marmot`.
- `model/nip47WalletConnect/NwcInfoCache` → `commons/model/nip47WalletConnect`.
- `ui/dal/AdditiveComplexFeedFilter` (6-line abstract class, zero imports) →
  `commons/ui/feeds`; `DefaultFeedOrderEvent` likewise.
- napplet clean half: `NappletRelayCleartext` (pure NIP-04/44 → quartz or
  `commons/napplet/protocol`), `NappletLaunchRegistry`,
  `NappletNotificationStore`, `NappletIdentityWatch` → `commons/napplet`
  (jvmAndroid).
- `service/namecoin/NamecoinNameService` → `commons/service/namecoin`.
  **Desktop's `DesktopNamecoinNameService.kt` says "Same functionality as the
  Android NamecoinNameService… mirrors AppModules#buildNamecoinBackend
  exactly"** and is the superset — merge into it, not alongside it.
- `service/images/` fetchers (7 of 11: `Base64Fetcher`, `BlurHashFetcher`,
  `ThumbHashFetcher`, `ProfilePictureFetcher`, `BlossomFetcher`,
  `BlossomReadAuthFetcher`, `DeferredDeleteFileSystem`) →
  `commons/service/image` (jvmAndroid). Desktop clones four of them
  file-for-file; `commons/{blurhash,thumbhash,base64Image}` actuals already
  exist to sit under them.
- `service/resourceusage/` 13 of 18 files (`UsageKeys`, `UsageSummary`,
  `ResourceUsageStore`, `ResourceUsageAccountant`, `ResourceUsageAlerts`,
  `RefCountedSession`, `MeteringNostrSigner`, `BatteryDrainSampler`,
  `ResourceUsageReportAssembler`, time integrators, `RelayUsageListener`) →
  `commons/service/resourceusage` (only `SystemClock` → `TimeUtils` swaps).
- `service/cashu/` → `commons/cashu` (rest of the wallet is already there);
  `CachedCashuParser`/`MeltProcessor` need the KMP LRU.
- Small singles: `PodcastRemoteContent` → `commons/podcasts`;
  `BuzzInviteMinter` → `commons/actions` or `commons/buzz`;
  `WritingAssistant` (pure interface) → commonMain; `DetectedWorkout` +
  `WorkoutMerger` (197 lines of merge logic) → `commons/workouts`;
  `ConnectivityStatus` + `ConnectivityManager` flow plumbing → commonMain
  with the Android `ConnectivityFlow` as actual; crashreports logic
  (`UnexpectedCrashSaver`, `CrashReportCache`, `DevReportContact`) →
  jvmAndroid (desktop currently has *no* crash reporting);
  `ScheduledPostWorkGate` → `commons/scheduledposts` (desktop duplicates the
  drain gating); `PowJobStore`/`PowJobRestorer` → `commons/service/pow`
  (desktop lacks PoW persistence entirely);
  `service/uploads/` clean half (`ImageDownloader`, `MediaUploadResult`,
  `MediaMimeTypes`, `SuspendableConfirmation`, `blossom/*`, `hls/*` builders,
  `nip96/ServerInfoRetriever`) → `commons/service/upload` (jvmAndroid).
- `ui/tor/` non-service half (8 files: `TorSettings(+Flow)`,
  `TorServiceStatus`, `TorBackend`, `ArtiGuardState`, `TorPreferencesPort`,
  `TorManager`, `TorDialogViewModel`) → fold into existing `commons/tor`.
- `ui/screen` root strays: `AccountState`, `UserFeedState`,
  `DebouncedPublisher`, `relays/common/RelaySuggestionState`,
  `embed/SelectionUiState`.
- `ui/screen/loggedIn/discover/` is 67% Tier A — near-wholesale move of its
  datasource + subassemblies layers.

### Delete-only (shims whose move already happened)
`model/Note.kt`, `model/User.kt`, `model/HashtagIcon.kt`,
`model/torState/TorRelaySettings.kt`, `model/torState/TorRelayEvaluation.kt`,
`ui/dal/FeedFilters.kt`, `ui/dal/ChangesFlowFilter.kt`, `ui/feeds/FeedStates.kt`
(partly), `service/BundledUpdates.kt`, `service/relays/EOSE.kt` (after
Batch 1), `ui/screen/FeedViewModel.kt`,
`chats/privateDM/dal/ChatroomFeedViewModel.kt` + `ChatroomFeedFilter.kt`,
`threadview/dal/LevelFeedViewModel.kt`, `reqCommand/user/UserFinderShims.kt`,
`reqCommand/event/EventFinderShims.kt` — 13+ typealias re-export files.
Rewrite importers to the commons FQNs and delete.

Also: adopt `commons/util/toTimeAgo` and **delete
`ui/note/TimeAgoFormatter.kt`** (377 lines on `android.text.format.DateUtils`
— the app and desktop currently render time-ago with two divergent
formatters).

## MOVE-AFTER (blocked, with the named blocker)

| Area | Files | Blocker(s) | Target |
|---|---|---|---|
| `ui/screen/**/dal/` feed filters + thin VMs | ~114 | `Account` (106 of 109 block on it alone), `LocalCache` | `commons/ui/feeds` + `commons/viewmodels` |
| `ui/screen/**/datasource/` assemblers | ~168 | `AccountViewModel`, `TopFilter`, app-side eoseManagers | `commons/relayClient` |
| `model/nip51Lists/` state classes | 18 | `ICacheProvider` swap + `ListBackupStore` port | `commons/model/nip51Lists` |
| `model/topNavFeeds/` | 37 | the `IFeedTopNavFilter` signature edit | `commons/model/topNavFeeds` |
| ~18 one-file `model/nipNN` state pkgs (`nip17Dms`, `nip65RelayList`, `nipB7Blossom`, `edits`, `localRelays`, `zap`, `buzz`, `algoFeeds`, `trustedAssertions`, …) | ~25 | same two ports; **four already have commons twins — reconcile-and-delete, don't copy** (`nip65RelayList`, `nipB7Blossom`, `nip30CustomEmojis`, `nip72Communities`) | matching `commons/model/nipNN` |
| `model/serverList`, `model/nip01UserMetadata`, `model/nip02FollowLists` | 17 | `LocalCache` | `commons/model/…`; replaces desktop's hand-rolled `DesktopAccountRelays` (250 lines) |
| `AntiSpamFilter`, `MediaAspectRatioCache`, `UrlCachedPreviewer`, `Nip11CachedRetriever` | 5 | KMP `LruCache` expect (or quartz `LargeCache`) | `commons/model`, `commons/util` |
| `relays/` list-editing VMs (17 `*RelayListViewModel`) | ~20 | 2-3 `R.string` toasts each | `commons/viewmodels`; desktop hand-rolls all seven relay editors today |
| `wallet/WalletViewModel` (750 lines, zero android imports) | 1 | 19 `R.string` refs + `Amethyst` singleton | `commons/viewmodels` |
| `SearchBarViewModel` (435) | 1 | `Account` + `Route` (make route-building a callback) | `commons/viewmodels` (its `SearchBarState` is already there) |
| `chess/ChessViewModelNew` | 3 | trivial — **desktop's `DesktopChessViewModelNew.kt` is line-for-line the same class** | `commons/nip64Chess` |
| `ui/note/types/` kind renderers | ~89 | `AccountViewModel` threading (81 files) + strings; introduce a `NoteRenderContext`/callback bundle | `commons/ui/note` (22 files there prove the pattern) |
| `ReactionsRow` (2,602 lines) + `note/buttons/` | ~10 | 30 strings, 1 stray `Context` import | `commons/ui/note` — **desktop has zero reactions/zaps UI today** |
| `UsernameDisplay`, `NIP05VerificationDisplay`, `UserProfilePicture` | 3 | strings; merge avatar stack into commons `UserAvatar` | `commons/ui/note` |
| `RichTextViewer` family | ~8 | strings; commons `ui/richtext` exists and desktop already uses it — reconcile the app's fork | `commons/ui` |
| notifications state (`CardFeedContentState`, `CardFeedState`, `NotificationSummaryState`, `OpenPollsState`) | 6 | `Account`; commons `ui/notifications/CardFeedState` is the other half of a half-done move | `commons/ui/notifications` |
| 6 structurally-identical `*MetadataViewModel`s (lists, followPacks, bookmarkgroups, emojipacks, interestSets, nip28 channel) | 6 | one shared error-enum extraction (8 `R.string` + `Context` each) | `commons/viewmodels` |
| uploads core (`UploadOrchestrator` 525, `MediaCompressor`, `MetadataStripper`) | ~8 | `Uri`/`ContentResolver` → stream core; `Int` string-res errors → typed enum; promote commons' jvmMain orchestrator to jvmAndroid | `commons/service/upload` — today **two independent orchestrators** exist |
| `ZapPaymentHandler` (580) / `V4VPaymentHandler` (295) | 2 | split zap-split math + NWC/LNURL sequencing (shareable) from `Context`/string error surface | `commons/model/nip57Zaps` / `commons/onchain` |
| playback policy leaves (`SimultaneousPlaybackCalculator`, `VideoViewedPositionCache`, `AutoReplayLimiter`, `HlsLivenessCache`, `LowLatencyHlsStripper`, `websocket/Wss*`) | ~7 | extract from the 71-file media3 package | `commons/service/playback` |
| push logic (`PushWrapDecryptor`, `RegisterAccounts`) | 2 | none real — just entangled with FCM glue | `commons/service/push` |
| `napplet/NappletLiveSubscriptions`, `gateways/AccountIdentityReader`, `NappletManifestLookup` | 3 | `Account` / inject cache | `commons/napplet` |
| `ui/broadcast/` banners | 3 | strings + theme + `AccountViewModel`; logic already in `commons/service/broadcast` | `commons/service/broadcast/ui` |
| post composers (`ShortNotePostViewModel` 2,046, `*NewMessageViewModel`, HLS/music authoring) | ~20 | deep: uploads core + location + `StringProvider` + `Amethyst` singleton | last wave |

## STAY (correctly platform-native)

> **Revised 2026-09-27.** The navigation shell and the screens no longer stay: they move
> to `commonsUI` (see the one-UI plan). What is listed below is what remains
> platform-native. The media, capture and service entries stay as *implementations*;
> the screens that render them reach them through ports or slots.

- ~~**Navigation shell & screens**: `*Screen.kt`, `*TopBar.kt`, `New*Button.kt`,
  `INav`/`Route`/`RouteMaker`/`AppNavigation`, drawer/bottom-bar,
  `AccountScreen`/`AccountSessionManager`/`LoggedInPage`, `loggedOff/`,
  `settings/` screens.~~ Moving to `commonsUI` (`RouteMaker` to `commons`).
- **Process/DI roots**: `Amethyst.kt`, `AppModules.kt`, `EncryptedStorage`,
  `LocalPreferences`, `DebugUtils`, `model/accountsCache`,
  `model/preferences/` (the one genuinely-Android model package: DataStore/
  Keystore actuals — but define their ports in commons).
- **Media & capture**: `service/playback/` (media3/MediaSession/PiP — desktop's
  player is a genuinely different implementation, not a port),
  `ui/actions/uploads` pickers/camera/voice, `creators/` capture files,
  Zoomable/PDF viewers, `GifVideoView`.
- **Android services**: notifications (channels/FCM/TileService), calendar
  (WorkManager), location (`LocationManager` — `LocationGeoHash` caches could
  move), tts, cast (SDK), nests foreground service, foreground/eventCache/
  priority/logging (Choreographer/StrictMode instrumentation), workouts'
  Health Connect half.
- **napplet broker & sandbox** (Messenger IPC, WebView, consent activities),
  `connectedApps/` (the shareable signer/nip46 halves are already in commons;
  what remains is DataStore actuals + activities), `favorites/` registries
  (models already extracted to `commons/favorites`).
- **Flavor sources** `src/play/` + `src/fdroid/` — they exist precisely to
  encode the Play-vs-FOSS split; only interfaces they implement may move.
- **`StringResourceCache`, `SafeImeInsets`, `NwcResponseMessages`** — the
  Android halves of i18n/IME bridges.

## Desktop duplication catalog (the "should" evidence)

| Desktop file | Hand-rolled copy of |
|---|---|
| `cache/DesktopLocalCache.kt` (1,173) | `model/LocalCache.kt` (3,980) |
| `model/DesktopIAccount.kt` (539) | the ~14% of `Account.kt` a second front end needs |
| `subscriptions/FilterBuilders.kt` (742) | ~34 `ui/screen/**/subassemblies/` filter fns |
| `subscriptions/SearchFilterFactory.kt` | `searchCommand/subassemblies/SearchPostsByText` (self-documented port) |
| `subscriptions/{FeedSubscription,ProfileSubscription,FilterDMs,ChessSubscription}.kt` | reqCommand watchers / gift-wrap / profile filters |
| `feeds/DesktopFeedFilters.kt` (404) + `DesktopFeedViewModel.kt` | `ui/screen/**/dal/` feed filters |
| `model/DesktopAccountRelays.kt` (250) | `model/serverList/` + `model/nip01UserMetadata/` relay states |
| `model/{DesktopHiddenUsersState,DesktopDmRelayState,BlossomServers}.kt` | `nip51Lists/HiddenUsersState`, `nip17Dms/*`, `nipB7Blossom/*` |
| `chess/DesktopChessViewModelNew.kt` (203) | `chess/ChessViewModelNew.kt` (215) — line-for-line |
| `service/namecoin/DesktopNamecoinNameService.kt` | `service/namecoin/NamecoinNameService.kt` (says so in its KDoc) |
| `service/images/Desktop{Base64,BlurHash,ThumbHash}Fetcher.kt` | `service/images/` fetchers |
| `network/DesktopHttpClient.kt` | `DualHttpClientManagerForRelays` (minus all interceptors — a live behavior gap) |
| `platform/PlatformColorScheme.kt` | `ui/theme/Theme.kt` palette |
| `ui/relay/*Editor.kt` (7 files) | `relays/*RelayListViewModel` edit logic |
| `ui/notifications/NotificationGroup.kt` | `notifications/` MultiSetCard grouping |
| `ui/thread/` (784) | threadview dal + UI |
| `ui/search/` (1,495) + `search/DesktopRelayUserSearchDelegate.kt` | `SearchBarViewModel` |
| `network/RelayConnectionManager.kt::RelayMetrics` | `speedLogger/` telemetry subset |
| Desktop has **no** reactions/zaps row, **no** crash reporting, **no** PoW persistence | gaps that sharing closes for free |

## Recommended sequence

1. **Wave 0 (mechanical, this branch's follow-ups):** delete the 13+ typealias
   shims; land Batches 1-5 above (~350 files) — no refactoring required.
2. **Wave 1 (three small edits, huge fan-out):** finish `EOSE.kt`;
   `IFeedTopNavFilter` → `ICacheProvider`; add KMP `LruCache` expect.
   Unlocks subassemblies (145 — **landed 2026-09-05**, see the handoff
   below), topNavFeeds (23), caches (5).
3. **Wave 2 (ports):** extract `TopFilter` + `ListBackupStore` from
   `AccountSettings`; move `LocalCache` behind its 3 tiny expects.
   Unlocks nip51Lists, the nipNN state packages, serverList/userMetadata,
   `ui/screen/**/dal/` — and deletes `DesktopLocalCache`.
4. **Wave 3 (strings):** build the Compose-resources `stringRes` twin, then
   migrate strings feature-by-feature with their composables (theme, layouts,
   feed shell first — they need no strings at all and can go in Wave 0).
5. **Wave 4 (decompose the god-objects):** grow `IAccount` and migrate
   `Account`'s six concern groups (relay-set derivation, nip51 state, the
   30-feed-type × 2 field explosion → a `Map<FeedType, FollowListPair>`,
   relay-auth policy, buzz state, the 218 action methods → `commons/actions`)
   until app-side `Account` is only composition wiring. Shrink
   `AccountViewModel` the same way. This is what unblocks the remaining
   ~400 `datasource/` + `ui/note/types/` files.
6. **Wave 5 (deep platform seams):** uploads core (`Uri`→stream + typed
   errors), zap payment handlers, playback policy extraction, post composers.

## jvmAndroid audit: what the migration parked there, and what can promote to commonMain

Everything below landed in `commons/src/jvmAndroid` (or `androidMain`) during
Waves 0-1. For each file: the exact JVM-only API pinning it there, and whether
the repo already has a KMP replacement. Replacements referenced:
`KmpLock`/`withLock` (commons/util), `TimeUtils.nowMillis()` (quartz),
`kotlin.concurrent.atomics` (stdlib KMP, already used in quartz BLE),
`quartz/utils/concurrent/ConcurrentMap` + `ConcurrentSet` (expect/actual),
`RandomInstance` (quartz secure random), `kotlin.time.TimeSource.Monotonic`
(used in quartz RelayProber), okio (KMP), kotlinx-serialization +
`commons/util/JsonTreeUtils`, and the `PlatformImage` expect (android + jvm +
ios actuals in commons/blurhash).

### Tier 1 — promotable to commonMain by moving the file (no code change)

> Executed on this branch: the 7 auth files, `EncryptionKeyCache` and
> `HttpClientEnvironment` now live in commonMain (verified against JVM, iOS
> and `verifyKmpPurity`). `NWCPaymentWatcherSubAssembler` turned out to use
> `NWCPaymentQueryState` from its pinned same-package sibling — reclassified
> to Tier 2.

| File | JVM pin | Note |
|---|---|---|
| `relayClient/auth/` `RelayAuthPermissionCache`, `RelayAuthPermissionLedger`, `RelayAuthSessionGrants`, `InMemoryRelayAuthPermissionStore`, `RelayAuthVenues`, `RelayAuthPurposeDeriver`, `RelayAuthFirstParty` (7 files) | none | Zero JVM imports; no deps on the two pinned siblings. Wave 0b parked the whole group conservatively. |
| `relayClient/nip47WalletConnect/NWCPaymentWatcherSubAssembler` | none directly — but uses `NWCPaymentQueryState`, declared *same-package* in the pinned assembler | Reclassified to Tier 2: moves with `NWCPaymentFilterAssembler`. |
| `service/http/EncryptionKeyCache` | none | androidx.collection LruCache is commonMain-safe. |
| `service/http/HttpClientEnvironment` | none | Plain object; conceptually pairs with the factories but nothing pins it. |

### Tier 2 — promotable with mechanical one-line swaps (replacement exists in-repo)

> Executed on this branch, with a concurrency review per file rather than
> blind swaps. Locks that guarded nothing were removed instead of ported:
> `RelayAuthPromptBus` is now lock-free (`ConcurrentMap.getOrPut` +
> identity-check ownership), and `NappletLaunchRegistry` dropped all three
> `@Synchronized` by replacing its JVM-only access-ordered LinkedHashMap
> with `androidx.collection.LruCache` (internally synchronized,
> access-ordered cap — same semantics). Locks that protect real multi-field
> invariants stayed as `KmpLock` deliberately: `ChatDeliveryTracker` (its
> hot OK path was already lock-free via volatile immutable maps; CAS-ing
> the three coordinated structures would copy maps per write — GC churn for
> zero contention win), `NWCPaymentFilterAssembler` (debounce set + job
> swapped atomically), `NappletNotificationStore` (per-coordinate ordered
> buckets), `DeferredDeleteFileSystem` (pending-set membership must decide
> deletion atomically). `java.util.concurrent` atomics/CHM became stdlib
> `kotlin.concurrent.atomics` + quartz `ConcurrentMap` (which grew
> `putIfAbsent`/`remove(key,value)`/`clear` for the leader-follower caches).
> Extra pins found while executing: speedLogger used `kotlin.concurrent.timer`
> and `::class.java` (the tick now runs on a cancellable coroutine scope —
> the old daemon timer outlived `destroy()`); `OnionLocationCache`,
> `BlossomReadAuthTokenProvider`, `DmRelayDiagnosticsLogger`,
> `NappletNotificationStore` swapped to `TimeUtils.nowMillis()`.
> **Bonus promote:** `service/upload/BlossomAuth` (quartz-only imports).
> **Two reclassified to Tier 4:** `NappletIdentityWatch` (depends on
> `NappletProtocolJson`, pinned by `java.util.Base64`) and
> `NamecoinNameService` (quartz `ElectrumXClient` is jvmAndroid).

| File | JVM pin | KMP replacement |
|---|---|---|
| `relayClient/auth/RelayAuthPromptBus` | `synchronized()` | `KmpLock.withLock` |
| `relayClient/auth/ListWithUniqueSetCache` | `AtomicReference` | `kotlin.concurrent.atomics.AtomicReference` |
| `relayClient/chatDelivery/ChatDeliveryTracker` | `@Volatile` + `synchronized()` ×9 | `KmpLock` + stdlib atomics |
| `relayClient/speedLogger/FrameStat`, `KindGroup` | `AtomicInteger` | `kotlin.concurrent.atomics.AtomicInt` |
| `relayClient/speedLogger/RelaySpeedLogger` | none (blocked by the two above) | moves with them |
| `relayClient/diagnostics/DmRelayDiagnosticsLogger` | `System.currentTimeMillis` | `TimeUtils.nowMillis()` |
| `relayClient/nip47WalletConnect/NWCPaymentFilterAssembler` | `@Volatile` + `synchronized()` | `KmpLock` + atomics |
| `model/nip47WalletConnect/NwcInfoCache` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `marmot/InMemoryMlsGroupStateStore` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `napplet/NappletIdentityWatch` | `ConcurrentHashMap` | quartz `ConcurrentMap` |
| `napplet/NappletLaunchRegistry` | `SecureRandom`, `@Synchronized` | `RandomInstance.bytes()`, `KmpLock` |
| `napplet/NappletNotificationStore` | `ConcurrentHashMap`, `AtomicLong`, `synchronized()`, millis | `ConcurrentMap` + atomics + `KmpLock` + `TimeUtils` |
| `service/namecoin/NamecoinNameService` | `@Volatile` ×1 | atomics (verify the injected resolver ifaces are commonMain) |
| `service/http/BlossomReadAuthTokenProvider` | `ConcurrentHashMap`, millis | `ConcurrentMap` + `TimeUtils.nowMillis()` |
| `service/http/OnionLocationCache` | `ConcurrentHashMap`, `TimeUnit`, millis | `ConcurrentMap` + plain-ms TTL + `TimeUtils` |
| `service/image/DeferredDeleteFileSystem` | `java.io.IOException`, `synchronized()` | already okio-based → `okio.IOException` + `KmpLock` |

### Tier 3 — promotable with a small refactor

| File | JVM pin | Path |
|---|---|---|
| `relayClient/diagnostics/BootRelayDiagnostics` | `kotlin.concurrent.thread`, `ConcurrentHashMap`, atomics, millis | swap thread → coroutine launch; rest is Tier-2 swaps |
| `actions/BuzzInviteMinter` | Jackson `ObjectMapper`, OkHttp call | Jackson → kotlinx-serialization (`JsonTreeUtils` landed in commonMain); inject a `suspend (url, body) -> String` fetch or Ktor client |
| `podcasts/PodcastRemoteContent` | OkHttp call | inject a fetch function or Ktor client |
| `[androidMain]` `Base64Fetcher`, `BlurHashFetcher`, `ThumbHashFetcher` | android `Bitmap` bridge (`toAndroidBitmap`) | coil3 core is KMP and `PlatformImage` has android/jvm/ios actuals — add an expect `PlatformImage → coil3.Image` bridge; deletes desktop's three clone fetchers |

### Tier 4 — blocked by a dependency that must move first

| File | Blocker |
|---|---|
| `scheduledposts/ScheduledPostWorkGate` | `ScheduledPostStore` (pre-existing jvmAndroid: Jackson + `java.io.File`) — store needs kotlinx-serialization + okio first |
| `model/cache/LargeSoftCacheAddressExt` | `LargeSoftCache` (`WeakReference`, `ConcurrentSkipListMap`) — soft/weak-reference caching has no KMP equivalent; a native actual would change eviction semantics. Long-term expect/actual candidate. |
| `model/nip03Timestamp/BitcoinExplorerEndpoint`, `OtsSettings`, `TorAwareOkHttpOtsResolverBuilder` | quartz's own OTS explorer/calendar clients are OkHttp-only (`quartz…nip03Timestamp.okhttp.*`) — quartz needs a KMP (Ktor) OTS transport before these can follow |

### Tier 5 — stays jvmAndroid by design (typed against OkHttp / java.net)

`service/http/`: `IHttpClientManager`, `IRoleBasedHttpClientBuilder`,
`DualHttpClientManager(+ForRelays)`, `OkHttpClientFactory(+ForRelays)`,
`Empty/SingleRoleBasedHttpClientBuilder`, `ProxiedSocketFactory`, and the
interceptor/listener set (`BlossomReadAuth`, `EncryptedBlob`,
`LocalBlossomCacheRedirect`, `OnionLocation`, `OnionUrlRewrite`,
`DefaultContentType`, `Logging`, `DnsInvalidatingEventListener`,
`MediaCallEventListener`, `OkHttpDebugLogging`), plus
`service/image/BlossomReadAuthFetcher` (coil-network-okhttp).

These are the OkHttp engine itself: `okhttp3.Interceptor`/`EventListener`
types, `java.net.Proxy`/`Socket`. The KMP path is a Ktor-client rewrite
(Ktor 3.5.2 is already in the catalog, server-side only today), but quartz's
relay websockets are equally OkHttp-bound on jvmAndroid, so an iOS transport
story has to land in quartz first; rewriting commons alone buys nothing.
Revisit when quartz grows a non-JVM socket/HTTP engine.

**Score:** of the 59 files parked, 11 move with zero code change, 17 with
one-line in-repo swaps, 6 with small refactors, 5 wait on a dependency, and
20 are the OkHttp engine that should stay until quartz has a KMP transport.

## Corrections to `commons/ARCHITECTURE.md` found during the sweep

- §2 is stale: `commons/service` also holds `pow/`, `georelay/`, `broadcast/`;
  several service-ish concerns live as top-level packages
  (`scheduledposts`, `cashu`, `podcasts`, `audio`, `connectedApps`,
  `favorites`, `napplet`, `browser`) — the `service/` vs top-level split
  deserves a stated rule.
- `commons/model/nip02FollowList` (singular) vs app `model/nip02FollowLists`
  (plural) — reconcile on the quartz name when merging.

## Session handoff — state as of 2026-09-01 (PR #4025)

Everything below is the live state for whoever picks this up next; the
sections above are the original audit and stay as reference.

### What is DONE and pushed (branch `claude/amethyst-commons-migration-hm8vgm`)

- **Waves 0 + 1** (~145 files moved to `:commons`, 12 typealias shims
  deleted, importers rewritten repo-wide). See the batch tables above.
- **jvmAndroid promotion Tiers 1–3** (the 5-tier table above, annotated
  per-tier): ~28 more files promoted to commonMain, incl. the diagnostics
  stack (BootRelayDiagnostics, RelaySpeedLogger, DmRelayDiagnosticsLogger),
  the image fetchers (Base64/BlurHash/ThumbHash via the new
  `CoilImageBridge` expect/actual, three Desktop clones deleted), and the
  Tier-2 concurrency review (2 locks removed, 4 kept as `KmpLock` with
  documented invariants; quartz `ConcurrentMap` gained
  `putIfAbsent`/`remove(k,v)`/`clear`).
- **Merged main twice**; second merge brought the #4026 Shorts rename, and
  commit `5cf47f6f` dropped the 30 orphaned `route_video`/`new_short`
  translation entries (15 locales × 2 keys) that were failing
  `:amethyst:lintFdroidBenchmark` on main and every branch. Main is still
  red until that cleanup lands there — cherry-picking `5cf47f6f` fixes it.
- **CI fully green** on head `5cf47f6f`; PR #4025 mergeable_state clean.

### Audit findings (2026-09-01 review) — 5 of 6 FIXED on the branch

Findings 1, 2, 3, 5 and 6 below are fixed and covered where testable
(`TopFilterSerialNameTest` in commons commonTest pins the pre-move serial
names and legacy-JSON decoding on JVM **and** iOS). The one still open:

- **Baseline profile stale (finding 4)** — `baseline-prof.txt` has ~251
  rules naming pre-move classes; regenerating requires the
  `:baselineprofile` macrobenchmark on a device, which this environment
  cannot run. Regenerate before or shortly after release.

Original findings, for reference:

1. **[ship-blocker] `TopFilter` serial names changed** — the move to
   `commons.model.topNavFeeds` changed every subclass's kotlinx default
   serial name; persisted per-tab feed-filter prefs (written by
   `JsonMapper.toJson`, read via `parseTopFilterOrDefault` which swallows
   decode errors) silently reset for every user on upgrade. Fix: add
   `@SerialName("com.vitorpamplona.amethyst.model.TopFilter.…")` (old FQNs)
   to each subclass.
2. **`HttpClientEnvironment.isEmulator` set too late** — `Amethyst.onCreate`
   builds `AppModules` (which eagerly constructs both OkHttp factories and
   their dispatchers) before setting the flag; the emulator branch is dead.
   Set the flag before `AppModules(this)`, or read it lazily.
3. **`@Contextual Address` in `TopFilter` has no iOS serializer** — the
   nativeMain `Address` actual is not `@Serializable` and JsonMapper
   registers no contextual serializer; serializing address-carrying filters
   throws on iOS. Annotate the native actual or register a serializer.
4. **Baseline profile stale** — `baseline-prof.txt` has ~251 rules naming
   pre-move classes; regenerate via `:baselineprofile` (cold-start wins
   regress until then).
5. **`NappletLaunchRegistry` in commons breaches the documented napplet
   sandbox boundary** (CLAUDE.md says the broker-side registry stays in
   `:amethyst` so `:nappletHost` cannot import it). Move it back or update
   the boundary doc + add a guard.
6. **`PlatformImage.toSkiaBitmap()` duplicated** verbatim in
   `CoilImageBridge.jvm.kt` and `.ios.kt` — hoist to a shared Skiko source
   set.

### Batch 2 landed — subassemblies are in `commons/relayClient` (2026-09-05)

The audit's Batch 2 (the `ui/screen/**/subassemblies/` filter functions) was
never executed by the earlier waves; it is done now on branch
`claude/amethyst-commons-migration-ua8ma8`. What moved and where:

- All 161 subassembly files → `commons/relayClient/<feature>/…`, with the
  `subassemblies` path segment dropped to match how Batch 1 landed
  (`relayClient/account/metadata/FilterBasicAccountInfoFromKeys.kt`). Mapping:
  `searchCommand/subassemblies` → `relayClient/search`,
  `chats/publicChannels/datasource/subassemblies` → `relayClient/channel`,
  `…/relayGroup/datasource/subassemblies` → `relayClient/channel/relayGroup`,
  `discover/<nip>/subassemblies` → `relayClient/discover/<nip>`, and
  `<feature>/datasource/subassemblies[/<sub>]` → `relayClient/<feature>[/<sub>]`.
  The misnamed `shorts/…/FilterPollsByAllCommunities.kt` became
  `FilterShortsByAllCommunities.kt` on the way.
- Pure companions the 161 needed, moved alongside: `video/datasource/FeedBasis.kt`
  (the picture/video kind lists) → `relayClient/video`, and
  `relayGroup/datasource/RelayGroupFilterBuilders.kt` (+ its test) →
  `relayClient/channel/relayGroup`. The two `*_PAGE_LIMIT` consts went to
  `relayClient/nsites` / `relayClient/napplets`.
- The three query-state keys the sub-assemblers are typed on now live in commons
  on `IAccount`: `ChannelQueryState` (unchanged shape),
  `SearchQueryState` (carries the search / indexer / follow-plus-mine relay
  sets as `StateFlow`s instead of reaching into `Account`), and
  `VideoQueryState` (carries `listName`, `followsPerRelay` and
  `lastNoteCreatedAtWhenFullyLoaded` flows instead of `Account` +
  `AccountFeedContentStates`). The app constructs them in
  `SearchBarViewModel`, `UserSuggestionState` and
  `VideoFilterAssemblerSubscription`; the `*FilterAssembler` classes stayed in
  the app (they are now import-clean and can follow with the datasource batch).
- `ICacheProvider` grew the seams the six `LocalCache`-coupled files needed:
  `consume(nip19: Entity)` (hint + placeholder seeding for a search query),
  `allRelayGroupChannels()` and `getRelayGroupChannelsOnRelay(relay)`.
  `DesktopLocalCache` implements them (relay groups: empty, desktop has no
  NIP-29 cache yet). `filterByAuthor/Event/Address` and the set-level
  `filterRelayGroupsBy{Authors,MutedAuthors,Follows}` +
  `filterRelayGroupsDiscovery` take `cache: ICacheProvider` as their first
  parameter; the per-relay `filterRelayGroupsByAuthors` lost its `LocalCache`
  default for `cachedChannels` (the test always passed it explicitly).
- The four tests moved to `commons/src/commonTest` on `kotlin.test` (JUnit's
  message-first asserts were reordered).

Not done on purpose: the `SubAssemblyHelper.kt` / `*Filter.kt` dispatchers and
the `*FilterAssembler` / `*Subscription` classes in each `datasource` package —
they are the datasource batch (333 files, 102 of them `Account`/
`AccountViewModel`-coupled) and belong with Wave 4. Desktop's
`subscriptions/FilterBuilders.kt` (742 lines of hand-rolled copies of these
filters) is now deletable in favour of the commons functions — a desktop
follow-up, not part of this batch.

### Top-nav feed datasource layer landed in `commons/relayClient` (2026-09-05)

The family that dispatches to the Batch-2 filters — 26 `XFilterAssembler` +
`XSubAssembler` + `XQueryState` triples, one per top-nav feed (polls,
pictures, longs, badges, nests, podcasts ×2, music ×2, discover, …) — was 26
copies of the same ~90 lines differing only in the `makeXFilter` dispatcher and
which `FeedContentState` floor bounds `since`. Moving the copies would have
relocated the duplication, so they collapsed onto three shared classes in
`commons/relayClient/topNavFeeds/`:

- `TopNavFeedQueryState(account: IAccount, listName, followsPerRelay, scope,
  feeds)` — the one key type for all of them (open; `DiscoveryQueryState`
  adds its seven named tabs, `SoftwareAppsQueryState` adds the blocked-relay
  set).
- `TopNavFeedSubAssembler<K>` — the abstract EOSE manager carrying the shared
  job wiring (list-name watcher, per-relay follows sampled at 500 ms, feed
  floors combined and sampled at 5 s, plus `onListChanged` /
  `extraInvalidators` hooks). `SingleTopNavFeedSubAssembler(client, keys,
  ::makeXFilter, resetEoseOnListChange)` is the concrete one every
  single-dispatcher feed uses; Discovery's three and SoftwareApps' one are
  subclasses.
- `TopNavFeedFilterAssembler<K>` — the `ComposeSubscriptionManager` that owns
  the sub-assemblers. Each feature keeps a one-line
  `commons/relayClient/<feature>/XFilterAssembler.kt` naming its dispatcher,
  so `RelaySubscriptionsCoordinator` and the `*Subscription` composables kept
  their types and names.

The pure `makeXFilter` dispatchers (`SubAssemblyHelper.kt`, `XFilter.kt`,
`FilterBadges.kt`, the seven `discover/<nip>/SubAssemblyHelper.kt`) moved next
to their filters. What stays in the app is exactly the Compose glue: each
`XFilterAssemblerSubscription.kt` now builds the key with
`AccountViewModel.topNavFeedQueryState(listName, followsPerRelay, feed…)`
(`ui/screen/loggedIn/TopNavFeedQueryStates.kt`), reading the `Account` /
`AccountSettings` / `AccountFeedContentStates` fields there.

Deliberate behaviour deltas, all small: every watcher now runs on the key's
(screen) scope — the floor watcher used to run on `account.scope`; the
per-relay-follows sampler is 500 ms everywhere (Discovery tab 2 and Video had
1000 ms); Video gained the list-name watcher the others always had, and its
inline `when` became `makePictureAndVideoFilter`. `VideoQueryState` from the
previous batch is gone in favour of the shared key.

**Not in this batch** — the datasource files with other key shapes:
`RelayGroupsDiscovery*` (reads `LocalCache.live.newEventBundles` and the
relay-group server lists), `home/` (its filters are still app-side and
`HomeOutboxEventsEoseManager` is a different animal), and the non-top-nav
families (profile, thread, chatroom(s), hashtag, geohash, url, relay feed,
community, gitRepo, chess, one/my podcast, connectedApps, profile badges, app
recommendations, nest room, follow-pack feed, onchain zaps, NIP-66 relay
info). Many are import-clean and can follow mechanically; the `Account`-typed
ones wait on Wave 4.

### Import-clean non-top-nav datasource families landed (2026-09-05)

The families whose keys are a `User`, an `AddressableNote`, a string, or an
`IAccount`-compatible account moved wholesale into `commons/relayClient/`:
chess, relay feed, NIP-66 relay info, url, geohash, the channel assembler,
communities, git repo, profile (all 14 files), one/my podcast, onchain zaps,
poll responses, plus the pure `filterEventsInThreadForRoot` /
`filterMissingEventsForThread` functions and `FilterPostsByScopes.kt`
(`CommentKinds`), which those filters and the home feed share. The URL filter
test came along onto `kotlin.test`. Three Subscription composables that never
touched `AccountViewModel` (community, repository, profile) moved too.

The only seam was `LocalCache`, read for `relayHints` and
`checkGetOrCreateUser` — both already on `ICacheProvider` — so the affected
assemblers take `cache: ICacheProvider` as their first constructor parameter,
thread it into their sub-assemblers, and the filter functions that need it
take it as their first parameter. `RelaySubscriptionsCoordinator` passes the
cache it already holds.

Still in the app, with the reason: `hashtag/` (`FilterHashtagLabels` reads
`Account.followsPerRelay`, a per-outbox follow resolution `IAccount` lacks);
`threadview/datasources/` assembler + sub-assemblers and `apps/recommendations`,
`badges/profile`, `napplets/ConnectedApps*` (each reads one relay-list state
off `Account` — `followPlusAllMineWithSearch`, `outboxRelays` /
`defaultGlobalRelays`, `notificationRelays`, `homeRelays`; the
`SearchQueryState` recipe of carrying the flow on the key applies, one small
change each); `followPacks/feed` (dispatches to the app-side home filters);
`chats/*` (DM plumbing: `DmRelayLog`, `GeohashRelays`,
`launchChatFeedToggleObserver`); `nests/NestRoom*`, `relayGroup/*`, `concord/*`
and `home/` (`Account` + `LocalCache` live streams + `AccountViewModel`).

### Single-relay-read families and the home feed landed (2026-09-05, after #4059)

The four families that read one relay-set state off `Account` now carry that
set as a `StateFlow` on their key, the way `SearchQueryState` does:
`ProfileAppRecommendationsQueryState(outboxRelays, defaultGlobalRelays)`,
`ProfileBadgesQueryState(notificationRelays)`,
`ConnectedAppsQueryState(homeRelays)` and `ThreadQueryState(defaultRelays)`.
Thread also takes the cache (`ThreadFilterAssembler(cache, client)`; its two
sub-assemblers run `ThreadAssembler(cache)`). All live under
`commons/relayClient/{apps/recommendations,badges/profile,napplets,thread}`.

The home feed moved whole: the `FilterHomePosts*` dispatchers under
`commons/relayClient/home/<nip>/`, and `HomeOutboxEventsEoseManager` is now a
`TopNavFeedSubAssembler<HomeQueryState>` whose key carries the new-threads and
replies floors plus `enabledHomeFeedTypes`; the Settings › Home toggle is an
`extraInvalidators` entry (still `drop(1)`), and the disabled-kinds stripping
is unchanged. Deltas as for the other feeds: watchers on the screen scope,
follows sampler 500 ms (was 1000). The commented-out alternative
sub-assemblers in `HomeFilterAssembler` were dropped.

What remains in `ui/screen/**/datasource*/` (92 files) is the Compose glue —
one `*Subscription.kt` per feature, which builds the key from
`AccountViewModel` — plus the families that need Wave 4: `hashtag/`
(`Account.followsPerRelay`), `followPacks/feed` (`Account.proxyRelayList`,
`blockedRelayList`, `cache`), `chats/*`, `nests/NestRoom*`, `relayGroup/*`,
`concord/*`, and `wallet`'s and `polls/results`' composables.

### Desktop `subscriptions/FilterBuilders.kt` — investigated, not migrated (2026-09-05)

The audit listed this 742-line file as a hand-rolled copy of ~34
subassemblies that the Batch-2 move would make deletable. That premise is
wrong, and the two sides do not share a model:

- **Desktop** builds plain quartz `Filter`s and broadcasts the same list to a
  whole relay set (`RelayConnectionManager.subscribe` does
  `relays.associateWith { filters }`; relay choice is "all connected/configured
  relays"). No per-relay `since`, no EOSE bookkeeping, no `SubPurpose`.
- **Commons** builds `List<RelayBasedFilter>` — one `ExplainedFilter` per relay
  — from an `IFeedTopNavPerRelayFilterSet` the outbox model derives from
  follow lists + each author's NIP-65 write relays, with `since` per relay
  from the EOSE managers, driven by `ComposeSubscriptionManager`.
- Both end in the same quartz call, `NostrClient.subscribe(subId,
  Map<relay, List<Filter>>)`; the mismatch is one layer up (who picks the
  relay per filter, where `since` lives).

What the file actually is: 27 functions, 6 with no callers at all
(`nip04DmsToUser/FromUser`, `giftWrapsToUser`, `dmRelayList`,
`chessChallengesToUser`, `chessAllEvents`); a 90-line `buildFilter` DSL used
only by its own 525-line test; most of the rest are one-line named-argument
wrappers over the `Filter` constructor with hard-coded kind integers
(`listOf(0)`, `9735`, `30023`, `30064`…). The genuine overlaps with commons are
the search kind groups (`SearchFilterFactory` says "ported from Android
SearchPostsByText"; commons now exports `SearchPostsByTextKinds1..3`), the
chess filters (commons `nip64Chess/subscription/ChessFilterBuilder` has
`challengesFilter`/`userGamesFilter`/`userTaggedFilter`), and
`notificationsForUser`, which already delegates to commons.

Options, for the maintainer to pick:

1. **Adapter, partial match** — teach `SubscriptionConfig` /
   `RelayConnectionManager` to accept `List<RelayBasedFilter>` (group by relay
   into the map) so desktop can call commons functions with a uniform set,
   e.g. `GlobalTopNavPerRelayFilterSet(relays.associateWith {
   GlobalTopNavPerRelayFilter })` or an `AuthorsTopNavPerRelayFilterSet` from
   the follow list. Reuses the shared filter shapes and deletes most of
   `FilterBuilders`; keeps desktop's broadcast relay policy and `since = null`.
   Prerequisite for (2). **Recommended next step.**
2. **Adopt the outbox model, full match** — desktop builds filter sets via
   commons `OutboxLoaderState` (needs the users' NIP-65 lists in its cache) and
   drives subscriptions through the commons EOSE + subscription managers. This
   is the Wave-2/`DesktopLocalCache` direction; a project, not a cleanup.
3. **No model change** — inline the trivial wrappers as `Filter(...)` with
   quartz kind constants, delete the dead functions and the test-only DSL,
   switch search/chess to the commons functions. Removes the file, gains no
   routing parity.

### Next work, in recommended order (needs maintainer go-ahead per item)

- **Tier 4 unlocks** — **done 2026-09-26**, see the last section. (Was: small, mechanical: `NappletProtocolJson`
  `java.util.Base64` → `kotlin.io.encoding.Base64` (frees
  `NappletIdentityWatch`); `ScheduledPostStore` Jackson+`java.io.File` →
  kotlinx-serialization+okio (frees `ScheduledPostWorkGate`).
  `LargeSoftCache` stays parked (needs a WeakReference expect/actual).)
- **Wave 2: LocalCache move-group** — **part A landed (2026-09-19)**, see the
  section below. Part B (repoint `desktopApp`, delete `DesktopLocalCache.kt`)
  is the remaining half.
- **Wave 3: strings bridge** — **BUILT (2026-09-01)**. The pieces:
  - `commons/ui/StringRes.kt` (commonMain): `stringRes(StringResource)`,
    formatted + plural variants, `loadStringRes`/`loadPluralStringRes` for
    non-composable scopes. Thin delegates over compose-resources — no extra
    cache (the library caches parsed locale files process-wide).
  - App-side `ui/StringResourceCache.kt` gained `stringRes(StringResource)`
    overloads delegating to the bridge, so ONE `stringRes` import serves
    mixed files: migrating a key is just `R.string.x` → `Res.string.x`
    (+ the two `commons.resources` imports). No aliasing, no churn.
  - `tools/strings-migrate/migrate.py <keys…>`: moves keys from app res to
    commons composeResources across all 57 locales byte-for-byte (Crowdin
    covers both trees with the same mapping — see crowdin.yml). It refuses
    keys with bare `%s`/`%d` (compose-resources needs positional `%1$s`).
  - Exemplar shipped: `profile_banner` (the ui/layouts cluster's only
    string blocker) migrated + all 7 call-site files repointed.
  - **Bulk migration executed (2026-09-01, commit 7c6a18ee):** all 2,565
    mechanically-safe keys (composable-only call sites, no XML refs, only
    `%N$s`/`%N$d` args, no markup) moved to commons; 568 app files
    repointed. The app keeps 1,866 keys that are genuinely Android-bound:
    ctx-based call sites (adapt to `loadStringRes` to free them),
    `Int`-typed id storage (maps/`when`s over resource ids), `@string/`
    XML references, and exotic format specifiers.
  - **Remaining Wave-3 work:** free the ctx-based keys by adapting call
    sites, then move the now-unblocked composables. Note the layouts cluster ALSO needs theme
    constants (`DividerThickness`, `Size55Modifier`, …) hoisted from app
    `ui/theme/Shape.kt` into `commons/ui/theme/Sizes.kt`, plus
    `painterRes`/`TimeAgo`/`NewItemsBubble` decisions — the audit's
    "strings-only" tally under-counted transitive deps.
- **Wave 4: Account/AccountViewModel** — **re-scoped 2026-09-27** from
  "decomposition" to **moving both to `commons`**. Measured and sequenced in
  [2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md): the
  `Account` group is 77 files / ~23.9k lines inside `model/`, with 5 hard-blocked files
  and 14 exit edges to cut.

### Environment notes for the next session (hard-won)

- Run gradle tests and pushes with `LC_ALL=C.UTF-8 LANG=C.UTF-8` (a POSIX
  locale breaks an em-dash test-report filename).
- One gradle invocation at a time; concurrent runs die on the project lock.
- Pre-push hook runs `:quartz:jvmTest :commons:jvmTest :nestsClient:jvmTest
  :quic:jvmTest :amethyst:testPlayDebugUnitTest :cli:test` (not geode); a
  PreToolUse hook blocks pushes when spotlessApply reformatted files —
  commit first, then push.
- `:commons:compileKotlinIosArm64` is the cheap local gate for iOS breakage
  (CI's test-quartz-ios compiles commons for iosSimulatorArm64). If the
  Kotlin/Native toolchain corrupts (dangling `liblto_plugin.so` symlink),
  delete the extracted dirs under `/root/.konan/dependencies` and let
  gradle re-extract.
- stdlib atomics have no `incrementAndFetch()` here — use `addAndFetch(1)`;
  `withLock {}` can't assign outer `val`s — restructure to lambda-return.


### Wave 2 part A landed — `LocalCache` is in `commons` (2026-09-19)

`LocalCache` (4,021 lines) and its move-group now live in
`commons/src/jvmAndroid/…/commons/model/`. `desktopApp` is untouched and still
runs `DesktopLocalCache`; repointing it and deleting that 1,177-line fork is
part B.

**Where things went**

| From `amethyst/…/model/` | To `commons/…/commons/model/` |
|---|---|
| `LocalCache.kt` | `cache/LocalCache.kt` |
| `AntiSpamFilter.kt` | `cache/AntiSpamFilter.kt` |
| `CachePruner.kt` | `cache/CachePruner.kt` |
| `CacheSearch.kt` | `cache/CacheSearch.kt` |
| `DvmHeartbeatRegistry.kt` | `nip90DVMs/DvmHeartbeatRegistry.kt` |
| `nipBCOnchainZaps/OnchainZapResolver.kt` | `nipBCOnchainZaps/OnchainZapResolver.kt` |

All in `jvmAndroid`: `LocalCache` spills NIP-95 blobs through `java.io.File`,
and the other five either name `LocalCache` or use `java.util.concurrent`.

**The seam: `LocalCacheHost`**

A new port in `commons/…/model/cache/LocalCacheHost.kt` collects everything the
cache needed from `Amethyst.instance` and friends — `scope`, `isDebug`,
`nip95BlobDir`, `relayStats`, `relaySelfPubKey(relay)`, `assertNotMainThread()`.
Every member has a default that is the honest answer for a host that supplies
none of them, so the cache runs shell-less (Desktop, tests). `LocalCache.appHost`
is the install point; Android installs `AmethystLocalCacheHost` from `AppModules`
next to `val cache`, before any event is consumed. Named `appHost`, not `host`,
because `host` is already a domain word in that file (NIP-29 host relay,
gift-wrap host).

The whole seam cut is 101 changed lines in a 4,021-line file:

| Was | Now |
|---|---|
| `Amethyst.instance.nip11Cache.getFromCache(relay).self` | `appHost.relaySelfPubKey(relay)` |
| `Amethyst.instance.applicationIOScope.launch` (×2) | `appHost.scope.launch` |
| `Amethyst.instance.nip95cache` | `appHost.nip95BlobDir` |
| `isDebug` (×2) | `appHost.isDebug` |
| `checkNotInMainThread()` | `appHost.assertNotMainThread()` |
| `ui.note.dateFormatter` | `commons.util.dateFormatter` (the twin already existed) |

`nip95BlobDir` is nullable, so the `FileStorageEvent` path had to grow one
branch: with nowhere to spill to, the note keeps the event whole instead of
loading a content-stripped copy whose bytes are on no disk. Android always
supplies the directory, so its behaviour is unchanged.

**The design question the plan flagged, resolved**

`CachePruner`/`CacheSearch` took `Account`. It turned out `IAccount` did not
need to grow anything and Desktop needed no change:

- `CacheSearch.findUsersStartingWith(…, forAccount: IAccount?)` — `isHidden`
  and `hiddenWordsCase` are already on `IAccount`, and `isFollowing(user)` is
  by definition `user.pubkeyHex in followingKeySet()`, which is too. Hoisting
  the set out of the per-candidate lambda also stops rebuilding it per result.
- `CachePruner.pruneHiddenMessages(account: IAccount)` — `Channel.pruneHiddenMessages`
  already took `IAccount`.
- `CachePruner.pruneHiddenEvents(hidden: LiveHiddenUsers)` — takes the mute list
  by value, the idiom `ICacheProvider.findNotesMatching` already documents.
  `IAccount` exposes hidden users only as hash codes, which would prune authors
  the reader never muted.

**Other pieces**

- `ILocalCache` was declared inline in `LocalCache.kt` and used nowhere else;
  hoisted to `commons/…/model/cache/ILocalCache.kt` (commonMain) as the
  write-side port next to the read-side `ICacheProvider`.
- `AntiSpamFilter`: `android.util.LruCache` → `androidx.collection.LruCache`,
  already the commons idiom. Its `get` is Kotlin-nullable where the platform
  class returned a platform type, so the two duplicate checks now read each
  entry once into a local and smart-cast. It takes its host as `() -> LocalCacheHost`
  rather than a value, because the cache that owns it is built before the shell
  installs one.
- `njumpLink` moved from `ui/note/NoteQuickActionMenu.kt` to
  `commons/…/util/ExternalLinks.kt` (AntiSpamFilter logs one); the app's four
  call sites import it from there.
- `OnchainZapResolver.onchainTipHeightFlow` dropped its
  `runCatching { Amethyst.instance }` fallback — `cache.appHost.scope` always
  answers.

**Left in `amethyst` on purpose:** `MiniFhir.kt`, which the original move-group
listed. It is clean and movable, but `LocalCache` never referenced it (the hit
was the phrase "Resource-usage ledger" in a comment), so it unlocks nothing here
and would only widen the diff. `OnchainWalletState` likewise stays — `Account`
and `RailCapability` use it, `OnchainZapResolver` does not.

**Verified:** `:commons:compileKotlinJvm`, `:amethyst:compileFdroidDebugKotlin`,
`:desktopApp:compileKotlin`, `:cli:compileKotlin`, `:commons:verifyKmpPurity`,
`:commons:jvmTest`, `:cli:test`, `:amethyst:testPlayDebugUnitTest`,
`spotlessApply`.


### Wave 2 part B — compatibility analysis and the road to deleting `DesktopLocalCache` (2026-09-19)

> **Dropped 2026-09-27.** The new desktop app uses `LocalCache` and `Account` directly;
> the current `desktopApp` keeps its fork until it is retired. The analysis below is
> kept for the record.

Part A moved the cache. Part B is retiring the Desktop fork. The naive framing
("repoint ~70 consumers and delete 1,177 lines") is wrong; what follows is the
measured picture.

#### What is already compatible

- **`consume()` coverage is a strict superset.** All 22 kinds Desktop routes
  are handled; the 9 without a dedicated `EventCache` overload
  (`TextNoteEvent`, `ContactListEvent`, `CommentEvent`,
  `AdvertisedRelayListEvent`, `BlossomServersEvent`, `BookmarkListEvent`,
  `OldBookmarkListEvent`, `DmRelayListEvent`, `StarterPackEvent`) fall
  into the generic replaceable/addressable group.
- **13 of 13 core read methods match** by name and signature.
- **Feed retention is already aligned** — both platforms hold feed content
  strongly in the shared `FeedContentState`.
- **The event stream is separable.** Desktop's `newEventBundles` is driven by
  `DesktopRelaySubscriptionsCoordinator`, not by the cache, so Desktop can keep
  owning `DesktopCacheEventStream` and does not inherit `LocalCacheFlow`'s
  1 s `BundledInsert` window.

#### Resolved during part B

- **`notesByAuthor`** — a strong `ConcurrentHashMap<HexKey, MutableSet<Note>>`
  of every note ever consumed, which defeated the `LargeSoftCache`
  (`WeakReference`, despite the name) and made Desktop retain every note for
  the life of the process. It also drove kind-0 metadata invalidation, which
  Android has no counterpart for. Removed: every display-data site now observes
  the author's `User` metadata flow through `Event.rememberDisplayData` (which
  already existed, documented for this, with zero call sites).
- **`object` vs `class`** — `EventCache` is now the class, `LocalCache` the
  process-wide `object` over it. Android's ~350 static call sites were untouched.
- **`findUsersStartingWith(prefix, limit)`** — `EventCache` now overrides the
  port method instead of inheriting its `emptyList()` default.

#### Still to do, and the shape it should take

`DesktopLocalCache` should become a **facade over an owned `EventCache`**, not
a deletion: ~500 lines of genuinely Desktop-specific state survive, and the
~700 lines of `consumeXxx` routing go.

1. **Storage unification first** (safe, mechanical): drop Desktop's `users` /
   `notes` / `addressableNotes` / `liveChatChannels` and delegate to the owned
   `EventCache`. Desktop's own `consumeXxx` methods keep working because they
   go through `getOrCreateNote` / `getOrCreateAddressableNote`. Note the key
   type widens (`addressableNotes<String, _>` → `addressables<Address, _>`);
   the one external reader (`DesktopRelaySubscriptionsCoordinator:599`) ignores
   the key.
2. **Then swap routing kind by kind**, each step green against the 62 tests in
   the 8 `desktop/cache` test classes, ending at
   `cache.checkDeletionAndConsume(event, relay, true)`.
3. **Desktop-only state stays**, moved behind the facade: `localRelayStore`
   write-through, `followedUsers`, `accountPubkey`, `contactListEvents`,
   `metadataVersion`, `followPackVersion` / `liveActivityVersion`, the
   snapshots, `cachedAdvertisedRelayList`, follower/following counts,
   `onProfileMetadataConsumed`, `appScope`, `eventStream`. The mutations are
   concentrated in five places (metadata, contact list, follow pack, live
   activity, `clear`) and re-derive cleanly from the event after consume.
4. **Two Desktop-shaped `consume` overloads do not generalize** — the NIP-47
   `NwcRequestEvent` one takes a `zappedNote` and an `onResponse`
   callback, and the response one drives `paymentTracker` + `appScope`. Android
   reaches the same tracker through account state. Keep them Desktop-side.
5. **`clear()`** has only 2 production call sites (`Main.kt`, logout/account
   switch) plus 2 in tests. `EventCache` deliberately has no `clear()`:
   `DeletionIndex`, `FilterIndex`, `HintIndexer` and `NwcPaymentTracker` have
   no way to reset, so one would be a half-truth. Construct a fresh
   `EventCache` instead — which is what making it a class bought.

**Known behaviour changes to watch for when this lands:** kind-0 currently
returns `false` from Desktop's `route()` and is therefore never written through
to `LocalRelayStore`; `EventCache` returns `true` when the metadata updated, so
profiles would start persisting. And notes outside a loaded feed become
GC-eligible on Desktop, as they already are on Android.

**The test suite is not sufficient for this step.** 62 tests cover the consume
path well, but nothing covers a stale render. Run the desktop app against a
real relay before calling part B done.


### Can `EventCache` go to `commonMain`? — audit (2026-09-21)

Short answer: yes in principle, but it is a **port, not a move**, and the
earlier note in this file ("needs an okio sink … nothing else in the
move-group blocks iOS") was wrong. The file sink is the visible blocker; the
binding one is that the cache's own storage is `jvmAndroid`.

`commons` really does build `iosArm64`/`iosSimulatorArm64`, so this is a real
constraint rather than a hypothetical one.

**What must move with it** (`EventCache` cannot go alone):

| Dependency | Why it is `jvmAndroid` |
|---|---|
| `LargeSoftCache` — `users`, `notes`, `addressables` | `java.lang.ref.WeakReference`, `ConcurrentSkipListMap`, `BiConsumer` |
| `EventListMatchingFilter`, `NoteListMatchingFilter` | `SortedSet`, `ConcurrentHashMap`, `ConcurrentSkipListSet` |
| `MintDirectoryIndex` | `ConcurrentHashMap` |
| `NwcPaymentTracker` | `ConcurrentHashMap`, `AtomicInteger` |
| `DvmHeartbeatRegistry`, `OnchainZapResolver` | `ConcurrentHashMap` |
| `LocalCacheHost` | `java.io.File` |

**Most of it already has an answer in the tree:**

- `commons.util.WeakReference` is already an `expect`/`actual`, and its KDoc
  already anticipates `kotlin.native.ref.WeakReference` for iOS.
- quartz `commonMain` already ships `ConcurrentMap`, `ConcurrentSet`,
  `ConcurrentHashCache` and `LargeCache` — so every `ConcurrentHashMap` /
  `ConcurrentSkipListSet` above is a swap, not a design problem.
- ~~`LargeSoftCache`'s skip-list ordering is not load-bearing~~ — **wrong,
  corrected 2026-09-21.** It is load-bearing, and it is the thing that stops
  this migration. `LargeSoftCache` implements `CacheOperations` (which is
  itself `quartz/jvmAndroid`, not commonMain) for its ranged
  `forEach(from, to, …)`, backed by `ConcurrentSkipListMap.subMap`. The whole
  of `LargeSoftCacheAddressExt` is built on it: `filter(kindStart(kind),
  kindEnd(kind), …)` walks only one kind's slice of the `Address` key space,
  and `filterIntoSet` is called 28 times. On a hash map every one of those
  becomes a full scan of all addressables.
- `AtomicInteger` → `kotlin.concurrent.atomics`; `BiConsumer` → a function
  type; the `dateFormatter` call is one log line and can go.
- `androidx.collection.LruCache` in `AntiSpamFilter` is already KMP —
  `commonMain` uses it in `blurhash/CosineCache` and `relays/EOSE`.

**The two pieces of genuinely new work:**

1. **NIP-95 blob sink.** `EventCache.consume(FileStorageEvent)` writes through
   `java.io.File`/`FileOutputStream`. Needs okio or an `expect` sink behind
   `LocalCacheHost.nip95BlobDir`.
2. **`java.util.SortedSet` in the public API.** `EventCache.filter(Filter)`
   returns one, and both `*ListMatchingFilter` observables take
   `(Filter) -> SortedSet<Note>`. Kotlin has no common `SortedSet`, so this is
   a signature change rippling into `CacheSearch`, both observables and
   `LocalCacheSearchParityTest`.

**The invariant this would break.** `quartz/linuxTest/LargeCacheRangeFallbackTest`
pins the native `LargeCache` range overloads to a deliberate full-scan
fallback, and says why that is acceptable: *"the range overloads have no
callers outside the JVM-only `LargeSoftCache`"*. Promoting `LargeSoftCache` to
`commonMain` makes that statement false. Any move has to either give iOS a
genuinely sorted store or accept — explicitly, not silently — that addressable
lookups there are full scans.

**Revised cost.** This is not the swap described above. It needs either an
`expect`/`actual` `LargeSoftCache` with a hand-written sorted iOS actual (the
`LargeCache` apple actual, for comparison, is 432 lines), or a KMP
sorted-concurrent map that does not exist in the tree or the stdlib — which
would want a `Comparable` bound on `Address` and would benefit quartz too.
Neither is a step; both are projects.

**Recommended order** (bottom-up; each step is independently shippable), *if
the ordering question above is answered first*:

1. `LargeSoftCache` → `commonMain` on the existing `WeakReference` expect plus
   quartz's `ConcurrentMap`. This is the keystone — everything else is behind it.
2. The `ConcurrentHashMap`/`ConcurrentSkipListSet` holders, mechanically.
3. `SortedSet` out of `EventCache.filter`'s signature.
4. The NIP-95 sink.
5. `EventCache` + `LocalCacheHost` themselves.

**Not on part B's critical path.** Desktop is JVM, so retiring
`DesktopLocalCache` needs none of this. The payoff here is an iOS front end
later, not anything queued now.

### Step 1 shipped: `LargeSoftCache` is now `expect`/`actual` (iOS unimplemented)

Decision on the ordering question above: **`expect`/`actual`, with the iOS
actual deliberately missing.** Not a hash-map actual, and not a full sorted
concurrent map yet — the third option, which keeps the invariant honest at the
cost of iOS not being able to construct the cache.

- `model/cache/LargeSoftCache.kt` → `commonMain`, as
  `expect class LargeSoftCache<K : Any, V : Any>() : ICacheOperations<K, V>`.
  Like quartz's `LargeCache`, the expect has to redeclare every member it
  inherits from the interface — an expect class is not abstract, so the
  inherited abstracts are its own to declare.
- `LargeSoftCache.jvmAndroid.kt` is today's implementation unchanged in
  behaviour: `ConcurrentSkipListMap<K, WeakReference<V>>` behind
  `CacheOperations`. The collector defaults and both `forEach(BiConsumer)`
  overloads stay JVM-side; only `size()` needed an `actual override`, the rest
  actualize through inheritance. `java.lang.ref.WeakReference` became the
  commons `WeakReference` expect — the same type on JVM, it is an `actual
  typealias`.
- `LargeSoftCache.ios.kt` **throws on every member.** Kotlin/Native has no
  sorted concurrent map and no weak-valued one; a `HashMap` actual would
  compile and then be wrong in exactly the way `LargeCacheRangeFallbackTest`
  warns about — every bounded scan silently walking every entry. The stub
  keeps `:commons` compiling for iOS and fails loudly if anything there tries
  to construct a cache. Replacing it is now a self-contained task with a
  written-down contract (key-sorted, weak values) rather than a blocker on the
  rest of the migration.
- `LargeSoftCacheAddressExt.kt` moved to `commonMain` with it — its ranged
  `filter`/`filterIntoSet`/`mapNotNullIntoSet` calls are all `ICacheOperations`
  members, so they need no platform code.

Verified: `:commons:compileCommonMainKotlinMetadata`,
`:commons:compileIosMainKotlinMetadata` (this runs on Linux and *does* enforce
expect/actual matching — checked by breaking a member name on purpose and
watching it fail), `:commons:verifyKmpPurity`, `:commons:jvmTest`,
`:desktopApp:test`, `:cli:test`, `:amethyst:compileFdroidDebugKotlin`,
`LargeCacheAddressableFilterTest`, `spotlessCheck`.

Steps 2–5 are unchanged and still ahead; the iOS actual is now their peer, not
their gate.

### Step 2 shipped: the `ConcurrentHashMap` holders

Three of the six holders in the audit table above are now `commonMain`, each a
straight swap onto a KMP primitive that already existed:

- `MintDirectoryIndex` — `ConcurrentHashMap<String, Int>` → quartz
  `ConcurrentMap`. `counts.merge(key, 1, Int::plus)` becomes
  `merge(key, 1) { old, new -> old + new }`, which is atomic on every target,
  so a count still cannot be lost to a racing `add`. `suggest()` now ranks over
  `snapshot()` — a copy the comparator cannot see shift underneath it.
- `DvmHeartbeatRegistry` — `ConcurrentMap.getOrPut` is the same atomic
  get-or-create the `ConcurrentHashMap` version relied on, so two threads
  racing the first beat for an address still agree on one `MutableStateFlow`.
- `NwcPaymentTracker` — `ConcurrentMap` plus `kotlin.concurrent.atomics.AtomicInt`
  for `spoofAttempts` (`incrementAndGet()` → `fetchAndIncrement()`, `get()` →
  `load()`, under `@OptIn(ExperimentalAtomicApi::class)` as quartz already does
  in `BleChunkAssembler` and `BanStore`). `containsKey` has no `ConcurrentMap`
  equivalent and became a null check on `get`, which is the same question for a
  map that never stores nulls. Its test moved to `commonTest`, so the tracker's
  4 race cases now also run on iOS rather than JVM only.

`OnchainZapResolver` cannot move yet — it reads `EventCache` — but its two
`ConcurrentHashMap.newKeySet()` in-flight gates are now the commons
`ConcurrentSet`, which is exactly `newKeySet()` on JVM/Android. That leaves it
with no `java.*` import, so it travels with `EventCache` in step 5 for free.

`MintDirectoryIndexTest` stayed in `jvmTest`: it is written against
`org.junit.Assert`, and converting it to `kotlin.test` is unrelated churn.

Remaining in the table: `EventListMatchingFilter` / `NoteListMatchingFilter`,
which also carry `SortedSet` — they belong to step 3, not this one.

Verified: `:commons:compileCommonMainKotlinMetadata`,
`:commons:compileIosMainKotlinMetadata`, `:commons:jvmTest` (incl. the moved
`NwcPaymentTrackerTest`, 4 tests), `:desktopApp:test`, `:cli:test`,
`:amethyst:compileFdroidDebugKotlin`, `DvmHeartbeatTest`, `spotlessCheck`.

### Steps 3–5 shipped: the cache group is `commonMain`

`commons/src/jvmAndroid/…/model/cache/` now holds exactly one file, the
`LargeSoftCache` actual. Everything else — `EventCache` (4k lines),
`LocalCache`, `LocalCacheHost`, `AntiSpamFilter`, `CachePruner`, `CacheSearch`,
`OnchainZapResolver` — is shared.

**The NIP-95 sink.** `nip95BlobDir: File?` became `nip95Blobs: Nip95BlobStore?`,
because a directory is not what the cache needs — somewhere to put bytes and a
way to ask whether they are there already is. `FileSystemNip95BlobStore` is the
okio-backed one, and okio was already a commons dependency. Its constructor
takes a plain path string so Android needs no okio of its own, and
`platformFileSystem` is a two-line expect/actual because okio declares
`FileSystem.SYSTEM` per platform.

Two bugs surfaced while rewriting that block. `decode()` returns `ByteArray?`
and went straight into `FileOutputStream.write` — a platform type, so a null
would have thrown past the `IOException` catch. And the note's copy dropped its
content whenever a directory existed, *including* right after a failed write,
losing the only copy of those bytes; content is now dropped only when the blob
really is stored.

**`SortedSet`.** `EventCache.filter` returns a `List<Note>` in the comparator's
order, newest first. That order is load-bearing (the napplet gateway answers
REQs from it). The comparator also defines uniqueness by reference, so the
sorted set was collapsing the duplicate references a filter with a repeated
kind produces; `toSet()` before sorting keeps exactly that, since `Note`
declares no `equals()`.

**The observables.** `NoteListMatchingFilter` / `EventListMatchingFilter` are
now `expect`/`actual` with today's implementation untouched as the jvmAndroid
actual and a throwing iOS stub. Their concurrency — every sorted-set write
inside that key's `ConcurrentHashMap.compute` critical section — is not
reassemblable from quartz's KMP primitives: `getOrPut` covers `new()`, but
`remove()` needs the sorted-set removal *inside* the section, or a concurrent
re-add inserts a comparator-equal entry that the later removal takes out
instead, dropping the note for good. A copy-on-write `compute` cannot stand in
either, since its CAS retry may run a side-effecting lambda twice.

**What the move itself turned up**, none of it visible to an import grep:
`@Synchronized` / `@Volatile` / `synchronized {}` (→ `KmpLock`,
`kotlin.concurrent.Volatile`), `System.nanoTime` (→ `TimeSource.Monotonic`),
`HashMap.merge` (→ a local `mergeMax`), `Dispatchers.IO` (just needs
`import kotlinx.coroutines.IO`), the one `dateFormatter` log line (now logs the
raw `created_at`), and `LnurlEndpointCache` — a quartz **jvmAndroid** singleton,
reached through a new `LocalCacheHost.lnurlEndpoint` port whose default `null`
means "cold cache", which is what a miss already meant.

**Still true, and worth repeating:** commonMain is not iOS support. iOS now
compiles the cache, and the cache's store and its two observables throw there.
The remaining gap is three named files, not a module boundary.

Verified: `:commons:compileCommonMainKotlinMetadata`,
`:commons:compileIosMainKotlinMetadata`, `:commons:verifyKmpPurity`,
`:commons:jvmTest`, `:desktopApp:test`, `:cli:test`,
`:amethyst:compileFdroidDebugKotlin`, `:amethyst:testPlayDebugUnitTest`,
`spotlessCheck`.

## Step 8 — measuring the copy-on-write observables (the "is it actually faster?" question)

The rewrite of `NoteListMatchingFilter` / `EventListMatchingFilter` from
`ConcurrentSkipListSet` + `ConcurrentHashMap` to copy-on-write over an
`AtomicReference` was made for **portability** — `java.util.concurrent` has no
KMP equivalent, and that is what kept these two in `jvmAndroid` with an iOS
stub. That argument says nothing about speed, and these sit on a hot path:
every consumed event is offered to each observer whose filter could match it,
from each relay's socket coroutine. A few hundred events a second across a
dozen relays reaches this code thousands of times a second.

So it was measured rather than argued. `ObserverListBenchmark`
(`commons/src/jvmTest/.../prodbench/`) keeps the skip-list implementation
verbatim as the baseline and as a **differential oracle**: `bothImplementations
Agree` asserts the two emit identical lists for identical input at limits
`null` / 50 / 400, which is the property the rewrite had to preserve. It runs
in ~7s and asserts only on correctness, never on wall time.

**What the first run found:** the copy-on-write version was *slower* on the
limited-filter insert path and **4.9x slower on 8 concurrent threads**, with
zero thread scaling. The cause was not the design but one line —
`State.plus` published `ids + added` and, over the limit, `ids + added -
dropped`. Each operator allocates a full copy of the set, so the eviction path
rebuilt the membership set **twice per insert**. Replacing both with a single
`HashSet` copy (sized up front, mutated, then published) is the whole of the
fix.

**After that fix**, against the implementation it replaced:

| shape | result |
|---|---|
| re-deliver an already listed note (steady state once a screen is warm) | **3.3–4.1x faster** |
| insert into a populated *unlimited* list, n = 100 / 1000 | **1.3–1.5x faster**, widening with n |
| cold fill, n = 5000 | **1.8x faster** |
| concurrent inserts into the same observer, 1 thread | parity |
| concurrent inserts into the same observer, 4 / 8 threads | **2.1–2.7x slower** |

The last row is the real cost and is documented on both classes rather than
buried here: threads serialize on one reference and a lost CAS discards its
copy, where the skip list striped across keys and scaled with thread count. Two
things bound it. The benchmark's threads do nothing but insert, while real
ingest spends most of its per-event budget on signature verification and
parsing before reaching an observer, so the contention window is a fraction of
the measured one. And nearly every production observer registers with **no
`limit`** — grep the `observeNotes` / `observeEvents` call sites — which is the
shape copy-on-write wins.

Worth revisiting if a profile ever disagrees. The lever would be the emit, not
the lock: both implementations already materialize the whole list on every
write, so an observer that only ever appends is paying O(n) to tell the UI
about one new row.

**Also worth recording:** "lock-free" was never the differentiator between the
two. The skip-list version was lock-free too — `ConcurrentHashMap` stripes per
key, so `compute` holds one bin, not a monitor. The choice was portability and
speed, not locking.

Verified: `:commons:jvmTest`, `:commons:compileIosMainKotlinMetadata`,
`:commons:spotlessApply`.

## Step 9 — the weak note cache has no strong referent in tests

`compose-ui-test` went red on `DesktopCachePipelineTest`:
`FollowingFeedFilter only includes notes from followed users`, `expected:<1> but
was:<0>`, and it would not reproduce on a dev machine.

The cause is this branch, indirectly. `DesktopLocalCache` kept a
`notesByAuthor: ConcurrentHashMap<HexKey, MutableSet<Note>>` index for metadata
invalidation — an unbounded strong map holding every note the Desktop app ever
saw, which quietly defeated the point of storing them in a `LargeSoftCache`.
Removing it was right (the Android cache never had one). But it was also the
only thing keeping the test fixtures alive: every test consumes events and then
queries the cache for the notes they produced, with nothing in between holding a
reference. A GC landing in that window empties the cache.

It reproduces deterministically with two `System.gc()` calls before the query,
and it is not specific to that one test — all 46 consume sites have the shape,
so CI's tighter heap just picked the victim. The fix routes every consume
through an `ingest` helper that pins what the cache built for the lifetime of
the test instance, the way a screen holds the notes it is showing in the app,
and keeps the forced GC in the test that failed so the contract is asserted
rather than left to the heap.

## 2026-09-26 — Tier 4 unlocks, the strings drift, and the UI stragglers

### Tier 4: two jvmAndroid groups promoted to `commonMain`

- **Napplet wire protocol.** `NappletProtocolJson`, `NappletIdentityWatch` and
  `NappletRequestRouter` moved to `commons/commonMain/…/napplet`. The only
  pins were `java.util.Base64` and one `System.currentTimeMillis()`. Base64 is
  now `kotlin.io.encoding.Base64`, decoding with `PaddingOption.PRESENT_OPTIONAL`
  because `java.util.Base64.getDecoder()` accepted unpadded input and applets
  may send it. The clock is `TimeUtils.now()`.
- **Scheduled posts.** `ScheduledPostStore` had already dropped Jackson for
  kotlinx-serialization; what pinned it was `java.io.File` and a POSIX
  `chmod`. It now takes an okio `Path` plus a `FileSystem` (default
  `platformFileSystem`), and a `String` constructor so callers need no okio,
  the same shape as `FileSystemNip95BlobStore`. The owner-only permission is a
  new `expect fun restrictFileToOwner(path, tag)`:
  `Files.setPosixFilePermissions` on jvmAndroid, POSIX `chmod(0600)` on iOS.
  The temp-file swap is `FileSystem.atomicMove`, which replaces an existing
  target on every platform, so the old "rename, else delete and rename again"
  fallback went with it. `ScheduledPostWorkGate`, `ScheduledPostPublisher` and
  `ScheduledPostNotifier` came along; none had a JVM pin of its own.

### The strings drift, fixed

Between the 2026-09-22 move and today, the cordn UI and the backup-conflict
review added **447 keys** to `amethyst/src/main/res` (`cordn_*`, `backup_*`),
taking it from 190 keys back to 597. All 447 moved with
`tools/strings-migrate/migrate.py` (1,131 elements over 57 locales) and the 18
files that read them were repointed. The non-mechanical sites:

- `stringRes(context, …)` inside `suspend` senders (`sendAttachment`,
  `sendVoiceNote`) → `loadStringRes(…)`. `adminFailureText` became `suspend`;
  its only caller is already in `scope.launch`.
- The backup review kept its labels as `@StringRes Int` (`DiffGroup.label`,
  `ReviewRow.*Res`, `eventTypeName()`, the `countTiles`/`moneyHero`/`laneItems`
  params) → `StringResource` / `PluralStringResource`.
- `rememberPresentation` resolved labels inside `remember { }` through
  `LocalResources`. Compose resources have no non-suspend accessor, so the 16
  labels `presentationOf` writes into item text are resolved in composition into
  a map, and the map is the `remember` key.
- `androidx.compose.ui.res.pluralStringResource` → the compose-resources one.

Six moved keys have no reader today (`cordn_backup_restore_title`,
`cordn_coordinators_relays`, `cordn_create_admin_only_me`,
`cordn_create_coordinator`, `cordn_send`, `cordn_voice_play`). They moved
rather than being deleted; dropping them is the feature owner's call.
`pow_notification_sending` stays in Android `res/`: the PoW foreground service
reads it synchronously.

The rule is now written down in `.claude/CLAUDE.md` ("Strings"), in
`commonsUI/ARCHITECTURE.md`, and in the `android-expert` and
`find-missing-translations` skills: new strings go in `commonsUI`
composeResources, even for Android-only screens.

### UI stragglers moved to `commonsUI`

A survey against `main` found that most of "Batch 4" had already landed in
other PRs (`dde228d2`, `1a563cf2`, `8dd409cb`, `1f1b6dab`): the theme
constants (in `commonsUI/…/theme/Shape.kt`, not the `Sizes.kt` this plan
named), the feed shell, the top bars, `M3ActionDialog`, `NoteComposeLayout`,
`RepostLayout` and the toast queue. This round moved what was left that had no
real blocker:

| Moved | To | Note |
|---|---|---|
| `NewItemsBubble` (from `ChatroomHeaderCompose.kt`) | `commonsUI/…/ui/components` | |
| `ChatHeaderLayout`, `LeftPictureLayout` | `commonsUI/…/ui/layouts` | Their `@Preview`s use app drawables, `TimeAgo` and `TextCount`, so the previews stay app-side in `*Preview.kt` files |
| `listItem/SlimListItemLayout` | `commonsUI/…/ui/layouts/listItem` | Same preview split; `@VisibleForTesting` dropped (no androidx.annotation in commonMain) |
| `WatchScrollToTop` (list/grid/pager overloads) | `commonsUI/…/ui/feeds` | The `CardFeedContentState` overload stays in the app |
| app `StickToTopOnPrepend` | deleted | It was a copy of the commons one. The commons copy now collects with `collectAsStateWithLifecycle`, as the app copy did |
| `ChannelFeedState`, `ChannelFeedContentState` | `commons/commonMain/…/feeds` | `checkNotInMainThread()` → `LocalCache.appHost.assertNotMainThread()`, which Android wires to the same check |
| `ClickableBuzzInviteLink`, `ClickableConcordInviteLink`, `ClickableRelayGroupLink`, `ClickableRelayUrl`, `OutlinedThinPaddingTextField` | `commonsUI/…/ui/components` | |
| app `ClickableEmail`, `ClickablePhone` | deleted | Android now uses the commons ones (`mailto:` / `tel:` through `LocalUriHandler`) instead of `ACTION_SEND` / `ACTION_DIAL` intents. The commons `ClickablePhone` is new. Desktop still renders phone numbers as plain text, on purpose |

**Still app-side, with the reason:**

- `AmethystClickableIcon`: 9 lines around an Android debug action; moving it
  only pushes `LocalContext` into its two callers.
- `ScreenLayout`: needs `LocalConfiguration` → `LocalWindowInfo`, and the
  window-size-class breakpoints inlined.
- `SlidingCarousel`: needs `animatedViewerChromeInset` hoisted out of the
  viewer-chrome file.
- `AudioWaveformReadOnly`: needs two enums from the Android-only waveform
  library replaced.
- `FileAttachmentCard`: needs `extractFilename` hoisted.
- `PdfFetcher`: needs the disk cache injected.
- `ClickablePhone` / `ClickableEmail` on Desktop: deliberately unchanged.
- **`AmethystTheme` split** (the pure scheme/typography half to commonsUI; the
  `Amethyst.instance` overload and the system-bar `SideEffect` stay).
- **`TimeAgo` split**: the labels, `NowProvider` and the style enum are pure;
  the absolute and short formatters need expect/actual over
  `android.text.format`.
- **Palette unification**: Desktop's `PlatformColorScheme` and the app palette
  differ in primary, secondary, surfaces and outlines, and every shared token
  (`placeholderText`, `chatBubbleThem`, …) is frozen from the app palette. This
  is a design decision, not a move.

### Audit of the 2026-09-26 round

The four commits were reviewed file by file against the originals. They had no
functional bugs. The follow-ups:

- **Compose resources keep XML whitespace; Android didn't.** This is older than this
  round (it dates back to the first string moves), and it is the one real
  regression. aapt collapses every whitespace run in an unquoted value and trims
  the ends; Compose draws the XML text verbatim. So the values wrapped over
  indented lines (`account_backup_tips2_md`, `push_server_install_app_description`,
  `couldnt_find_nwc_wallets_description`, the chat explainers, and 349 translated
  values) rendered with a leading line break and eight spaces. In the `_md` strings
  CommonMark turns that into a code block. Crowdin also exports a translator's stray
  edge space, which aapt used to drop, for example ` miejsca zniknęły` and Hindi's
  ` दि॰` time suffix. `fix_escapes.py` now applies aapt's rule to runs holding a line
  break or tab. In a translation it also trims an edge space its source string lacks,
  which keeps deliberate ones like `" and "`. The Crowdin workflow already runs it
  after every sync, and `compose_escaping_check.py` now fails on raw line breaks, so
  a sync can't bring it back. 598 values across 54 files were repaired.
- `rememberPresentation` read all 16 backup labels on every recomposition, even for
  diff types that write none. It now reads only the ones its diff type uses, and
  `BackupPresentationLabelsTest` fails if `presentationOf` asks for a label that list
  lacks.
- `ScheduledPostStore`: a failed stat now counts as "no file" rather than throwing
  (okio's posix metadata throws on EACCES where `File.exists()` returned false).
  Symlinked store files still count as present. The chmod is skipped for an injected
  non-system `FileSystem`, and the cleanup log keeps its throwable.
- `ClickableEmail` percent-encodes `%` in the `mailto:` URI (RFC 6068).
- Left as-is, by design: Kotlin's Base64 rejects non-zero pad bits (`"SGl="`) that
  Java ignored, which no browser encoder produces. `%1$d` arguments now render ASCII
  digits in every locale, the same as every other migrated string.
  `ChannelFeedContentState` reaches `LocalCache.appHost` for its main-thread check.

## 2026-09-27 — the rest of the single-blocker UI, `TimeAgo`, and the theme

### Moved

| What | To | The blocker, and how it went |
|---|---|---|
| `ScreenLayout` tier logic, `CappedScreenContent`, the pane widths | `commonsUI/…/ui/layouts/ScreenLayout.kt` | `material3-window-size-class` (app-only): its width breakpoints (Medium ≥ 600dp, Expanded ≥ 840dp) are inlined, and `ScreenLayoutTest` (18 cases, now in commonsUI jvmTest) passes against them. `LocalConfiguration` stays in the app, which asks the shared `rememberScreenLayoutSpec(widthDp, heightDp)`, so Desktop can supply its own window size. |
| `animatedViewerChromeInset`, `rememberViewerControlsVisibility` | `commonsUI/…/ui/components/ViewerChrome.kt` | They sat in a file full of Android window code. Only `ImmersiveSystemBarsEffect` (Window/insets controller) stays. |
| `SlidingCarousel` | `commonsUI/…/ui/components` | needed the inset above |
| `AudioWaveformReadOnly` | `commonsUI/…/ui/components` | two enums from the Android-only audiowaveform library, now local enums with the same values |
| `FileAttachmentCard` (+ `FileAttachmentRow`, now public) | `commonsUI/…/ui/components` | `extractFilename` hoisted to `commons/…/util/MimeTypeLabels.kt` |
| `PdfFetcher` | `commonsUI/src/jvmAndroid/…/service/pdf` | `Amethyst.instance.diskCache` → a `diskCache` parameter. commonsUI jvmAndroid gained `okhttp-coroutines` (already on every module that ships OkHttp). |
| `TimeAgoFormatter` (`timeAgo*`, `timeAbsolute*`, `dateFormatter`, `lastSeenSentence`, `timeAgoShort`, `TimeAgoLabels`) | `commonsUI/…/ui/note/TimeAgoFormatter.kt` | `android.text.format` — see below |
| `ToggleableTimeAgoText`, `TimeAgo`, `NormalTimeAgo`, `TimeAgoStyle`, `NowProvider`, `LocalNowSeconds` | `commonsUI/…/ui/note/elements` | followed the formatter |
| `AmethystTheme`'s scheme, typography and providers | `commonsUI/…/ui/theme/AmethystTheme.kt` (`AmethystMaterialTheme`, `amethystDark/LightColors`, `isDarkTheme`, `previewColor`, `toFontFamily`) | The app's `AmethystTheme` resolves the prefs, calls it, then tints the system bars. The Vico chart colours stay app-side (Vico is Android-only here). |

**The date-format seam.** `PlatformDateFormat.kt` (commonsUI) has four expects:

- `DateSkeletonFormatter(skeleton)`:
  - Android: `getBestDateTimePattern` in a `ThreadLocal` `SimpleDateFormat`, exactly the old code.
  - JVM: `DateTimeFormatter.ofLocalizedPattern` (JDK 19+, the same CLDR skeleton lookup), cached per locale and zone.
  - iOS: `NSDateFormatter.setLocalizedDateFormatFromTemplate`.
- `calendarYearAndDay`: `Calendar` on jvmAndroid, `NSCalendar` on iOS.
- `rememberTimeOfDayFormatter`:
  - Android: `DateFormat.getTimeFormat(context)` per call, as before, so the system 12/24-hour setting is still followed.
  - JVM: the locale's SHORT time.
  - iOS: `NSDateFormatterShortStyle`.
- `relativeTimeSpanShort`:
  - Android: `DateUtils`, as before.
  - Elsewhere: the compact "5m" form.

`DateSkeletonFormatterTest` pins the JVM side: en-US vs en-GB order from the same instance after a locale switch, the three skeletons, day/year boundaries, and the same-day branch of `timeAbsoluteWith`.

Desktop still has its own `ToggleableTimeAgoText` and the older `commons/…/util/TimeAgoFormatter.kt` (hard-coded English units, `DateFormat.MEDIUM`). Merging those onto this one is the Desktop phase.

### What is left in `amethyst/ui`, measured (2026-09-27)

A transitive-blocker sweep of the 1,385 files under `amethyst/…/ui/`, after this round:

- **80 files have no blocker left.**
  - Many are headless and belong in `commons`, not `commonsUI`: the filter assemblers and
    `*LastRead`, `NewMessageTagger`, `SplitConversor`, `PubKeyFormatter`, `SettingsCatalog`,
    and the Tor status/dialog VM.
  - The Compose ones include the chat bubble set (`ChatBubbleLayout`, `ChatGroupPosition`,
    `JumboEmoji`, `NewDateOrSubjectDivisor`, `AutoScrollToNewest`). It moved to `commonsUI`
    in the follow-up below. Desktop's `ui/chats/ChatBubbleLayout.kt` is an older fork of it.
- **`AccountViewModel` is the wall:**
  - 892 files touch it, and 310 touch nothing else app-side. Yet swapping it for an interface frees only 76 files by itself, because the rest call hub composables that are blocked themselves.
  - The ui files use 210 distinct members of it. The note renderers use 28.
  - Only ~10 note renderers become movable with a context interface alone.
- **The real levers are about ten hub composables**, each blocking the ui files that call it:

  | Hub | Files blocked |
  |---|---|
  | `UserProfilePicture` | 123 |
  | `RouteMaker` | 89 |
  | `UsernameDisplay` | 77 |
  | `Loaders` | 66 |
  | `DisappearingScaffold` | 64 (AVM only, 53 lines) |
  | `RichTextViewer` | 62 |
  | `NoteCompose` | 58 |
  | `FeedContentStateView` / `FeedView` | AVM only |

  Also on the list:
  - the `reqCommand` `observe*` helpers (176 files; they take `accountViewModel` themselves);
  - the flavour-only `TranslatableRichTextViewer` (54 files), which wants a slot or a CompositionLocal.
- **Two corrections to the MOVE-AFTER table above:**
  - `INav`/`Route` are no longer blockers: every ui file imports the commons ones.
  - `ui/note/types` is 111 files, not ~89, and "AVM threading" understates it: the hubs matter more than the parameter.

### Follow-up in the same round: the chat bubble group, and the audit

- **Moved to `commonsUI/…/commons/chats/ui`**, beside `ChatDivisor` and
  `UserDisplayNameLayout`: `ChatBubbleLayout`, `ChatGroupPosition`, `JumboEmoji`,
  `NewDateOrSubjectDivisor` and `AutoScrollToNewest`. `AutoScrollToNewest` and
  `CHAT_GROUP_WINDOW_SECONDS` went from `internal` to public so the app can reach them.
  Desktop's `ui/chats/ChatBubbleLayout.kt` is an older fork of this one (no group
  position, jumbo emoji, swipe-to-reply or reaction row). Replacing it is the Desktop
  phase.
- **Audit follow-ups:**
  - `PdfFetcher` takes the disk cache as a provider, read on the IO dispatcher, so a PDF
    card composing on a cold start doesn't build the app's lazy cache on the main thread.
  - The two DM lists resolve `TimeAgoLabels` once per list instead of once per row.
  - The iOS `DateSkeletonFormatter` rebuilds on a locale change, as Android's does. (The
    JVM one also keys on the time zone; iOS caches its system zone until reset, so keying on
    it there would cost lookups and still not notice.)
  - `ScreenLayoutTest` pins the inclusive 600dp boundary.
  - Two stale KDoc links and a same-package import are fixed.
  - The generated baseline profile has its stable-name entries repointed:
    `NowProviderKt`, and the five theme functions now in `AmethystThemeKt`. Its R8
    lambda entries were already stale before this round (it still lists
    `MarkDownStyleOnDark` under `ThemeKt`), and the new theme root
    (`AmethystThemeKt;->AmethystMaterialTheme`, `isDarkTheme`) has no entries at all, so
    **regenerate the profile** after these moves.

### Second audit, and one Desktop decision

- **Fixed:**
  - `PdfPreviewCard` closed its cache snapshot on the main thread, after `withContext(IO)`
    returned. That was older than this branch. Closing takes the same global DiskLruCache
    lock as opening, so `PdfFetcher.useSnapshot` now fetches, runs the block and closes, all
    in one IO block, leaving no suspension point for cancellation to leak through.
  - The PDF viewer's `onDispose` now closes its handle on the app IO scope, under the
    render mutex.
  - JVM and iOS built a time-of-day formatter per feed item; they now share one cached
    instance. `NSDateFormatter` is costly to build.
  - `timeAgoShort` no longer allocates an unused fallback lambda per tick on Android.
  - The iOS skeleton formatter keys on the locale only, like Android. iOS caches its system
    zone until reset, so keying on the zone cost lookups and still never saw a change.
  - New tests: `JumboEmojiTest` (counts, ZWJ/skin tone/flag/keycap sequences, bubble
    shapes). `ScreenLayoutTest` moved to commonsUI jvmTest, beside the code it tests.
- **Desktop needs `jdk.localedata` before it uses these formatters.**
  - `desktopApp/build.gradle.kts` `nativeDistributions.modules(...)` doesn't include it,
    so the packaged runtime carries only en/root CLDR data.
  - `DateTimeFormatter.ofLocalizedPattern` then gives en-GB `Jan 5, 2024` and de-DE
    `2024 Jan 5`, and the JDK tests (full runtime) can't see it.
  - Desktop's existing `java.time` formatting (`DesktopScheduleAtPicker`) already has the
    same gap.
  - Adding the module costs about +28 MB to the unpacked runtime. That is a packaging
    call, left for the Desktop phase.

## 2026-09-27 (later) — the unblocked `ui/` files, and one chess view model

### How the list was rebuilt

The morning's "80 files have no blocker" count was not saved, so it was recomputed
with a script (not committed; it is a one-off) that:

- indexes every public top-level declaration in `amethyst/src/{main,play,fdroid}`;
- follows each file's imports **and** its same-package references (the
  under-count this plan's header warns about);
- marks a file blocked by `android.*`, a non-KMP `androidx.*`, `R.`/`BuildConfig`,
  or a library the shared modules lack;
- takes the fixpoint, then assigns each survivor a module and source set from what it
  (transitively) uses: Compose UI, `Res` or a `commonsUI` symbol means `commonsUI`;
  `java.*`/JVM tokens or a jvmAndroid-only `commons` symbol means `jvmAndroid`.

It found **96** such files under `ui/` (142 app-wide). 68 of the `ui/` ones moved
(plus `service/uploads/SuspendableConfirmation`, which one of them needs), 3 were app
copies of existing `commons` code and were deleted, and 25 stay, for the reasons below.

### Moved

**To `commons`**

| Package | Files |
|---|---|
| `model` | `ConcordLastRead`, `MarmotGroupLastRead`, `RelayGroupLastRead` (beside `privateChatLastReadRoute`) |
| `model/chats` | `ConcordServerRoomNote`, `RelayGroupServerRoomNote`, `HistoryDateFormat` (jvmAndroid) |
| `model/composer` | `NewMessageTagger`, `DraftTagState`, `IExpiration`, `SplitConversor`, `PreviewState` (jvmAndroid) |
| `model/navigation` | `RouteTextArgs`, `ShareToDMRouteRewriter` |
| `model/buzz` | `AgentWork` |
| `model/nip29RelayGroups` | `GroupDiscoveryConstraintResolver` (was `dal/GroupDiscoveryConstraint.kt`; renamed because the matcher it resolves to already owns that file name here) |
| `model/nip47WalletConnect` | `TransactionRowLabels` |
| `model/nip72Communities` | `CommunityRulesLookup` |
| `model/nip92IMeta` | `ImetaContent` |
| `relayClient/chatrooms` (new) | the five `Filter*` functions of the chatroom-list assembler |
| `relayClient/channel/relayGroup` | `RelayGroupOpenThreadsFilterAssembler`, `RelayGroupsDiscoveryFilter` |
| `relayClient/hashtag` (new), `relayClient/geocaches`, `relayClient/polls/results` | `FilterPostsByHashtags`, `GeocacheListingKinds`, `RelayPollResponseLoader` |
| `feeds` | `FilterByListParams`, `UserFeedState`, `StringFeedState`, `SupportedContent` |
| `viewmodels` | `RelayFeedViewModel`, `UserExternalIdentitiesViewModel` |
| `service/upload` | `MediaUploadTracker`, `HlsPublishState`, `SuspendableConfirmation` |
| `audio`, `music`, `qrcode`, `cashu`, `mediaServers`, `tor` | `PitchShifter`; `MusicFormatting`; `ScanResult` + `StructuredAppendAccumulator`; `CashuWalletDiscovery` (jvmAndroid); `MediaServerHealth` (jvmAndroid); `TorDialogViewModel` |
| `util` | `DebouncedPublisher`, `TimeFormatUtils` + `ZapFormatterNoDecimals` (jvmAndroid) |

**To `commonsUI`**

| Package | Files |
|---|---|
| `ui/components` | `InformationDialog`, `UrlPreviewCard` (both jvmAndroid) |
| `ui/text` | `MentionPreservingInputTransformation` (its `MENTION_REGEX` is now public; the app's `UrlUserTagOutputTransformation` shares it) |
| `ui/navigation/navs` | `ObservableNav`, `TwoPaneNav`, `ShareToDMNav` |
| `ui/navigation/bottombars`, `ui/insets`, `ui/theme`, `ui/settings` (new) | `TabReselectCoordinator`; `KeyboardState`; `ColorSchemePreview` (jvmAndroid: it formats with `String.format`); `SettingsCatalog` |
| feature `ui/` packages | `account/ui/login/LoginErrorManager`, `clink/ui/ClinkBudgetDialog`, `cordn/ui/{BusyLabel,SettingsFormBlock}`, `nip46RemoteSigner/ui/Nip46ActivityUi`, `nip52Calendar/ui/CalendarsViewMode`, `nip53LiveActivities/ui/StreamingStatusFlags`, `nip72ModCommunities/ui/CommunityRulesViolationBanner`, `relays/ui/SubPurposeLabels`, `service/upload/ui/StrippingFailureDialog` |
| `audio` | `VoicePreset`, `RecordingIndicators` (jvmAndroid) |

Most files moved unchanged apart from the package line. The exceptions, all for
`commonMain`:

- `formatTrackDuration` went from `internal` to public so the app's music renderers can
  still call it. Its `"%d:%02d".format(...)` is now a `padStart`, because `String.format`
  is JVM-only.
- `RouteTextArgs`: `codePointAt` / `Character.charCount` → the commons
  `codePointAtKmp` / `codePointCharCount`, which mirror the JDK ones.
- `TorDialogViewModel`: `Integer.parseInt` → `toInt()`. Both throw
  `NumberFormatException` on the JVM.
- `UserExternalIdentitiesViewModel.Factory`: `create(Class<T>)` → the multiplatform
  `create(KClass<T>, CreationExtras)`, as `PollResultsViewModel` already does.
- `Dispatchers.IO` needs `import kotlinx.coroutines.IO` in common code, and
  `RelayFeedViewModel`'s `javaClass.simpleName` log is now `this::class.simpleName`.

A tooling note for the next sweep: stripping comments with a regex before scanning
identifiers breaks on a `/*` inside a string (`"image/*"`), which swallows code up to the
next `*/`. Tokenize strings and comments together. And scan bodies, not only imports,
for inline `android.`/`java.` names and `javaClass`.

Eleven pure unit tests moved with their code to `commons` jvmTest (NewMessageTagger key
parsing, PitchShifter, RouteTextArgs, ShareToDMRouteRewriter, DebouncedPublisher,
RelayGroupDiscoveryConstraint, StructuredAppendAccumulator, SupportedContent,
TransactionRowLabels, CommunityRulesLookup) and `commonsUI` jvmTest
(SettingsCatalogFilter).

**Deleted app copies** (callers repointed at the `commons` original):
`ui/note/PubKeyFormatter.kt` (the commons `toShortDisplay` is the same with an optional
`prefixSize`), `ui/note/ZapFormatter.kt` (same functions; the commons `TenGiga`…`OneKilo`
constants became public for `ZapFormatterNoDecimals`), and the
`EqualImmutableLists.kt` re-export shim.

### `ChessViewModelNew` and `DesktopChessViewModelNew` → one `ChessViewModel`

The two classes forwarded the same ~40 members to `ChessLobbyLogic`; only the
adapters and the scope differed. `commons/nip64Chess/ChessViewModel` now takes the
adapters, the polling config, the dismissed-games store and an optional scope:

- **Android:** `ChessViewModelFactory` builds it with the Android adapters and no
  scope, so it runs on `viewModelScope` and stops polling in `onCleared`, as before.
  The factory lost its unused `Application` parameter. The `viewModel(key = …)` key
  changed from `ChessViewModelNew-<pubkey>` to `ChessViewModel-<pubkey>`, in all four
  call sites together, so the lobby, game screen, note card and home button still share
  one instance.
- **Desktop:** `ChessScreen` builds it with the Desktop adapters and its
  `rememberCoroutineScope()`, which is what `DesktopChessViewModelNew` did. The
  `UserMetadataCache` that class owned is now remembered next to it in `ChessScreen`.
  The debug `instanceId` (`System.identityHashCode`) went, since it isn't KMP.

### Left in the app on purpose (unblocked, but not shared code)

- `ui/screen/AndroidFeedViewModel`, `threadview/dal/AndroidLevelFeedViewModel`: they
  exist to bind Android's process-wide `LocalCache`. Desktop still runs
  `DesktopLocalCache` (Wave 2 part B).
- `ui/navigation/ShareIntentRouting`: names the manifest's `<activity-alias>`es.
- `ui/screen/loggedIn/embed/*` (9 files) and `browser/EmbeddedPageRequests`: bridges
  for the Android WebView host. No other front end embeds a WebView.
- `workouts/health/HealthConnectRationaleScreen`: Health Connect is Android-only.
- `ui/tor/{TorManager,TorBackend,ArtiNative,ArtiGuardState,TorConnectionFailureDialog}`:
  the in-process Arti client. Desktop drives an external Tor.
- `ui/tor/TorServiceStatus`: `commons/tor` has its own copy that differs on purpose
  (Android has `Bootstrapping`, Desktop has `Error`). Merging them changes every `when`
  over it on both platforms; that's a design step, not a move.
- `ui/actions/UrlUserTagOutputTransformation`: calls `android.util.Patterns.WEB_URL`
  by its fully-qualified name, which an import-based scan cannot see. It needs a KMP URL
  matcher (commons `richtext` has one) before it can move.
- `nests/room/{lifecycle/NestRoomEventCollectors, participants/RoomParticipantActions,
  stage/SpeakerReactionOverlay, stage/SpeakerZapOverlay}`: `internal` pieces of the
  Android audio-room screen, which Desktop doesn't have.

### Unblocked outside `ui/` (not moved in this round)

The same sweep finds 46 more movable files outside `ui/`. The notable groups are
`service/resourceusage` (9, the resource-usage ledger), `service/uploads/blossom`
(`InFlightInvoices`, `PaymentPromptLedger`, the `bud10` resolver trio),
`service/playback` leaves (`HlsLivenessCache`, `SessionRegistry`,
`WssDataStreamCollector`, the player control buttons), and `model/MediaAspectRatioCache`.

### Audit of this round

Three reviewers read the 69 moved files. I read the chess merge and the hand edits. The
edits kept behaviour: `codePointAtKmp`/`codePointCharCount` match the JDK calls,
`padStart` matches `%02d` for every Int, `toInt()` throws the same exception as
`parseInt`, and lifecycle's `ViewModelProvider` calls the `KClass` factory overload
first. None of the findings below came from the move; all are on `main`.

**Fixed:**

- **Zap amounts rounded half-even** (`showAmount`, `showAmountInteger`). Kotlin's
  `BigDecimal.div` already rounds to the dividend's scale with HALF_EVEN, so the
  `setScale(0, HALF_UP)` after it never ran: 12,500 read "12k" and 2,500,000 read "2M".
  The unit was also picked before rounding, so 999,500 read "1000k". Both now use
  `divide(_, 0, HALF_UP)` and pick the unit after rounding. The `BigDecimal(0.01)` built
  on every call is now a constant. The `showAmount` KDoc examples were wrong and now match
  what it returns. `ZapFormatterTest` covers this.
- **`AgentWorkBoard.merge` sorted every band by upvotes.** Its KDoc says upvotes order
  only the queue. An old upvoted job sat above a fresh one in Shipped, and every job sat
  above every workflow run in Working. Upvotes now count only in the queue.
- **`SuspendableConfirmation`** could resume its continuation twice: a button tap plus
  an outside-tap dismiss before the dialog recomposed away threw "Already resumed". A
  cancelled upload also left the dialog up with dead buttons. It now resumes only while
  active, and clears the dialog on cancellation, but only if the dialog is still its own.
  `SuspendableConfirmationTest` covers this.
- **`TorDialogViewModel.save`** parsed the SOCKS port even with the field hidden. Junk
  typed while External was selected blocked saving Internal or Off. It also took 0 or
  70000. Now only External requires a port in 1..65535; otherwise the last saved port is
  kept. `TorDialogViewModelTest` covers this.
- **`ExpandingCirclesAnimation`** built its infinite transition before checking
  `isRecording`. The composer toolbar always shows the record button, so it asked for a
  frame on every vsync while the composer was open. The transition now exists only while
  recording. The circles and the stop dot read their animated values in `graphicsLayer`,
  so a frame redraws a layer instead of recomposing.
- **`DebouncedPublisher.flush`** treated a job whose `publish()` was already running as
  pending. Leaving the nav picker while a publish waited on a remote signer cancelled it
  and started a second signing request. `flush` now acts only during the delay. A new case
  in `DebouncedPublisherTest` covers this.
- **`RelayFeedViewModel`:**
  - The DM list's first emission read `nip65RelayListNote` as a `DmRelayListEvent`,
    which is always null. It now reads `dmRelayListNote`.
  - `subscribeTo`/`unsubscribeTo` compared the `MutableStateFlow` itself to a `User`,
    so `unsubscribeTo` never cleared it. They now compare `.value`.
- **`ScheduledFlag`** built a `DateFormat` on every composition of a feed card. It is
  now remembered per start time.
- **`CashuWalletDiscovery`:** up to 50 relays finishing at once could write the crawl
  progress out of order, so it stepped backwards. The write is now monotonic.
- **Desktop `ChessScreen`:** polling runs on the screen's scope. When `remember` replaced
  the view model on an account switch, the old one kept polling. A `DisposableEffect` now
  stops it.

**Reported, not changed (need an owner's call):**

- `FilterLastMessageFollowingPublicChats` asks each relay for kind 41 with `limit = 1`
  across all followed channels, so only one channel's metadata comes back per relay. One
  busy channel can also fill the kind-42 `limit = 100`. The fix (a limit per channel, or a
  filter per channel) changes relay load.
- `PitchShifter`'s brute-force WSOLA search costs about 500 multiply-adds per output
  sample, and the loop never checks for cancellation. Decimating the correlation changes
  the audio, and a cancellation check changes its API.
- `RelayFeedViewModel.invalidateData` re-emits the same user into a `StateFlow`, which
  drops it, so a refresh only spins for a second.
- `ScheduledFlag` still reads `TimeUtils.now()` once, so a card composed before the start
  time keeps the date after it passes.

## 2026-09-28 — cordn file stores and `EncryptedAppendLog` to `commonMain`

`cordn/FileCordnStores.kt` (`CordnStorageLayout` and the group, key-package,
coordinator and handoff stores), `cordn/FileBackedCordnScopeFactory.kt`,
`cordn/CordnMigrationStores.kt` and `storage/EncryptedAppendLog.kt` moved from
jvmAndroid to commonMain. They take an okio `Path` plus a `FileSystem`
(default `platformFileSystem`), the `ScheduledPostStore` shape.

These files hold encrypted MLS group state that cannot be re-derived, so the
format is pinned first: `CordnStorageFormatGoldenTest` was committed against the
`java.io` implementation, asserting the exact tree of paths and the exact bytes
of every file, and passes unchanged on the okio one. Notes on the port:

- `ByteBuffer` framing (the cursor file) is okio `Buffer.writeLong`/`readLong`;
  both are big-endian.
- `java.util.Base64` in the migration snapshot is `kotlin.io.encoding.Base64.Default`
  with `PaddingOption.PRESENT_OPTIONAL`, the same shape as the napplet move.
- `File.delete()`/`deleteRecursively()` returned false instead of throwing, and
  `renameTo` fell back to copy-and-delete. okio throws; the File behaviour is kept
  by `deleteQuietly`, `deleteRecursivelyQuietly` and `moveOrCopy` in
  `commons/util/FileSystemExt.kt`. One deliberate difference: the recursive delete
  removes symlinks instead of following them.
- The log's `fsync`s are `FileHandle.flush()`, which is `FileDescriptor.sync()`
  on the JVM and Android.
- `EncryptedMarmotMessageStore` stays in jvmAndroid and passes `toOkioPath()` to the log.
