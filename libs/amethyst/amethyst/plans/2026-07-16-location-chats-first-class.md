# Making location (geohash) chats first-class in Amethyst

Builds on `2026-07-15-bitchat-geohash-interop.md` (Phase 1 shipped: protocol,
geo-relay routing, a self-contained chat screen, `amy geochat`). This plan takes
it from a bolt-on screen to a native feature woven into Home, Messages, and the
map.

## The one constraint that shapes everything

Geohash **chat** (kind 20000) is signed with anonymous per-cell throwaway keys,
unlinkable to npubs. So "which of my follows are chatting here" is **not
derivable from the chat stream**. Any "follows are active near you" signal must
come from a *linkable* source:

- **kind-1 geohash notes** (`GeoHashFeedFilter` scans `LocalCache.notes` for
  `isTaggedGeoHash`; authors are real npubs) → intersect with
  `account.kind3FollowList` = genuine "follows near this place." This is the
  template `HomeLiveFilter.followsThatParticipateOn` already uses, just sourced
  from notes instead of chat events.
- **kind-10081 geohash follow lists** (`GeohashListEvent`) — the user's own, and
  optionally follows' public lists.
- Anonymous **liveliness** (kind-20000/20001 presence counts) — "N people here",
  no identities.

The Home bubble is therefore: *anonymous liveliness + follows' geo-note activity
→ tap into the cell's chat.* We will not imply the chat reveals who's there.

## Key architectural decision: reuse what exists

1. **Joined channels = the geohash follow list (kind 10081).** `account.geohashList`
   (`model/nip51Lists/geohashLists/GeohashListState.kt`, `flow: StateFlow<Set<String>>`,
   `follow`/`unfollow`, NIP-44-private capable) already models "geohashes I care
   about." Treat *following a geohash* as *joining its location channel*. No new
   list type, and it's private-capable. (Trade-off: today it also drives the
   kind-1 notes feed; we're overloading one list for both "notes near here" and
   "chat here." Acceptable — it's the same user intent. Alternative if we want
   separation: a local, unpublished joined-set matching Bitchat's ephemerality.)

2. **Make geohash chat LocalCache-backed** so it can flow through the same feed
   machinery as every other room. This is the crux of "first-class": the Messages
   list, Home bubbles, unread counts, and pins all read from `LocalCache`
   channels. Model it on the ephemeral-chat feature end to end.

## Phase A — Data model + LocalCache integration (foundation)

- `commons/.../model/geohashChat/GeohashChatChannel.kt` — a `Channel` subtype
  keyed by geohash (mirror `model/emphChat/EphemeralChatChannel.kt`).
  `toBestDisplayName()` returns the reverse-geocoded place (or `#geohash`).
- `LocalCache`: add a `geohashChannels` map + a consumer that routes
  `GeohashChatEvent`/`GeohashPresenceEvent` into the channel (mirror
  `ephemeralChannels`/`liveChatChannels` + `getOrCreateEphemeralChannel`).
- A rooms-list **subassembler** that subscribes to the joined geohashes
  (`account.geohashList.flow` → `GeoRelayDirectory.closestRelays(g)`, kinds
  20000/20001, `#g`) and feeds LocalCache — mirror
  `chats/rooms/datasource/FollowingEphemeralChatSubAssembler.kt` +
  `FilterFollowingEphemeralChats.kt`.
- Migrate `GeohashChatViewModel` to read the channel's notes from LocalCache
  (via `LocalCache.observeNotes` like `NestLobbyScreen`) instead of its private
  subscription — unifies the live view with the cached one and lets the screen
  reuse the full `ChatroomMessageCompose` (reactions, replies) later. Keep the
  send path (per-geohash signer + PoW + geo relays) as-is.
- Ephemeral caveat: relays don't store kind 20000, so joined-cell rooms only show
  messages seen while subscribed. Scope background subscriptions to *joined* cells
  (+ the current-location cell) to bound battery/relay load; document the cap.

## Phase B — Messages tab

- `chats/rooms/dal/ChatroomListKnownFeedFilter.kt` — add a 7th family
  (`geohashChannels` from `account.geohashList`) to `feed()` + `applyFilter`
  (newest message per cell), mirroring `filterRelevantEphemeralChats`.
- `chats/rooms/ChatroomHeaderCompose.kt` — add a `GeohashRoomCompose` branch in
  `ChatroomEntry` → `nav.nav(Route.GeohashChat(geohash))`, with a location-pin
  `HeaderPill`, the cell name (`LoadCityName`), and a live participant count (no
  avatars — anonymous).
- `chats/rooms/NewConversationScreen.kt` — append one `ConversationType`
  ("Location chat", geohash icon/accent, pros/cons, `route = Route.NewGeohashChat`)
  to the *Relay* section of `conversationSections` (single source of truth).

## Phase C — The builder ("+" → create/join)

