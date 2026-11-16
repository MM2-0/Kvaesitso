# Retiring the legacy desktop app: what each legacy-only feature becomes

> **Status:** done 2026-10-09. Every item below is built, and the legacy desktop app is deleted. Supersedes the
> "Legacy-only feature" table in [2026-09-27-one-ui-android-desktop.md](2026-09-27-one-ui-android-desktop.md)
> step 8d.

The legacy desktop app (`./gradlew :desktopApp:runLegacy`, everything in `desktopApp` outside
`desktop/app/`) stayed runnable until its desktop-only features had a home in the shared UI.

Android often has a better version of the same idea (its NIP-85 web of trust is far more than
the legacy app's follows-of-follows count). So for each feature, the legacy implementation was
compared with what Android and the shared modules already do, and the maintainer chose to discard
it, port only the missing parts, or rebuild it on the shared code. Nothing below ports legacy UI
as-is: the legacy screens are not shared screens and cannot be reused.

## Decisions

| # | Legacy feature | Decision |
|---|---|---|
| 1 | Messages privacy lock | **Port, and extend.** New shared settings screen; Android gets it too. |
| 2 | Hashtag-spam filter, WoT badge | **Discard.** The shared max-hashtag filter and the NIP-85 score chip cover them. |
| 3a | Live-now bar | **Discard.** The shared Home live bubbles are a superset. |
| 3b | Relay metrics dashboard | **Discard the dashboard; port the latency engine and its display.** |
| 4 | Spotlight and advanced search | **Discard.** The shared search screen is a superset; the Spotlight was never wired. |
| 5 | Custom feed builder | **Rebuild in shared code, for Android too.** Feeds appear in the top-bar feed picker; stored in our own per-account structure. |
| 6a | Upload compression preview | **Rebuild in the shared composer:** image preview before posting; the quality slider sets resolution. |
| 6b | GIF picker (NIP-94 search) | **Discard.** Replace with a NIP-30 custom-emoji picker button. |
| 7 | Deck and workspaces | **Rebuild on the shared shell, with workspaces, and migrate legacy users' decks.** |

### 1. Privacy lock

The state machine is already shared and tested (`commons/privacylock`: `PrivacyLockState`,
`InactivityTimer`, `LockScope`, backoff), and so are the gates (`commonsUI/ui/privacylock`:
`MessagesLockGate`, `WalletLockGate`, `LockScreen`, `DeviceAuthenticator`). Nothing calls them:
Android has no app lock at all (the only authentication is revealing the nsec on the key-backup
screen), and the new desktop does not wire them.

**Built.** `PrivacyLockHost` (around the shell in `AmethystApp`) installs one state per scope and
the app gate; `NavDestinations` gates each route by `Route.lockScope()`; `RelockOnLeave` re-locks
a scope when navigation leaves its route group, so going from the message list into a chat does
not ask again. Settings live in the shared DataStore (`DataStorePrivacyLockSettings`); the desktop
imports the legacy java.util.prefs lock once. Engaging the app lock drops the screens behind it,
so unlocking starts from the first screen.

- A new **Privacy lock** settings screen in `commonsUI` with three independent switches, **all off
  by default**:
  - **Lock the entire app.** On start and after the idle timeout.
  - **Lock messages.** The Messages screens and chat rooms.
  - **Lock the wallet.** The wallet screens.
- An idle timeout (1, 5, 15 or 60 minutes, or never; default 5) that applies to whichever locks are on.
  Leaving a locked screen re-locks it, as the legacy app does.
- `LockScope` grows an app-wide scope beside Messages and Wallet.
- Unlocking:
  - **Android:** biometrics or the device credential, through the existing `DeviceAuthenticator`.
  - **Desktop:** an app password. The legacy `PasswordHasher` (PBKDF2-SHA256, 600k iterations, legacy
    hashes still verify) moves to `commons/jvmMain`, and `DeviceAuthenticator.jvm` stops approving
    unconditionally. Set, change and clear the password from the settings screen (desktop only).
- The lockout backoff (5 failures, then 30 s doubling to 5 min, persisted) is shown on the shared
  `LockScreen`.
- Keep: blurring wallet amounts while the desktop window is unfocused, when the wallet lock is on.
- Drop:
  - The first-run banners.
  - `DmRedactionLevel` (DM text in notifications). It was stored but no notifier ever read it.

### 2. Hashtag spam, WoT badge

- Discard both, with no ports.
- They go away with the legacy app:
  - `WoTService`, `OutboxDispatcher` (about 800 lines that crawl every follow's kind 3),
    `LocalWoTService`.
  - `HashtagSpamCheck`, `HashtagSpamSettings`, `CollapsedSpamNote`, `LocalSpamExemptKeys`.
- Before deleting them, check that nothing outside the legacy app uses `OutboxDispatcher`.

### 3. Live bar, relay metrics

- Live-now bar: discard. There will be no viewer-count tiebreak in `HomeLiveFilter`. The commons
  `LiveActivitySorting` helper is dead code once the legacy app is gone.
- Relay metrics: discard `RelayDashboardScreen`, `RelayMetricsTab`, `RelayMetricCard`,
  `RelayDetailPanel` and `RelayStatusCard`, and the desktop-only `RelayMetrics` counter, which
  overlaps quartz `RelayStat`. Port two things into the shared app:
  1. Build `RelayLatencyTracker` and `RelayHealthStore` (commons `relays/health/`, today only built
     by the legacy `Main.kt`) in both apps' modules. Persistence goes behind a shared settings
     store, replacing `PreferencesRelayHealthPersistence`.
  2. Show the p50 times (OK-ack, EOSE, first result) and a "Slow ×N" chip (`classifySlowRelays`,
     against the cohort median) on the shared `RelayStatusRow`, and a latency section on
     `RelayInformationScreen`.
- Not ported:
  - The unhealthy-relay banner and popup.
  - "Last event N ago".
  - "Reconnect all".

### 4. Search

- Discard the legacy search: `SearchSpotlight`, `AdvancedSearchPanel`, the legacy `SearchScreen`,
  `SearchHistoryStore` and the search-relay editor.
- Also delete commons `AdvancedSearchBarState`, which only the legacy app uses.
- No quick-search popup and no saved-search UI for now.

### 5. Custom feeds

- The shared app gets a feed builder:
  - Users can make **as many feeds as they like**, and every one appears in the **top-bar feed picker**
    beside Follows, Global, lists, interest sets and DVM feeds.
  - A feed combines authors, hashtags, kinds and relays (AND), with excluded authors and excluded
    keywords. The legacy `FeedBuilderDialog` already does this; there is a name and an emoji too.
- Model and storage:
  - Reuse the commons model in `feeds/custom/` (`FeedDefinition`, `FeedBuilderState`, the
    serializer), which today only the legacy app uses.
  - Store the feeds in **our own per-account data structure**, not as kind 31890 events
    (maintainer, 2026-10-09). Whether other clients' 31890 definitions would help or hinder is an
    open question; publishing and importing them can come later.
- Filters:
  - A new top-nav filter type (`IFeedTopNavFilter` under `commons/model/topNavFeeds/`) runs a
    custom feed. Relay REQ filters and client-side excludes go through the shared relay client and
    feed filters; the legacy `DesktopCustomFeedFilter` and `createCustomFeedSubscription` are not
    reused.
  - Resolve the name clash: commonsUI `TopNavFilterState.kt` declares its own `FeedDefinition`.
- Editor: a shared create/edit sheet over `FeedBuilderState`, reached from the feed picker
  ("New feed…") with edit and delete on each custom feed.
- Migration: legacy desktop feeds are local JSON in java.util.prefs (`LocalFeedProvider`). On the
  first start of the new desktop, copy them into the new store for the account that made them.

**Built.**
- Stored per account in `AccountSettings.customFeeds` (the feed-visibility DataStore group,
  `FeedDefinitionSerializer` JSON); `saveCustomFeed` / `deleteCustomFeed`, and deleting a feed sends
  any screen showing it back to its default.
- `TopFilter.CustomFeed(id)` runs through `CustomFeedFlow` and `CustomFeedTopNavFilter`: every set
  condition must hold (kinds, authors, any hashtag), minus excluded authors and keywords. Relays:
  the feed's own, else the proxy relays, else the authors' outboxes, else the follows relays. The
  relay side is one combined REQ per relay (`filterHomePostsByCustomFeed`), so authors and hashtags
  are ANDed on the relay too (the legacy app ORed them there).
- Only Home offers them (`TopNavFilterState.homeRoutes`), under "Your feeds", with a "Manage your
  feeds" entry that opens `CustomFeedsScreen`; `EditCustomFeedScreen` builds or edits one over
  `FeedBuilderState`. The other screens' REQ builders do not know custom feeds.
- The legacy prefs are global, not per login, so the first account to load on the new desktop
  takes them (`LegacyCustomFeedImport`, filter feeds only).
- Fixed on the way: `filterHomePostsByHashtags` returned from inside `mapNotNull`, so a hashtag
  feed only asked its first relay.

### 6. Composer

Compression (images; video previews would take too long):

- **A preview before posting.** Show the re-encoded image itself, not just its size, so quality and
  artifacts can be judged. The preview also shows the new size and dimensions next to the
  original's, and tapping or clicking it compares the original with the compressed version.
  Compression is not only about size.
  - The libraries cannot estimate a result, so the preview really compresses the image in the
    background (Android: Zelory; desktop: `ImageReencoder`) and is cached per image and setting.
  - Both apps need a platform hook beside `MediaUploader` to produce it.
- **The quality slider sets the resolution too.** Today Android asks for 640 px whatever the
  setting (`MediaCompressor.compressImage`, `default(width = 640, …)`); the slider only changes the
  JPEG quality. Zelory's `default` only halves the image while it stays above 640 x 816, so a
  4032 x 3024 photo actually went up at 2016 x 1512. The new desktop asked for 640 px, while the
  legacy desktop uploaded at 1920 px.
  - Each step will set both the quality and a maximum size, for example 640 / 1280 / 1920 px.
  - Desktop gets extra steps for large screens, for example 2560 px and the original size.
- **No remembered default.** Each post starts from the default, because each post has a different
  goal.

**Built.** `CompressorQuality` carries each level's size limit and JPEG quality (640 / 1280 / 1920
px; desktop adds 2560 px) and the platform's slider steps (`sliderSteps`, an expect: phones stop
at 1920 px and start at 1280; desktop starts at 1920). Android resizes exactly with a Zelory
`FitWithinConstraint`; desktop hands the level to `ImageReencoder` as an `ImageSizeTarget`. The
four quality sliders are now one shared `MediaQualitySlider`, which previews the first still image
through `MediaUploader.previewImageCompression`.
- Shared `ImageVideoDescription` already has alt text, AI alt, the sensitive flag, the server choice,
  H.265 and GIF→MP4; those stay.

