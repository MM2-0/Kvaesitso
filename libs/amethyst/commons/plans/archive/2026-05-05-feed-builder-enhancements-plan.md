# Feed Builder Enhancements

> **Status:** shipped — all 6 enhancements (npub decode, author search, kind checkboxes, edit/delete, "Save as Feed", exclude authors) are present in `FeedBuilderDialog.kt`, `FeedsDrawerTab.kt`, and `SearchScreen.kt`.
> _Audited 2026-06-30._

**Date:** 2026-05-05
**Status:** Implementation

## Scope

Enhance `FeedBuilderDialog` with 6 items:
1. npub auto-decode (paste npub -> hex + display name)
2. Author search (type name -> search LocalCache -> pick)
3. Kind filter checkboxes (Notes, Reposts, Articles)
4. Edit + Delete feeds (CRUD completeness)
5. "Save as Feed" button in search results
6. Exclude authors field in dialog

## Implementation

### 1. npub Auto-Decode

**Where:** `FeedBuilderDialog.kt` ChipInputField for Authors

**Logic:**
- On add: if input starts with `npub1`, decode via `decodePublicKeyAsHexOrNull()`
- If decode succeeds, add hex to authors list
- Show display name in chip (resolve from LocalCache)

### 2. Author Search

**Where:** New `AuthorSearchField` composable in `FeedBuilderDialog.kt`

**Flow:**
- Replace plain text field for authors with search field
- Type >= 2 chars -> filter `DesktopLocalCache.users` by displayName/nip05
- Show dropdown with matching profiles (avatar placeholder + name + npub short)
- Click adds hex to authors list
- Still allow raw npub/hex paste (falls through to auto-decode)

**Data source:** `DesktopLocalCache.users` (already populated from metadata subscriptions)

### 3. Kind Filter Checkboxes

**Where:** `FeedBuilderDialog.kt`, new section between relays and exclude

**UI:** FlowRow of FilterChips:
- [x] Notes (kind 1)
- [x] Reposts (kind 6, 16)
- [ ] Articles (kind 30023)
- [ ] Highlights (kind 9802)
- [ ] Reactions (kind 7)

Default: Notes + Reposts checked. Maps to `FeedBuilderState.kinds`.

### 4. Edit + Delete in Drawer

**Where:** `FeedsDrawerTab.kt`

**Edit:** Add edit button on each FeedRow -> opens `FeedBuilderDialog(initial = feed)`
- On save: calls `feedRepository.update(feed)`

**Delete:** Add delete in context or as swipe action
- Confirm dialog: "Delete feed X?"
- Calls `feedRepository.delete(feed.id)`

### 5. "Save as Feed" from Search

**Where:** `SearchScreen.kt` or `SearchResultsList.kt`

**UI:** Show "Save as Feed" button when `query.canBecomeFeed()` is true
- Button appears in the header area, next to the search bar actions
- Click opens `FeedBuilderDialog` pre-filled from `query.toFeedDefinition()`

### 6. Exclude Authors in Dialog

**Where:** `FeedBuilderDialog.kt`

**UI:** Same chip input pattern as regular authors but for excludes
- Uses same author search/npub-decode logic
- Maps to `FeedBuilderState.excludeAuthors`

## File Changes

| File | Change |
|------|--------|
| `FeedBuilderDialog.kt` | Author search field, npub decode, kind checkboxes, exclude authors |
| `FeedsDrawerTab.kt` | Edit/delete buttons on feed rows |
| `SearchScreen.kt` | "Save as Feed" button |
| `FeedBuilderState.kt` | No changes needed (already has all fields) |

## Dependencies

- `decodePublicKeyAsHexOrNull` from `quartz/nip19Bech32`
- `DesktopLocalCache.users` for profile search
- `SearchQuery.canBecomeFeed()` + `toFeedDefinition()` already exist
