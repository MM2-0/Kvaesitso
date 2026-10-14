# Native foundation — independent diagnostics

2026-10-14, task 040. Work stays on `codex/intelia-wallpaper-design`,
based on `753cea330`; the delivery commit is recorded in Git. Existing task 030 changes and all phone data preserved.

## Recovery

Source checkpoint before this extension: external recovery ZIP, SHA-256
`fa70a841698f6b7e17ea36e89c257eb67a17fc8e84bf0687085b074a2908f76c`,
CRC verified. Captures tracked/nonignored source plus status/HEAD, not user phone
data or signing keys. Previous APK retained outside Git for rollback. Existing
private backup remains only on the phone, never read/pulled/overwritten.

## Dark inner rectangle — actual cause and verified repair

The Home contrast gradient was a fillMaxSize Box **inside** Scaffold content's
system inset and 20 dp horizontal padding. It drew a hard inset rectangle over
the wallpaper. Root theme already had transparent window background and
SHOW_WALLPAPER; this was not a missing wallpaper image or Recents task bug.

Moved backdrop/gradient into the full-window root Box outside padded content;
Scaffold background is transparent. No opaque patch, clipping mask, copied
wallpaper or fake illustration. Signed candidate installed with `adb install -r`.
Normal Home before/after screenshots visually confirm the inner edges are gone
and the user's existing wallpaper extends edge to edge. Clock/LENS retain safe
content insets. Root-fix APK SHA-256
`dd4dd0d8bdc3cde7392932f89069562b60ba7c8d350df2075829cba87460491e`;
embedded source `20ebd656e3e839cef41dcf9181a8f0ec315d25d810d8c1e9c48ee4c4ef7f844a`,
30 changed/new source files exactly matched; archive CRC and tests/build passed.
Private before/after screenshots are outside Git. This fixes rendering only.
Pixel inspection supports the visual result: mean grayscale jump across former
left/right edges x=53/1027, rows 700–1949 (3 px strips on each side), changed from
-6.589/+3.549 to +0.370/-0.327 levels out of 255. Same real wallpaper, no image
editing. This is a rendering observation, not a Recents/animation measurement.

## HOME/Recents — separate investigation

Actual default HOME role is Intelia. System MAIN/HOME intent resolves our exported
singleTask Home activity in a real `type=home` root task, full-display bounds.
Repeated Home returns to the same task. Manifest/theme/lifecycle were inspected;
no fake recent-app task/finish-relaunch loop introduced as a cosmetic workaround.

Nothing OS's public config_recentsComponentName resolves to its own
`com.nothing.launcher/com.android.quickstep.RecentsActivity`. KEYCODE_APP_SWITCH
was observed resuming that component and HOME returned to Intelia. An external
Settings app followed by upward Home swipe returned to Intelia. Repeated injected
swipe/hold did not reliably enter overview; absence of a top-activity change is
not evidence of broken overview (Nothing may integrate overview in Home).

Temporarily selected original Nothing Home for comparison, observed
`com.nothing.launcher/com.android.searchlauncher.SearchLauncher`, invoked overview
and returned Home, then restored Intelia in a finally block; role verified.
No original launcher data or other applications were closed/reset/uninstalled.
This confirms task ownership/routing, not identical vendor animations. No claim
that moving the gradient fixes Recents, nor that a Quickstep limitation is the
confirmed cause of every minimization symptom. Physical gesture/animation
acceptance remains explicitly open until reproducible target evidence exists.

Lock test: system sleep removed Pulse window and media observation (`window=false`,
`observing=false`); wake requested. User unlocked the phone; final installed build subsequently reports
`connected=true`, `observing=true`, `window=true`. The user later gave a broad
manual response, “ok, asi dobrý”, with no specific fault. This is not a detailed
physical gesture/lock acceptance record. No lock-screen screenshot/private notifications read.

## Design/icon implementation and final verification

See [design system](DESIGN_SYSTEM.md). Four icon modes, safe per-app modes, grouped
appearance pages, live theme/font/date choices and shared bounded rendering are
implemented in source. UTF-8 labels and real installed pack choices (Lux Nothing,
Nothing Adaptive) were checked in the native appearance UI. An intermediate
encoding defect in newly added translations was caught in device testing and
fixed; all changed/new text now validates as UTF-8 without replacement characters.
Earlier mode checks stopped at the own-package guard when a different app became
foreground and are not counted as passed. After the user again confirmed exclusive
availability, all five concrete choices passed rendering, checked-state and Chrome
resumed-component checks: System, Intelia, Monochrome, Lux Nothing and Nothing
Adaptive. Private ARC screenshots visually confirm each style; School Online keeps
its original mark where the selected pack has no mapping. System mode was restored;
the last tested custom pack remains remembered for later optional selection.

