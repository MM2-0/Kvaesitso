# Audit: bugs and performance in `commons` / `commonsUI` (2026-09-12)

**Method.** Three independent read-only sweeps (commons hot paths: feeds,
relayClient, model, viewmodels; commons services; commonsUI composables)
produced 44 candidate findings. Every one was re-read in the source before
acting; 38 held up, 6 were rejected (below). 34 are fixed on this branch,
4 are deferred because they need a design decision.

## Fixed — correctness

| Where | Defect |
|---|---|
| `keystorage/SecureKeyStorage.kt` (jvm) | AES-GCM initialised with `IvParameterSpec` → `InvalidAlgorithmParameterException`; the whole no-keyring fallback (headless Linux, containers) was dead. Now `GCMParameterSpec(128, iv)`; round-trip test added. |
| `service/http/OnionLocationInterceptor.kt` | Any `Onion-Location` header was cached and every Tor request re-pointed at it for 24 h, https→http allowed. Only `.onion` hosts are cached now. |
| `service/http/EncryptedBlobInterceptor.kt` | `peekBody(MAX)` + `bytes()` buffered the blob twice and never closed the original body (a network interceptor: the pool slot stayed open). Reads once, closes. |
| `service/BundledUpdate.kt` `BasicBundledInsert` | No `finally`: one throw out of `onUpdate` left `isProcessing = true` and every later `invalidateList` enqueued forever. Mirrors `BasicBundledUpdate`. |
| `richtext/CachedRichTextParser.kt` | Parse cache keyed on a 32-bit hash with no input check; a `String.hashCode` collision served one note's segments under another author. Hits now verify content/tags/uri/author. |
| `richtext/RichTextParser.kt` `noProtocolUrlValidator` | `(sep?[word]+)*` backtracks exponentially on a line terminator (13 s at 26 chars, run per keystroke in the composer). Separator made mandatory; timing test added. |
| `blurhash/Base83.kt` | `charMap[c.code]` on a 255-entry table → `ArrayIndexOutOfBounds` for any non-Latin-1 char in an attacker-controlled `imeta` blurhash. Bounds-checked. |
| `blurhash/CosineCache.kt` | Keyed on `size * components` so (50,4) and (100,2) shared a table; has()/get() pair could NPE under a concurrent eviction. Composite key + single lookup. |
| `service/upload/MediaCompressor.kt` | EXIF strip gated on the file *name* and failures swallowed silently; camera JPEGs could upload with GPS intact. Sniffs bytes, logs the throwable. |
| `service/upload/StaticSitePublisher.kt` | `walkTopDown()` followed symlinks out of the published root. Links are skipped and files must resolve under the root. |
| `preview/UrlPreview.kt` | `body.bytes()` on an attacker-controlled URL; a host streaming `text/html` forever OOMs the app. Reads at most 512 KB. |
| `service/namecoin/NamecoinNameService.kt` | `catch (e: Exception)` swallowed `CancellationException` and dropped the stack trace. |
| `relayClient/preload/MetadataRateLimiter.kt` | The "flush remaining" ran only after the channel closed, which never happens: fewer than 20 queued pubkeys were never requested (desktop DM list names stayed `npub1…`). Also an unguarded `HashSet` shared across threads. Timed flush + lock; tests added. |
| `relayClient/assemblers/FeedMetadataCoordinator.kt` | Six plain `HashSet`s mutated from Compose callbacks and IO coroutines; and `loadMetadataBatched` marked the whole list as asked while requesting only `take(100)`. One lock, chunked filters. |
| `model/Note.kt` | `isHiddenFor` computed and discarded the scoped-comment muted-word check; `relayHintUrl` computed and discarded live-activity relays; `flow()` check-then-`!!` could NPE against `clearFlow()`; `removeReport` left an empty bucket so deleted reports kept counting; reply/boost/edit/timestamp/reaction/report/label mutators did unlocked read-modify-write while zaps used `syncLock`. |
| `state/EventCollectionState.kt` | Cancel-and-restart on every insert (the KDoc promised the opposite): a feed busier than one event per 250 ms never flushed. Leading-edge throttle. |
| `model/privateChats/Chatroom.kt` | `pruneMessagesToTheLatestOnly` rewrote `messages` outside the lock its add/remove use; a just-decrypted DM could vanish. |
| `viewmodels/LiveStreamTopZappersViewModel.kt` | `publish()` iterated two maps outside the mutex their two writer coroutines hold. |
| `relayClient/assemblers/{Metadata,Reactions}FilterAssembler.kt`, `feeds/ChatroomFeedFilter.kt` | `hashCode()` used as the dedup / feed key; a collision dropped a whole pubkey set from the REQ or merged two rooms' feeds. Keys are the values themselves. |
| `model/ThreadAssembler.kt` `OnlyLatestVersionSet` | `addAll`/`removeAll` returned `elements.map{…}.any()` — always true for non-empty input. |
| `ui/components/ClickableTexts.kt` | `remember(text)` baked `onClick` into the link annotation; a recycled row fired the previous target. |
| `ui/components/ZonedSwipeModifier.kt` | `remember {}` captured the first `openDrawer` forever. |
| `ui/components/RobohashImage.kt` vs `UserAvatar.kt` | Two different "is light theme" predicates fed one global cache that evicts everything when the flag flips. Unified. |
| `nip64Chess/ui/InteractiveChessBoard.kt` | `remember(localMoveCount + positionVersion)` — a sum is not a composite key. |