GIF picker:

- Discard the legacy NIP-94 GIF search (`GifPickerPanel`, `GifResult`, the `gifRelays` pref) and the
  unicode emoji grid (and with it `kodein-emoji`).
- Add a **custom-emoji picker** button to the shared composers (notes and DMs, both apps). It shows a
  grid of the user's NIP-30 emoji (their emoji list and saved emoji sets) and inserts
  `:shortcode:`. Today, typing `:word` suggests from the same data.

**Built.** The legacy GIF search goes with the legacy app. The picker first shipped as
`CustomEmojiPickerButton` (NIP-30 only) and was then replaced by `EmojiPickerButton` (commonsUI
`unicodeEmoji/ui`), which brings a Unicode palette back because Desktop has no system emoji
keyboard. It is built in-house from Unicode's `emoji-test.txt` (`tools/emoji-data/`), not
`kodein-emoji`. It sits in the note, comment, DM, public chat, Concord and Nest composers and on
the reaction choices screen. One grid holds the favorites (the reaction choices,
`EmojiPackState.favorites`), the Unicode groups with a skin tone, and one section per selected
pack (`EmojiPackState.myPacks`). A Unicode pick goes in at the cursor as is. A custom pick goes in
as `:shortcode: ` (`insertWordAtCursor`), and sending turns the shortcode into an `emoji` tag
(`findEmojiTags`).

