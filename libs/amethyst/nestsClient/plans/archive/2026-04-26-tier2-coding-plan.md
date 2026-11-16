# Tier 2 — coding plan (participant grid, per-avatar context menu, zap, naddr share)

> **Status:** shipped — `ParticipantGrid` (commons) + `ParticipantsGrid`/`SpeakerZapOverlay`/`ParticipantHostActionsSheet` (amethyst) deliver the grid, context menu and zap entry points.
> _Audited 2026-06-30._

Concrete file-level plan for shipping Tier 2 of
`2026-04-26-nostrnests-integration-audit.md`. Assumes Tier 1 has
landed (presence aggregation, role parsing, edit-room flow).

## Step 1 — Participant grid (#9)

The flagship UI piece. Renders three groups:
1. **On-stage** — pubkeys in the kind-30312's `p` tags with role
   `host`, `admin`, or `speaker`, minus those whose latest presence
   has `["onstage", "0"]`.
2. **Currently broadcasting** — pubkeys with active moq-lite
   announces visible on `MoqLiteSession.announce(prefix=room).updates`.
3. **Audience** — every pubkey from kind-10312 presence in the last
   5 min that isn't on stage.

### Reuse

- `commons/.../viewmodels/AudioRoomViewModel.presences` (Tier 1
  Step 1) — gives us {pubkey → flags + lastSeen}.
- `MoqLiteSession.announce(prefix)` — already returns a flow of
  `MoqLiteAnnounce` updates; need to expose it on `MoqLiteNestsListener`
  via a `liveBroadcasters: StateFlow<Set<String>>`.

### New

- `commons/.../viewmodels/AudioRoomViewModel.kt`:
    - `liveBroadcasters: StateFlow<Set<String>>` populated by an
      announce-please subscription against the room's namespace
      prefix. Tracks only `Active` suffixes (drop on `Ended`).
    - `participantGrid: StateFlow<ParticipantGrid>` derived from
      `event.participants() + presences + liveBroadcasters`.
- `commons/.../viewmodels/ParticipantGrid.kt` (NEW) — pure data:
  ```kotlin
  data class ParticipantGrid(
      val onStage: List<RoomMember>,
      val audience: List<RoomMember>,
  )
  data class RoomMember(
      val pubkey: String,
      val role: ROLE?,                  // null for plain listeners
      val handRaised: Boolean,
      val muted: Boolean?,
      val publishing: Boolean,
      val lastSeenSeconds: Int?,
  )
  ```
- `nestsClient/.../MoqLiteNestsListener.kt` — expose
  `discoverPublishers(prefix: String): Flow<MoqLiteAnnounce>`
  (thin wrapper that delegates to the underlying
  `MoqLiteSession.announce(prefix).updates`).
- `amethyst/.../audiorooms/room/ParticipantsGrid.kt` (NEW) — Compose
  `LazyVerticalGrid` with section headers ("On stage", "Audience").
  Each cell is the avatar + small status icons (mic on/off, raised
  hand, broadcasting indicator).
- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — replace
  the current `onStage` / `audience` blocks with `ParticipantsGrid`.

### Risk / open

- Decide what to render when a `kind-30312` `p` tag has no
  presence (member never joined): show greyed-out? hide entirely?
  nostrnests greys out — match for parity.

---

## Step 2 — Per-participant context menu (#11)

A bottom sheet that opens on avatar tap.

### Reuse

- `accountViewModel.toggleFollow(user)` and similar helpers exist;
  audit `AccountViewModel.kt` for the public surface.
- `accountViewModel.zap(...)` for zaps (next step).
- `MutedUsersScreen` patterns for mute / unmute.

### New

- `amethyst/.../audiorooms/room/ParticipantContextSheet.kt` (NEW)
  — `ModalBottomSheet` with rows:
    - **View profile** → `nav.nav(Route.User(pubkey))`
    - **Follow / Unfollow** → `accountViewModel.toggleFollow`
    - **Mute** → mute-list edit
    - **Zap** → opens the existing zap dialog (Step 3 below)
    - **Promote to speaker / Demote** → only when local user is
      host/admin (Tier 1 Step 5 surface)
    - **Kick** → only when local user is host/admin
- Call site: `ParticipantsGrid` cell `onLongClick` (or tap, for
  parity with nostrnests web behaviour) opens the sheet keyed on
  the `pubkey`.

### Strings

`audio_room_participant_view_profile`,
`audio_room_participant_follow`, `audio_room_participant_unfollow`,
`audio_room_participant_mute`, `audio_room_participant_zap`,
(role + kick strings already added in Tier 1).

---

## Step 3 — Zap support inside the room (#12)

Mostly a matter of plumbing the existing zap dialog.

### Reuse

- Amethyst's existing zap flow — find the `ZapDialog` / `ZapBottomSheet`
  composable and its `accountViewModel.zap` plumbing.
- Reactions sub from Tier 1 Step 3 — already pulls
  `kinds=[7,9735], #a=[roomATag]`, so a paid zap appears in the
  reaction overlay automatically.

### New

- Two zap entry points:
    1. From the context sheet (Step 2) — zap a single participant.
    2. From a "Zap room" button in `AudioRoomFullScreen.kt`'s
       overflow — zap the kind-30312 host pubkey, tag the room
       address.
- A small `RoomZapAdapter.kt` (NEW) that builds the zap request
  pointing at either the room (NIP-57 `["a", roomATag]`) or a
  speaker (NIP-57 `["p", pubkey]`).

### Risk

- The existing zap dialog likely takes a `Note`. The room IS a
  `Note` (`AddressableNote` over the kind-30312); confirm the
  dialog accepts it. If not, add an overload accepting
  `(authorPubkey, optionalRoomATag)`.

---

## Step 4 — Share via naddr (#13)

### Reuse

- `quartz/.../nip19Bech32/NAddress.kt` — has `create(...)`.
- Amethyst likely has a generic `ShareDialog` for `naddr` /
  `nevent` share — find it, reuse.

### New

- `amethyst/.../audiorooms/room/AudioRoomFullScreen.kt` — overflow
  menu "Share room":
    - Build `NAddress.create(KIND, hostPubkey, dTag, relays)` from
      the room event.
    - Open `ShareDialog` with the resulting `naddr1...` + a
      pre-filled `nostr:naddr1...` clipboard option + a
      "Share to Nostr" button that opens the existing
      `ShortNotePostScreen` pre-loaded with the naddr URI.

### Strings

`audio_room_share_action`, `audio_room_share_text` (template like
`"Join {room name} {nostr:naddr1...}"`).

---

## Suggested commit order

1. participant-grid (Step 1) — the foundation; everything else hangs
   off it.
2. context-sheet skeleton (Step 2 without role / kick rows; reuses
   Tier 1 Step 5 wiring once it's in).
3. zap (Step 3) — plumbing-only.
4. naddr share (Step 4) — pure UI.

Tiers 3 and 4 live in sibling docs.
