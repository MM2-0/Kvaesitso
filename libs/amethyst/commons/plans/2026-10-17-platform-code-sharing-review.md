---
title: "Review: what is left in amethyst/ and desktopApp/, and what each platform can share with the other"
type: refactor
status: in-progress (§1, §2 and the §5 step-2 quick wins done)
date: 2026-10-17
owner: commons
consumers: amethyst, desktopApp, commonsUI, commons, nestsClient
depends-on: commons/plans/2026-09-27-one-ui-android-desktop.md
---

# What is left on each platform, and what it can share

The one-UI plan
([2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md)) has moved 231 of
252 screens, the navigation shell and the app root to `commonsUI`. This review covers what is
still platform-side:

- `amethyst/src/{main,play,fdroid}`: 350 Kotlin files in `main`;
- `desktopApp/src/jvmMain`: 85 files.

For each file, the question was whether it can move to `commons`/`commonsUI` so the other
platform gets the feature, and whether the other platform already has a copy that could be unified.

**Method.**
- The imports of every file were read, and every Android/Desktop slot implementation was compared
  against the `AppPlatform`, `NotePlatform` and `RichTextPlatform` defaults.
- Every finding in "Bugs" was confirmed by reading the code. None was reproduced in a running app.
- Licences quoted below come from Maven POMs, not from LICENSE files. Re-check them against the
  LICENSE file before adding a dependency, as AGENTS.md requires.

Paths are relative to `amethyst/src/main/java/com/vitorpamplona/amethyst/` (Android),
`desktopApp/src/jvmMain/kotlin/com/vitorpamplona/amethyst/desktop/` (Desktop) or the module root.

## 1. Desktop bugs found on the way

**Fixed 2026-10-18.**
1. GIFs take `MediaHttp.imageClient`, the image role's client.
2. `LoadCityName` defaults to the known place name, or else the geohash.
3. The meeting slots are gone; `NoteCompose` calls the shared renderers.
4. Desktop's `DesktopQrScannerDialog` scans a picture from a file or the clipboard, with zxing core.
5. A shared `CalendarReminderScanner` (commons) runs from Android's worker and from a once-a-minute
   `DesktopCalendarReminders`. On Desktop, reminders also need OS notifications on, and pause during
   do-not-disturb.
6. Desktop runs `syncIfStale()` for the logged-in account on login and then every hour.
7. Payment cards, `blossom:` resolution (`BlossomUriRenderer` over `blossomServerFinder`) and the
   secret-emoji reveal (`SecretMessageContent`) are in the shared renderer. `BlossomFetcher` moved to
   `commonsUI/jvmAndroid`; Desktop builds a `BlossomServerResolver` and registers the fetcher.
   LaTeX stays Android's.
8. The Concord decryption moved to `commonsUI/jvmAndroid` (`rememberDecryptedConcordImageModel`);
   Desktop keeps the plaintext in `concord-images/` under its data directory.


