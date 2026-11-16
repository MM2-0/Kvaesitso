# Custom Feeds for Amethyst Desktop

> **Status:** in-progress — data model, repository, serializer, builder, search bridge, kind 31890 event, and desktop drawer/dialog/screen all shipped, but Phase 1.5 relay filters (`FeedFilterFactory`/`CustomFilterFeedFilter`/assemblers), the DVM marketplace, kind 10090 sync, and PeopleList/InterestSet resolution are unbuilt ("coming soon").
> _Audited 2026-06-30._

**Date:** 2026-05-04
**Branch:** `feat/desktop-custom-feeds`
**Status:** Planning
**Deepened:** 2026-05-04

## Enhancement Summary

**Research agents used:** feed-patterns, compose-expert, desktop-expert, relay-client, kotlin-expert, nostr-expert, account-state, compose-dnd-research, nip90-research

### Key Improvements from Research
1. Feeds must be **account-scoped** (not java.util.prefs) — supports multi-account + cross-device sync via kind 10090
2. Added **Phase 1.5** (FeedFilter mapping + relay subscriptions) — critical missing layer between data model and UI
3. Use `ImmutableList` from kotlinx.collections.immutable for all FeedSource collections — Compose stability
4. Sync pinned feeds with existing **kind 10090 `FavoriteAlgoFeedsListEvent`** for cross-device persistence
5. Use **Calvin-LL/Reorderable** library for drag-reorder (KMP, proven)
6. Changed chord shortcut from `Cmd+F, 1/2/3` to `Cmd+1/2/3` (standard tab-switch, no conflict)
7. Added EOSE-aware loading states and subscription lifecycle management

---

## Overview

Add custom feed creation, discovery, and management to Amethyst Desktop. Users can create feeds from hashtags, authors, relays, keywords; browse DVM algorithmic feeds; pin top 3 to the sidebar; manage all feeds via the app drawer's new Feeds tab; and optionally publish/import feeds via kind 31890 events.

## Goals

- Intuitive feed lookup and creation (from search or builder)
- Customizable feed navbar (top 3 pinned in sidebar, expandable via drawer)
- DVM marketplace for algorithmic feeds
- Local-first with optional protocol-level sharing (kind 31890 + naddr)

## Non-Goals (for now)

- Set operations (union/intersection/difference)
- WoT-based filtering
- Auto-zapping DVMs

## Design Decisions

| # | Decision |
|---|----------|
| 1 | Top 3 feeds pinned in left sidebar, hard cap |
| 2 | "More" opens app drawer with FEEDS tab |
| 3 | Following + Global pre-created as FeedDefinitions |
| 4 | Local + private first; optional publish to relays |
| 5 | Live stream refresh; periodic poll fallback for DVMs |
| 6 | DVM zap requires confirm popup |
| 7 | Emoji-only feed icons |
| 8 | Drag-reorder in both sidebar and drawer |
| 9 | Reuse existing HomeFeed column rendering (swap filter) |
| 10 | Feed sharing via kind 31890 naddr (publish, copy, paste) |
| 11 | Account-scoped persistence (not machine-global java.util.prefs) |
| 12 | Sync pinned feeds with kind 10090 FavoriteAlgoFeedsListEvent |

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Cmd+Shift+F` | Open drawer on Feeds tab |
| `Cmd+K` | Open drawer (last tab) |
| `Cmd+1/2/3` | Switch to pinned feed 1/2/3 |
| `Cmd+N` (in feeds tab) | Create new feed |

> **Note:** `Cmd+1/2/3` is standard tab-switching (like browsers, Slack). Avoids `Cmd+F` conflict with search. Implemented via `MenuBar` items in a "Feeds" menu — no chord state machine needed.

## Data Model

```kotlin
// commons/src/commonMain/.../feeds/custom/FeedDefinition.kt

@Immutable
data class FeedDefinition(
    val id: String,               // UUID
    val name: String,
    val emoji: String,            // single emoji as icon
    val pinned: Boolean,
    val pinOrder: Int,            // 0, 1, 2
    val source: FeedSource,
    val refreshMode: RefreshMode,
    val createdAt: Long,
)

