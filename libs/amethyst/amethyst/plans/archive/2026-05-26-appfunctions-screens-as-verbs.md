# All Amethyst screens as AppFunctions / MCP endpoints

> **Status:** shipped — 46 `@AppFunction` verbs ship in `AmethystAppFunctions.kt`, exceeding the screen-to-verb surface this plan proposed.
> _Audited 2026-06-30._

**Date:** 2026-05-26
**Status:** Active — informs the v1 read-verb surface and guides
future MCP work

## The principle

Every screen in Amethyst has a dedicated `FeedContentState` driven by
a `*FeedFilter` that reads from `LocalCache`. The list is in
`amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/AccountFeedContentStates.kt`
— there are ~30 entries today.

> Every Amethyst screen → one AppFunction verb. The verb invokes the
> same `*FeedFilter` the screen uses, runs `feed()` against
> `LocalCache`, and projects the result into a Gemini-friendly
> `NoteHit` / `ProfileHit` / etc.

This keeps the agent surface in sync with what the user sees, with no
duplicate filtering logic.

## Why this works

* `*FeedFilter.feed()` is stateless and idempotent — it reads
  `LocalCache` (a global singleton) and `account` state. Safe to
  invoke from any thread, any process state, no UI lifecycle
  required.
* `AccountFeedContentStates` itself is owned by `AccountViewModel`,
  but we don't need the precached state — we just need the filter
  class. Invoking it on each AppFunction call is acceptable (a few
  ms even on large caches).
* The catch: `LocalCache` only contains what the foreground app
  subscriptions have already fetched. If the user hasn't opened
  Amethyst in days, the cache may be sparse. Acceptable trade-off:
  the agent reflects "what's on your screen now", not "what exists
  on Nostr right now". For freshness, the user can open the app or
  the verb can fall back to a relay drain.

## Existing verb → feed mapping

| AppFunction verb | Feed source | Notes |
|---|---|---|
| `getFeedDigest` | `HomeNewThreadFeedFilter` | Matches the home page (new threads only, all kinds, mute-filtered) |
| `getRecentFromFollows` | direct `INostrClient.fetchAll` (kind:1 only) | Pure kind:1 from kind:3 follows. Different shape than home — keeping both: `getRecentFromFollows` is fast / always fresh, `getFeedDigest` is "what's on my screen" |
| `getMyMentions` | direct relay drain | Could move to `NotificationFeedFilter` |
| `getRecentDms` | direct relay drain + decrypt | Could move to `ChatroomListKnownFeedFilter` / `ChatroomListNewFeedFilter` |
| `getLiveStreams` | direct relay drain | Could move to `LiveStreamsFeedFilter` |
| `searchArticles` | direct relay drain (NIP-50) | Read-side only; users already in cache via `ArticlesFeedFilter` could be merged |

## Unmapped feeds (proposed verbs)

These all have existing `FeedContentState`s. Adding a verb each is
~30 lines of glue.

| Screen | FeedContentState | Proposed verb name | User intent |
|---|---|---|---|
| Home — replies | `homeReplies` | `getRecentReplies` | "what conversations am I in?" |
| Home — everything | `homeEverything` | `getEverythingFeed` | "the full firehose of my follows" |
| Home — live | `homeLive` | `getLiveActivityFromFollows` | "what's live from my follows?" |
| Video | `videoFeed` | `getVideoFeed` | "show me Nostr videos" |
| Pictures | `picturesFeed` | `getPictureFeed` | "what photos are people posting?" |
| Shorts | `shortsFeed` | `getShortVideoFeed` | NIP-71 short video |
| Long-form (your follows) | `longsFeed` | `getLongFormFromFollows` | "what articles are my follows publishing?" |
| Long-form (discover) | `discoverReads` | `discoverArticles` | "find interesting Nostr articles" |
| Marketplace | `discoverMarketplace` | `discoverMarketplaceListings` | "what's for sale on Nostr?" |
| Communities (discover) | `discoverCommunities` | `discoverCommunities` | "find Nostr communities" |
| Communities (list) | `communitiesList` | `getMyCommunities` | "communities I'm a member of" |
| Public chats (discover) | `discoverPublicChats` | `discoverPublicChats` | "find Nostr chat channels" |
| Public chats (list) | `publicChatsFeed` | `getMyPublicChats` | "chats I'm in" |
| DVMs | `discoverDVMs` | `discoverDvms` | "what compute services are available?" |
| Follow sets | `discoverFollowSets` | `discoverFollowSets` | "find curated follow lists" |
| Live streams | `liveStreamsFeed` | (replace `getLiveStreams`) | already exists |
| Nests | `nestsFeed` | `getNests` | "audio rooms" |
| Articles (mine + follows) | `articlesFeed` | `getMyArticles` | combined long-form |
| Polls (open) | `openPollsFeed` | `getOpenPolls` | "what should I vote on?" |
| Polls (closed) | `closedPollsFeed` | `getRecentPollResults` | "what did people vote on?" |
| All polls | `pollsFeed` | (combined; less useful as a verb) | — |
| Badges | `badgesFeed` | `getBadges` | "show me my Nostr badges" |
| Software apps | `softwareAppsFeed` | `discoverNostrApps` | "what apps exist on Nostr?" |
| Emoji packs | `browseEmojiSetsFeed` | `discoverEmojiPacks` | "find custom emoji" |
| Follow packs | `followPacksFeed` | `discoverFollowPacks` | "find people to follow by topic" |
| Products | `productsFeed` | `getProductListings` | "what products are listed?" |
| Calendar appointments | `calendarAppointmentsFeed` | `getUpcomingEvents` | "what Nostr events are coming up?" |
| Calendar collections | `calendarCollectionsFeed` | `getEventCollections` | "what conferences are happening?" |
| Notifications (all) | `notifications` | `getRecentNotifications` | "what's happened to me on Nostr?" |
| Notifications (follows) | `notificationsFollowing` | (variant param) | "notifications from follows" |
| Notifications (everyone) | `notificationsEveryone` | (variant param) | "all notifications" |
| Drafts | `drafts` | `getMyDrafts` | "what did I start writing?" |
| Web bookmarks | `webBookmarks` | `getMyBookmarks` | "what did I bookmark?" |