| # | Bug | Evidence | Fix |
|---|---|---|---|
| 1 | **Animated GIFs bypass Tor.** | `ui/media/AnimatedGifImage.kt:53` fetches through `DesktopHttpClient.currentClient()`. Nothing in the new app calls `DesktopHttpClient.setInstance`, so `currentClient()` returns `simpleClient`, a direct connection (`network/DesktopHttpClient.kt:184`). Notes (`app/DesktopNotePlatform.kt:215`) and the lightbox (`ui/media/ZoomableImage.kt:125`) both go through it. Coil itself is wired correctly (`app/AmethystDesktop.kt:160`). | Use `roleBasedHttpClientBuilder.okHttpClientForImage(url)`, as Coil does. Then delete `DesktopHttpClient` and drop the default argument in `DesktopImageLoaderSetup`. |
| 2 | **Geohash place names are blank.** | The `NotePlatform.LoadCityName` default is `{}` (`commonsUI/…/ui/note/platform/NotePlatform.kt:182-186`), so neither `content` nor `onLoading` runs. Desktop does not override it. Affected: the geohash feed and chat headers, `FeedFilterSpinner`, relay-group metadata, active subscriptions and geohash bubbles. | Change the shared default to `content(geohashStr)`, or to the cached place name when one exists. |
| 3 | **Meeting events (kinds 30312, 30313 and 10312) render blank.** | The renderers are already shared (`commonsUI/…/note/types/MeetingSpace.kt`). Android only forwards to them (`ui/note/platform/AndroidNotePlatform.kt:399-430`); the slot defaults are `{}` (`NotePlatform.kt:314-332`). | Call the shared renderers directly and delete the three slots. |
| 4 | **QR scanning is a dead end.** | The `AppPlatform.QrCodeScannerDialog` default is `{}` (`AppPlatform.kt:207-210`), so `onDismiss` never fires. Six shared flows open it unguarded: show-QR, the NWC and Clink wallet setup, the NIP-46 signer, the geocache log and the Cordn migration. | Short term: dismiss at once, or hide the scan button when no scanner exists. Proper fix: §3, A2. |
| 5 | **The calendar-reminder switch does nothing.** | The shared settings screen calls `AppServices.setCalendarRemindersScheduled`, whose default is a no-op (`commons/…/service/AppServices.kt:132`); Desktop does not override it. | §3, A4. |
| 6 | **The web-of-trust index is never refreshed.** | `TrustNetworkState.syncDue()` has a single caller, Android's `service/wot/TrustNetworkSyncWorker.kt:61`. A long-running Desktop app keeps the first download. | §3, A5. |
| 7 | **Payment and Blossom content shows as raw text.** | Desktop uses the base `DefaultRichTextSegmentRenderer`. Invoices, withdrawals, Cashu and Clink offers, and `blossom:` URIs render as text, and LaTeX and secret emoji stay unrendered. | §3, A1. |
| 8 | **Encrypted Concord community images fall back to robohash.** | `rememberConcordImageModel` uses the plain URL by default; only Android decrypts. | §3, A6. |

## 2. Dead code in `desktopApp` (delete)

**Done 2026-10-18.**
- Deleted, with their tests: `AccountManager`, `LoginProgress`, `DesktopHttpClient`, the four theme
  files, `PlatformMaterialTheme`, `LocalWindowState` and `DesktopScheduleAtPickerTest`.
- `MediaHttp` and `DesktopImageLoaderSetup` no longer fall back to a direct client: a fetch before
  `main` installs them fails instead of skipping Tor.
- `BlossomClientTest`, `MediaCompressorTest`, `MediaMetadataReaderTest` and
  `UploadOrchestratorClientTest` (renamed: commons already has an `UploadOrchestratorTest`) moved
  to `commons/jvmTest`, and `NwcRpcIntegrationTest` to `quartz/jvmTest`.
- The Compose UI tests and `DecodeImageWithSkiaTest` stay in `desktopApp`. They need Compose's UI
  test library or Skia, and only the desktop smoke job runs them under Xvfb.


Reachability was checked with grep from `desktop/app/`:

- `account/AccountManager.kt` (1,076 lines) and `account/LoginProgress.kt`. The only references are
  a KDoc mention in `DesktopAccountStorage.kt:87` and their own tests (9 `AccountManager*Test`
  files).
- `network/DesktopHttpClient.kt` and its test, once bug 1 is fixed.
- `platform/PlatformColorScheme.kt`, `PlatformShapes.kt`, `PlatformTypography.kt` and
  `ui/theme/AmethystSpacing.kt`. These are reached only from `PlatformMaterialTheme` in
  `platform/PlatformTheme.kt`, which nothing calls. Keep `applyNativeWindowChrome` and
  `titleBarInsetTop` from that file.
- `jvmTest/…/ui/scheduling/DesktopScheduleAtPickerTest`. It tests a copy of picker math whose class
  no longer exists.
- **Later:** `account/DesktopAccountStorage.kt`, `EncryptedKeyBackupTest` and the four
  `app/Legacy*Import.kt`, once the legacy-app migration window closes.

These Desktop tests exercise `commons`/`commonsUI` code and belong in those modules' `jvmTest`:
`service/upload/*`, `ui/BlockInteractionsUiTest`, `ui/chats/*`, `ui/search/*`, `ui/uploads/*`,
`nwc/NwcRpcIntegrationTest`, `app/DecodeImageWithSkiaTest` and `app/InlineImageSizeTest`.

## 3. Moves

Effort: XS is under an hour, S is a day, M is a few days, L is a project.

### A. Android code that would give Desktop a feature