## Fixed — performance

| Where | Change |
|---|---|
| `viewmodels/SearchBarState.kt`, `search/UserSearchEngine.kt` | Full user-cache scan ran on the Compose dispatcher per debounced keystroke; `flowOn(Dispatchers.Default)`. |
| `search/SearchResultSorter.kt` | 1+N `Regex` compiled per scored event; hoisted and cached. |
| `ui/components/ShimmerPlaceholder.kt`, `LoadingAnimation.kt`, `BunkerHeartbeatIndicator.kt` | Frame-rate animation values read in composition (whole composable recomposed every frame, brush/list re-allocated); moved into draw/layer lambdas. |
| `ui/components/GlowingCard.kt` | Brush, dash array and stroke allocated on every draw; `drawWithCache`. |
| `ui/richtext/CustomEmojiRenderer.kt` | `inlineContent` map rebuilt per recomposition on every emoji-bearing name/note; remembered. |
| `ui/search/SearchPeoplePicker.kt` | `indexOf` inside `items` (O(n²) per arrow key, wrong for duplicates); `itemsIndexed`. |
| `nip64Chess/ui/LiveChessGame.kt` | `moves.chunked(2)` per recomposition; remembered. |

## Deferred (need a design call)

- **`feeds/FeedContentState.kt`** — the full-refresh bundler and the additive-insert
  bundler both end in `updateFeed`, a read-modify-write on `_feedContent` with
  separate mutexes. A slow `loadTop()` can overwrite an additive emission that
  landed in between. Fix is a single mutex/bundler or a CAS `update {}`; the
  choice affects feed latency, so it is left to the maintainer.
- **`model/Note.kt` child lists** — `replies`/`boosts`/`edits` are `List`s, so
  every insert is an O(n) `contains` plus a full copy (O(n²) per thread load).
  `Chatroom.messages` already uses a `Set`; switching these changes a public type.
- **`ui/note/GitDiffView.kt`** — every line of every file in a patch is composed
  eagerly in a non-lazy `Column` (expanded by default). Needs a `LazyColumn`
  restructure or a collapse-past-N-lines default.
- **`ui/components/UserAvatar.kt`** — the robohash fallback `ImageVector` is
  assembled synchronously in composition even when Coil already has the image;
  past 100 distinct authors every one is a cache miss on the UI thread. Needs
  the fallback to be produced lazily/off-thread (`produceCachedStateAsync`).

## Rejected after verification

- `NewPostsChipState`: "chip can never appear" — `val isAtTop by derivedStateOf`
  routes reads through the State, so the outer `derivedStateOf` does track it.
- `EditNicknameDialog`: the initial `snapshotFlow` emission mis-targets the
  emoji field, but typing retargets before any suggestion can exist.
- Three "internal visibility" and "same-package reference" hits were
  false positives of the name scan.
