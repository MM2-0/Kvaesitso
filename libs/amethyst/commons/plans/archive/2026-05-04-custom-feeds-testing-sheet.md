# Custom Feeds — Testing Sheet

> **Status:** shipped — records completed test coverage for the custom-feeds phases that landed (data model, serializer, builder, search bridge, kind 31890); the matching `feeds/custom/` code and tests exist.
> _Audited 2026-06-30._

**Branch:** `feat/desktop-custom-feeds`
**Run:** `./gradlew :desktopApp:run`

## Prerequisites

Wiring is complete. `FeedDefinitionRepository` is provided via `CompositionLocalProvider` in `Main.kt`.
Default feeds (Following + Global) are loaded on startup.

---

## Phase 1: Data Model + Serialization (Unit Tests)

```bash
./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.feeds.custom.*"
```

| # | Test | Expected |
|---|------|----------|
| 1 | `FeedDefinitionSerializerTest.roundTripFilterSource` | Serialize/deserialize Filter with all fields |
| 2 | `FeedDefinitionSerializerTest.roundTripGlobalSource` | Global source round-trip |
| 3 | `FeedDefinitionSerializerTest.roundTripFollowingSource` | Following source round-trip |
| 4 | `FeedDefinitionSerializerTest.roundTripDvmSource` | DVM source round-trip |
| 5 | `FeedDefinitionSerializerTest.roundTripPeopleListSource` | PeopleList source round-trip |
| 6 | `FeedDefinitionSerializerTest.roundTripInterestSetSource` | InterestSet source round-trip |
| 7 | `FeedDefinitionSerializerTest.roundTripSingleRelaySource` | SingleRelay source round-trip |
| 8 | `FeedDefinitionSerializerTest.multipleFeeds` | Multiple feeds in single JSON |
| 9 | `FeedDefinitionSerializerTest.emptyJsonReturnsEmptyList` | Empty/blank input |
| 10 | `FeedDefinitionSerializerTest.defaultFeedsAreValid` | Default feeds (Following+Global) pinned |
| 11 | `SearchQueryToFeedTest.convertsHashtagsToFeedFilter` | SearchQuery hashtags -> FeedSource.Filter |
| 12 | `SearchQueryToFeedTest.convertsAuthorsToFeedFilter` | SearchQuery authors -> FeedSource.Filter |
| 13 | `SearchQueryToFeedTest.convertsExcludeTerms` | Exclude terms mapping |
| 14 | `SearchQueryToFeedTest.convertsKinds` | Kind mapping |
| 15 | `SearchQueryToFeedTest.canBecomeFeedWithHashtags` | canBecomeFeed = true |
| 16 | `SearchQueryToFeedTest.canBecomeFeedWithAuthors` | canBecomeFeed = true |
| 17 | `SearchQueryToFeedTest.canNotBecomeFeedEmpty` | canBecomeFeed = false |
| 18 | `SearchQueryToFeedTest.canNotBecomeFeedTextOnly` | canBecomeFeed = false |

---

## Phase 2/3: Sidebar + App Drawer Feeds Tab (Manual)

### App Drawer Integration

| # | Action | Expected |
|---|--------|----------|
| 1 | Open app drawer (Cmd+K) | Drawer opens, shows 3 tabs: Screens, Workspaces, Feeds |
| 2 | Click "Feeds" tab | Feed list appears (empty initially + defaults after wiring) |
| 3 | Cmd+Shift+F | Drawer opens directly on Feeds tab |
| 4 | Feeds tab shows sections | "Pinned", "My Feeds", "Algo Feeds" sections visible |
| 5 | Click a feed row | Drawer closes, main content switches to that feed |

### Feed CRUD (in Feeds tab)

| # | Action | Expected |
|---|--------|----------|
| 6 | Click "+ Create Feed" | Feed Builder dialog opens |
| 7 | Enter name + emoji + hashtags | Form validates (Save enabled when name + at least 1 source) |
| 8 | Click "Save" | Dialog closes, new feed appears in "My Feeds" section |
| 9 | Click "Pin" on a feed | Feed moves to "Pinned" section (max 3) |
| 10 | Try pinning a 4th feed | Pin fails (PinLimitReached event, button stays) |
| 11 | Click "Unpin" on a pinned feed | Feed moves back to "My Feeds" |

---

## Phase 4: Feed Builder Dialog (Manual)

