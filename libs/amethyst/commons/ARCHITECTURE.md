# `commons` — Shared Module Architecture & Goals

`commons` is the **shared layer** between every Amethyst front end:

| Consumer       | Kind                         | Uses from `commons`                          |
|----------------|------------------------------|----------------------------------------------|
| `amethyst`     | Android app (phones, tablets, laptops) | everything (models, state, ViewModels) + `commonsUI` |
| `desktopApp`   | Desktop JVM app              | everything (models, state, ViewModels) + `commonsUI`; today's mouse-first screens are to be replaced by the shared UI |
| `cli` (`amy`)  | Headless JVM CLI (no UI)     | everything — `commons` is headless by construction; it never sees `commonsUI` |
| `nappletHost`  | Android WebView sandbox      | napplet contract + `commonsUI` (for the shell/shim Compose resources) |
| iOS (future)   | iOS app                      | everything + `commonsUI`; expected to share most UI with Android |

`commons` sits **above** `quartz` (the protocol-only Nostr KMP library) and
**below** the apps. The split between the three is:

- **`quartz/`** — Nostr protocol: events, NIPs, crypto, relay framing. No app
  state, no UI, no caches of "what this user follows."
- **`commons/`** — everything an Amethyst *client* needs that isn't
  platform-specific **or Compose UI**: domain models
  (`Note`, `User`), in-memory state holders, ViewModels, the relay-subscription
  client, shared business services.
- **`commonsUI/`** — the Compose UI components that more than one front end
  renders, plus everything only they need (icons, theme, Coil fetchers,
  markdown, the `composeResources` strings/fonts and the generated `Res`).
  Depends on `commons` as `api`. See `commonsUI/ARCHITECTURE.md`.
- **`amethyst/` & `desktopApp/`** — platform shims: the Activity / Window
  entry points, services, system integration, and the platform implementations
  of shared ports. They assemble `commons` pieces; they should not
  re-implement them. **Screens and navigation are being moved into
  `commonsUI`** so Android (which now also runs on laptops) and a new JVM
  Desktop app render one UI; see
  [`plans/2026-09-27-one-ui-android-desktop.md`](plans/2026-09-27-one-ui-android-desktop.md).

> The goal is **"write it once in `commons`"**. Before adding a manager, cache,
> filter, ViewModel, or composable to an app module, check whether it already
> exists here or belongs here. See the "Where does my code go?" guide below.

---

## 1. The one rule that shapes the package tree: the **UI / non-UI boundary**

The shared layer is **two modules** with one package tree:

> **`commons` = CLI-safe code.** It does not depend on Compose UI. It may use
> the `androidx.compose.runtime` *annotations* `@Stable` / `@Immutable` (they
> are just stability tags) and snapshot state (`mutableStateOf`, `State`), but
> it must **not** import `androidx.compose.ui`, `androidx.compose.foundation`,
> `androidx.compose.material3`, Coil, the generated `Res`, declare
> `@Composable` functions, or build `ImageVector`s. Its `build.gradle.kts`
> simply has none of those dependencies, so a violation fails to compile.
>
> **`commonsUI` = UI code.** Anything that does the above. It is only usable
> by the GUI front ends (Android, Desktop, iOS), never by `cli`.

Both modules share the **same `com.vitorpamplona.amethyst.commons.*` package
tree** — the split is a module boundary, not a package rename, so a file moves
between `commons/src/…` and `commonsUI/src/…` without changing its package or
any consumer's imports. Kotlin resolves same-package declarations across
modules without imports; the only thing that stops working across the boundary
is `internal` visibility (a UI file cannot see an `internal` declaration in
`commons` — make it public or move it).

This boundary is **not** a top-level `ui/` vs `logic/` partition of the package
tree (we chose to stay feature-oriented, §3). It is a property of each file:
a feature keeps its logic in `commons/…/<feature>/` and its composables in
`commonsUI/…/<feature>/ui/` (or `commonsUI/…/<feature>/` for the historical
flat packages), and the table in §2 says which module each package lives in.

---

## 2. Package taxonomy

Top-level packages under
`commonMain/.../commons/`, grouped by concern. **UI?** marks whether the
package contains Compose UI (and therefore lives in **`commonsUI`**, not
here). "mixed" means the feature's logic is in `commons` and its composables
in `commonsUI`, under the same package.