@Immutable
sealed interface FeedSource {
    @Immutable
    data class Filter(
        val hashtags: ImmutableList<String>,
        val authors: ImmutableList<HexKey>,
        val relays: ImmutableList<String>,
        val excludeAuthors: ImmutableList<HexKey>,
        val excludeKeywords: ImmutableList<String>,
        val kinds: ImmutableList<Int>,
    ) : FeedSource

    @Immutable data class PeopleList(val address: ATag) : FeedSource
    @Immutable data class InterestSet(val address: ATag) : FeedSource
    @Immutable data class DVM(val address: ATag) : FeedSource
    @Immutable data class SingleRelay(val url: String) : FeedSource
    @Immutable data object Global : FeedSource
    @Immutable data object Following : FeedSource
}

enum class RefreshMode {
    LIVE_STREAM,
    POLL_5MIN,
}
```

### Research Insights: Data Model

- **ImmutableList** (from `kotlinx.collections.immutable`, already in project) required for Compose stability — bare `List` is treated as unstable
- **@Immutable** on sealed interface + all subtypes ensures skip-safe recomposition in FeedCard/sidebar
- **feedKey identity**: use `"custom-${definition.id}"` in generated filters to avoid cache collision between feeds with same parameters but different names
- **DSL builder** for test/programmatic construction:

```kotlin
inline fun feedDefinition(init: FeedDefinitionBuilder.() -> Unit): FeedDefinition =
    FeedDefinitionBuilder().apply(init).build()

// Usage in tests:
val feed = feedDefinition {
    name = "Bitcoin"
    emoji = "₿"
    filter {
        hashtags += "bitcoin"
        kinds += 1
    }
}
```

## Repository & State Management

```kotlin
// commons/src/commonMain/.../feeds/custom/FeedDefinitionRepository.kt

@Stable
class FeedDefinitionRepository(
    private val scope: CoroutineScope,
    private val serializer: FeedDefinitionSerializer,
) {
    private val _feeds = MutableStateFlow<ImmutableList<FeedDefinition>>(persistentListOf())
    val feeds: StateFlow<ImmutableList<FeedDefinition>> = _feeds.asStateFlow()

    // Pre-computed grouped view for drawer UI
    val groupedFeeds: StateFlow<GroupedFeeds> = _feeds.mapLatest { all ->
        GroupedFeeds(
            pinned = all.filter { it.pinned }.sortedBy { it.pinOrder }.toImmutableList(),
            myFeeds = all.filter { !it.pinned && it.source !is FeedSource.DVM }.toImmutableList(),
            algoFeeds = all.filter { it.source is FeedSource.DVM }.toImmutableList(),
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, GroupedFeeds.EMPTY)

    // Derived for sidebar (only recomposes when pinned change)
    val pinnedFeeds: StateFlow<ImmutableList<FeedDefinition>> = groupedFeeds.map { it.pinned }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, persistentListOf())

    // Transient UI events
    private val _events = MutableSharedFlow<FeedEvent>(replay = 0)
    val events: SharedFlow<FeedEvent> = _events.asSharedFlow()
}

sealed interface FeedEvent {
    data class Created(val feed: FeedDefinition) : FeedEvent
    data class PinLimitReached(val max: Int) : FeedEvent
}

