# NIP-29 Relay-Based Groups — Deep Study of Armada + Amethyst Integration Plan

**Date:** 2026-07-07
**Status:** Research / design study (no code yet)
**Reference client studied:** [Armada](https://gitlab.com/soapbox-pub/armada) (Soapbox), commit at HEAD of `main`
**Spec:** [NIP-29](https://github.com/nostr-protocol/nips/blob/master/29.md)

This document is a deep study of how **Armada** — a Discord-style NIP-29 client
by Soapbox — creates events, manages chat, displays information, and handles
invites/joins/leaves, followed by a concrete plan for bringing NIP-29 into
Amethyst. It is written to answer "how does relay-based group chat actually work
in a shipping client, and where does it slot into Amethyst's existing chat
stack."

---

## 0. TL;DR for the impatient

- **NIP-29 groups live on ONE relay.** A group is addressed by the pair
  `(relayUrl, groupId)`. The relay is the source of truth — it signs the group's
  metadata/membership/roles and enforces who may write. This is fundamentally
  different from Nostr's usual "publish everywhere" model.
- **Armada models it as Discord:** a **server = a relay**, a **channel = a
  NIP-29 group**. The far-left rail is a list of relays; picking one shows that
  relay's channels; picking a channel shows its kind-9 message timeline.
- **The user's home base is kind 10009** (NIP-51 "simple groups list"): it stores
  both the relays they've added (`r` tags) and the groups they've joined
  (`group` tags), privately (NIP-44 encrypted to self).
- **Amethyst is already ~70% wired for this at the protocol layer.** Quartz has a
  `nip29RelayGroups` package (metadata/moderation/request events) **and** a
  `nip43RelayMembers` package (the relay-level membership handshake Armada uses).
  The main protocol gap is the **kind-9 group chat *message* event** itself.
- **Amethyst also already has the perfect UX analog:** the `ephemChat` feature
  (NIP-C7, kind 23333) is a *relay-scoped* chat room identified by a
  `RoomId(id, relayUrl)` — the exact shape of a NIP-29 group address — with a
  full channel/screen/join/leave UI under
  `amethyst/.../ui/screen/loggedIn/chats/publicChannels/ephemChat/`. NIP-29 is a
  managed, moderated sibling of that feature.

---

## 1. NIP-29 protocol primer (the parts that matter)

### 1.1 The addressing model

A group is **not** a global object. It exists on a specific relay, and its id is
only unique *within that relay*. So every reference to a group is the pair:

```
(relay websocket URL, group id)
```

The group id is a short opaque string (Armada mints 8 random bytes → 16 hex
chars). Everything that happens "in" the group is tagged with `["h", "<groupId>"]`
and published **only** to that relay.

### 1.2 Event kinds

| Kind | Name | Signed by | Purpose |
|------|------|-----------|---------|
| **9** | group chat message | user | the actual chat line (has `["h", groupId]`) |
| 11 | group thread/forum post | user | long-form/threaded root |
| 1111 | NIP-22 comment | user | threaded reply to a chat message |
| 7 | reaction | user | emoji reaction (scoped by `h`) |
| 1068 | poll (NIP-88) | user | poll posted into the timeline |
| **9000** | put-user | user (admin) | add member / set roles |
| **9001** | remove-user | user (admin) | remove member |
| **9002** | edit-metadata | user (admin) | change name/about/picture/flags |
| 9005 | delete-event | user (admin) | moderator delete a message |
| **9007** | create-group | user (admin) | create a new group |
| 9008 | delete-group | user (admin) | delete a group |
| **9009** | create-invite | user (admin) | mint an invite code |
| **9021** | join-request | user | ask to join (optional `code`) |
| **9022** | leave-request | user | leave the group |
| **39000** | group metadata | **relay** | name, picture, about, flags (addressable, `d`=groupId) |
| **39001** | group admins | **relay** | `["p", pubkey, role…]` list |
| **39002** | group members | **relay** | `["p", pubkey]` list |
| **39003** | group roles | **relay** | `["role", name, desc]` definitions |
| 39004 | live AV participants | **relay** | LiveKit room presence (Armada extension use) |

The **9xxx** events are user-authored **requests/commands**; the relay validates
the author's role and, if accepted, updates the group state and re-emits the
**39xxx** relay-signed snapshots. **Clients read 39xxx, write 9xxx.**

### 1.3 Group metadata flags (kind 39000 tags)

`name`, `picture`, `about` carry display data. Boolean status is presence-based:

- `private` — only members can **read** (relay gates reads behind NIP-42 AUTH).
- `restricted`* — only members can **write**.
- `closed` — join requests are ignored; you need an invite code.
- `hidden` — metadata hidden from non-members.
- `livekit` — group has an AV room.
- `supported_kinds` — whitelist of accepted kinds (absent = all).

The antonyms are `public` / `open` (Armada's `edit-metadata` emits the antonym
tag to *clear* `private`/`closed`; see §2.4).

### 1.4 The `previous` tag (timeline references) — and why Armada drops it

NIP-29 defines an optional `["previous", <id-prefix>, …]` tag: each event should
reference the first-8-chars of several of the group's recent events so a relay
can reject events that were composed against a *forked* view of the timeline
(anti-context-spam). **Armada deliberately does not emit it** — see the
verbatim rationale in `ChatComposer.buildMessageTags`:

> relay29's `CheckPreviousTag` rejects any event whose first `previous` ref isn't
> in the group's in-memory last-50 ring. We can only pick refs from a local (and
> own-excluded) message snapshot, which routinely drifts out of that window —
> especially when replying to older messages — causing the relay to silently drop
> legitimate messages/replies. `previous` is optional in NIP-29 and only guards
> against relay-fork attacks, which don't apply to this single-host-per-group
> deployment.

**Takeaway for Amethyst:** Quartz *has* a `PreviousTag` class, but a
single-host-per-group deployment does not need it, and emitting it naively causes
silent drops. Start without it (like Armada); only add it if targeting a relay
that enforces it.

---

## 2. How Armada creates events

Armada's whole NIP-29 protocol layer is ~770 lines in
`client/src/lib/nip29.ts` (constants, parsers, tag builders), driven by a set of
TanStack-Query hooks in `client/src/hooks/`. Every write goes through a single
`useNostrPublish` mutation whose `relay` option **pins the event to the group's
host relay** — this is the linchpin of the whole design.

### 2.1 The publish chokepoint (`useNostrPublish.ts`)

```ts
// EventTemplate has an optional `relay?: string`.
// When set, publish ONLY to this relay (NIP-29 group traffic must stay on
// the group's host server). When omitted, the event goes to all app relays.
if (relay) {
  await nostr.relay(relay).event(event, …);   // single-relay send
} else {
  await nostr.event(event, …);                // fan out to app relays
}
```

It also: adds a NIP-89 `["client", APP_NAME]` tag, adds `published_at` for
replaceable kinds, stores the signed event locally *before* the network call (so
optimistic UI + offline retry work), and normalizes relay rejection strings for
toasts (`relayRejectionMessage`).

**Every NIP-29 write in Armada passes `relay: relayUrl`.** Group/channel events
*never* touch the general app relays. This is the single most important
invariant to replicate.

### 2.2 Create a group (`CreateGroupDialog.tsx` + `useGroupModeration.ts`)

A "create channel" is a **two-event sequence**, then a bookkeeping write:

```ts
// 1. kind 9007 create-group — just the h tag.
await createGroup({ groupId });
//    → publishEvent({ kind: 9007, content: "", tags: [["h", groupId]], relay })

// 2. kind 9002 edit-metadata — name/about/visibility.
await editMetadata.mutateAsync({ name, about, isPrivate, isClosed });
//    → tags: [["h", groupId], ["name", name], ["about", about],
//             [isPrivate ? "private" : "public"], [isClosed ? "closed" : "open"]]

// 3. remember it in the user's kind 10009 list (best-effort).
updateList({ type: "add-group", ref: { id: groupId, relay: relayUrl } });
```

The group id is client-minted (`crypto.getRandomValues(8)` → hex). The creator
becomes admin automatically (the relay assigns the group-creator role). Then the
UI navigates to `/s/<relayParam>/<groupId>`.

> Server note: Armada's own relay (`server/group.go`) **restricts kind 9007 to
> configured admin pubkeys** — on that deployment only operators create channels.
> That's a relay-policy choice, not a NIP-29 requirement.

### 2.3 Send a chat message (`ChatComposer.tsx`)

A message is **kind 9** with `["h", groupId]` plus standard Nostr tags. The tag
builder (`buildMessageTags`) assembles:

- `["h", groupId]` — always first, required.
- `["t", hashtag]` — extracted hashtags.
- `["p", pubkey]` — NIP-27 mentions decoded from `nostr:npub…` in content.
- NIP-10 marked reply tags when replying:
  `["e", rootId, relay, "root", rootAuthor]` + `["e", replyId, relay, "reply", replyAuthor]`.
- `["q", …]` — NIP-18 quotes for embedded nevent/naddr.
- NIP-30 custom emoji tags, NIP-92 `imeta` tags for uploads.
- **No `previous` tag** (see §1.4).

Sending is **optimistic**: the event is signed locally, inserted into the
timeline cache with a `pending` status, then sent. Because the id is computed at
sign time, the relay's echo of the same event dedupes automatically against the
optimistic copy (id match) and flips it to confirmed.

### 2.4 Edit metadata / moderation (`useGroupModeration.ts`)

All moderation is one small hook returning mutations, each a single pinned
publish. Exact tag shapes:

| Action | kind | tags |
|--------|------|------|
| putUser | 9000 | `[["h",g], ["p", pubkey, ...roles]]` |
| removeUser | 9001 | `[["h",g], ["p", pubkey]]` (content = reason) |
| deleteEvent | 9005 | `[["h",g], ["e", eventId]]` |
| editMetadata | 9002 | `[["h",g], ...metadataTags(patch)]` |
| deleteGroup | 9008 | `[["h",g]]` |
| createInvite | 9009 | `[["h",g], ["code", code]]` |

`metadataTags` emits antonym tags to clear flags (`public`/`open`) but only
*asserts* `restricted`/`hidden` (no documented antonyms). After a successful
moderation write, the relevant TanStack query keys are invalidated so the
39xxx-derived views refetch.

### 2.5 Invites (`InvitePeopleDialog.tsx`)

Opening the invite dialog **immediately mints an invite and builds a link** — the
"silly-easy part." Two-tier logic:

1. **Prefer a relay-level claim (NIP-43 / zooid).** `useRelayClaim` queries the
   relay for a `kind 28935` invite it issues to authed members; if present, its
   `claim` tag value is the invite code.
2. **Fall back to a per-group NIP-29 code (kind 9009).** If the relay issues no
   claim, mint a random 6-byte code and publish `createInvite({ code })`.

The shareable URL is `…/s/<relayParam>/<groupId>?code=<code>`. Anyone opening it
hits `GroupPage`, which auto-joins using the `code` query param.

### 2.6 Join / leave / membership (`useGroupMembership.ts`, `useRelayMembership.ts`)

**Join (kind 9021)** is a two-step handshake, in this order:

```ts
// 1. Best-effort RELAY-level join first (zooid/Coracle relays gate ALL writes
//    behind relay membership, rejecting non-members before group join is even
//    considered). Ephemeral kind 28934 carrying the invite as a `claim` tag.
//    NEVER throws — no-ops on relays that don't implement it (e.g. relay29).
await joinRelay({ relayUrl, claim: code });

// 2. The real NIP-29 group join. Same invite carried as a `code` tag.
await publishEvent({ kind: 9021, content: reason, tags: [["h",g], ["code",code]], relay });
//    "already a member" rejection is treated as success.
```

**Leave (kind 9022):** `publishEvent({ kind: 9022, tags: [["h",g]], relay })`.
The relay auto-issues the corresponding 9001 remove-user.

**Membership state** is derived per NIP-29 by querying the *latest* of
`{kinds:[9000,9001], "#h":[g], "#p":[me]}` on the host relay — 9000 latest ⇒
member, 9001 latest ⇒ not. Polls every 30s; 15s stale time.

**Relay membership (NIP-43)** is its own layer, cleanly separated: `useRelayClaim`
(fetch a 28935 claim), `useJoinRelay` (publish 28934 with the claim),
`useLeaveRelay` (28936). All best-effort and non-throwing — the group join is the
source of truth for the actual outcome.

---

## 3. How Armada displays information

### 3.1 The three-pane Discord layout

```
┌────┬──────────────┬───────────────────────────┐
│rail│ channel list │  message timeline          │
│    │ (this relay) │  + composer                │
│ 🟦 │  # general   │  ...kind 9 messages...     │
│ 🟩 │  # random    │                            │
│ ➕ │  # dev       │  [type a message]          │
└────┴──────────────┴───────────────────────────┘
 servers  channels          chat
 =relays  =NIP-29 groups     =kind 9
```

- **`ServerRail.tsx`** — the far-left vertical rail. Each icon is a **relay**
  (`{ kind: "server", url }`), fed by the pinned `PLATFORM_RELAYS` +
  `config.addedRelays`. Supports Discord-style drag-to-reorder and **folders**.
  (It also unifies in Concord E2EE communities, which are *not* NIP-29 — ignore
  those.) Server order is synced to the kind-10009 `r` tags.
- **`ChannelSidebar`** — for the selected relay, lists its groups via
  `useRelayGroups(relayUrl)`.
- **`GroupChat.tsx`** — the timeline + composer for the selected group.

Selecting a relay first, then a room, is *exactly* the "select the relay first
and then pick the rooms in each relay" UX in the ask. Folders are Armada's answer
to "organization to group rooms on" — but note they group **relays**, not rooms;
rooms are grouped implicitly by their host relay.

### 3.2 Fetching a relay's channels (`useRelayGroups.ts`)

Lists a relay's groups by querying `{kinds:[39000]}` on that relay. Key
subtleties:

- **Trust:** kind 39000 must be signed by the relay's own key. Armada reads the
  relay's `self`/`pubkey` from its NIP-11 doc and adds `authors:[relaySelf]` so
  **forged metadata from other publishers is never trusted**. Until NIP-11
  resolves it races a direct fetch (2s) then refetches once when the key lands.
- **Hidden groups:** relays hide closed/private groups from open listings, so the
  ids the user *remembers* (their kind-10009 `group` tags for this relay) are
  queried explicitly by `#d` and merged in.
- **Provenance scoping:** several relays can share a signing key (zooid ships a
  shared identity), so author-scoping alone bleeds channels across relays. Armada
  records *which relay actually served* each cached event ("provenance") and
  scopes the IndexedDB cache read by it. This is a real, painful edge case worth
  remembering.
- **Cache-as-floor:** cached events are merged *under* live ones so a sparse/flaky
  relay read can only add, never clear the list. Long stale time (1h), no polling
  — channel metadata is the most stable thing in the app.

### 3.3 Fetching a group's roster (`useGroup.ts`)

One query pulls the newest of `{kinds:[39000,39001,39002,39003], "#d":[groupId]}`
(optionally author-scoped to the relay key) and composes
`{ group, admins, members, roles }` (newest event per kind wins). **Local-first:**
the plaintext 39xxx events are mirrored to IndexedDB, so a previously-opened
group renders its roster instantly, with a background relay refresh.

### 3.4 The message timeline (`useGroupMessages.ts`)

The most sophisticated hook. For `(relayUrl, groupId)`:

- **Timeline kinds:** kind 9 + kind 1068 (polls); live sub also watches kind 5
  (deletions).
- **Local-first + snapshot-first paint:** seeds from a synchronous localStorage
  "last screenful" snapshot → IndexedDB store → background relay page, merged
  append-only so nothing already shown is dropped.
- **Scroll-up pagination:** `loadOlder()` walks an `until` cursor with a
  gap-guard (Ditto's pattern) so a stale straggler doesn't leap the cursor past
  real history.
- **Live subscription:** one `req` with a 5-minute `since` lookback (so a message
  that arrived via push before the group was opened still replays); dedupes by id;
  processes kind-5 deletions by dropping referenced ids.
- **Optimistic send status** map (`pending`/`failed`) reconciled by relay echo.
- **Resilience:** 60s backstop poll + refetch-on-focus/reconnect to heal
  half-dead mobile sockets where the live socket silently died.

### 3.5 Group discovery / "home" (`useUserGroupList.ts`, kind 10009)

The **cross-device source of truth** for a user's memberships is a single kind
10009 event (NIP-51 "simple groups"):

- `group` tags `["group", id, relay]` — joined groups (with host relay).
- `r` tags `["r", relayUrl]` — servers/relays in use.
- Both stored as **NIP-44 private items** (encrypted to self in `.content`);
  read-modify-write via `useUpdateUserGroupList`. Armada persists the *decrypted*
  list to disk ("folded cache") so boot doesn't pay a signer round-trip, and
  refuses to write if it couldn't decrypt the prior list (avoids wiping it).

There is also a `useGroupSearch` (NIP-50 `search` scoped by `#h`, merged with the
local timeline cache) for in-group message search.

### 3.6 What else rides on the `h` tag

Armada extends the group with several kinds, all scoped by `["h", groupId]` so
the relay routes/authorizes them: **pins** (kind 39041, an Armada extension,
addressable `d`=groupId, admin-only), **calendar events** (NIP-52
31922/31923/31925), **reactions** (kind 7), **threaded replies** (kind 1111
NIP-22 comments), and **webxdc mini-apps** (9450/24450). All follow the same
pattern: `["h", groupId]` + pin to host relay. Useful precedent that "anything
can be a group event if it carries `h` and the relay accepts the kind."

---

## 4. Design constraints & gotchas (the expensive lessons)

1. **Relay-scoping is absolute.** Group events go only to the host relay, and
   group queries hit only the host relay. Amethyst's relay client fans out to
   many relays by default — NIP-29 needs a *single-relay* send/subscribe path.
2. **39xxx is relay-signed; trust it by the relay's own key.** Filter directory
   queries by `authors:[relayNip11Pubkey]`. Never trust group metadata otherwise.
3. **Shared relay identities bleed groups.** If you cache 39000 by author only,
   two relays sharing a key cross-contaminate. Track per-relay provenance.
4. **`previous` tags cause silent drops** unless you can guarantee refs are in the
   relay's last-50 ring. Omit them for single-host groups.
5. **Two membership layers.** NIP-29 group membership (9000/9001) is distinct from
   relay-level membership (NIP-43, 28934/28935). Community relays (zooid) gate on
   the latter *first*. Do the relay handshake best-effort, treat the group join as
   authoritative.
6. **Cache-as-floor everywhere.** A flaky relay returning nothing must never blank
   a channel list or roster. Merge cache under live, never overwrite.
7. **Optimistic UI needs local signing.** Sign locally, insert with pending
   status, dedupe on relay echo by id.

---

## 5. Amethyst integration plan

### 5.1 What already exists (survey)

**Protocol (Quartz) — largely present.** `quartz/.../nip29RelayGroups/` already
has:

- `metadata/` — `GroupMetadataEvent` (kind **39000**), `GroupAdminsEvent`
  (39001), `GroupMembersEvent` (39002), `GroupRolesEvent` (39003).
- `moderation/` — `CreateGroupEvent` (9007), `GroupEditMetadataEvent` (9002),
  `GroupPutUserEvent` (9000), `GroupRemoveUserEvent` (9001), `GroupDeleteEventEvent` (9005),
  `DeleteGroupEvent` (9008), `GroupCreateInviteEvent` (9009), plus tag helpers.
- `request/` — `GroupJoinRequestEvent` (9021), `GroupLeaveRequestEvent` (9022).
- `tags/` — `GroupIdTag` (the `h` tag), `CodeTag`, `GroupAdminTag`, `RoleTag`,
  and even a `PreviousTag`.

**Relay membership (Quartz) — present.** `quartz/.../nip43RelayMembers/` has the
full NIP-43 handshake Armada calls "relay membership": `RelayJoinRequestEvent`,
`RelayInviteRequestEvent`, `RelayAddMemberEvent`, `RelayLeaveRequestEvent`,
`RelayMembershipListEvent`, `ClaimTag`, `MemberTag`. There's even an
`amethyst/.../ui/screen/loggedIn/relays/nip43/RelayMembersScreen.kt`.

**The UX analog (commons + amethyst) — present and close.** The `ephemChat`
feature (NIP-C7, kind **23333**) is a *relay-scoped* chat room:

- `quartz/.../experimental/ephemChat/chat/EphemeralChatEvent.kt` — kind 23333,
  with `RoomId(room, relayUrl)` — **the same `(relay, id)` shape as a NIP-29
  address.**
- `commons/.../model/emphChat/EphemeralChatChannel.kt` — `EphemeralChatChannel`
  with `relays() = setOf(roomId.relayUrl)`, wired into `LocalCache`, `Account`,
  `Note`, plus an `EphemeralChatListState`.
- `amethyst/.../ui/screen/loggedIn/chats/publicChannels/ephemChat/` — a full UI:
  `EphemeralChatScreen`, `LoadEphemeralChatChannel`, `EphemeralChatChannelHeader`,
  `JoinChatButton`/`LeaveChatButton`, a `NewEphemeralChatScreen`, and datasource
  sub-assemblers (`FilterMessagesToEphemeralChat`, etc.).

So the Messages/Chats screen already hosts **four** conversation types:
NIP-04/17 DMs, NIP-28 public channels, and NIP-C7 ephemeral chats — all under
`chats/`. **NIP-29 groups become the fourth sibling.**

### 5.2 The main protocol gap

There is **no kind-9 group chat message event** in `nip29RelayGroups/`
(`GroupCreateInviteEvent` at 9009 is the highest kind present; nothing for kind 9).
The 9xxx moderation, 39xxx metadata, and 9021/9022 request events exist, but the
actual message carrier does not. This is the first thing to build:

- `nip29RelayGroups/chat/GroupChatEvent.kt` — kind 9, `["h", groupId]` required,
  NIP-10 reply markers, NIP-27 mentions, NIP-92 imeta, NIP-30 emoji — mirror
  `ChannelMessageEvent` (NIP-28) which already does all of this, but swap the
  channel `e`-root tag for the `h` group tag. Optionally kind 11 (thread) and
  reuse NIP-22 `CommentEvent` for replies.

Also verify the existing 39000–39003 parsers expose the flag tags
(`private`/`closed`/`restricted`/`hidden`/`livekit`/`supported_kinds`) and roles
per §1.3; extend if not.

### 5.3 Recommended architecture mapping

| Armada (React) | Amethyst target | Notes |
|----------------|-----------------|-------|
| `lib/nip29.ts` | `quartz/.../nip29RelayGroups/` | mostly exists; add kind-9 `GroupChatEvent` + any missing parsers |
| `useNostrPublish({relay})` | a single-relay send in `commons/.../relayClient/` | **critical new capability**: publish/subscribe pinned to one relay |
| `useRelayGroups` | a `RelayGroupsState` / filter assembler | query 39000 on one relay, author-scoped to its NIP-11 key |
| `useGroup` | `GroupChannel` model + roster state | compose newest 39000–39003; mirror `EphemeralChatChannel` |
| `useGroupMessages` | a NIP-29 `FeedFilter` + `FeedContentState` | reuse `chats/publicChannels/datasource` sub-assembler pattern |
| `useUserGroupList` (10009) | an `Account` state object (like `ephemeralChatListState`) | NIP-44 private items; StateFlow of joined groups + servers |
| `useGroupMembership` | membership derivation from 9000/9001 | latest-wins per NIP-29 |
| `useRelayMembership` (NIP-43) | already in `nip43RelayMembers` + `RelayMembersScreen` | best-effort handshake before join |
| `ServerRail` + `ChannelSidebar` | Android: a relay picker → channel list inside the Chats tab | see §5.4 |
| `GroupChat` + `ChatComposer` | reuse the ephemChat/NIP-28 chat screen + composer | swap the datasource + send to kind 9 + `h` |

**Placement per CLAUDE.md:** protocol → `quartz/`; the group model, list state,
membership derivation, ViewModels/filters → `commons/` (so Desktop + CLI share);
screen composables + navigation → `amethyst/` (bottom-nav) and `desktopApp/`
(sidebar). The ephemChat feature is the template to copy for all three layers.

### 5.4 Recommended Amethyst UX

The ask floats three options; the study points to a clear answer:

- **Not** a flat list of rooms mixed into the DM inbox. NIP-29 rooms are
  relay-scoped and there can be many per relay — mixing them into the DM room
  list loses the relay grouping and doesn't scale.
- **Yes** to "select the relay first, then pick rooms in that relay." This is
  Armada's model and it matches the protocol's addressing exactly. On Android
  (bottom-nav, no room for a permanent Discord rail), the natural shape is:
  - A **"Groups"/"Servers" entry inside the existing Chats tab** (alongside DMs,
    Public Chats, Ephemeral Chats).
  - Level 1: **your relays** (from kind-10009 `r` tags) — an "add relay" affordance
    and each row shows unread rollup.
  - Level 2: tap a relay → **its channels** (from `useRelayGroups`-equivalent),
    with a create-channel action (subject to relay policy).
  - Level 3: tap a channel → the **existing chat screen**, re-pointed at a NIP-29
    kind-9 datasource.
  - "Organization to group rooms on" = the **relay is the grouping**; add
    Discord-style relay folders later if desired (Armada's `railLayout`).
- Desktop can render the true three-pane rail (it already uses a sidebar shell).

Deep-linking: adopt Armada's invite-link idea via Nostr-native addressing —
`naddr` to the kind-39000 (kind + relay-key author + `d`=groupId + relay hint),
plus an optional invite `code`. Amethyst already resolves `naddr`; a group `naddr`
should route into the channel and, if a code is present, fire a 9021 join.

### 5.5 Suggested build order

1. **Quartz:** add `GroupChatEvent` (kind 9) + builders; confirm 39000 flag/role
   parsing. Unit-test against Armada-produced events (spin up `./start.sh` or use
   `chat.soapbox.pub`).
2. **Relay client:** add a single-relay pinned publish + subscription path
   (the `relay: relayUrl` equivalent). This unblocks everything else.
3. **commons:** `GroupChannel` model + `UserGroupListState` on `Account` (kind
   10009, mirror `EphemeralChatListState`) + membership derivation.
4. **commons:** NIP-29 message `FeedFilter`/`FeedContentState` (copy the
   ephemChat/NIP-28 sub-assembler; timeline kinds 9 + 1068 + 5).
5. **amethyst:** relay-picker → channel-list screens in the Chats tab; re-point
   the chat screen/composer at the kind-9 datasource; join/leave/create/invite
   dialogs (reuse `nip43RelayMembers` for the relay handshake).
6. **Later:** roles/moderation UI, pins, reactions, threads, calendar, LiveKit AV.

### 5.6 Explicitly out of scope

Armada's `concord-v1`/`concord-v2` directories are a **separate** end-to-end
encrypted community protocol (sealed envelopes, rekeying) — *not* NIP-29. They
share the chat *components* via a `ChatTransport` abstraction but nothing else.
Ignore them for NIP-29. (The `ChatTransport` pattern — a presentational chat UI
fed by a capability interface — is itself a nice idea worth borrowing so DMs,
NIP-28, ephemeral, and NIP-29 all render through one component.)

---

## 6. Key file references

**Armada (studied):**
- `client/src/lib/nip29.ts` — constants, parsers, tag builders
- `client/src/hooks/useNostrPublish.ts` — the `relay`-pinned publish chokepoint
- `client/src/hooks/useGroupModeration.ts` — 9000/9001/9002/9005/9007/9008/9009
- `client/src/hooks/useGroupMembership.ts` + `useRelayMembership.ts` — 9021/9022 + NIP-43
- `client/src/hooks/useRelayGroups.ts` / `useGroup.ts` / `useGroupMessages.ts` — display
- `client/src/hooks/useUserGroupList.ts` — kind 10009 home base
- `client/src/components/layout/ServerRail.tsx` — Discord rail (relay = server)
- `client/src/components/chat/ChatComposer.tsx` — kind-9 send + the `previous`-tag rationale
- `client/src/components/dialogs/{CreateGroup,InvitePeople}Dialog.tsx`
- `server/group.go`, `server/invites.go`, `server/unmanaged.go` — relay29 policy

**Amethyst (integration surface):**
- `quartz/.../nip29RelayGroups/**` — existing protocol events (add kind-9 chat)
- `quartz/.../nip43RelayMembers/**` — relay membership handshake
- `quartz/.../experimental/ephemChat/**` + `commons/.../model/emphChat/**` — the relay-scoped chat analog
- `amethyst/.../ui/screen/loggedIn/chats/publicChannels/ephemChat/**` — UI template
- `amethyst/.../ui/screen/loggedIn/relays/nip43/RelayMembersScreen.kt`
- `quartz/.../nip28PublicChat/message/ChannelMessageEvent.kt` — the kind-9 builder to mirror
