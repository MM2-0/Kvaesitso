# Nostrnests integration audit (2026-04-26)

> **Status:** shipped — punchlist split into the Tier 1-4 coding plans, all of which landed (chat, reactions, roles, kick, participant grid, theming, reconnect all present in the nests modules).
> _Audited 2026-06-30._

What `nostrnests/NestsUI-v2` does that Amethyst doesn't yet — sourced
from a code-walk of the React app, the moq-auth sidecar, and the
moq-lite reference. Use this as the punchlist for the next chunks of
audio-room work.

> **Already shipped** (don't re-add to the gap list):
> HTTP `/auth` JWT mint, moq-lite Lite-03 listener + speaker, kind
> 30312 with title/summary/image/status/service/endpoint + single host
> participant, kind 10312 presence with `hand` + `muted` + final
> "leaving" emit, kind 10112 host-server list (Settings UI), foreground
> service, PIP, mute, "Start space" sheet that publishes the kind-30312
> with the user as host.

## What `API.md` is **NOT**

`nostrnests/API.md` documents a LiveKit-era HTTP surface
(`PUT /api/v1/nests`, `/permissions`, `/recording*`, `/info`, `/guest`).
**That whole file is dead** — the moq-lite refactor dropped every
endpoint except `POST /auth` and `GET /.well-known/jwks.json`. There
is no recording surface, no permissions endpoint, no `/info`, no
`/guest`. Don't waste implementation time on those.

## Tier 1 — Visible, low-effort (ship first)

### 1. Live chat (kind 1311) — ~1 day, **user-visible**
NestsUI publishes `{kind:1311, content, tags:[["a", roomATag]]}` and
subscribes to `{kinds:[1311], "#a":[roomATag]}` for the in-room chat
panel. Quartz already has `LiveActivitiesChatMessageEvent` at
`quartz/.../nip53LiveActivities/chat/LiveActivitiesChatMessageEvent.kt:132`
— just not wired into `AudioRoomFullScreen`. Need a chat pane +
`RoomChatViewModel` reading `LocalCache` filtered by `#a` and a
`AudioRoomsSubAssembler` subscription.

Source: `NestsUI-v2/src/components/WriteMessage.tsx:31-37`,
`hooks/useChatMessages.ts:31-66`.

### 2. Reactions (kind 7 + custom emoji) — ~1 day, **user-visible**
Emoji reactions tagged `["a", roomATag]`; NIP-30
`["emoji", shortcode, url]` for custom. Floating overlay shows last
30 s of reactions on the avatar. `ReactionEvent` already in Quartz at
`quartz/.../nip25Reactions/ReactionEvent.kt:73`. Need a small picker +
per-avatar overlay.

Source: `components/ReactionsButton.tsx:31-53`,
`hooks/useRoomReactions.ts:14-22` (queries kinds 7 + 9735),
`components/ReactionOverlay.tsx`,
`RoomContextProvider.tsx:105` (30-second visibility window).

### 3. Speaker / admin promotion via `p`-tag role markers — 2 days, **user-visible**
Role lives in the `p` tag's 4th element:
`["p", pubkey, "<relay>", "host"|"admin"|"speaker"]`. Promotion =
host/admin re-publishes the kind-30312 with the target's role updated;
demotion / "remove from stage" = drop the role.

Amethyst today only emits a single host. Need to:
1. Parse `admin` and `speaker` roles on incoming events
2. Add a host UI "edit room → set role" path that re-publishes 30312
3. Gate "publish microphone" on having `host|admin|speaker` (a plain
   listener should NOT auto-publish unless promoted)

Source: `lib/const.ts:27-31` (ParticipantRole), `lib/room.ts:66-74`
(parser), `components/ProfileCard.tsx:106-119` (`updateRoomParticipant`),
`hooks/useIsAdmin.ts:25-31`.

### 4. Hand-raise queue surface for hosts — ~0.5 day after #3, **user-visible**
There is **no separate "approval" event**. Host inspects the kind-10312
presence list, sees `["hand","1"]`, and uses the same role re-publish
flow above to promote. Just a "raised hands" section in the host's
participant grid. Source: `hooks/useRoomPresence.ts:14-39`,
`ParticipantsGrid.tsx:69`.

### 5. Kick (kind 4312 admin command) — ~1 day, **user-visible**
Ephemeral event:
```
{ kind: 4312, content: "",
  tags: [["a", roomATag], ["p", target], ["action", "kick"]] }
```
Targets watch for it; if signed by host/admin in last 60 s, they
self-disconnect. Host also drops the target's `p`-tag from the 30312.

Need a new Quartz `AdminCommandEvent` (kind 4312, currently absent),
client subscription, and auto-disconnect handler.

Source: `lib/const.ts:18` (`ADMIN_COMMAND = 4312`),
`components/ProfileCard.tsx:121-134`, `hooks/useAdminCommands.ts:38-74`.

### 6. Edit room / close room — ~0.5 day, **user-visible**
Host re-publishes the same `d`-tag 30312 with new title/summary/image,
or with `["status","ended"]` to close. Amethyst's CreateAudioRoomSheet
covers create; need a host-only "edit" sheet for an existing room.

Source: `components/EditRoomDialog.tsx:125-213`. Theme tags (`c`, `f`,
`bg`) can be ignored for now (see #14).

### 7. Scheduled / planned rooms — 2-4 hours, **user-visible**
`["status","planned"]` + `["starts","<unix>"]`. `StatusTag.STATUS` and
`starts` already exist in Quartz; the create-sheet just doesn't expose
a date/time picker yet.

Source: `pages/NewRoom.tsx:50-58`.

### 8. Listener counter ("N listening") — ~2 hours, **user-visible**
Subscribe to `{kinds:[10312], "#a":[roomATag], since: now-300}`,
dedupe by pubkey, render `presenceList.length`. Currently Amethyst
emits its own presence but doesn't read others'. Unblocks #4 and #9.

Source: `pages/RoomPage.tsx:67-68`, `hooks/useRoomPresence.ts:14-37`.

## Tier 2 — Visible, medium-effort

### 9. Participant grid (speakers vs listeners) — 2-3 days, **user-visible**
Render every pubkey from
1. MeetingSpace `p` tags (with their role)
2. moq-lite announcements (active publishers, surfaced via the listener
   session's announce flow)
3. Recent kind-10312 presence

Speakers = `host|admin|speaker` minus presences with `["onstage","0"]`.

Source: `components/ParticipantsGrid.tsx:75-101`. Note new presence
tags Amethyst doesn't emit yet — `["publishing","0|1"]`,
`["onstage","0|1"]` — see #10.

### 10. Augment kind-10312 with `publishing` + `onstage` tags — ~1 hour, infrastructure
`usePresence.ts:33-41` emits
```
["a", roomATag], ["hand","0|1"], ["publishing","0|1"], ["muted","0|1"], ["onstage","0|1"]
```
"Leave the stage" UX hinges on `onstage=0`. Extend the existing 10312
emitter in `AudioRoomActivityContent.publishPresence`.

### 11. Per-participant context menu — 1 day, **user-visible**
Tap an avatar → View profile, Follow (kind 3), Mute (kind 10000), Zap.
All exist elsewhere in Amethyst — assemble in a room-scoped sheet.

Source: `components/ProfileCard.tsx:177-258`.

### 12. Zap support inside the room — ~0.5 day, **user-visible**
Standard NIP-57 zap of the room event itself or a specific speaker.
Reactions hook also pulls `kind:9735` so paid zaps appear in the
reaction stream. Reuse Amethyst's existing zap UI.

Source: `components/ZapDialog.tsx`, `hooks/useZaps.ts`.

### 13. Share room (`naddr` deep link) — ~0.5 day, **user-visible**
`ShareDialog` builds an `naddr` from the room event and offers
"share to Nostr" (publishes a kind 1 referencing the naddr) plus
clipboard. Quartz already has `NAddress.create`.

Source: `components/ShareDialog.tsx:45`,
`lib/room.ts:77-83` (`buildRoomNaddr`).

## Tier 3 — Larger / lower priority

### 14. Room theming (tags `c`, `f`, `bg` + kinds 36767 / 16767) — 3-5 days
Inline color triplet `["c", hex, "background|text|primary"]`, font
`["f", family, url]`, background `["bg", "url <u>", "mode <tile|cover>"]`.
Optional `a`-tag pointing to a kind-36767 Ditto theme; per-user
kind-16767 profile theme.

Effort range: full theming is 3-5 days; just *parsing* the tags so
themed rooms don't render badly is ~0.5 day. Compose Multiplatform
can pull dynamic colors but font loading is platform-specific.

Source: `lib/const.ts:21-24`, `lib/ditto-theme.ts`,
`components/ThemeChooser.tsx`, `pages/NewRoom.tsx:69-88`.

### 15. Background audio + wake-lock audit — ~2 hours
NestsUI uses `useWakeLock`, `useAudioKeepAlive`, `useBackgroundAudio`.
Amethyst already has `AudioRoomForegroundService` + PIP; just confirm
`PARTIAL_WAKE_LOCK` is acquired during a broadcast. Probably already
covered.

## Tier 4 — Infrastructure (mostly already correct)

### 16. moq-auth token lifetime — ~2 hours
`/auth` JWT lives 600 s (10 min). No refresh endpoint — re-mint on
expiry. Confirm Amethyst re-mints before the token's `exp` on long
sessions.

JWT claims emitted by the sidecar:
- `root` = the `namespace` from the request body
- `get: [""]` (read-anything-under-root, for subscribers)
- `put: [<pubkey>]` (publish only your own sub-namespace, for `publish:true`)
- `iat` / `exp`

Source: `moq-auth/src/index.ts:160-166`.

### 17. moq-lite features unused (mostly intentional)
| Feature | NestsUI | Amethyst | Verdict |
|---|---|---|---|
| Multi-track / video | not used (audio only) | not used | — |
| Fetch / replay | not used | not used | — |
| Per-track priority / bitrate probes | not used | not used | — |
| `Connection.Reload` auto-reconnect (1s→2s→…→30s backoff) | yes | **MAYBE GAP** | confirm `MoqLiteSession` reconnects with backoff on transport failure; if not, add ~0.5 day. |
| WebSocket transport fallback | yes (browser) | no (WebTransport only over QUIC) | not a gap on Android |
| Announcement-driven participant discovery | yes | needs to expose announce flow on `MoqLiteNestsListener` | small wire-up; covered by #9 |

Source: `transport/moq-transport.ts:145-151, 257-265, 386-422`.

### 18. NIP-71 / hashtags / spotlight / `ends` — **not a gap**
None of these are emitted by NestsUI on kind 30312. The only tags it
writes: `d, title, summary, status, starts, color, image, streaming,
auth, relays, p`, plus theme tags. No NIP-71 streaming tags, no
hashtags, no spotlight, no `ends`. Don't chase parity here.

## Recommended punchlist order

1. **Listener-side presence aggregation + listener counter** (#8, #10)
2. **Live chat panel** (#1)
3. **Reactions** (#2)
4. **Participant grid + per-avatar context menu + zap** (#9, #11, #12)
5. **Role parsing + hand-raise queue + promote / demote / kick** (#3, #4, #5)
6. **Edit room / close room** (#6) + **scheduled rooms** (#7)
7. **Share via naddr** (#13)
8. **moq-auth token refresh sanity check** (#16) + **`Connection.Reload`
   backoff confirmation** (#17)
9. **Room theme parsing — graceful fallback only** (#14)

Items 1-3 give you "feels like a real audio room" inside ~3 days.
Items 4-5 unlock the full host workflow inside another ~1 week.
Everything else is polish.

## Key files for the implementer

- Listener: `nestsClient/src/commonMain/kotlin/com/vitorpamplona/nestsclient/MoqLiteNestsListener.kt`
- MeetingSpace event: `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip53LiveActivities/meetingSpaces/MeetingSpaceEvent.kt`
- Live-chat event (kind 1311): `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/nip53LiveActivities/chat/LiveActivitiesChatMessageEvent.kt`
- Full-screen room UI: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/audiorooms/room/AudioRoomFullScreen.kt`
- Subscriptions for the rooms feed: `amethyst/src/main/java/com/vitorpamplona/amethyst/ui/screen/loggedIn/audiorooms/datasource/AudioRoomsSubAssembler.kt`
