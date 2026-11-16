# Tier 3 — coding plan (room theming, background-audio audit)

> **Status:** shipped — `ColorTag`/`FontTag`/`BackgroundTag` parsers exist in quartz meetingSpaces and the background-audio audit (separate doc) closed.
> _Audited 2026-06-30._

Tier 3 of `2026-04-26-nostrnests-integration-audit.md`. These are
larger or lower-priority items; do them after Tier 1 and Tier 2 ship.

## Step 1 — Room theming PARSER ONLY (#14, partial)

The minimum-viable slice. Goal: **don't render themed rooms badly.**
We parse the theme tags and either honour them with Compose color
overrides or ignore them with a graceful fallback (current visual).

Skip the kind 36767 / 16767 Ditto-theme refs and the per-user
profile theme — those are full features deferable to a later phase.

### Quartz

- `quartz/.../nip53LiveActivities/meetingSpaces/tags/ColorTag.kt`
  (NEW) — parser for `["c", hex, "background"|"text"|"primary"]`.
  `assemble(hex, target)` builder.
- `quartz/.../meetingSpaces/tags/FontTag.kt` (NEW) —
  parser for `["f", family, optionalUrl]`. Builder.
- `quartz/.../meetingSpaces/tags/BackgroundTag.kt` (NEW) —
  parser for `["bg", "url <u>", "mode tile|cover"]`. Builder.
- `quartz/.../meetingSpaces/MeetingSpaceEvent.kt` — accessors:
  `colors(): List<ColorTag>`, `font(): FontTag?`,
  `background(): BackgroundTag?`.
- `quartz/.../meetingSpaces/TagArrayBuilderExt.kt` — `colors`,
  `font`, `background` DSL functions.

### Commons / Amethyst

- `commons/.../viewmodels/AudioRoomViewModel.kt` — expose
  `roomTheme: RoomTheme?` derived from the event.
- `commons/.../viewmodels/RoomTheme.kt` (NEW):
  ```kotlin
  data class RoomTheme(
      val background: Color?,
      val text: Color?,
      val primary: Color?,
      val backgroundImageUrl: String?,
      val backgroundMode: BgMode = BgMode.COVER,
  )
  enum class BgMode { TILE, COVER }
  ```
- `amethyst/.../audiorooms/room/AudioRoomThemedScope.kt` (NEW) —
  Composable wrapper that takes a `RoomTheme?` and overrides
  Material3 `colorScheme` for its content. If null → no override.
  Wrap `AudioRoomFullScreen` body in this.
- Background image: render via Coil `AsyncImage` behind everything,
  honouring `backgroundMode` (cover via `ContentScale.Crop`, tile
  via custom `Modifier.repeatedBackground`).

### Skip / defer

- Font loading (`["f", family, url]`) — would need a
  `FontFamily` loader. Not user-blocking for parity.
- Kind 36767 (Ditto themes) and kind 16767 (per-user profile
  themes). Mark as out-of-scope for now.

### Strings

None — pure visual.

### Tests

- `quartz/.../ColorTagTest.kt` — round-trip + invalid hex / target.
- `commons/.../AudioRoomViewModelTest.kt` — `roomTheme` populates
  from a fake `MeetingSpaceEvent` with `c` / `bg` tags.

### Risk

- Some rooms use unusual hex shapes (`#abc`, named colors). Keep
  the parser strict (`^#?[0-9a-fA-F]{6}$`) and fall back on parse
  failure rather than crashing.

---

## Step 2 — Background audio + wake-lock audit (#15)

### Audit checklist

For each item, look in the named file and either confirm the
behaviour or open a follow-up bullet:

1. **`PARTIAL_WAKE_LOCK`** during a broadcast.
   File: `amethyst/src/main/java/com/vitorpamplona/amethyst/service/audiorooms/AudioRoomForegroundService.kt`.
   Check: is `PowerManager.newWakeLock(PARTIAL_WAKE_LOCK, "...")`
   acquired in `promoteToMicrophone(...)` and released in
   `stop(...)`? If not, add it.
2. **Foreground service type `microphone`** for Android 14+.
   Same file. Confirm the manifest declares
   `android:foregroundServiceType="microphone"` and the service
   call uses `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`.
3. **Listener-only foreground type** when not broadcasting.
   `AudioRoomForegroundService.startListening(context)` should use
   `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` (NOT microphone — Play
   Store rejects the elevated permission for listen-only users).
4. **Audio focus**. AudioTrack alone doesn't request focus; verify
   `AudioManager.requestAudioFocus(AudioFocusRequest.Builder(...))`
   is called in `AudioTrackPlayer.start()` and abandoned in
   `stop()`. If not, add it (so a phone call ducks the room).
5. **PIP keep-alive**. Confirm `AudioRoomActivity` survives the
   transition to PIP without dropping the foreground service —
   should already be the case (commit history shows this was
   audited previously).

### Output

A short audit notes file under `nestsClient/plans/` if any item
turns up missing — otherwise mark this step done.

---

## Suggested commit order

1. theme parser (Quartz: tags + accessors + tests) — small, safe
2. theme renderer (Compose: themed scope + background image)
3. background-audio audit notes (and any fixes that surface)

## Out of scope for Tier 3

- Kind 36767 (Ditto theme reference) and kind 16767 (per-user
  profile theme) — these are full sub-features behind the basic
  theming. Defer to a separate phase if needed.
- Per-user font loading — defer.