@Immutable
data class GroupedFeeds(
    val pinned: ImmutableList<FeedDefinition>,
    val myFeeds: ImmutableList<FeedDefinition>,
    val algoFeeds: ImmutableList<FeedDefinition>,
) {
    companion object { val EMPTY = GroupedFeeds(persistentListOf(), persistentListOf(), persistentListOf()) }
}
```

### Account-Scoped Persistence

Feeds are per-account, NOT machine-global:
- Serialize alongside `AccountSettings` (same mechanism as `defaultHomeFollowList`, `favoriteAlgoFeeds`)
- On account switch, feed list swaps automatically
- On login, also subscribe to own kind 10090 events to restore pinned feeds from relay

## Navigation Layout

```
+--------+------------------------------------+
|   A    |                                    |
|        |                                    |
| [em1]  |  <- Pinned feed 1 (active)        |
| [em2]  |                                    |
| [em3]  |     Feed Content (reuses HomeFeed) |
|        |                                    |
|  ...   |  <- "More feeds" (opens drawer)    |
|        |                                    |
|        |                                    |
|  gear  |                                    |
+--------+------------------------------------+
```

### Research Insights: Sidebar

- Extend existing `DeckSidebar` params: `pinnedFeeds`, `activeFeedId`, `onSwitchFeed`, `onOpenFeedsDrawer`
- Insert emoji buttons between "Add Column" button and `Spacer(weight=1)`
- Active feed: `primaryContainer` background with `CircleShape`
- Tooltip: `"${feed.name} (Cmd+${index+1})"`
- For 3 items, use simple `Column` + `pointerInput` with `detectDragGestures` (no library needed)
- Drag state: track `draggedIndex` + `dragOffsetY`, swap on threshold cross

## App Drawer Feeds Tab

```
+--- App Drawer (Cmd+K / Cmd+Shift+F) ------+
| Search: [________________________]         |
|                                             |
| [Screens] [Workspaces] [Feeds <-active]    |
|                                             |
| Pinned (3/3)                                |
|   em Following        [unpin] [menu]       |
|   em Bitcoin           [unpin] [edit] [menu]|
|   em Trending (DVM)   [unpin] [menu]       |
|                                             |
| My Feeds                                    |
|   em Dev Talk          [pin] [edit] [menu]  |
|   em Memes             [pin] [edit] [menu]  |
|                                             |
| Algo Feeds                                  |
|   em Primal Popular    [pin] [menu]         |
|                                             |
| [+ Create Feed]       [Browse DVMs]        |
+---------------------------------------------+
```

### Research Insights: Drawer

- Extend `AppDrawerTab` enum + `AppDrawerState` with filtered feeds
- `Cmd+Shift+F` → add `MenuBar` item with `KeyShortcut(Key.F, meta=true, shift=true)` that sets `showAppDrawer=true` + `appDrawerInitialTab=FEEDS`
- Use `derivedStateOf` for search filtering (avoids recomposition on every keystroke when filtered result unchanged)
- LazyColumn with `key = { it.id }` + `Modifier.animateItem()` for smooth reorder
- Right-click: `onPointerEvent(PointerEventType.Press)` + `isSecondaryPressed` → `DropdownMenu` (existing pattern in AppDrawer)
- For drawer reorder: use **Calvin-LL/Reorderable** (v3.1.0, full KMP support)

## Feed Creation Paths

| Path | Entry | Result |
|------|-------|--------|
| Search -> Feed | Search results -> "Save as Feed" | SearchQuery -> FeedSource.Filter |
| Builder | Drawer -> "+ Create Feed" | Feed Builder dialog |
| DVM Browse | Drawer -> "Browse DVMs" -> pick | FeedSource.DVM |
| Import | Paste naddr in search/drawer | Fetch kind 31890 -> add |

## Feed Sharing

| Action | Mechanism |
|--------|-----------|
| Publish | Menu -> "Publish to Relays" -> signs kind 31890 |
| Share | After publish -> "Copy naddr" via `NAddress.create(31890, pubkey, dTag, relays)` |
| Import | Paste naddr -> client decodes with `Nip19Parser` -> if kind==31890 render feed card -> "Add to My Feeds" |

## Kind 31890 Event Structure

```
kind: 31890 (addressable replaceable)
content: JSON-serialized FeedSource (see schema below)
tags:
  ["d", "<feed-id>"]
  ["title", "<feed name>"]
  ["emoji", "<single emoji>"]
  ["alt", "Feed definition: <name>"]
  // Discoverability tags (duplicated from content for relay filtering):
  ["t", "<hashtag>"]           // for each hashtag in filter
  ["p", "<author-hex>"]       // for each author in filter
  ["relay", "<relay-url>"]    // for relay-based feeds
  ["a", "31990:<dvm-pubkey>:<d>"]  // DVM reference
  ["a", "30000:<pubkey>:<d>"]      // PeopleList reference
  ["a", "30015:<pubkey>:<d>"]      // InterestSet reference
