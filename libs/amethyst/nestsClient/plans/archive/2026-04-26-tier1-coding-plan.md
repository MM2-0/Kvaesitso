# Tier 1 — coding plan (presence aggregation, chat, reactions, roles, kick, edit/close, scheduled, listener counter)

> **Status:** shipped — `RoomPresenceState`, `MeetingRoomPresenceEvent` publishing/onstage tags, `NestChatPanel`, hand-raise queue and kick (4312) actions all exist in commons/amethyst/quartz.
> _Audited 2026-06-30._

Concrete file-level plan for shipping Tier 1 of
`2026-04-26-nostrnests-integration-audit.md`. Sequence is **strict** —
later items consume types added by earlier ones.

## Step 1 — Listener-side presence aggregation (#8) + augmented presence emit (#10)

Unblocks the participant grid (Tier 2 #9), the hand-raise queue (#4),
and the listener counter.

### New / changed

- `quartz/.../nip53LiveActivities/presence/MeetingRoomPresenceEvent.kt`
  — extend `build(...)` (and add a `withFlags` overload if cleaner)
  to emit `["publishing", "0|1"]` and `["onstage", "0|1"]` alongside
  the existing `["hand", ...]` + `["muted", ...]` tags.
- `quartz/.../nip53LiveActivities/presence/MeetingRoomPresenceEvent.kt`
  — add accessor parsers: `publishing(): Boolean?` and
  `onstage(): Boolean?` mirroring the existing `handRaised()` /
  `muted()`.
- `commons/.../viewmodels/AudioRoomViewModel.kt` — add
  `publishingNow: Boolean` and `onStageNow: Boolean` to
  `RoomUiState`; thread them through `setMuted` / `setOnStage` /
  `startBroadcast` so the heartbeat picks the right values.
- `amethyst/.../audiorooms/room/AudioRoomActivityContent.kt` —
  the `publishPresence(...)` helper + the LaunchedEffect that drives
  it: include `publishing` (true while `BroadcastUiState.Broadcasting`
  is active) and `onstage` (default true; flipped false on a
  "leave the stage" tap that #9 will add).

### New listener-side aggregation

- `commons/.../viewmodels/RoomPresenceState.kt` (NEW) — pure data
  class:
  ```kotlin
  data class RoomPresence(
      val pubkey: String,
      val handRaised: Boolean,
      val muted: Boolean?,
      val publishing: Boolean,
      val onstage: Boolean,
      val updatedAt: Long,
  )
  ```
  Equality + hash by `pubkey` so a `Map<String, RoomPresence>` swaps
  cleanly on update.
- `commons/.../viewmodels/AudioRoomViewModel.kt` — new
  `presences: StateFlow<Map<String, RoomPresence>>` populated by a
  subscription to LocalCache filtered by
  `kinds=[10312], #a=[roomATag], since=now-5min`. Updates dedupe by
  pubkey, keeping the most recent. Records older than 5 min get
  garbage-collected on every emission.
- `amethyst/.../audiorooms/datasource/AudioRoomsFilterAssembler.kt`
  (or sibling — confirm which assembler the room screen uses) — add a
  `RoomPresenceFilter` that REQs the above and feeds `LocalCache`.

### Listener counter (#8)

- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — small
  `Text(stringRes(R.string.audio_room_listener_count, presences.size))`
  badge near the room title. Strings: add `audio_room_listener_count`
  with `%1$d` placeholder.

### Tests

- `quartz/.../MeetingRoomPresenceEventTest.kt` — round-trip the new
  `publishing` + `onstage` tags.
- `commons/.../AudioRoomViewModelTest.kt` — `presences` map updates
  on a fake `LocalCache` add; pubkey dedupes on a re-emit; entries
  older than 5 min get evicted.

### Risk / open questions

- The 5-min eviction can race with a peer's heartbeat being late.
  Use a 6-min window in code, 5 min in the user-visible "active"
  count? Document in the ViewModel.

---

## Step 2 — Live chat panel (#1)

Depends only on Step 1 being merged (so the chat sub uses the same
`a`-tag assembler pattern).

### Reuse

- `quartz/.../nip53LiveActivities/chat/LiveActivitiesChatMessageEvent.kt`
  — already present; covers parse + build + reply.
- `commons/.../viewmodels/AudioRoomViewModel.kt` — model already has
  the room's address; pull it.

### New

- `commons/.../viewmodels/RoomChatViewModel.kt` (NEW) —
  `messages: StateFlow<List<LiveActivitiesChatMessageEvent>>`
  sourced from LocalCache filter
  `kinds=[1311], #a=[roomATag]`, ordered by `created_at` ascending.
  `send(text: String)` builds + signs via
  `LiveActivitiesChatMessageEvent.message(text, roomATag) {...}`
  and broadcasts via `account.signAndComputeBroadcast(template)`
  (mirror of `ChannelNewMessageViewModel`).
- `amethyst/.../audiorooms/room/AudioRoomChatPanel.kt` (NEW) — Compose
  list with auto-scroll-to-bottom; per-message row with avatar +
  display name + content; bottom row with `OutlinedTextField` +
  send button.
- `amethyst/.../audiorooms/datasource/AudioRoomChatSubAssembler.kt`
  (NEW) — REQ for `kinds=[1311], #a=[roomATag]` while the room
  screen is composed.
- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — add a
  collapsible / bottom-sheet chat panel; on phone, slide-up over the
  audience grid.

### Strings

`audio_room_chat_send`, `audio_room_chat_placeholder`,
`audio_room_chat_empty`.

### Tests

- `commons/.../RoomChatViewModelTest.kt` — `messages` updates on
  fake LocalCache add; `send` produces a kind-1311 with the
  expected `["a", roomATag]` tag.

---

## Step 3 — Reactions (#2)

### Reuse

- `quartz/.../nip25Reactions/ReactionEvent.kt` — present.
- `AccountViewModel.reactToOrDelete(note, reaction)` — already does
  the broadcast.

### New

- `commons/.../viewmodels/RoomReactionsViewModel.kt` (NEW) —
  `recentReactions: StateFlow<Map<String, List<RoomReaction>>>`
  keyed by target pubkey, dropping entries older than 30 s.
  Sourced from `kinds=[7,9735], #a=[roomATag]`.
- `amethyst/.../audiorooms/room/RoomReactionPickerSheet.kt` (NEW) —
  small bottom sheet with default emojis + `EmojiPackEvent` favourites.
- `amethyst/.../audiorooms/room/SpeakerReactionOverlay.kt` (NEW) —
  per-avatar overlay rendering the last 30 s of reactions as
  floating-up icons.
- `amethyst/.../audiorooms/datasource/AudioRoomReactionsSubAssembler.kt`
  (NEW) — REQ for `kinds=[7,9735], #a=[roomATag]`.
- Reactions button in `AudioRoomFullScreen.kt` near the mic toggle.

### Tests

- `commons/.../RoomReactionsViewModelTest.kt` — 30-s window sliding;
  per-pubkey grouping.

### Risk

- The kind-7 `["a", roomATag]` shape isn't standard NIP-25 (which
  reacts to a single event). Confirm against
  `NestsUI-v2/hooks/useRoomReactions.ts` exact tag emission and
  whether the `e` tag also points at the room's id.

---

## Step 4 — Edit room / close room (#6) + Scheduled rooms (#7)

Pure UI work + reuse `account.signAndComputeBroadcast`.

### Changed

- `amethyst/.../audiorooms/room/EditAudioRoomSheet.kt` (NEW) —
  copy of `CreateAudioRoomSheet` pre-populated from the existing
  `MeetingSpaceEvent`. On submit, re-publish kind-30312 with the
  same `d` tag.
- `EditAudioRoomViewModel.kt` (NEW) — mirror of
  `CreateAudioRoomViewModel` with two extra paths:
    - `closeRoom()` → re-publish with `["status", "closed"]`
    - `endRoom()` → re-publish with `["status", "ended"]` if
      nostrnests treats that as the canonical "kill"
- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — overflow
  menu visible only when `account.userProfile() == event.pubKey`;
  options "Edit room" / "Close room".

### Scheduled rooms (#7)

- `amethyst/.../audiorooms/create/CreateAudioRoomSheet.kt` — add a
  toggle "Start now / Schedule"; show a `DatePicker + TimePicker`
  when scheduled. ViewModel already has a status field; emit
  `STATUS.PLANNED` + `["starts", <unix>]` via the existing
  `TagArrayBuilderExt.starts(...)` helper.
- `MeetingSpaceEvent`'s tag DSL has `starts` already; verify it
  (`quartz/.../meetingSpaces/TagArrayBuilderExt.kt`).

### Strings

`audio_room_edit_title`, `audio_room_close_action`,
`audio_room_create_schedule_toggle`, `audio_room_create_when`.

### Risk

- Re-publishing a kind-30312 with a smaller `p`-tag set (e.g. host
  removed someone) requires the FULL list of participants to be
  rebuilt — don't lose anyone who'd already been promoted. The
  ViewModel needs to read the current participant list and only
  diff the one row the user touched.

---

## Step 5 — Speaker / admin role parsing + promotion (#3) + hand-raise queue (#4)

Depends on Step 1 (presence aggregation) for the hand-raised list.

### Quartz

- `quartz/.../nip53LiveActivities/streaming/tags/ParticipantTag.kt` —
  already parses the role byte. Add `isAdmin(...)` helper alongside
  `isHost(...)`.
- `quartz/.../meetingSpaces/MeetingSpaceEvent.kt` — verify
  `participants()` returns ALL roles (`host`, `admin`, `speaker`,
  `participant`); add `admins()` / `speakers()` filters if missing.

### Commons / VM

- `commons/.../viewmodels/AudioRoomViewModel.kt`:
    - Replace single-host gating with role check:
      `isLocalUserSpeaker = roles.contains(localPubkey)` where
      `roles = host ∪ admin ∪ speaker`.
    - Gate `startBroadcast()` on `isLocalUserSpeaker`.

### Amethyst

- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — host /
  admin overflow on each participant avatar:
    - "Promote to speaker" / "Demote to listener"
    - Re-publishes kind-30312 with the target's `p`-tag role updated.
- `amethyst/.../audiorooms/room/HandRaiseQueueSection.kt` (NEW) —
  list of pubkeys whose latest presence has `["hand", "1"]` and who
  aren't already `speaker|admin|host`. Each row has an "Approve"
  button that promotes them.

### Strings

`audio_room_promote_speaker`, `audio_room_demote_listener`,
`audio_room_raised_hands_section`,
`audio_room_approve_speaker`.

### Tests

- `commons/.../AudioRoomViewModelTest.kt` — role gating: a plain
  listener can't startBroadcast; promotion via the host's emit makes
  the local user eligible.

---

## Step 6 — Kick (#5)

### Quartz

- `quartz/.../experimental/audiorooms/admin/AdminCommandEvent.kt`
  (NEW) — kind 4312, ephemeral. Builder takes
  `(roomATag, target, action)`. `parse(...)` returns
  `(targetPubkey, action)`. Register in `EventFactory.kt`.

### Commons

- `commons/.../viewmodels/AudioRoomViewModel.kt`:
    - Subscribe to `kinds=[4312], #a=[roomATag], #p=[localPubkey],
      since=now-60s`. On a match where the event is signed by a
      `host|admin`, fire `disconnect()`.

### Amethyst

- Kick action on the role overflow (Step 5) — host/admin only.
- Tear down: confirm `disconnect()` fully closes the WT session +
  clears the foreground service.

### Tests

- `quartz/.../AdminCommandEventTest.kt` — round-trip + reject when
  signer isn't a host.
- `commons/.../AudioRoomViewModelTest.kt` — receive a valid kick →
  `connection` flips to `Idle` within the test scope.

---

## Strings batch for Tier 1

Single PR can collect these into `strings.xml` to avoid back-and-forth:

```
audio_room_listener_count, audio_room_chat_send,
audio_room_chat_placeholder, audio_room_chat_empty,
audio_room_reactions_button, audio_room_edit_title,
audio_room_close_action, audio_room_create_schedule_toggle,
audio_room_create_when, audio_room_promote_speaker,
audio_room_demote_listener, audio_room_raised_hands_section,
audio_room_approve_speaker, audio_room_kick_speaker,
audio_room_kicked_toast
```

## Suggested commit order (one PR each, mergeable independently)

1. presence-tags + listener-counter (Step 1 + #8)
2. live-chat (Step 2)
3. reactions (Step 3)
4. edit + close + scheduled (Step 4)
5. role-parsing + promote / demote + hand-raise queue (Step 5)
6. kick admin command (Step 6)

Tier 2 and beyond live in sibling docs in this folder.