| # | Action | Expected |
|---|--------|----------|
| 1 | Open builder, leave empty | "Save" button disabled |
| 2 | Enter name only | Still disabled (needs source) |
| 3 | Enter name + add hashtag "bitcoin" | "Save" enabled |
| 4 | Add multiple hashtags | All show as chips, removable by click |
| 5 | Add author pubkey | Shows as chip |
| 6 | Add relay URL | Shows as chip |
| 7 | Add exclude keyword | Shows as chip in exclude section |
| 8 | Toggle refresh mode (Live / Every 5 min) | Selection changes |
| 9 | Click "Cancel" | Dialog dismissed, no feed created |
| 10 | Click "Save" | Feed created with all specified params |

---

## Phase 5: Search -> Feed Bridge (Manual)

| # | Action | Expected |
|---|--------|----------|
| 1 | Search "#bitcoin" in search screen | Results appear |
| 2 | Programmatically: `SearchQuery(hashtags=listOf("bitcoin")).canBecomeFeed()` | Returns true |
| 3 | `query.toFeedDefinition("BTC", "₿")` | Creates FeedDefinition with FeedSource.Filter(hashtags=["bitcoin"]) |

*(Note: "Save as Feed" button in search UI is not yet wired — the bridge logic exists but UI button pending)*

---

## Phase 1.5: Custom Feed Content (Manual)

| # | Action | Expected |
|---|--------|----------|
| 1 | Create feed with hashtag "nostr" | Feed created |
| 2 | Select that feed from drawer | Content area shows FeedScreen |
| 3 | Wait for events | Notes containing #nostr appear (from relay subscription) |
| 4 | Create feed with author pubkey | Only notes from that author appear |
| 5 | Create feed with excludeKeyword "spam" | Notes containing "spam" filtered out |
| 6 | Switch between feeds | Content updates, old subscription closed |
| 7 | Feed with specific relay URL | Subscription targets only that relay |

---

## Phase 7: Kind 31890 Event (Compile-level)

| # | Check | Expected |
|---|-------|----------|
| 1 | `FeedDefinitionEvent.KIND == 31890` | Correct |
| 2 | `FeedDefinitionEvent` extends `BaseAddressableEvent` | Has `dTag()`, `address()`, `addressTag()` |
| 3 | `title()` extracts from tags | Parses `["title", "..."]` tag |
| 4 | `emoji()` extracts from tags | Parses `["emoji", "..."]` tag |
| 5 | `feedConfigJson()` returns content | JSON-serialized FeedSource |

---

## Compilation Verification

```bash
# Full compile (all modules)
./gradlew :quartz:compileKotlinJvm :commons:compileKotlinJvm :desktopApp:compileKotlin

# Unit tests
./gradlew :commons:jvmTest --tests "com.vitorpamplona.amethyst.commons.feeds.custom.*"

# Code formatting
./gradlew spotlessApply
```

---

## Wiring TODO (Required for Manual Testing)

The `FeedDefinitionRepository` needs to be instantiated and provided via `CompositionLocalProvider` in `Main.kt`. Steps:

1. In `Main.kt` `App()` function (around line 710), create:
```kotlin
val feedRepository = remember { FeedDefinitionRepository(appScope) }
LaunchedEffect(Unit) { feedRepository.load(defaultFeeds()) }
```

2. In the `CompositionLocalProvider` block (around line 1166), add:
```kotlin
LocalFeedRepository provides feedRepository,
LocalFeedScope provides appScope,
```

3. This enables:
   - `FeedsDrawerTab` to read/write feeds
   - `CustomFeedScreen` to resolve feed definitions by ID

---

## Known Limitations (not bugs)

| Item | Status |
|------|--------|
| DVM marketplace (Phase 6) | Button wired, content shows "coming soon" |
| PeopleList/InterestSet resolution | Shows "coming soon" — needs ATag resolution |
| Kind 10090 cross-device sync | Not wired yet — local-only |
| Feed publish/import (naddr) | Event class exists, publish UI not wired |
| Sidebar pinned feed emojis | Needs `PinnedNavBarState` integration for feed-specific items |
| Account-scoped persistence | Currently in-memory only (needs serialize to account settings) |
| Cmd+1/2/3 shortcuts | Not wired yet (needs MenuBar items in Main.kt) |
| Drag-to-reorder | Repository supports it, UI gesture handlers pending |