That's ~25 unmapped feeds. Each verb is a ~30-line wrapper following
the `getFeedDigest` shape — read filter, project to result type,
return.

## Implementation pattern

```kotlin
@AppFunction(isDescribedByKDoc = true)
suspend fun getMyBookmarks(
    appFunctionContext: AppFunctionContext,
    hoursBack: Int = 168,
    maxNotes: Int = 50,
): SearchNotesResult {
    val account = Amethyst.instance.sessionManager.loggedInAccount()
        ?: return SearchNotesResult.empty()
    val sinceSecs = TimeUtils.now() - hoursBack.coerceIn(1, 24 * 365).toLong() * 3600L

    val feed = WebBookmarkFeedFilter(account).feed()
        .asSequence()
        .mapNotNull { it.event }
        .filter { it.createdAt >= sinceSecs }
        .sortedByDescending { it.createdAt }
        .take(maxNotes.coerceIn(1, 200))
        .map { it.toFeedNoteHit() }
        .toList()

    return SearchNotesResult(matches = feed)
}
```

The pattern is genuinely uniform. Most verbs would even share a
helper like `feedAsResult(filter, sinceHours, max) -> SearchNotesResult`.

## Result-type strategy

Most feeds project to `SearchNotesResult` since the screen is "a list
of notes." A few need bespoke types:
* Notifications — could return a `NotificationHit` carrying the
  notification kind (reply, mention, zap, repost, reaction) since
  the LLM needs to know "you got 3 zaps and 1 reply".
* Calendar events — natural fit for an `EventHit` with start/end
  times.
* Communities / public chats — list-of-rooms more than list-of-notes.

Default to reusing `NoteHit` (now carries `kind`); add bespoke types
only when the LLM needs structure the LLM can't derive from `kind` +
`content`.

## What doesn't fit cleanly

Some screens are too interactive for a single AppFunction call:
* **Chats / DMs** — sending and reading a stream of messages is
  more conversational; the agent loop should handle it. `sendDm` +
  `getRecentDms` cover the basics.
* **Profile pages** — already covered by `getProfile` +
  `getNotesByUser` rather than a "profile feed."
* **Settings screens** — out of scope; the agent shouldn't mutate
  user prefs.

## When the cache is cold

For verbs backed by `LocalCache` (the screens), the result is sparse
when the user hasn't opened the app recently. The mitigation strategy
is:

1. The relay-drain verbs (`searchProfiles`, `searchNotes`,
   `searchByHashtag`, `searchArticles`, `getRecentFromFollows`,
   `getNotesByUser`, `getProfile`, `getRecentDms`,
   `getZapsReceived`, `getLiveStreams`) all do their own fetch.
   Use these when freshness matters.
2. The screen-mirror verbs (`getFeedDigest` and the proposed
   additions) reflect what the foreground saw. Use these when
   "what was the user looking at?" is the semantic.

Both shapes have value. The agent's prompt-matching kdoc decides
which gets called.

## MCP angle

When we add an MCP server for Amethyst (separate effort), the same
`*FeedFilter.feed()` calls power the MCP tool implementations. The
AppFunctions adapter and the MCP server share the projection
helpers (`toFeedNoteHit`, `toProfileHit`, etc.) and the result
types. The transport layer is the only difference.

The screen-feed mapping above is the source of truth for both
surfaces.

## Concrete next steps

Highest user-value follow-ups (each ~30 min):

1. `getRecentNotifications` — answers "what's been happening to me
   on Nostr?" without a per-kind walk.
2. `getMyBookmarks` — agent recall over saved Nostr content.
3. `getOpenPolls` — "what should I vote on?"
4. `getMyDrafts` — "what was I writing?"
5. `getUpcomingEvents` — calendar / agenda integration.

After these, the rest are mostly "discover X" variants that follow
the same template.