### Domain models & data
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `model`        | no¹ | Core domain types (`Note`, `User`, `Channel`), thread assembly (`ThreadAssembler`, `ThreadLevelCalculator`, `ReplyContext`, `replyingDirectlyTo`), publish routing (`BroadcastRelayPlanner`, behind `EventBroadcaster`; runs over any `EventCache`, which is how amy shares it), and per-NIP event model extensions in `model/nipNN…` subpackages. `model/cache` holds the in-memory event store, all in commonMain: the `ICacheProvider` / `ILocalCache` ports, `EventCache` (the store and its `justConsume` dispatch) and its `object LocalCache` singleton, `UserMetadataCache`, `AntiSpamFilter`, `CachePruner`, `CacheSearch` and the `LocalCacheHost` app-shell port. `model/account`, `model/observables`. The largest package; keep it organized by NIP. |
| `defaults`     | no  | Static bootstrap data (default relays, channels). |

`model/navigation` also holds the headless navigation identifiers: `NavBarItem`, `BottomBarEntry` and the app's `@Serializable` `Route` catalog (`Routes.kt`). `model/composer` holds post-composer state that is not UI (`AudienceSelection`, `SplitBuilder`, `IZapRaiser`).

¹ `model` uses only the `@Stable`/`@Immutable` runtime annotations — CLI-safe.