On the final APK, per-app Chrome monochrome visibly updated Search without changing
its component/key, then was reset to inherit. System icon mode survived `install -r`.
App light/dark, sage accent and platform serif visibly hot-applied and original
active color/font choices were restored. Global system theme was not changed;
forced app modes and following the current dark system are verified, a global
night-mode transition test is not claimed.

Device inspection also caught an unrelated fuzzy public shortcut in an app query.
Native results now additionally require an accent-insensitive literal token match
in the shortcut label or its parent-app name. A Chrome query no longer surfaces
the unrelated shortcut. App search and exact explicit FLOW remain unchanged.

Final stable-source build PASS (2m51s, 2332 tasks, 30 executed/2302 up-to-date):

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest :app:app:assembleDefaultRelease '-Pintelia.localPreview=true' --offline --console=plain --max-workers=2 --no-problems-report
```

JUnit XML: 119 tests, **118 PASS / 1 pre-existing skip / 0 failure / 0 error**.
Skipped: `OpeningHoursTest.testEvenOddWeeks`, inherited
`@Ignore("Expected to fail with the current implementation")`; unrelated location
schedule parser TODO, not disabled to make this change pass.
Appearance codec tests cover old defaults, round-trip and invalid restore values.
`apksigner verify --verbose <APK>` PASS v2, non-debuggable preview identity retained.
APK `app/app/build/outputs/apk/default/release/app-default-release.apk` SHA-256
`bc90ff9c3509d9c452b907c1048c75f8f5d320e3ea42afe884228771899798e4`.
Embedded source SHA-256
`79fb9183c75053990fc6bde1eaa6e1c3da236e4376e285dca5ff80435d7f0a85`.
All 33 changed/new source/resource/test files byte-match source ZIP, deleted old
cache absent; 2775 source entries, required licenses/attribution/wrapper present.
APK/source CRC PASS. Scoped known-secret/artifact scan: 2165 source files and
1033 APK entries, no findings. This is bounded evidence, not a universal safety
certification. `git diff --check` PASS. `npm.cmd run docs:build` PASS; final evidence text is rebuilt before commit.

No additional dependency or task-040 permission. Full backup restore with private
data, reboot, real-player audio output, TalkBack/large fonts and sustained energy/
thermal measurements remain unrun. Icon codec/backup contracts do not substitute
for these checks. Task 050 is a proposed next boundary, not implemented work.


## Final phone regression and frame observation

Latest APK installed with `adb install -r`, preserving the signed package, data,
HOME role, five ARC slots and saved original FLOW. Only `dev.intelia.launcher.debug`
remains among the own test-package prefix; synthetic media fixture absent. Phone-only
backup still 3741 bytes with its original timestamp; only metadata checked.

PASS: LENS Tab focus, Enter/Space search, Up/five ARC targets, icon focus, Escape
cancellation, touch search, exact saved FLOW versus non-running prefix, ten held
releases without a selection, and ten LENS/system Home cycles. Short upward LENS
swipe also opened search. Final Pulse diagnostic: enabled/connected/observing/window
all true, hidden false. No other apps closed/reset; no global animation/blur/power/
refresh/theme settings altered.

`adb shell dumpsys gfxinfo dev.intelia.launcher.debug reset` before each workload,
then read counters, no UI dumps inside loops. Normal motion, centre reach, System
icons, enabled compact Pulse; same 750/420 ms ARC and 350/350 ms LENS pacing as the
previous rebind candidate:

| Workload | Previous rebind candidate | Final build | Final p95 / p99 |
| --- | --- | --- | --- |
| Ten ARC holds/releases | 359 frames, 5.57% jank, p95 24 ms | 360 frames, 5.56% | 26 / 34 ms |
| Ten LENS/system Home cycles | 722 frames, 9.56%, p95 38 ms | 783 frames, 5.87% | 21 / 48 ms |

No obvious broad frame regression appears in this short round; ARC p95 rose 2 ms.
Different root/background/rebind changes coexist with the new icon adapter, so this
cannot isolate an icon-engine speed gain or establish statistical/battery/FPS
acceptance. Frame tails and controlled long-session profiling remain open.

A later extra FLOW-close check was interrupted by a different foreground app and
was not counted as passed. Automated overview evidence is from the root-fix
candidate with unchanged HOME manifest/routing. User was invited to check real
Home/Recents/FLOW/lock gestures, replied “ok, asi dobrý, dodělej to a budeme
pokračovat zítra”. Finish today's source/evidence checkpoint; do not turn this
qualified response into an unmeasured smooth-animation guarantee. No additional
phone control after that instruction. Next smallest task is user design/gesture
review and focused acceptance from task 050, not more unsolicited features.