| # | What | Blockers today | Move | Effort |
|---|---|---|---|---|
| A1 | **Rich-text leaves.** The `Payment` and `SecretMessage` branches of `ui/components/AmethystRichText.kt`, plus the Blossom renderers and secret emoji in `RichTextPlatformLeaves.kt`. | The `Payment` and `SecretMessage` branches have none: they call shared composables (`MayBeInvoicePreview`, `CashuPreview`, …). The Blossom renderers use `Amethyst.instance.blossomResolver` (`:429-474`). | Fold them into `DefaultRichTextSegmentRenderer`, and read Blossom through `LocalAppServices.blossomServerFinder`, which Desktop must wire. LaTeX stays a slot. | S |
| A2 | **QR from an image or the clipboard.** `QrScannerState`, `QrScannerOverlay`, `QrImageImport` and `ScanQrImageScreen`. | `QrScannerState` and `QrScannerOverlay` have none. `QrImageImport` uses `Bitmap` and zxing-cpp. `ScanQrImageScreen` uses `Intent`. | Move the state and the overlay as they are. Split the import behind a decoder interface: zxing core on the JVM, which `commonsUI/jvmAndroid` already links. Desktop's scanner slot opens the import path. The CameraX dialog stays native. | S–M |
| A3 | **Notification gate.** The filter rules in `service/notifications/EventNotificationConsumer.kt:188-260` (own events, chat toggles, muted threads, `curatedHidesByTrust`) and `NotificationDispatcher.NOTIFICATION_KINDS`. | `MainActivity.isResumed`, a wakelock and the renderers. | A commons `(account, event) → decision + draft`, with "in foreground" injected. Desktop's `ui/notifications/DesktopNotificationAutoDispatcher.kt:125-200`, which has no mute, WoT or chat-toggle checks, then calls it. Merge the three kind taxonomies: `NotificationTopic`, `NotifKind` and `NotificationCategory`. | M |
| A4 | **Calendar reminder scan.** The body of `service/calendar/CalendarReminderWorker.kt` (`acceptedRsvpsInCache`, `couldStillFire`). | WorkManager and the notifier only. | A commons `CalendarReminderScanner` with a notifier port. Desktop runs it on a timer through `DesktopNotifications`. This fixes bug 5. | S |
| A5 | **Daily WoT sync.** The 4-line body of `service/wot/TrustNetworkSyncWorker.kt:52-61`. | None. | A commons `syncAllDue(accounts)`, plus a Desktop ticker. This fixes bug 6. | XS |
| A6 | **`ui/screen/loggedIn/chats/publicChannels/concord/ConcordCommunityImage.kt`.** | `context.cacheDir` only. | Move to `commonsUI/jvmAndroid` with a cache-dir port. This fixes bug 8. | S |
| A7 | **Image loading.** `service/images/BlossomFetcher.kt` (`blossom:` URIs, read-auth), `ProfilePictureFetcher.kt`, and the assembly in `ImageLoaderSetup.kt`. | `BlossomFetcher` has none (Coil, OkHttp, commons). `ProfilePictureFetcher` sits on `ThumbnailDiskCache`, which uses `Bitmap`. `ImageLoaderSetup` uses `Context` and `Build.VERSION`. | Move `BlossomFetcher` to `commonsUI/jvmAndroid` as it is. Add one shared `buildAmethystImageLoader(decoders, diskCache, callFactory, readAuth)` that replaces `service/images/DesktopImageLoaderSetup.kt`, including its duplicate `TorAwareOkHttpFactory`. Put the thumbnail cache behind an expect. | S |
| A8 | **Blossom upload policy.** In `service/uploads/AndroidMediaUploader.kt`: `mirrorToOtherServers` (`:423`), `SharedUploadAuth` (`:292`) and `sanitizeForBuzzWorkspace` (`:273`). | Mixed into the Android uploader. | Lift them into the shared `UploadOrchestrator`. Fold `service/uploads/blossom/BlossomUploader.kt` (a second OkHttp BUD-02 client) into `commons/jvmAndroid/…/BlossomClient` as a streaming overload. Desktop gains BUD-04 mirroring and Buzz-workspace sanitising. | M |
| A9 | **Upload header and audio stripping.** `FileHeader.prepare(url)` (`service/uploads/FileHeaderPreparation.kt:35`) and `stripMp3Metadata` (`MetadataStripper.kt:386`), which is pure byte logic. | `Context` temp-file plumbing; `Bitmap` for the blurhash. | Move both to `commons/jvmAndroid`, with a `PreviewHasher` expect (Android uses `Bitmap`, the JVM uses the existing `MediaMetadata.compute`). Desktop's `stripMetadata` (`app/DesktopMediaUploader.kt:600-611`) leaves audio tags in place today. | S |
| A10 | **NIP-38 now playing.** `service/nowPlaying/InAppPlayback.kt` and `NowPlayingStatusCoordinator.kt`. | The registry map is keyed by media3 `Player`. | Key it by `Any`, move both to commons, and have Desktop's `service/media/GlobalMediaPlayer` feed it. | S |
| A11 | **Napplet logic with no blocker, or one trivial blocker.** `NappletManifestLookup`, `gateways/AccountIdentityReader`, `connectedApps/consent/Nip46ConsentInfoBuilder`, `SignerConsentDetails`, `NappletLaunchRegistry`, the DataStore permission and storage stores. | None, or DataStore (already used in commons commonMain). | Move them as they are. Desktop then replaces `InMemoryNappletPermissionStore` (`app/DesktopAppModules.kt:371-372`) with persistent grants. | S |
| A12 | **Napplet logic after a small cut.** `NostrSignerOpLabels` and `NappletConsentSummary` (an unused `Context` and `browserIcons`, which is on `AppServices`); `NappletLiveSubscriptions` and `NappletIncBus` (`Messenger` used only as an owner key); `gateways/NappletResourceFetcher` (one `android.util.Base64` call); the network and permission registries (`Amethyst.instance.appStores`); the consent dialogs inside the three consent Activities. The `nappletHost` blob cache, prefetcher and CSP response building are pure as well. | The cuts named in this row. | Move to commons/commonsUI. This is the groundwork for a Desktop napplet host (C6). | M |
| A13 | **Tor state machine.** `ui/tor/TorManager.kt` (591 lines) and `TorBackend.kt` have no Android import; `ArtiGuardState` uses Jackson only; `TorConnectionFailureDialog` is Compose plus `Res`. `ui/tor/TorServiceStatus.kt` duplicates `commons/tor/TorServiceStatus.kt`, and `AndroidAppServices.kt:82-88` maps one onto the other. | None. | Move to commons/commonsUI and merge the two status classes. `tor/DesktopTorManager.kt` (kmp-tor, the C tor binary) becomes a ~100-line `TorBackend`. Desktop gains the stuck-connecting watchdog, the one-hour session bypass, the self-heal cooldown, network-change resets and the failure dialog. Android keeps `TorService` and `ArtiNative`. | M |
| A14 | **Settings › Resource usage.** `ResourceUsageScreen` and `UsageTrendChart`. | Vico is already `api` in commonsUI. `Amethyst.instance.resourceUsage` (`:179`, `:215`), and one `ACTION_SEND` (`:700`) that `FileSharer` covers. | Move both, and expose the accountant (already in `commons/jvmAndroid`) on `AppServices`. Desktop also needs its meters wired. Plan step 9 says the screen stays; its Android ties are this thin. | S |
| A15 | **`ConnectedAppDetailScreen`** (914 lines). | `Amethyst.instance` ledgers that Desktop's `AppServices` already provides, a `Toast`, and `NappletBrokerService.revokeSessionGrants`. | Split it. The shared connected-apps lists already navigate to `Route.ConnectedAppDetail`, which Desktop does not register. | S–M |
| A16 | **Share note as image.** `ui/note/share/ShareNoteAsImageScreen.kt`. The capture already uses Compose's `rememberGraphicsLayer`. | `R.drawable.amethyst` (`:506`), `Bitmap.compress` + `FileProvider` (`:528-536`). | Move the logo to Compose resources and add an image-sharer port. Desktop saves a PNG or copies it to the clipboard. | M |
| A17 | **Audio rooms.** `NestFullScreen`, `NestActionBar`, `ParticipantHostActionsSheet` and `NestActivityContent`. `NestViewModel` is already in `commons/jvmAndroid` and talks to `AudioCapture`, `AudioPlayer` and Opus interfaces. | `RECORD_AUDIO` and the Settings intent, `payViaIntent` (the shared `WalletAppLauncher` exists), one share intent. | Move the UI. Add JVM `AudioCapture`/`AudioPlayer`/Opus actuals in `nestsClient/jvmMain` (javax.sound, plus the JNA Opus already in jvmTest; licence check needed) and a Desktop `openNestRoom`, which is a no-op today. Plan step 9 keeps the room UI native; most of it does not need to be. | L |