### 7. Deck and workspaces

Rebuild on the shared shell. The legacy deck (about 5,000 lines) renders legacy screens and does
not port.

- **A Deck mode of `ScreenLayoutSpec`.** It is opt-in in settings and only offered on windows about
  1,200 dp or wider: desktop, Chromebooks and Android tablets in landscape.
- `MultiPaneShell` keeps the main screen on the left and puts extra columns to its right, in a
  horizontal scroll with draggable dividers. The notification side panel becomes the default first
  column.
- Each column is a `Route` with its own back stack, through a `ColumnNav : INav` wrapper (like
  `TwoPaneNav`), so every shared screen works as a column.
- Columns can be added, closed, moved and resized. The pure resize and fit logic in the legacy
  `DeckState` moves to `commons`. An "add column" picker lists Route types.
- **Workspaces:** named layouts, each a column list with widths, switched from the shell. They are
  stored per account with kotlinx-serialization (the old storage was Jackson in `DesktopPreferences`).
- Desktop keyboard shortcuts in `DesktopMenuBar`:
  - Ctrl/Cmd+T to add a column, +W to close it.
  - +Shift+←/→ to move a column.
  - +1…9 to focus a column.
  - +Shift+S to save a workspace.
- **Migration:** `LegacyDesktopAccountImport` also imports `deckColumns` and the workspaces. Each
  legacy `DeckColumnType` maps to a `Route`:
  - A custom-feed column maps to the migrated custom feed (item 5).
  - A type with no shared screen is dropped, and the user is told.