```

**Content JSON schema:**
```json
{
  "type": "filter|people_list|interest_set|dvm|relay|global|following",
  "hashtags": ["bitcoin"],
  "authors": ["hex..."],
  "relays": ["wss://..."],
  "exclude_authors": ["hex..."],
  "exclude_keywords": ["spam"],
  "kinds": [1, 6, 30023],
  "refresh": "live|poll_5min",
  "source_address": "30000:hex:dtag"
}
```

## Implementation Phases

### Phase 1: Data Model + Persistence

**Location:** `commons/src/commonMain/kotlin/.../feeds/custom/`

- `FeedDefinition` data class with `@Immutable`, `ImmutableList` collections
- `FeedSource` sealed interface with all variants
- `RefreshMode` enum
- `FeedDefinitionRepository` with `StateFlow<ImmutableList<FeedDefinition>>` + `groupedFeeds` + `pinnedFeeds`
- `FeedDefinitionSerializer` (JSON via Jackson, exhaustive `when` on FeedSource for compile safety)
- `FeedDefinitionBuilder` DSL for tests and programmatic creation
- Account-scoped persistence (serialize alongside AccountSettings)
- Pre-create Following + Global as defaults on first launch
- Unit tests for serialization round-trip + builder DSL

### Phase 1.5: FeedFilter Mapping + Relay Subscriptions

**Location:** `commons/src/commonMain/kotlin/.../feeds/custom/`

This is the critical bridge between data model and UI rendering.

**FeedFilter per FeedSource variant:**

| FeedSource | Filter Type | Base Class |
|------------|-------------|------------|
| Filter | `CustomFilterFeedFilter` | `AdditiveComplexFeedFilter` |
| Following | Reuse existing `HomeFeedFilter` | — |
| Global | Reuse existing `GlobalFeedFilter` | — |
| PeopleList | Resolve ATag -> extract pubkeys -> author filter | `AdditiveComplexFeedFilter` |
| InterestSet | Resolve ATag -> extract hashtags -> tag filter | `AdditiveComplexFeedFilter` |
| DVM | `DvmFeedFilter` (non-additive, results from external) | `FeedFilter` |
| SingleRelay | `CustomFilterFeedFilter` (targeted to one relay) | `AdditiveComplexFeedFilter` |

**FeedFilterFactory:**
```kotlin
class FeedFilterFactory {
    fun createFilter(definition: FeedDefinition): IFeedFilter<Note> = when (definition.source) {
        is FeedSource.Filter -> CustomFilterFeedFilter(definition)
        is FeedSource.Following -> HomeFeedFilter(account)
        is FeedSource.Global -> GlobalFeedFilter()
        // ... etc
    }
}
```

**Key rules:**
- `feedKey() = "custom-${definition.id}"` — unique per feed, avoids cache collision
- `excludeAuthors`/`excludeKeywords` applied client-side in `applyFilter()`, not at relay level
- Unit test: `applyFilter(event)` must match what `feed()` would include/exclude

**Relay subscription assembler:**
```kotlin
class CustomFeedFilterAssembler(private val source: FeedSource.Filter) {
    fun toFilter(): Filter = filter {
        if (source.kinds.isNotEmpty()) kinds(source.kinds)
        if (source.authors.isNotEmpty()) authors(source.authors.toSet())
        if (source.hashtags.isNotEmpty()) tags("t", source.hashtags.toSet())
        limit(200)
    }
}
```

**ViewModel selection:**
- Standard feeds (hashtags, authors, relays) -> `FeedViewModel`
- DVM feeds -> `FeedViewModel` with poll-based invalidation
- PeopleList feeds -> `ListChangeFeedViewModel` (membership changes)

**EOSE-aware loading state:**
```kotlin
class CustomFeedSubscriptionState(
    val events: StateFlow<List<Note>>,
    val eoseReceived: StateFlow<Boolean>,
    val lastRefreshed: StateFlow<Instant?>,
)
```

**Subscription lifecycle:**
- Only the ACTIVE feed has a live subscription
- Pinned feeds not currently displayed = NO open subscription
- On switch: old feed `unsubscribe()`, new feed `subscribe()`
- For POLL_5MIN: subscribe -> wait EOSE -> unsubscribe -> timer -> repeat

**Invalidation signals per FeedSource:**
- `Filter` with authors -> invalidate when those authors' notes arrive in LocalCache
- `Following` -> invalidate on `Account.followListFlow` change
- `PeopleList` -> invalidate when referenced list event updates

### Phase 2: Sidebar Pinned Feeds

**Location:** `desktopApp/src/jvmMain/.../deck/DeckSidebar.kt`

- Add params to `DeckSidebar`: `pinnedFeeds`, `activeFeedId`, `onSwitchFeed`, `onOpenFeedsDrawer`
- Insert pinned feed emoji buttons between "Add Column" and spacer
- Active state: `primaryContainer` background + `CircleShape`
- Click switches active feed (triggers subscription swap)
- Drag-to-reorder: `detectDragGestures` on each item (3 items, Column, no library needed)
- Tooltip with shortcut hint: `"${feed.name} (Cmd+${index+1})"`
- "More" button (MaterialSymbols.MoreHoriz) opens drawer on Feeds tab
- Wire `Cmd+1/2/3` via `MenuBar` items in "Feeds" menu (OS-aware: meta on macOS, ctrl on others)

### Phase 3: App Drawer Feeds Tab

**Location:** `desktopApp/src/jvmMain/.../deck/AppDrawer.kt`

- Add `FEEDS` to `AppDrawerTab` enum
- Extend `AppDrawerState` with `filteredFeeds()` method + keyboard nav for 3rd tab
- `Cmd+Shift+F` → MenuBar item that opens drawer on Feeds tab (pass `appDrawerInitialTab`)
- Feed list grouped via `groupedFeeds` StateFlow (pre-computed in repository)
- Search: `derivedStateOf` filtering by name/emoji
- Pin/unpin buttons (grayed out at 3 cap, emit `FeedEvent.PinLimitReached`)
- Right-click: `onPointerEvent` + `isSecondaryPressed` -> DropdownMenu (Edit, Duplicate, Delete, Publish)
- Drag-reorder in pinned section: Calvin-LL/Reorderable v3.1.0 with `LazyColumn` + `key = { it.id }`
- `animateItem()` for smooth movement on reorder

### Phase 4: Feed Builder Dialog

**Location:** `commons/src/commonMain/.../feeds/custom/ui/` (composable) + `desktopApp` (host)

**State hoisting pattern:**
```kotlin
@Stable
class FeedBuilderState(initial: FeedDefinition?) {
    var name by mutableStateOf(initial?.name ?: "")
    var emoji by mutableStateOf(initial?.emoji ?: "")
    val hashtags = mutableStateListOf<String>()
    val authors = mutableStateListOf<HexKey>()
    val relays = mutableStateListOf<String>()
    val excludeAuthors = mutableStateListOf<HexKey>()
    val excludeKeywords = mutableStateListOf<String>()
    // ...
    fun toDefinition(): FeedDefinition = ...
}
```

- Stateless composable: `FeedBuilderDialog(initialDefinition, onSave, onDismiss)`
- Internal state via `rememberFeedBuilderState(initial)`
- Emoji picker: simple grid of common emojis in `FlowRow` (use Emoji.kt data library for full Unicode set)
- Author autocomplete via ViewModel (never query LocalCache directly from composable)
- `dismissOnBackPress = true, dismissOnClickOutside = false` (prevent accidental data loss)
- Material3: `AlertDialog` or `Dialog` with `surface` bg

### Phase 5: Search -> Feed Bridge

**Location:** `commons/src/commonMain/.../feeds/custom/`

- `SearchQuery.toFeedDefinition()` extension
- Maps hashtag operators -> `FeedSource.Filter.hashtags`
- Maps from: operators -> `FeedSource.Filter.authors`
- Maps relay: operators -> `FeedSource.Filter.relays`
- Maps exclude operators -> excludeAuthors/excludeKeywords
- Maps kind: operators -> `FeedSource.Filter.kinds`
- "Save as Feed" button in search results UI
- Uses `FeedDefinitionBuilder` DSL internally

### Phase 6: DVM Marketplace

**Location:** `desktopApp/src/jvmMain/.../feeds/`

- Browse kind 31990 `AppDefinitionEvent` filtered by `isTaggedKind(5300)` (existing Quartz class)
- List with name, description, author, cost indicator
- Preview: send kind 5300 request, show results in preview panel
- "Add to My Feeds" creates `FeedSource.DVM(address)` entry
- Zap confirm popup when kind 7000 status = "payment-required" with `firstAmount()` + invoice
- DVM request goes to DVM's advertised relays (from kind 31990 `relay` tags)
- Response subscription listens on both user's relays AND DVM's relays
- Use `MetadataPreloader` for bulk-fetching author metadata of returned notes
- Reuse existing `DvmContentDiscoveryRequestEvent.build()` pattern from Quartz

### Phase 7: Publish/Import (kind 31890)

**Location:** `quartz/src/commonMain/.../feedDefinition/` (event type) + `commons` (UI)

- `FeedDefinitionEvent` extends `BaseAddressableEvent` (kind 31890)
- d-tag = feed UUID, content = JSON FeedSource, tags for discoverability
- Serialize `FeedDefinition` -> event via `FeedDefinitionEvent.build(signer, definition)`
- Parse kind 31890 events -> `FeedDefinition` via content JSON deserialization
- "Publish to Relays" action in feed context menu (signs + publishes)
- "Copy naddr" via `NAddress.create(31890, pubkey, dTag, relays)`
- Import: `Nip19Parser` detects naddr with kind 31890 -> fetch event -> render feed card preview
- "Add to My Feeds" clones with new UUID (marks as not-published-by-me)
- On login: subscribe to own kind 31890 + kind 10090 to restore from relay

### Cross-Device Sync (kind 10090)

- Sync pinned feed addresses with existing `FavoriteAlgoFeedsListEvent` (kind 10090)
- On pin/unpin, update kind 10090 event with current pinned feed addresses
- On login/restore, fetch own kind 10090, resolve `AddressBookmark` entries, populate sidebar
- This reuses the existing protocol — no new event kind needed for pin sync

## File Map (expected new files)

```
commons/src/commonMain/kotlin/.../feeds/custom/
  FeedDefinition.kt              # @Immutable data class + FeedSource + RefreshMode
  FeedDefinitionRepository.kt    # StateFlow-based, account-scoped
  FeedDefinitionSerializer.kt    # JSON serialization (exhaustive when)
  FeedDefinitionBuilder.kt       # DSL for tests + SearchQuery bridge
  GroupedFeeds.kt                # @Immutable pre-computed grouping
  FeedEvent.kt                   # SharedFlow events (PinLimitReached, etc.)
  SearchQueryToFeed.kt           # SearchQuery.toFeedDefinition() extension
  filters/
    CustomFilterFeedFilter.kt    # AdditiveComplexFeedFilter for FeedSource.Filter
    FeedFilterFactory.kt         # FeedSource -> IFeedFilter<Note> mapping
  assemblers/
    CustomFeedFilterAssembler.kt # FeedSource.Filter -> relay Filter
    PeopleListFilterAssembler.kt # Resolve ATag -> author set -> Filter
    DvmFeedSubscribable.kt       # NIP-90 request/response lifecycle
  ui/
    FeedBuilderDialog.kt         # Shared composable (stateless)
    FeedBuilderState.kt          # @Stable state holder
    FeedCard.kt                  # Feed preview card
    EmojiPicker.kt               # Simple emoji grid (FlowRow)