- `Route.NewGeohashChat` + `NewGeohashChatScreen.kt` (mirror
  `ephemChat/metadata/NewEphemeralChatScreen.kt`). Three ways to pick a cell:
  1. **Current location levels.** New `GeohashChannelLevel` mapper
     (region=2, province=4, city=5, neighborhood=6, block=7, building=8 chars —
     the Bitchat levels; `GeohashPrecision` has the char counts but not the
     names). From `LocationState.geohashStateFlow` (raise its hardcoded 5-char
     precision to 8 so we can truncate to each level), list the six cells with
     `LoadCityName` + a Join/Open button.
  2. **Manual geohash** text field (validate against the base32 alphabet).
  3. **Teleport** → the map picker (Phase E).
- Join = `account.geohashList.follow(geohash)` then `nav.nav(Route.GeohashChat)`.
- Reuse `LocationAsHash`/`ILocationGrabber` for the permission flow.

### Geohash-list management (the kind-10081 add/remove UI)

Today the **only** way to add to the kind-10081 list is the Follow toggle on the
kind-1 `GeoHashScreen` — you must already be viewing that cell. Followed cells
then appear as read-only feed chips in the Home top-nav (`TopNavFilterState`).
There is **no** screen to view the list, remove entries, or add an *arbitrary*
geohash. This builder is that missing "add" UI (it writes via
`account.geohashList.follow`, the same path). Round it out with a small manage
screen:

- `NewGeohashChatScreen` doubles as the **add** surface (current-location levels /
  manual / map).
- Add a lightweight **"My location channels"** list (its own route, or a section
  in the builder): render `account.geohashList.flow` with `LoadCityName` per cell,
  a remove (`unfollowGeohash`) swipe/menu, and an "add" button into the builder.
  This is also what Phase B's Messages rows and Phase D's Home bubble read from,
  so it's the one management surface for the whole feature.

## Phase D — Home "live near you" bubble

- New feed state `homeGeohashLive` in `AccountFeedContentStates.kt` (parallel to
  `homeLive`; different signal source, so not folded into `HomeLiveFilter`).
  Sources: joined cells with recent activity (LocalCache, post Phase A) + the
  current-location cell.
- `home/live/RenderGeohashBubble.kt` — a bubble showing the cell name, a
  liveliness dot (reuse `LiveStatusIndicator` pattern; "online" = recent presence),
  and social proof from **geo-notes**: "N follows posted near · M chatting"
  (follows-near count = `GeoHashFeedFilter` authors ∩ `kind3FollowList`).
- `HomeScreen.kt` `DisplayLiveBubbles` — add the `GeohashChatChannel` (or a
  synthetic geohash item) case to the type dispatch; click → `Route.GeohashChat`.

## Phase E — Teleport + map

- `LocationPickerMap.kt` — extend the display-only osmdroid `LocationPreviewMap`
  with a `MapEventsOverlay`/`MapEventsReceiver` so long-press/tap drops a pin and
  yields a coordinate → `GeoHash.encode(lat, lon, level)`. osmdroid (Apache-2.0)
  already supports this; only the wiring is new.
- Teleport screen (or a mode in `NewGeohashChatScreen`): pick a point on the map,
  show the resulting cell name + level selector, Open → `Route.GeohashChat`.
- **Auto-teleport flag:** in `GeohashChatViewModel`, compare the channel's geohash
  to the current-location cell (`LocationState`); when they differ (or no
  permission), pass `teleported = true` to the already-plumbed
  `sendMessage(..., teleported)`. Add a manual override toggle in the composer.
- Optional: forward geocoding (place-name search) via
  `Geocoder.getFromLocationName` — none exists today; small addition for a
  "search a place" box.

## Phase F — Privacy, presence, polish

- **"Post as my real account" opt-in** (global or per-channel, with a
  location-exposure warning). This is also the *only* way a follow becomes
  visible in the chat itself — relevant to the Home social-proof story.
- **Presence heartbeats:** emit kind 20001 periodically while a channel is open
  (`GeohashChatViewModel.announcePresence` already exists; schedule it).
- **Privacy note:** subscribing to a cell reveals interest in that location to
  its relays (via the `#g` REQ from your IP); Tor mitigates. The kind-10081 list
  can stay NIP-44-private.
- Extract hardcoded screen strings to `strings.xml` (`<plurals>` for the counts —
  see `res/CLAUDE.md`); desktop `GeohashChatScreen` equivalent.

## Open decisions (need a call)

1. **Joined list:** reuse kind-10081 geohash follow list (recommended, private,
   already wired) vs. a separate local ephemeral joined-set (closer to Bitchat).
2. **LocalCache integration depth:** full (Phase A — enables Messages/Home/unread,
   bigger) vs. keep the self-contained screen and only add the builder + a
   Home bubble fed by geo-notes (smaller, but not truly "in the Messages list").
3. **Default identity in these rooms:** stays anonymous per-cell (recommended);
   the real-account opt-in is Phase F.

## Verification

- Unit: `GeohashChannelLevel` bucketing, the joined-list ↔ subscription wiring,
  the geo-note follow-intersection count.
- `amy geochat` remains the wire-level interop check against a real Bitchat cell.
- Drive the app: join a cell from the "+" chooser, confirm it appears in Messages,
  post/receive, teleport via the map and confirm the `["t","teleport"]` tag, and
  confirm the Home bubble reflects a follow's kind-1 geo-note near you.