**Small leftovers.** These move with little or no change; the shared side gains their tests, and
there is little to gain beyond that:
- `service/playback/composable/RenderPlaybackError.kt` and `controls/SkipButton.kt` (no Android
  imports; Desktop's `ui/media/VideoControls.kt:86-95` re-declares `SKIP_SECONDS` and its own skip
  button);
- `playerPool/positions/VideoViewedPositionCache.kt` (androidx `LruCache` instead of the Android
  one; it duplicates `savedPositions` in `GlobalMediaPlayer.kt:207`);
- `service/cast/{CastMediaSummary,CastRoutePlan}`;
- `service/resourceusage/MeteringNostrSigner`;
- `model/accountsCache/MarmotStreamTransport.kt`, which `DesktopAppModules.kt:436-437` repeats
  inline: give `:marmotQuic` a default instead;
- the host-agnostic browser chrome state in `ui/screen/loggedIn/embed/` (`ConsoleBridge`,
  `FindBridge`, `SelectionUiState`, `EmbeddedLoadStatus`, `EmbeddedAutoRecovery`,
  `EmbeddedTabChrome`, …).

### B. Desktop code that would give Android (and iOS) a feature

| # | What | Move | Effort |
|---|---|---|---|
| B1 | **`ui/map/DesktopTileMap.kt`** (505 lines), `DesktopLocationPreviewMap` and `DesktopGeocacheMapTab`: pure Compose, Coil and `Res`. | Move them to `commonsUI/commonMain` as they are. Then rebuild Android's osmdroid screens on them: `LocationPickerMap`, `LocationPreviewMap`, `GeocacheMapTab` and the map part of `GeohashLocationPickerDialog` (788 lines; `GeoPoint`/`BoundingBox` and the forward geocoder go behind a port). Desktop gets the geohash picker and teleport screen, which are blank today. Android can drop osmdroid and iOS gets maps. | M |
| B2 | **`service/images/SkiaDownsamplingDecoder.kt`, `SkiaGifDecoder.kt`.** They import only Coil and Skia. | Move to `commonsUI/skikoMain`, so iOS gets downsampled decoding and GIFs. | XS |
| B3 | **`ui/media/NowPlayingBar.kt`.** Android has no persistent now-playing bar. | Move it with C2. | with C2 |
| B4 | **Scheduled posts.** Desktop's `DesktopScheduledPostScheduler.drainOnce` uses the shared `ScheduledPostPublisher.drainDue`. Android's `service/scheduledposts/ScheduledPostWorker.kt:132-212` re-implements it (`publishAndConfirmDetailed` at `:173`) without the publisher's pubkey and signature checks. | Call `drainDue` from the worker and keep only the WorkManager scheduling. | XS |