### Protocol-adjacent business logic (CLI-safe)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `actions`      | no  | Event builders for user actions (follow, zap…). The canonical entry point for non-UI callers. |
| `account`      | mixed | New-account bootstrap events; the logged-off login/sign-up buttons in `commonsUI` `account/ui/login` and `account/ui/signup`. |
| `onchain`      | mixed | On-chain zap splitting/broadcasting; user-facing failure strings in `commonsUI` `onchain/ui`. |
| `marmot`       | mixed | The encrypted at-rest stores behind marmot-kmp's `MarmotManager` (`jvmAndroid`); group-chat composables (retention picker, agent stream banner) in `commonsUI` `marmot/ui`. The group pipeline itself is `com.vitorpamplona.marmot.client` in marmot-kmp, and the rules for what members send inside a group (polls, emoji, edits, reports) are its `foundation/appEvents`. The account wiring is `model/AccountMarmotActions` and `model/marmotGroups` (chatrooms, the poll tally adapter, upload caches). |
| `cordn`        | no  | cordn's at-rest stores (`FileCordnStores`, the keystore ciphers) and the blob store over Blossom. The account wiring is `model/cordn/CordnRuntime`. Protocol decisions every cordn client shares belong in cordn-kmp, not here (`CordnLinks`, discovery, the handoff, the snapshot, the tip blob store all live there); the relay pool is quartz's `INostrClient.cvmRelayPool`, labelled for the activity screens by `coordinatorRelayPool`. |
| `cyberspace`   | no  | `CYBERSPACE_V2` §7.7 region-bag search: the free quote off a bag's `hint`/`h` tags, the device-measured budget, and the cold sweep flow over quartz's `RegionSweep`. The protocol itself (coordinates, Cantor trees, keys, the bag) is `quartz/.../cyberspace`. |
| `sno`          | mixed | DECK-0003 object rendering math — rasterizer, lighting, face winding, default avatar — here; the Compose viewer/thumbnail and the Coil fetcher in `commonsUI` under `sno` and `sno/ui`. |
| `nip53LiveActivities` | mixed | Live-activity zapper aggregation (logic) + the stream card in `nip53LiveActivities/ui`. |
| `search`       | no  | Event search filtering/ranking, kind registry. |
| `rendering`    | no  | UI-agnostic event rendering: `RenderedEvent`, the kind-keyed `EventRendererRegistry` (renderers under `rendering/renderers`), the `BodySpans` rich-text post-processor over `richtext`, and `rendering/json/JsonEventFormatter` (amy's `--json` shape). A Compose formatter would live in `commonsUI` under the same package. See `plans/2026-04-21-event-renderer.md`. |
| `preview`      | no  | OpenGraph / meta-tag link-preview parsing. |
| `emojicoder`   | no  | Variation-selector emoji encode/decode. |
| `unicodeEmoji` | mixed | Unicode emoji palette (`UnicodeEmojiCatalog`: groups, skin tones, name search) here; the data file (`composeResources/files/emoji/emoji.tsv`, built by `tools/emoji-data/generate.py`) and the picker (`EmojiPickerButton`) in `commonsUI` `unicodeEmoji/ui`. The picker's favorites and per-pack sections come from `model/nip30CustomEmojis/EmojiPackState` (`favorites`, `myPacks`). Desktop has no system emoji keyboard. |
| `richtext`     | no  | URL/media/pattern parsing for rich text. |
| `blurhash`     | no  | BlurHash encode/decode (pure math; platform image bridge in platform sets). |
| `thumbhash`    | no  | ThumbHash encode/decode. |
| `nipACWebRtcCalls` | mixed | NIP-AC WebRTC **call state machine** + peer-session abstraction. See `nipACWebRtcCalls/ARCHITECTURE.md`. (Mirrors `quartz/.../nipACWebRtcCalls`.) Call-screen previews in `commonsUI` `nipACWebRtcCalls/ui`. |
| `qrcode`       | mixed | Scanned-payload classification (`classifyScannedPayload`: NIP-19, `nostr:` URIs, relays, NWC/LN) here; scanner sheets in `commonsUI` `qrcode/ui`. |
| `scheduledposts` | mixed | Scheduled-post model + signed-event parsers (`extractFirstMediaUrl`, `extractEventId`, `extractContentPreview`); the `MediaThumbnail` composable in `commonsUI` `scheduledposts/ui`. |

### State holders & ViewModels
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `state`        | no² | Small feature `StateFlow` machines (`FollowState`, `UserMetadataState`, `LoadingState`). |
| `viewmodels`   | no² | Larger list/feed-backed ViewModels (`androidx.lifecycle.ViewModel`). Shared by all GUI front ends; `cli` usually drives the layers below instead. A ViewModel lives in `commonsUI` only when it cannot compile headless: it holds Compose UI state (`ChatNewMessageState` — `TextFieldValue`; `thread/LevelFeedViewModel` — `LazyListState`), reads `Res`, or needs `AccountViewModel.toastManager`. `BaseAccountViewModel` (the account ViewModel minus its toast manager and platform bridges) is here; take it, or just `Account`, to keep a new ViewModel here. |
| `notices`      | no  | `UserNotice`: everything headless code tells the user, as typed facts (`SignerNotice.ReadOnly`, `InvoiceNotice.CallbackNotFound(lnAddress)`, …), reported to a `UserNoticeSink` or worded through a `UserNoticeResolver`. A sealed hierarchy, so `commonsUI`'s `notices/ui` rendering is exhaustive. Never put a `Res` key or English text here. |
| feature screens' logic: `badges`, `bolt12Offers`, `bookmarkgroups`, `buzz`, `calendars`, `chats`, `communities`, `dvms`, `followPacks`, `geocaches`, `geohash`, `gitRepo`, `hashtag`, `lists`, `mediaServers`, `music`, `paymentTargets`, `pinnednotes`, `podcasts`, `polls`, `profile`, `relay`, `relays`, `scheduledposts`, `settings`, `url`, `wallet` | no² | The ViewModels and concrete `FeedFilter`s behind one feature's screens, usually in a `dal/` subpackage (`hashtag/dal/HashtagFeedFilter` + `HashtagFeedViewModel`). Named after the `commonsUI` folder that holds the screens (`ui/screen/loggedIn/<feature>/`), so the two halves pair up. Never under `ui.*` here. |
| `feeds`        | no  | The feed data-access layer at the root (`FeedFilter`, `AdditiveFeedFilter`, `AdditiveComplexFeedFilter`, `ChangesFlowFilter`, `FeedContentState`, `FeedState`, `InvalidatableContent`, `DefaultFeedOrder`, `RepostRenderability`…) plus `feeds/custom` (`FeedDefinition`, `FeedBuilderState` — the feeds users build, run by `TopFilter.CustomFeed`) and `feeds/related`. See the `account-state` skill (`references/feeds.md`). |
| `profile`      | mixed | `ProfileBroadcastStatus` (state) + `EditProfileFields` at the root; the `ProfileBroadcastBanner` composable lives in `commonsUI` `profile/ui`. |
| `privacylock`  | mixed | Lock state machine + settings here; `LocalPrivacyLockState`/`lockStateFor` (CompositionLocal accessor) in `commonsUI`. |

² may touch `compose.runtime` state types (snapshot state, `@Stable`); they
are shared across the GUI apps. A state holder that needs a `foundation`/`ui`
type (`LazyListState`, `TextFieldValue`, `TextFieldState`) goes to `commonsUI`.

### Relay client
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `relayClient`  | mixed | Compose-scoped subscription managers, filter assemblers, EOSE managers, preloaders. (Despite a `composeSubscriptionManagers` subpackage name, this is subscription-lifecycle logic, not UI.) The `@Composable` entry points — `relayClient/user/` (`observeUser*` — kind-0 metadata), `relayClient/event/` (`EventFinderFilterAssemblerSubscription`/`observeNote*`), the other `*FilterAssemblerSubscription`s, `KeyDataSourceSubscription`, `auth/AuthApprovalBanner` — are in `commonsUI` under the same packages. `relayClient/oneshot` holds store-first, one-shot loaders (event lookup by reference, profiles, threads, notifications, an `EventCache` filled on demand) over the `OneShotRelayAccess` port, for front ends without live subscriptions (amy). See the `relay-client` skill. |
| `relays`       | mixed | Low-level EOSE/relay-timing bookkeeping (`EOSECache`, `EOSERelayList`); relay-list settings composables (`DraggableRelayList`, `RelayEventCountRow`, the NIP-45 count result types) in `commonsUI` `relays/ui`. |

### Platform abstractions (`expect`/`actual`)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `util`         | no  | KMP primitives: `KmpLock`, `WeakReference`, number/URL/codepoint helpers, list/debug helpers. **This is the only general-utility package** — there is no `utils`. |
| `keystorage`   | no  | Secure key storage interface (Keystore / keychain / keyring actuals). |
| `appupdate`    | mixed | The desktop app's self-update and the release events behind it, headless: NIP-82 release lookup (`Nip82UpdateSource`, `Nip82UpdateFinder`: only releases signed by `DesktopRelease.PUBLISHER`), the `AppUpdateManager` state machine behind the `AppUpdateController` port, and the `AppUpdateInstaller` port the platform implements. The release events `amy releases publish` signs: the release itself (`Nip82ReleaseEvents`, one shape for every app), `DesktopReleaseEvents`, and `AndroidReleaseEvents` + `ZapstoreConfig` for Zapstore (zsp's event shape), `FlatpakRelease` + `FlatpakReleaseEvents` for Orchard (the Flatpak bundles by URL); in `jvmMain`, `ApkInspector` (apksig, which the desktop app leaves out of its build) and `ReleaseBundles` (files → signed events, Blossom mirroring). The prompt and the Settings section are in `commonsUI` `appupdate/ui`; the installers in `desktopApp` `desktop/update`. |
| `tor`          | no  | Tor manager interface + settings. |
| `service`      | no  | Cross-cutting services: `BundledUpdate` batching (common); `service/upload` (JVM), `service/nwc`, `service/lnurl` (jvmAndroid). **Singular `service`** — there is no `services`. |

