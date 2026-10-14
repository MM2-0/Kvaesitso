# Intelia interactions, ordered FLOW and Pulse — 2026-10-14

This records the task 030 snapshot. The later priority task 040 extends it;
current artifact hashes, appearance checks and separate background/HOME diagnosis
are in [native foundation report](NATIVE_FOUNDATION_REPORT.md). Earlier measurements
below are historical observations, not the final task 040 performance result.

Task 030 on `codex/intelia-wallpaper-design`, based on `753cea330`. User approved
the original-design refinements and explicitly extended scope to a global
island. D011 records the architecture/privacy boundary. No external design/code
assets, dependency upgrades, AI, Accessibility automation or network access added.

## Delivered behavior / changed paths

- `ArcGesture.kt`, `InteliaLens.kt`: bounded sticky selection survives a quick
  overshoot and detects an icon crossed between pointer samples. Returning to
  LENS, a far exit, second pointer, cancellation and duplicate release remain
  safe. Short upward LENS swipe opens search. Left/right thumb reach moves both
  LENS and ARC; changing reach restarts pointer recognition with new geometry.
- `InteliaIdentity.kt`, `InteliaHome.kt`: original monoline numeral paths and
  paired-stroke clock punctuation; same motif in Pulse. More visible real system
  wallpaper with local contrast gradients; no permanent marketing copy/dock.
- `InteliaIndex.kt`: open letter lanes and four manually assigned spaces, without
  category boxes or inferred usage tracking. Searching still searches all apps.
  Existing search/shortcuts stay available; no new catalog/provider is introduced.
- FLOW codec/executor/runtime/service, shared editor/settings and native view
  model: one exact alias opens one to four personal-profile apps in explicit
  order. Editor supports ordered selection/removal/moving earlier. Preview names
  the complete sequence before explicit activation. All targets resolve before
  dispatch and revalidate as a set; existing guarded LauncherApps adapter runs
  consecutively in one main-thread turn. No timers/background continuation or
  third-party UI automation. Platform failure can leave an earlier app open;
  the error says so. Existing v1 aliases stay v1; sequences use version 2.
- `InteliaIslandService.kt`, policy, settings/manifest: opt-in Pulse overlay,
  compact paired-stroke handle, real media metadata/transport, five existing ARC
  targets and ordered FLOW. Uses system-bound NotificationListenerService and
  public media-session callbacks; notification payloads are not read/stored.
  Empty paused sessions are ignored, player label substitutes missing title.
  Disabled/locked/off/hidden policy removes the window and unregisters media
  callbacks. Unchanged metadata avoids rebuilding the overlay on position ticks.
- UI codec/preferences/resources/tests: spaces/reach/Pulse persist with existing
  preferences and backup adapter. UI backup reads bounded to 1 MiB (covers the
  validated worst-case Unicode map); restore always leaves global Pulse off.
  Original aliases/ARC/theme settings and license/source delivery retained.

