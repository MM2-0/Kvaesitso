# Concord — Mobile Integration Plan (mirroring NIP-29 Relay Groups)

## Context

The Concord protocol engine is complete in `quartz/…/concord/` (CORD-01…07,
~65 tests) and driven end-to-end by the `amy concord` CLI over a commons
`ConcordActions` layer. This plan covers the **Android app integration**, and it
deliberately **mirrors the just-merged NIP-29 relay-groups feature** — that work
used Soapbox's Armada as a study base and established the exact Amethyst touch
points a group-chat protocol should plug into. Wherever possible we clone the
NIP-29 file structure with Concord equivalents rather than inventing parallels.

Naming: user-facing = **"Concord Channels"** (Amethyst reserves "community" for
NIP-72). Protocol-internal code keeps the spec term `community`.

## The one structural difference from NIP-29

NIP-29 group metadata (kind 39000) is **relay-signed and public**, so groups are
browsable. Concord communities are **end-to-end encrypted**: the only public
artifact is the addressable kind-33301 invite **bundle**, whose content is
token-gated. Consequences for the mirror:

- **Addressing** is by *derived stream pubkey* (`group_key.pk` per plane/epoch),
  not `(hostRelay, groupId)`. A Concord channel lives at its plane address and
  may be mirrored on several relays (the community's relay set), not pinned to
  one host. So `ConcordChannel.relays()` = the community relay set.
- **Discovery** cannot preview E2EE content. The discovery feed surfaces **public
  invite links** (kind-33301 bundles + links shared in notes), filtered by
  author/hashtag — the entry action is *redeem a link*, not *browse contents*.
  This is a genuinely thinner surface than NIP-29; documented, not a bug.
- **Membership = key possession**, verified locally from the folded Control Plane
  + banlist (already implemented), not from relay-signed 39001/39002.

## Per-account persistence & subscription model (Concord is between NIP-17 and NIP-28/29)

Separate **addressing** from **encryption/membership** and Concord's place is clear:

| Concern | NIP-28 | NIP-29 | NIP-17 | **Concord** |
|---|---|---|---|---|
| Find messages by | channel id | `(relay, h)` | `#p = me` | **`authors=[derived plane pk]`** |
| Content | public | public | E2EE to you | **E2EE to a shared key** |
| Decrypt with | — | — | your key | **per-channel derived conv key** |
| Membership | open | relay roster | key possession | **key possession** |
| "My rooms" home | follow list | kind-10009 | chatroom set | **kind-13302 (carries secrets)** |

The decisive point: a Concord wrap's `p` tag is **ephemeral**, so you can never
find messages with `#p = me` (the NIP-17 model). You subscribe **by author = the
derived plane pubkey** (NIP-28/29 addressing), a query only a secret-holder can
form, and decrypt with the shared plane key (NIP-17 E2EE).

**Home base = kind-13302 `ConcordCommunityList`** (built in quartz): NIP-44
self-encrypted, replaceable, relay-synced. Unlike NIP-17 (only secret is your
identity key) or NIP-29 (public group tags), **each entry carries the community
secrets** (`community_root`, salt, epoch, private-channel keys). Same trust model
as NIP-17's recoverable giftwrapped history: a leaked nsec exposes them, nothing
worse. `ConcordChannelListState` wraps 13302 exactly like `RelayGroupListState`
wraps 10009 / `EphemeralChatListState` wraps its list — **same wiring, entries
hold keys.**

**In-memory projection (LocalCache):** `ConcordChannel` keyed by
`(communityId, channelId)`, holding the folded Control-Plane state + decrypted
messages — recomputed from events, never persisted as identity (the NIP-28/29
half).

**Subscription = per-plane author REQ, fanned out from the joined list** — not a
single `#p=me` catch-all. `ConcordMyChannelsFilterAssembler` (mirrors NIP-29's
`RelayGroupMyJoinedGroupsFilterAssembler`) walks `account.concordChannelList`,
derives each community's control-plane + channel-plane addresses, and issues
`{kinds:[1059], authors:[planePk]}` per plane across the community's relays.

**Secrets at rest:** relay copy is self-NIP-44-encrypted (13302); the on-device
mirror can be wrapped with `commons/keystorage`.

## Layering (same as NIP-29)

- `quartz/…/concord/` — protocol (done)
- `commons/…/model/concord/` — `ConcordChannel`, `ConcordChannelListState`,
  membership/view-mode enums, discovery constraint (platform-agnostic)
- `amethyst/…/chats/publicChannels/concord/` — screens, feed filters, datasource
  subassemblers, navigation
- `commons/…/actions/ConcordActions.kt` — builders/filters/folding (done)
- `cli/…/commands/Concord*Commands.kt` — verbs (done; already matches the
  `RelayGroupCommands` route+verb-map pattern)

## Mirror map (NIP-29 file → Concord equivalent)

### commons state
- `model/nip29RelayGroups/RelayGroupChannel.kt` → **`model/concord/ConcordChannel.kt`**
  — a `Channel` subclass keyed by a `ConcordChannelId(communityId, channelId)`,
  holding the folded `ConcordCommunityState` + this channel's messages StateFlow,
  `relays()` = community relay set, `membershipOf()` from the authority resolver,
  `placeholderNote()`.
- `RelayGroupListState.kt` → **`model/concord/ConcordChannelListState.kt`** —
  backed by the **kind-13302** joined-communities list (already in quartz:
  `ConcordCommunityList`). Exposes `liveCommunities: StateFlow<List<Entry>>` and
  `liveServers: StateFlow<Set<communityId>>`. `join(community)`/`leave` do
  read-modify-write of the 13302 event. Mirrors `EphemeralChatListState`.
- `RelayGroupMembership.kt` → **`ConcordMembership.kt`** (OWNER/ADMIN/MEMBER/BANNED/
  NONE) derived from `AuthorityResolver` (rank + banlist).
- `RelayGroupViewMode.kt` → **`ConcordViewMode.kt`** (INLINE/GROUPED).
- `model/nip29RelayGroups/GroupDiscoveryConstraint.kt` → **`ConcordDiscoveryConstraint.kt`**
  (AllPublic / ByPeople / ByHashtags) matching against a public invite bundle.

### Account wiring (`amethyst/…/model/Account.kt`)
Add right after the `relayGroupList` lines (~382): a
`ConcordChannelListState(signer, cache, decryptionCache, scope, settings)` field
+ its decryption cache. Action methods next to `joinRelayGroup` (~1472):
`createConcordCommunity`, `joinConcordFromLink`, `postConcordMessage`,
`createConcordInvite`, `banConcordMember`, `follow/unfollow(ConcordChannel)` →
delegate to `ConcordChannelListState`. Writes go through the community relay set.
Add `concordViewMode` to `AccountSettings.kt`.

### LocalCache (`amethyst/…/model/LocalCache.kt`)
Add a `LargeCache<ConcordChannelId, ConcordChannel>` index + `getOrCreateConcordChannel`,
and route inbound kind-1059 wraps on known plane addresses into the fold (decrypt
→ edition/message). Mirrors `getOrCreateRelayGroupChannel`.

### Messages inbox integration (THE key mirror)
- `chats/rooms/dal/ChatroomListKnownFeedFilter.kt` + `ChatroomListNewFeedFilter.kt`
  — extend the 5-way `feed()` concatenation to **6-way**: add a `concordChannels`
  block reading `account.concordChannelList.liveCommunities`, branching on
  `concordViewMode` (INLINE = one row per channel via
  `LocalCache.getOrCreateConcordChannel(...).newestChatNote() ?: placeholderNote()`;
  GROUPED = one synthetic `ConcordServerRoomNote(communityId, newest)` per
  community). Update `applyFilter`/`updateListWith` with a
  `filterRelevantConcordMessages(...)` keyed by `concordRowKey()`.
- `chats/rooms/dal/RelayGroupServerRoomNote.kt` → **`ConcordServerRoomNote.kt`** —
  synthetic event-less Note collapsing a community's channels into one inbox row.
- `chats/rooms/ChatroomHeaderCompose.kt` — add `rendersWithoutEvent` branches for
  `ConcordServerRoomNote` and channel placeholders; `ConcordServerRoomCompose` →
  `Route.ConcordServer(communityId)`; `ConcordRoomCompose` (chip = community name)
  → `routeFor(channel)`. **This is where the "chip opens the Concord Channel"
  requirement lands.**

### Screens (`amethyst/…/chats/publicChannels/concord/`, mirror `relayGroup/`)
- `ConcordServerList.kt` (community rows) · `ConcordChannelListScreen.kt(communityId)`
  (a community's channels, from the folded Control Plane) ·
  `ConcordChatScreen.kt(communityId, channelId, …)` (top-level route target) ·
  `ConcordChannelView.kt` (reuse the NIP-28 `ChannelFeedViewModel`/`ChannelView`
  stack via the `ConcordChannel: Channel` subclass) · `ConcordMembersScreen.kt` ·
  `ConcordMetadataScreen.kt`/`ViewModel.kt` (create/edit) · `ConcordTopBar.kt`
  (name + role badge + Members/Edit/Invite/Ban/Leave menu) · `LoadConcordChannel.kt`.
- Compose composer gated on `membershipOf(me).isMember()`; else a "redeem an
  invite to post" notice.

### Discovery feed (GitRepositories-style triad; thinner than NIP-29)
- `concord/dal/ConcordDiscoveryFeedFilter.kt` (`AdditiveFeedFilter<Note>` over
  public kind-33301 bundles; "My Communities" branch = the 13302 list) +
  `concord/dal/ConcordDiscoveryConstraint.kt` bridge +
  `concord/datasource/subassemblies/FilterConcordBundlesBy{Authors,Follows,Hashtag}.kt`.
  `ConcordDiscoveryScreen.kt` = `DisappearingScaffold` + `FeedFilterSpinner` +
  `RenderFeedContentState` with `ConcordDiscoveryCard` (name + Join button). FAB →
  `ConcordBrowse`/redeem-link.

### Navigation (`ui/navigation/routes/Routes.kt` + `AppNavigation.kt`)
`@Serializable` routes: `Concord`(communityId, channelId, +draftId?/inviteToken?),
`ConcordServer`(communityId), `ConcordMembers`, `ConcordCreate`, `ConcordEdit`,
`Concords`(object, bottom-nav → discovery), `ConcordBrowse`. `RouteMaker.routeFor(ConcordChannel)`
+ deep-link: an invite URL/`nostr:`-embedded link → `Route.Concord(..., inviteToken=…)`,
auto-redeeming on open (mirror NIP-29's inviteCode auto-join). Wire through
`BouncingIntentNav.kt`.

### Invite/redeem UI + linkification
- `InviteConcordDialog.kt` (moderator: mint + share link via `ConcordActions.mintInviteLink`)
  · `JoinConcordDialog.kt` (paste a link → redeem) · `ui/components/ConcordInviteCard.kt`
  (render a link as a preview card; tap → `Route.Concord(inviteToken)`) ·
  `ui/components/ClickableConcordInviteLink.kt` (inline linkify shared invite URLs).

### Notifications (your explicit ask)
Route a Concord message notification click to the **channel chat**, not the feed:
in the notification builder + `BouncingIntentNav`, map a Concord message
notification to `Route.Concord(communityId, channelId)`. Mirror how NIP-29
group notifications resolve via `routeFor`.

### Zaps & likes
Because `ConcordChannel` extends `Channel` and messages render through the shared
`ChannelView`, reactions (kind 7) and zaps attach through the existing chat
reaction/zap path — but they must be **wrapped on the channel plane** (kind-7/9735
rumors sealed like messages, bound to channel+epoch), not published in the clear.
Add `ConcordActions.buildReaction`/`buildZapRequest` that wrap on the plane, and
point the shared reaction/zap affordances at them for Concord notes.

## Build order (each a tested, shippable slice)
1. **commons foundation** — `ConcordChannel`, `ConcordChannelListState` (13302),
   membership/view-mode enums; unit tests. Wire into `Account.kt` + `AccountSettings`.
2. **LocalCache index** + inbound wrap folding.
3. **Messages inbox** 6-way concat + `ConcordServerRoomNote` + header render/nav
   (delivers the chip-opens-channel behavior).
4. **Chat screens** (reuse NIP-28 `ChannelView`) + nav routes + create/invite/join.
5. **Discovery feed** triad (public invite bundles).
6. **Notifications routing + zaps/likes on-plane.**

## Verification
- commons: `:commons:jvmTest` unit tests for `ConcordChannelListState` (13302
  round-trip/merge) and `ConcordChannel` folding, mirroring
  `RelayGroupListDecryptionTest`/`RelayGroupChannelTest`.
- Android: `:amethyst:installDebug`; create a community, see it in Messages with a
  chip, tap → channel opens, send/receive between two emulators, redeem an invite
  link deep-link, verify a notification click opens the chat. Cross-check against
  `amy concord` (same relay) for wire interop, and against Armada for protocol
  interop (`Nip29ArmadaInteropTest` is the precedent).

## Gotchas carried from the NIP-29 study
- Membership has two independent layers (Concord authority vs NIP-43 relay
  membership); we only implement Concord authority.
- Cache-as-floor + optimistic local signing for snappy UX.
- E2EE means no server-side moderation and no metadata preview — surface state
  from the local fold only.