### UI (Compose — lives in **`commonsUI`**)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `ui`           | yes | **Cross-cutting** shared composables only, organized by area: `ui/components`, `ui/theme`, `ui/signing`, `ui/thread`, `ui/note`, `ui/richtext`, `ui/search`, `ui/notifications`, `ui/screens`, `ui/layouts`, `ui/feeds` (feed shell: empty/error/loading states, refresh box, remembered scroll states), `ui/navigation` (`INav`, `EmptyNav`, `ObservableNav`/`TwoPaneNav`/`ShareToDMNav`, top bars, drawer swipe), `ui/settings` (the searchable settings catalog), `ui/markdown`, `ui/privacylock`, plus Compose helpers in `ui/state` (cached-state) and `ui/text` (TextField extensions). Feature-specific UI lives in `<feature>/ui`, **not** here. Nothing under `ui.*` lives in *this* module any more: the feed DAL that used to sit in `ui/feeds` is now `feeds/`, and the reply-context logic that sat in `ui/note` (`replyingDirectlyTo`, `ReplyContext`) is now in `model/` next to `ThreadAssembler`. |
| `nip23LongContent` | yes | Long-form (NIP-23) article UI: `nip23LongContent/ui/article` (reader) + `…/ui/editor` (authoring). The model lives in `model/nip23LongContent` (here). |
| `icons`        | yes | `ImageVector` icon definitions + builders, Material Symbols codepoints, the icon-font glyph tables. |
| `hashtags`     | yes | Custom hashtag `ImageVector`s. |
| `robohash`     | yes | Procedural robohash avatar `ImageVector` assembly. |
| `audio`        | mixed | Spectrum/visualizer *data* (`AudioSpectrum`, `SpectrumAnalyzer`…) here; the `VisualizerRenderer`s, `VisualizerRegistry` and the canvas composables in `commonsUI`. |
| `service/image` | mixed | `CoilImageBridge` + the BlurHash/ThumbHash/Base64/Blossom Coil fetchers are `commonsUI` (they are Coil); the headless image helpers stay here. |
| `napplet`      | mixed | Protocol/permission logic here; `NappletWebContract` (serves the shell/shim from `composeResources`) in `commonsUI`. |
| `favorites`, `nip30CustomEmojis`, `nip34Git`, `nip85TrustedAssertions`, `nip53LiveActivities` | mixed | Logic here; each feature's `ui/` (or the flat `FavoriteAppIcon`, `EmojiSuggestionState`) in `commonsUI`. |
| `chats` | yes | Composables shared by every chat kind (DMs, public chats, relay groups, concord): unread badge, divisors, system messages, author line, send button, reply toggle, new-conversation screen. Chat *models* are in `model/chats`. |
| `nip17Dm`, `nip23LongContent`, `nip28PublicChat`, `nip29RelayGroups`, `nip32Labeling`, `nip43RelayMembers`, `nip51Lists`, `nip52Calendar`, `nip56Reports`, `nip72ModCommunities`, `nip73ExternalIds`, `nip99Classifieds`, `nipC0CodeSnippets`, `nipCCGeocaching`, `birdstar`, `nipsOnNostr`, `buzz`, `cashu`, `clink`, `concord`, `music`, `mediaServers`, `nip46RemoteSigner`, `browser`, `profile`, `napplet` | yes / mixed | Single-feature UI under `<feature>/ui` (named after the `quartz` package, or its concern name when `quartz` has none). Where the package also has logic (`cashu`, `browser`, `napplet`, `profile`) that part stays here; the `buzz` and `concord` models are in `model/buzz` and `model/concord`. |

