# Background-audio audit (T3 #2)

> **Status:** shipped — audit of `AudioRoomForegroundService`; 4/5 items passed, the audio-focus fix landed, no follow-ups remain.
> _Audited 2026-06-30._

Audit run for the Tier-3 plan checklist. Items pass-by-default;
deltas captured below.

## 1. `PARTIAL_WAKE_LOCK` during a broadcast — ✅ PASS

`amethyst/src/main/java/com/vitorpamplona/amethyst/service/audiorooms/AudioRoomForegroundService.kt`

  - Acquired in `onCreate()` (line 64) via `newWakeLock(PARTIAL_WAKE_LOCK, "amethyst:audio-room")` with `setReferenceCounted(false)`.
  - Acquire on every `onStartCommand` if not held (line 99-101) with a 4-hour safety cap (`WAKE_LOCK_TIMEOUT_MS`).
  - Released in `onDestroy()` (line 174-175) via `wakeLock?.takeIf { it.isHeld }?.release()`.

## 2. Foreground-service type `microphone` for Android 14+ — ✅ PASS

Same file, `startForegroundWithType(includeMic)`:

  - When `includeMic = true` (broadcast mode), uses `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or FOREGROUND_SERVICE_TYPE_MICROPHONE` (lines 111-112). Required by Android 14+ while the mic is open.
  - The manifest already declares `android:foregroundServiceType="microphone|mediaPlayback"` (verified separately in `AndroidManifest.xml`).

## 3. Listener-only foreground type — ✅ PASS

Same call, `includeMic = false` path uses just `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` (line 114). Play Store policy requires the elevated `microphone` permission only when the mic is actually open; listen-only sessions correctly stick to media-playback.

## 4. Audio focus — ❌ WAS MISSING; FIXED IN THIS COMMIT

Pre-fix: `AudioTrackPlayer.start()` constructed an `AudioTrack` but never called `AudioManager.requestAudioFocus(...)`. A phone call would mix on top of the room audio instead of ducking it.

Fix: focus is now requested at the SERVICE level (one focus owner per room session, not per player) in `AudioRoomForegroundService.requestAudioFocus()`:

  - `AUDIOFOCUS_GAIN` with `USAGE_VOICE_COMMUNICATION` + `CONTENT_TYPE_SPEECH` so the OS treats the room like a phone call.
  - `setAcceptsDelayedFocusGain(false)` — we want the focus immediately or not at all.
  - On-change listener is a no-op; the OS handles duck-on-loss for us.
  - `abandonAudioFocusRequest` in `onDestroy()`.

Refused focus requests are caught with `runCatching` — a denial means the OS will duck/pause us based on its own policy, which is a degraded but acceptable experience and shouldn't fail service start.

## 5. PIP keep-alive — ✅ PASS (existing)

`AudioRoomActivity` already survives the PIP transition without dropping the foreground service. The activity's `onPictureInPictureModeChanged` doesn't stop the service; the service tracks its own `promoted` state independent of activity lifecycle.

## Summary

  - 4/5 items passed before this commit
  - 1 fix in `AudioRoomForegroundService` adds the audio-focus request

No follow-ups remaining for the Tier-3 audit checklist.