## Build and automated verification

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest :app:app:assembleDefaultRelease '-Pintelia.localPreview=true' --offline --console=plain --max-workers=2 --no-problems-report
```

Final PASS, 2m27s: 2332 tasks, 24 executed and 2308 up-to-date. Existing unchanged
modules reuse successful tests; native UI task executed. JUnit XML: 114 passed,
one pre-existing locations skip, zero errors/failures. New cases cover skipped
pointer samples/overshoot, deliberate exits, directional swipe, ordered payload
round-trip/limits, prevalidation of later missing steps, batch adapter failure,
preferences and Pulse visibility policy. These do not substitute for Android
integration tests. Earlier candidates passed; one intermediate Lint FIR analyzer
crashed. Stable-source rerun passed without suppressing Lint/changing dependencies.
A later title-variable compile collision was fixed before final build/install.

Final APK: `app/app/build/outputs/apk/default/release/app-default-release.apk`.
SHA-256 `9d801067c504734471d1e2bccf24a275995ab0d2156f93543903e7a0f5e7f005`.
Embedded source SHA-256
`ea160323781dfb28b9d8a61814db005ae47db64b132b8ec2c83e815155264f0c`.
Archive CRC and all 30 changed/new source/resource/test files matched embedded
source exactly; required license/wrapper/attribution present, private entries
absent. Documentation/reports are excluded from the source snapshot by design.

## Device verification

A059 / Android 16/API 36. Signed non-debuggable preview retains existing debug
package ID. Updates used `adb install -r <APK>`, without data wipe. User separately
enabled Pulse and personally granted SYSTEM_ALERT_WINDOW and notification-listener
access through Android settings. No grants were injected by ADB. Existing Home
role, five saved ARC apps, original alias `vlak` and Nothing/other apps retained.
Private phone-only backup was not read/copied/overwritten.

- Upward swipe across LENS opens search; original touch/keyboard paths remain.
- Held quick drag directly past the center icon opens the selected saved app;
  system resumed-component metadata confirms dispatch. Returning to LENS cancels.
- Created useful alias `cesta`: Alive App → Můj vlak. Exact preview showed both
  names. Explicit activation from search and from Pulse ended in Můj vlak;
  Alive App appears in task metadata. No private app screen/card/ticket was read.
  This proves dispatch/task placement, not that a cold ISIC screen has loaded.
- Můj vlak manually assigned to `Na cestě`; visible in that space. No-match query
  shows the existing empty result message. Search continues across all spaces.
- Right reach moved LENS bounds 63 px to the right at density 2.625; restored
  centre afterward. ARC list/order were not changed.
- Pulse visible above our separate temporary test app; tap/hold expanded controls.
  Actual Android synthetic MediaSession callbacks responded to Pause/Play/Next
  (track title 1 → 2 → 3 and pause icon changed). No audio/network emitted by the
  test fixture: this is media integration evidence, not Spotify/audio-output QA.
  Fixture was checked absent before installation and removed afterward; absence
  checked again. It is not included in Git or the launcher APK.
- Pulse's ARC opened Chrome; Pulse's FLOW launched the ordered `cesta` sequence.
- Own setting off removed the titled overlay window; on restored exactly one
  window. Fresh bounds were used after the bottom sheet resized; an earlier
  fixed-coordinate attempt did not re-enable the toggle and was corrected.
  User's enabled state restored. Other app overlays were not closed/modified.

`adb shell dumpsys activity activities` (resumed component only), own-package
uiautomator bounds, screenshots of own Home/test app and the titled own window
were used. Generic dumps do not include a non-focusable overlay in the main tree;
absence there was not treated as proof the window was missing. No target-app
private UI was captured. Final frame observations and regression results follow.

Final APK regression PASS: TAB/Enter/Space search, Up into all five ARC items,
focused icon/Escape cancellation, touch search, prefix/exact existing FLOW,
ten hold/release-without-selection cycles and system HOME return. Default HOME
role still Intelia. Long-pressing Pulse also opened the final panel.
`apksigner verify --verbose <APK>` PASS v2; final badging non-debuggable, permissions
only SYSTEM_ALERT_WINDOW, QUERY_ALL_PACKAGES and own private receiver. Listener
binding is guarded by the system signature permission. Known-secret/artifact
scan of 2159 source files and 1033 APK entries PASS; CRC PASS.

Frame observations with normal animations restored and Pulse enabled/compact:

| Workload | Frames | Janky | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Ten ARC hold 750 ms / release / 420 ms | 360 | 5.56% | 24 ms | 34 ms |
| Ten LENS / system HOME, 350 ms pauses | 782 | 6.27% | 23 ms | 48 ms |

Reset/read `adb shell dumpsys gfxinfo dev.intelia.launcher.debug` around each
loop, with no UI dumps inside it. These single rounds and prior versions do not
establish a speed improvement; frame tails remain. An initial run accidentally
used reduced motion after a fixed-coordinate toggle hit a shifted control:
ARC 40 frames/47.50%, LENS 787/5.34%. Original reduced-motion OFF was restored
through fresh own UI bounds and verified; the two workloads were then repeated
above. Do not compare that initial low-frame-count sample with normal animation.
No global animation, refresh, blur, rotation or power setting was changed.

## Limits and next task

Pulse is an application overlay below critical status bar/IME; protected screens
can hide it. It does not replace the system camera cutout or vendor UI. Android
requires broad notification-listener consent to expose other players' sessions:
[media-session API](https://developer.android.com/reference/android/media/session/MediaSessionManager),
[overlay API](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY).
No privacy/energy/performance certification is claimed. Audio output with actual
third-party players, physical lock/reboot/revocation, large fonts/TalkBack, older
devices and long-session battery/thermal behavior remain unrun. Full backup
restore of new spaces is covered by codec contracts, not a private-data restore
trial. Only app-launch steps are supported; configurable gesture binding or
exporting a FLOW as an Android pinned shortcut is not implemented in this slice.
Next smallest task: user design review, controlled LENS/IME tracing and focused
global-Pulse lifecycle/accessibility acceptance, without new feature expansion.