### Mixed (documented debt — see §4)
| Package        | UI? | Purpose |
|----------------|-----|---------|
| `nip64Chess`   | mixed | Live-chess feature: game/lobby/subscription logic here; board/lobby composables in `commonsUI` under `nip64Chess/ui`. (Mirrors `quartz/.../nip64Chess`.) |
| `domain`       | no  | Currently only `domain/nip46` (Nostr Connect signer flows). Sparse; candidate to fold into a clearer home. |

---

## 3. Conventions

### Feature-oriented, not layer-partitioned
We keep a feature's model, state, and UI **together** under one feature
package rather than splitting the whole module into top-level `ui/` /
`viewmodels/` / `model/` layers. Within a feature, separate UI from logic with a
`ui` **subpackage** (e.g. `profile/ProfileBroadcastStatus` vs
`profile/ui/ProfileBroadcastBanner`) so the CLI-safe boundary (§1) stays
visible.

The big shared cross-feature packages (`model`, `ui`, `relayClient`, `util`,
`icons`) are the exception — they are organized by layer because many features
share them.

### Naming
- **Singular, no synonyms.** `util` (not `utils`), `service` (not `services`).
  One concept → one package name.
- **Feature UI vs cross-cutting UI — the deciding test.** A composable goes in
  `<feature>/ui` if it renders/edits *one* feature's content (it would make no
  sense outside that feature) — e.g. `profile/ui`, `nip53LiveActivities/ui`,
  `nip23LongContent/ui`. It goes in `ui/<area>` only if it is reusable across
  features (theme, avatars, buttons, layouts, markdown rendering, shimmer…).
  When in doubt, ask "could a second, unrelated feature reuse this as-is?" —
  yes → `ui/<area>`, no → `<feature>/ui`. The top-level `ui/` package holds
  **no** feature-specific composables.

### NIP as the second axis (mirror `quartz`)
`quartz` is ~94% organized by NIP (`nipNN<slug>` per spec), and that is correct
*there* — the protocol layer is naturally NIP-partitioned. `commons` is **not**
organized by NIP at the top level, and should not be: most of it is
cross-cutting infrastructure (`ui`, `relayClient`, `viewmodels`, `feeds`,
`util`…) that serves many NIPs at once, and the load-bearing UI/non-UI boundary
(§1) cuts *across* NIPs, so a NIP-first top level would just nest the same
problem one level down.

Instead, **layer is the primary axis, NIP is the secondary axis**:

- The big shared layers stay layer-organized (`model`, `relayClient`, the
  cross-cutting `ui`, …).