**Built.**
- `UiSettings.deckMode` (Settings › App › Deck, off by default) turns it on; it shows when the window
  has room for the notification panel (1,200 dp), in place of that panel.
- `DeckLayout` (commons `model/deck`) holds the workspaces and columns with their routes and
  widths, kept in `AccountSettings.deck` (feed-visibility DataStore group, JSON). Operations are
  pure and tested.
- `DeckArea` (commonsUI `navigation/deck`) draws the columns after the main screen, each a
  `NavigationHost` over its own `Nav` and destinations (`DeckColumnNav` keeps the drawer the
  app's), with its own ViewModelStore (entry keys restart at nav-0 per stack) and a rail-tier
  `LocalScreenLayout`. A strip switches, saves and deletes workspaces and adds columns; each column
  header resets, moves and closes it; its left edge resizes it. `DeckSplit` keeps the main screen
  at least 420 dp wide.
- Desktop: Ctrl/Cmd+T add, +W close, +Shift+←/→ move, +Shift+1…9 focus (Ctrl+1…3 were already
  Home/Messages/Notifications), +Shift+S save a workspace, and a Deck menu on macOS, through
  `DeckCommandBus`.
- Migration: `LegacyDeckImport` reads the legacy prefs once into the first account to load and
  turns the deck on if the legacy app was in deck mode; `LegacyDeckParser` maps the column types.
  Dropped: the global and custom-feed columns (no per-column feed screen yet: Home's feed state is
  account-wide), the local highlights and the editor. The user is told which.
- Known limits: two columns on the same screen share its account-wide feed state (not their scroll
  positions); `RelockOnLeave` follows only the main navigation.

## Order

Small and independent first; the deck last, since it builds on item 5 (custom-feed columns):

1. The relay latency engine and display (3b).
2. The privacy lock (1).
3. Compression preview and resolution steps (6a), then the emoji picker (6b).
4. Custom feeds (5).
5. The deck and workspaces, with migration (7).
6. Delete the legacy app: discarded features (2, 3a, 4, 6b's GIF search) go with it.

## Deletion (done)

- `desktopApp`: the 231 files outside `desktop/app/` that `desktop/app/` does not reach (a
  reachability scan over imports and same-package names), their 32 tests and the legacy launch
  benchmark, and the `runLegacy` task. What stays outside `desktop/app/` is what the new app uses:
  the platform theme, the media players and lightbox, Tor, scheduled posts, the HTTP client and the
  account storage the login import reads.
- Shared code only the legacy app used: `LocalWoTService`, the hashtag-spam check and its settings,
  `CollapsedSpamNote`, `LiveActivitySorting`, `AdvancedSearchBarState`, `FeedDefinitionRepository`,
  `SearchQueryToFeed`, `UnhealthyRelayBanner`, and the java.util.prefs stores for the privacy lock,
  sensitive content, notification read state and dismissed chess games. `WoTService` and
  `OutboxDispatcher` stay: `amy fof` uses them.
- Left in place, now unused by any app: some shared UI and models that only the legacy screens
  called (`ArticleHeader`, `TableOfContents`, `MarkdownToolbar`, `MetadataPanel`, `NewPostsChip`,
  `UserSearchCard`, `AuthApprovalBanner`, `ReadingTimeCalculator`, `EditProfileFields`, …). They
  may be useful to the shared screens; delete them if not.