desktopApp/src/jvmMain/kotlin/.../deck/
  FeedSidebarSection.kt          # Pinned feeds in sidebar
  FeedDrawerTab.kt               # FEEDS tab content
  DvmMarketplace.kt              # DVM browse UI

quartz/src/commonMain/kotlin/.../feedDefinition/
  FeedDefinitionEvent.kt         # kind 31890 (BaseAddressableEvent)

commons/src/commonTest/kotlin/.../feeds/custom/
  FeedDefinitionSerializerTest.kt
  FeedDefinitionBuilderTest.kt
  SearchQueryToFeedTest.kt
  CustomFeedFilterAssemblerTest.kt
```

## Dependencies on Existing Code

| Component | Location | Usage |
|-----------|----------|-------|
| `TopFilter` | `amethyst/.../AccountSettings.kt` | Reference; `FeedSource.toTopFilter()` for bridge |
| `FavoriteAlgoFeedsOrchestrator` | `amethyst/.../algoFeeds/` | Extract to commons for DVM reuse |
| `FavoriteAlgoFeedsListEvent` (kind 10090) | `quartz/.../nip51Lists/` | Pinned feed sync |
| `DvmContentDiscoveryRequestEvent` | `quartz/.../nip90Dvms/` | DVM request building |
| `AppDefinitionEvent` (kind 31990) | `quartz/.../nip89AppHandlers/` | DVM marketplace discovery |
| `NAddress` | `quartz/.../nip19Bech32/entities/` | naddr encode/decode |
| `Nip19Parser` | `quartz/.../nip19Bech32/` | Detect pasted naddr |
| `SearchQuery` / `QueryParser` | `commons/.../search/` | Phase 5 bridge |
| `AppDrawer` / `AppDrawerTab` | `desktopApp/.../deck/AppDrawer.kt` | Phase 3 integration |
| `DeckSidebar` | `desktopApp/.../deck/DeckSidebar.kt` | Phase 2 integration |
| `PinnedNavBarState` | `desktopApp/.../deck/PinnedNavBarState.kt` | Reference pattern for pin state |
| `HomeFeed` rendering | `desktopApp/.../home/` | Phase 2 content reuse |
| `BaseAddressableEvent` | `quartz/.../nip01Core/core/` | Base for kind 31890 |
| `FollowSetEvent` / `InterestSetEvent` | `quartz/.../nip51Lists/` | Resolve ATag -> members |
| `MetadataPreloader` | `commons/.../relayClient/` | Bulk metadata fetch for feed results |
| `FeedMetadataCoordinator` | `commons/.../relayClient/` | Coordinate metadata for visible notes |
| `ComposeSubscriptionManager` | `commons/.../relayClient/` | Subscription lifecycle |

## External Dependencies

| Library | Version | Usage |
|---------|---------|-------|
| `sh.calvin.reorderable:reorderable` | 3.1.0 | Drag-reorder in drawer LazyColumn |
| `org.kodein.emoji:emoji-compose` (Emoji.kt) | latest | Emoji data for picker grid |
| `kotlinx.collections.immutable` | (already in project) | ImmutableList for stability |

## Risk & Mitigations

| Risk | Mitigation |
|------|------------|
| DVM latency makes feeds feel broken | Show loading skeleton + "last refreshed" timestamp + EOSE state |
| Kind 31890 NIP still in draft | Keep publish optional; local-first always works |
| Sidebar drag-reorder complexity | Only 3 items — simple `detectDragGestures`, no library |
| Feed builder autocomplete for authors | Reuse existing user search via ViewModel (never direct LocalCache query) |
| Account switching breaks feed state | Account-scoped repository auto-swaps with account |
| Kind 10090 sync conflicts | Last-write-wins (same as other replaceable events) |
| Preferences 8KB limit (if used for temp storage) | JSON chunking pattern or switch to account serialization |
| DVM payment format inconsistency | Support bolt11 from amount tag + NIP-57 zap; show raw amount if format unclear |