### C. Copies on both platforms to unify

| # | Android | Desktop | Unify as | Effort |
|---|---|---|---|---|
| C1 | `ui/tor/TorManager` (Arti) | `tor/DesktopTorManager` (kmp-tor) | A13 | M |
| C2 | The `service/playback/composable/controls/*` chrome | `ui/media/VideoControls`, `NowPlayingBar` | The `PlaybackController` port the plan defers ("do it when Desktop gets a player"). Desktop has one now: `GlobalMediaPlayer` on composemediaplayer, MIT per its POM. | L |
| C3 | `ui/components/ZoomableContentView` + `ZoomableContentDialog` (1,688 lines) | `ui/media/LightboxOverlay` + `ZoomableImage` (742 lines, hand-rolled gestures) | One shared image viewer on `net.engawapg` zoomable (already a commonsUI dependency). Video, save and share stay slots. | M |
| C4 | `service/playback/composable/mainVideo/VideoPlayerActiveMutex` | `ui/media/VideoVisibilityMutex` | A shared "nearest to centre wins" election, with a per-platform bounds source. | S |
| C5 | `service/images/ImageLoaderSetup` | `service/images/DesktopImageLoaderSetup` | A7 | S |
| C6 | Napplet and nsite host (WebView in the `:napplet` process) | `DesktopAppLauncher` does nothing for `FavoriteApp.NostrApp` (`app/DesktopAppPlatform.kt:115-122`); napplet cards render nothing on Desktop | After A11 and A12: a JCEF/KCEF host behind an `EmbeddedWebSurface` port. Chromium renderers run out of process; `CefResourceRequestHandler` plays the content server; `CefMessageRouter` reaches an in-process `NappletBroker`. Per their POMs, kcef and jcefmaven are Apache-2.0 and jcef-api is BSD. The licences of the Chromium binaries bundled in CEF are unverified, and ffmpeg may be LGPL (a WARN). | L |
| C7 | `app/AmethystLocalCacheHost.kt` | `app/DesktopLocalCacheHost.kt` | Nearly identical; they differ only in `usageCounter` and the NIP-95 directory. Not worth a seam on its own. | — |
| C8 | `AppModules.kt` (1,677 lines) | `app/DesktopAppModules.kt` (682 lines) | Later: a commons builder for the shared graph (relay client, HTTP builders, connectors), so new services stop being wired twice. | M |