- **Inside a layer, NIP-specific code goes in a `nipNN<slug>` subpackage whose
  name matches `quartz` exactly** — e.g. `model/nip57Zaps`. This gives a clean
  trace: `quartz/nip57Zaps` → `commons/model/nip57Zaps`.
- **A top-level package that *is* a single self-contained NIP feature takes the
  same name as its `quartz` counterpart**, and owns its own UI under
  `<feature>/ui`: `nip64Chess`, `nipACWebRtcCalls`, `nip53LiveActivities`,
  `nip23LongContent`, `marmot`. (`marmot` is un-numbered in marmot-kmp too.)
  Generic/multi-NIP packages keep their concern name (`search`, `preview`,
  `actions`, `richtext`…).
- **Feature UI is never under `ui/`.** A single-NIP feature's composables live
  in `<feature>/ui` (e.g. `nip53LiveActivities/ui`), not `ui/nip53LiveActivities`.
  `ui/` is exclusively cross-cutting (§2, Naming).

### Source sets
| Source set    | For |
|---------------|-----|
| `commonMain`  | KMP code for **all** targets (Android, JVM, iOS). Gated by `verifyKmpPurity` — no Jackson/OkHttp/`System.currentTimeMillis`/`java.util.UUID`/JVM `@Synchronized`/`@Volatile`. Use the KMP replacements. |
| `jvmAndroid`  | Shared by Android + Desktop, **not** iOS. Where JVM-bound deps live (`nestsClient`, OkHttp, NWC/LNURL). |
| `jvmMain`     | Desktop-only (keyring, EXIF, `service/upload`, OS notifications). `dependsOn(jvmAndroid)`. |
| `androidMain` | Android-only (Keystore, DataStore, the Android `R` string resources used by the napplet host). `dependsOn(jvmAndroid)`. |
| `iosMain`     | iOS `actual`s. Compile-only spike today. |

`commonsUI` mirrors the same source-set layout (plus `skikoMain`, shared by
desktop JVM + iOS for `org.jetbrains.skia` pixel helpers); Coil-OkHttp,
markdown and the `viewModel()` helper live in its `jvmAndroid`.

When adding platform code, prefer the **most common** source set that still
compiles: `commonMain` → `jvmAndroid` → platform-specific. See
`/kotlin-multiplatform`.

### Choosing a dispatcher
`Dispatchers.Default` has only one thread per core (minimum 2), shared by every
CPU-bound coroutine in the process. When 4–8 of those threads wait on anything,
whether disk, network, binder IPC or simply a lock someone else holds, the whole
pool stops. So:

- **`Dispatchers.IO` is the default choice.** Use it for anything that touches
  `LocalCache`, `Note`/`User`, `Account`, a signer, the relay client, a
  store, preferences, files, or any shared cache. All of those can take a lock
  (`Note` has a `syncLock`; quartz's `*Cache` classes and `ConcurrentLruCache`
  lock on write; Compose's snapshot system has a global lock), and with the
  number of coroutines this app runs, a short lock wait becomes a pileup.
- **`Dispatchers.Default` only for code you can show never waits:** signature
  verification, hashing, parsing a byte array, rasterizing, or arithmetic over
  immutable data you already hold. If the work calls back into caller-supplied
  code, assume the callback can block and run it on IO
  (see `ParallelEventVerifier`: verifies on Default, callbacks on IO).
- **Long CPU jobs get their own threads.** Work that holds a thread for seconds
  or minutes runs on IO-backed threads (`PoWMiner.MiningDispatcher` for mining,
  plain IO for the single-threaded `BagSweep`), never on Default, so it cannot
  queue every other Default task behind it.
- **A suspend function moves its own blocking work.** Wrap the blocking call in
  `withContext(Dispatchers.IO)` inside the function (as `NostrSignerExternal`
  does for its ContentResolver IPC) instead of trusting every caller to pick
  the right thread. Callers on Main and on Default are then both safe.
- **No `runBlocking`** outside tests, previews and the CLI's entry point.

In `commonMain`, `Dispatchers.IO` needs `import kotlinx.coroutines.IO`; it is
available on every target this module builds.

### Where does my code go? (quick guide)
0. **Would another client of the same protocol need it** (`amy`, White Noise, a bot, another
   app)? → not here, it goes in a library (`AGENTS.md` › Libraries). For Marmot and cordn that
   includes rules with no MLS state: the tags, limits and validation of the unsigned events members
   send inside a group (edits, polls, custom emoji, reports, deletions) are marmot-kmp's
   `foundation/appEvents/` or cordn-kmp's `spec02Envelopes/`, not `model/marmotGroups` here. What
   stays here is the wiring around them: which relays and signer, `LocalCache`/`Note`, the
   chatroom, the tally cache, the UI.
1. **Pure Nostr protocol** (events/NIPs/crypto)? → not here, it's `quartz`.
2. **A composable** (screens and navigation chrome included)? → `commonsUI`, in
   `ui/<area>` or `<feature>/ui` (same package tree as here). Also anything that
   imports Coil, `Res`, or a `foundation`/`ui` state type. Only the platform
   entry points and system integrations stay in `amethyst/`/`desktopApp/`.
3. **A ViewModel / `StateFlow` state holder**? → `viewmodels` or `state` (or
   `<feature>` if feature-scoped). Keep it CLI-safe where practical.
4. **Relay subscription / filter assembly**? → `relayClient`.
5. **A domain model or per-NIP event wrapper**? → `model` (`model/nipNN…`).
6. **A platform capability behind `expect`/`actual`** (storage, crypto)? → the matching abstraction package + actuals in platform sets.
7. **A generic helper**? → `util`. (Resist creating a new top-level package for
   one file.)

---

## 4. Known debt / follow-ups

These are intentionally *documented*, not silently tolerated. Fix opportunistically.

- **`commons` applies the Compose *compiler* plugin without declaring any
  composable.** Deliberate, not debt: the plugin's `@StabilityInferred`
  stamps are what keep unannotated commons classes stable from the apps'
  point of view. Measured on full recompiles (2026-09-12): without it,
  unstable composable params go 20→28 in `commonsUI`, 33→65 in `desktopApp`,
  90→149 in `amethyst`. Don't remove it; if a class must be stable for a
  hot path, annotate it explicitly as well.
- **Same package tree in two modules.** Intentional (zero-import-churn split),
  but it means a package's module is not visible from its name. Rule of
  thumb: if it imports Compose UI it is in `commonsUI`; check §2 when unsure.
- **`domain` is sparse** (only `nip46`). Either grow it as the home for
  use-case/flow types or rename it to the matching `nip46RemoteSigner` per the
  NIP-second-axis rule.
- **`relays` vs `relayClient`** are coherent but close in name; `relays` is
  low-level EOSE bookkeeping, `relayClient` is the subscription client. Keep the
  distinction in mind when adding files.
- Several **single-file feature packages** (`account`, `marmot`,
  `nip53LiveActivities`, `keystorage`) are kept as feature/abstraction
  namespaces expected to grow; do not fold them into `util` just for size.
- **`onchain`** (on-chain zap splitting) is `quartz`-adjacent but un-numbered;
  leave readable unless a clear NIP number lands.
- **`model/cache/EventCache` is `jvmAndroid`, not `commonMain`.** The NIP-95
  `java.io.File` spill is the obvious blocker but not the binding one: the
  cache's own storage, `LargeSoftCache`, is `jvmAndroid`
  (`WeakReference` + `ConcurrentSkipListMap`), as are the two
  `*ListMatchingFilter` observables, `MintDirectoryIndex` and
  `NwcPaymentTracker`. Promoting the cache means promoting those first.
  The binding constraint is `LargeSoftCache`'s **sorted** store: it backs the
  ranged `forEach(from, to, …)` that all of `LargeSoftCacheAddressExt` uses to
  scan one kind's slice of the `Address` key space, and a hash map turns each
  of those into a full scan. `quartz/linuxTest/LargeCacheRangeFallbackTest`
  documents the matching invariant from the other side — the native range
  overloads fall back to full scans, and that is deemed safe precisely
  *because* the range callers live in the JVM-only `LargeSoftCache`. Moving
  this needs a sorted KMP store first, not just the okio blob sink and the
  `java.util.SortedSet` signature change. See the audit in
  `commons/plans/2026-08-30-commons-migration-sweep.md`.

---

## 5. See also
- `nipACWebRtcCalls/ARCHITECTURE.md` — WebRTC call state machine deep-dive.
- Root `AGENTS.md` — module overview, sharing philosophy, build commands.
- `kotlin-multiplatform`, `compose-expert`, `relay-client`,
  `account-state` (feeds included) skills for the patterns referenced above.