**Slots that a shared implementation would make redundant:**
- `NotePlatform`: the five Git slots, the three meeting slots (bug 3) and the three URL-preview
  slots;
- `AppPlatform.AppBottomBar`.

Both platforms pass the same shared composable through each of them, so call it directly.

## 4. What stays native (no action)

**Android:**
- media3 and PiP, CameraX, WebRTC calls (`stream-webrtc-android`), LightCompressor HLS and video
  compression, Health Connect, `TextToSpeech`;
- notifications (`NotificationCompat`, channels, receivers, the tile, `ConversationShortcuts`), the
  foreground services, WorkManager scheduling, push and FCM/UnifiedPush;
- Arti JNI, Android's Geocoder, `MediaRecorder` voice capture, MediaStore saving and `FileProvider`
  sharing;
- the Messenger IPC between the main and `:napplet` processes, the consent and launcher Activities
  and the `SurfaceControlViewHost` tab layer;
- the legacy `EncryptedSharedPreferences` migration, and the flavours (ML Kit, Chromecast,
  AppFunctions).

**Desktop:**
- the window, menu bar and `platform/*` (UI scale, Wayland, fonts and window chrome);
- the keyring and the OS scheduler for scheduled posts;
- the auto-updater, the spell-check engines (the text field side is already shared) and the
  composemediaplayer engine.

**The call screens** (`CallScreen`, `ConnectedCallUI`, `PipCallUI`, ~1,000 lines) are Compose over the
shared `CallManager`. They could move behind a video-renderer slot, but only pay off once a JVM
WebRTC engine is chosen (licence gate first).

## 5. Suggested order

1. **Bugs 1–4 and the dead code** (§1, §2). One small PR each, and no new ports needed.
2. **Quick wins that close Desktop gaps.** All done 2026-10-18:
   - A5 (WoT sync), A1 (rich-text leaves), A4 (calendar reminders) and A6 (Concord images), with
     the §1 bugs;
   - B4 (scheduled-post publisher): Android's worker runs `ScheduledPostPublisher` per loaded
     account; the publisher no longer marks a post FAILED when its drain is cancelled;
   - A7/C5 (one image loader): `buildAmethystImageLoader` in `commonsUI/jvmAndroid`, used by both
     apps. Desktop still lacks the avatar thumbnail cache (`ProfilePictureFetcher` sits on Android's
     `Bitmap` cache);
   - B2 (Skia decoders to `skikoMain`).
3. **One version of a feature where there are two today:**
   - B1 (shared tile map, osmdroid retired);
   - A13/C1 (one Tor state machine);
   - A3 (one notification gate);
   - A8/A9 (one Blossom upload policy);
   - A2 (QR import);
   - C3/C4 (image viewer and video mutex).
4. **Larger features:**
   - A11/A12, then C6 (Desktop napplets);
   - C2/B3 (`PlaybackController`, the now-playing bar on both platforms);
   - A17 (audio rooms on Desktop);
   - A14–A16 (resource usage, connected-app detail, share as image).

Each step follows the one-UI rules: check with `run-headless.sh` before calling a UI change done,
and gate every new library on its licence.
