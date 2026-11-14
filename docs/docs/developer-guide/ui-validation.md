# UI validation record

This record distinguishes source-level implementation, automated checks and observed rendering.
It accompanies the adaptive UI branch based on merged PR #46 (main merge `feaff22`, head `211d1fac`).

## Implemented patterns

See [Adaptive UI contract](./adaptive-ui) for the in-repo scope, component contracts, completion
criteria and test matrix. Shared Material theme tokens remain the palette/type/shape source;
no new production library or replacement design framework is introduced.

## Execution evidence

### Άκου automatic entry and Android YouTube link handlers

The opt-in entry effect waits for preferences and a visible screen, consumes one attempt,
checks microphone permission and avoids triggering merely because the toggle changes. The
existing capture job is cancelled on stop/disposal; an outstanding cancelled job is joined before
another automatic sample. Rotation retains the consumed attempt. Official YouTube Music
availability is queried off Main and launches use an explicit package.

`YouTubeWebLinks` restricts exported handlers to exact declared HTTP(S) hosts, rejects user-info,
lookalikes and unexpected ports, builds explicit shell-approval and current-user domain-selection commands, and verifies
approved/allowed/enabled state plus the actual Android resolver. Shell approval is package-wide;
user allowed/selection state is current-user only. The transient aliases dispatch only validated URLs into the existing Media
identity and app-lock flow. Every explicit link gets a new request ID; restored navigation does
not replay video autoplay. Shizuku selection disables both root fallback and ambiguous spawn replay.

`validate-akou-links.yml` compiles the UI, runs communication unit tests and requires five emulator
regressions: entry/permission gating, no toggle-triggered sample, resolver separation, stable Media
task/fresh request ID, and shell domain selection/readback. Emulator shell execution validates the
Android commands; it does not verify an actual Shizuku binder or OEM-specific permission policy.
Live microphone matching, official-app interoperability, complete visual review and physical
Android 17/Shizuku verification remain device checks. The local build cannot resolve the existing
NewPipe JitPack dependency because the cloud proxy returns HTTP 403; CI performs compilation.

### System Apps freezer grid and AppOps/firewall fixes (2026-11-14)

The System Apps tab now uses `FreezeDeskGrid`: adaptive rounded icon tiles, running/frozen badges,
list mode and a scrolling full-span search/filter header. Tapping opens the existing management
actions in an opaque sheet. `FreezeDeskGridTest` checks three columns at 360 dp, selected package
callbacks, list switching, accessible frozen status and scrolling in a 320 × 280 dp window at
font scale 2.0. The test supplies placeholder icon content to isolate layout from Koin and package
lookups; it is not evidence that all installed-app icons were visually reviewed.

Local command on parent revision `710294d2d`:
`./gradlew :libs:appops-next:testDebugUnitTest :services:network:testDebugUnitTest --no-configuration-cache --no-daemon --max-workers=1 -Dorg.gradle.parallel=false -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx3072m`
passed **328 AppOps and 75 Network tests**, with zero failures/errors/skips. This includes read retry,
cancellation, both-transports-failed handling, stdin EOF/concurrent pipe draining, and explicit
firewall targets without Internet permission/shared-UID siblings. Route/manifest validation scripts
also passed. Final UI compilation and the two device layout tests run in `validate-appops.yml`.
The final shell identity change also runs the app-management suite (including startup scripts)
and disables root/spawn replay for AppOps commands after an ambiguous Shizuku failure.

Release verification for 1.45.55: final code revision `ff3b07c6a` passed 433 local unit tests
(328 AppOps, 75 Network, 30 app-management), with no failures/errors/skips.
[CI run 43584628521](https://github.com/koukomin/Telos-Launcher/actions/runs/43584628521)
passed native daemon protocol tests, Android test APK compilation, all three unit suites and
both emulator layout tests; the report verifier confirms both cases ran without skips.
This is automated layout evidence, not a visual review or physical Android 17 confirmation.

The local full UI build is blocked by HTTP 403 from JitPack for the existing Tesseract dependency.
The cloud has no KVM/desktop capability; no physical OnePlus Android 17 service command or visual
review is claimed. Remaining visual coverage includes real icons, wallpaper contrast, IME,
enlarged display size, medium/expanded windows and the full font-size matrix. The UI work must not
be treated as fully visually certified from unit tests alone.

### ART filter scrolling regression after v1.44.19

A OnePlus Android 17 report identifies a crash when scrolling down the ART optimizer to
the per-app search/filter header. That header nested a `horizontalScroll` row inside
`TelosSearchBar`'s own horizontal scroller, passing infinite width to the inner scroller.
The ART header and `TelosSearchAndFilterBar` now emit chips directly into the shared
search bar. The document viewer already uses this contract. Filter/query ownership,
saved selection, labels, theme surfaces and ART execution controls are unchanged.

`ArtOptimizerScrollTest` renders the actual stateless ART header and the shared app
filter bar below the fold in a lazy list. It scrolls vertically to the header, reaches
every filter, changes selection and edits the query with Greek resources at font
scales 1.0/light and 2.0/dark in a 320 dp viewport. These tests execute no privileged
ART commands. The dedicated `ART filter scrolling regression` workflow checks that
both cases actually run and pass.

The [read-only Android regression workflow](https://github.com/koukomin/Telos-Launcher/actions/runs/42357872285)
on `b481e573e` completed successfully: it compiled the real instrumented UI APK
and passed both cases on an API 35 emulator, including the Greek/font-scale
matrix above and the explicit check that both cases executed successfully.
Physical OnePlus/Android 17 verification and the complete visual matrix remain unrun.

The recovered cloud checkout contains 17 JUnit result XMLs: **86 tests, zero failures/errors/skips**,
including eight adaptive layout tests and ten contrast tests. Those results predate reconciliation
with newer main `8e3110c`; they are historical evidence, not validation of the reconciled commit.
The initial Calendar dp-expression compile failure was fixed before those tests.

The final PR CI runs the complete `:app:ui:testDefaultDebugUnitTest` suite, compiles the
instrumented tests, builds a debug APK and runs focused `:app:ui:lintDefaultDebug` with
`tools/validation/ui-lint.init.gradle`. The focused IDs are NewApi, InlinedApi,
ContentDescription and RtlHardcoded; dependency checking is off. This is not full app lint.
The authoritative reconciled outcomes, final head and artifact links are recorded in
[PR #47](https://github.com/koukomin/Telos-Launcher/pull/47); its checks must pass before merge.

On `f1714ec`, [validation run 39430166171](https://github.com/koukomin/Telos-Launcher/actions/runs/39430166171)
passed all UI unit tests, instrumentation compilation and debug APK build. Focused lint then
reported nine NewApi errors. The follow-up fixes remove an unused API-28 class reference,
type the contact grouping as Map to avoid a JDK-21 SequencedMap cast, use PackageInfoCompat and
the older URI ContentResolver.call overload, and guard API-29 recovery, API-31 wallpaper hints
and API-28 profile controls. No check was disabled or suppressed.

[Instrumented run 39430166272](https://github.com/koukomin/Telos-Launcher/actions/runs/39430166272)
executed **three tests, zero failures/errors/skips** on Android 35. The job failed afterwards
because Gradle uninstalled the test APK before adb could retrieve its cache PNGs. Screenshot
output now uses shared MediaStore images on API 29+ so evidence survives test-app cleanup;
the final check/artifact links in PR #47 establish collection and visual-review outcomes.

Reconciliation preserves all main commits through `8e3110c`, including freeze/work-profile rules,
Terminal runtime, Files FTP/APK extraction and direct TV links. PR #45 is not included.

## Visual closure

Do not mark this phase visually complete from a successful compile or JVM policy suite.
Hardware-backed emulator acceleration is unavailable in this cloud: `emulator -accel-check`
returned exit 3, `KVM requires a CPU that supports vmx or svm`; `/dev/kvm` and CPU vmx/svm flags
are absent. The Android 30 x86_64 software emulator booted with `-accel off` / SwiftShader, but the
captured system home screenshot showed **“System UI isn't responding” before Telos was installed**.
This is a host/emulator limitation, not a Telos crash. That local attempt completed no Telos screenshot matrix or instrumented
execution; the later CI component tests described above are separate evidence. The runtime interruption also discarded temporary SDK/log/screenshot files;
only repository build reports survived. No light/dark/wallpaper UI visual pass is claimed. Hardware camera, calls/SIP,
recording, account mail/sync and third-party live/widget internals still require suitable devices,
permissions/accounts and real content.

## Consolidated app polish (PR #60)

PR #60 incorporates #40 and #47, reconciled through main `0a7dadf` and removes
the embedded terminal in favor of external Termux. Earlier historical terminal
references above describe the old reconciliation, not the final feature set.

CI run [39586454561](https://github.com/koukomin/Telos-Launcher/actions/runs/39586454561)
passed five Android UI tests and collected 42 screenshots. Light/dark radio palette,
large-text preference and navigation component screenshots were retrieved and
visually inspected. They are component evidence, not full app/device certification.
A later instrumented-test compilation exhausted a 2 GiB heap; the compilation
steps now receive 3 GiB, retaining one worker and in-process Kotlin compilation.

The final notification pass checks shared glyph/large-icon identity on Android.
Media3 services now set explicit app glyphs; mail, alarm, SIP and Network use shared
identity. Ermis/System full icons retain their launcher colors, and Trash retains
its delete symbol. Static aliases provide 22 OS Recents identities. Final-head
build/test outcomes and artifacts are recorded in PR #60. Physical-device OEM
Recents caching, notification headers, playback/hardware and crash/update recovery
remain separate acceptance checks; none are claimed passed by these component tests.


## English dark-wallpaper gallery (PR #61)

[Capture run 39628520954](https://github.com/koukomin/Telos-Launcher/actions/runs/39628520954) succeeded and retained 30 PNGs, UI XML and capture metadata, with no reported capture failures. The contact sheet was visually inspected before publishing the gallery: all 30 routes rendered, including Settings and System, without a visible crash dialog. Preview images preserve dimensions and use WebP compression; original PNGs remain in the workflow artifact. See the [gallery](../user-guide/telos/screenshots) for exact build-source metadata and environment. This is one clean English dark-mode emulator pass, not the full font/window/wallpaper or physical-device acceptance matrix.


## Freeze manager consolidation (PR #63)

The unified Apps / Auto-freeze / Settings implementation adds app timing, explicit companion rules and UAD descriptions with on-entry updates (default on, opt-out preserved). The VitePress build passed locally; all 50 new locale sets parsed with preserved result placeholders, and the bundled source contains 5,381 entries. Local Gradle could not download its wrapper due to restricted network access; Android compilation and freeze/preferences regression outcomes are delegated to the PR’s `Validate freeze management` check. No new screenshot or device/Doze pass is claimed. See [freeze architecture and audit](./freeze-management) for test scope and device acceptance needs.


The same PR adds the disabled-by-default Recents removal opt-in. Unit tests distinguish last-task removal, remaining tasks and other-user events. Shizuku registration, OEM callbacks, “close all”, process/binder reconnect and physical-device refreezing remain unverified until device acceptance; JVM task-policy tests do not certify those capabilities.

## Notes sharing and multilingual Morse (PR #64)

Local validation on 2026-10-22: all 50 base-locale XML sets parse with six translated labels each; `npm run docs:build --prefix docs` passes. The exact MorseCodec and MorseCodecTest sources compile with Kotlin 2.3.21 and the pinned AnyAscii 0.3.3 jar; JUnit 4.13.2 reports six passing tests, including Greek accents/final sigma, other supported scripts, Turkish device locale, unsupported symbols, whitespace and PARIS timing. Full local Gradle cannot download its distribution due to the environment network restriction. GitHub CI compiles the Android screens and runs JVM tests plus bounded image-export and screen-Morse instrumentation. Actual sound, camera-flash response, OEM lifecycle behavior and the full theme/font/window visual matrix remain unverified on a physical device.

The first emulator execution passed both PNG export tests and all eight shared adaptive UI tests. Its Morse UI test lost the Compose hierarchy because the flashlight’s portrait lock recreated the test Activity before assertions; the test now settles portrait orientation before installing content. This initial run does not establish a passing Morse screen test.

The Morse screen fixture also runs with RTL layout direction and asserts an explicitly LTR Morse preview, so Arabic/Hebrew UI direction cannot reorder the displayed signal. Romanized previews use LTR as well.

## System package management follow-up (2026-10-25)

The unified SystemAppDetailsRoute is registered in both hosts. This change reuses PreferenceScreen, flowing action rows, a lazy permission/component list and opaque semantic containers. Navigation and component search are saveable; confirmation is transient. Local Gradle is blocked by distribution network access. Translation/diff/manifest checks run locally; CI compilation and regression jobs must be checked on the pushed head. Root component changes, shared APK consumption, installed-list interactions, VPN enforcement and the full wallpaper/font/window screenshot matrix have not been verified on a physical device. No icon assets changed.

The embedded host also registers the freeze hub, statistics and Device Owner setup destinations reached from System management. `python3 tools/validation/system_app_routes.py` guards these destination registrations in both hosts; it is a static navigation check, not a device interaction test. Dangerous manifest permission rows carry a warning glyph as well as color.

Audio policy follow-up: System details now include translated focus/playback AppOps lists, with readback before displaying success. Parser tests cover Android shell defaults/history, wrong operation output, UID overrides and command injection. Physical root/Shizuku audio, OEM enforcement and changes to active playback remain unverified.

## Media redesign follow-up

New instrumentation covers the actual call termination target at font scale 2 in a short window, media card play/radio actions and Listen/Watch card screenshots in light/dark at font scales 1/2. Evidence is collected through MediaStore in Pictures/TelosUiValidation and survives test-APK uninstall. Full seven-source account/library/device screenshots remain unverified until reviewed. See media-redesign.md for feature inventory and non-visual limitations.

The expanded YouTube Music player has portrait/landscape, font 1/2 screenshot fixtures and callback tests for previous/next, pause, stop and queue selection. These are UI/controller-wiring checks; they do not establish live YouTube playback or OEM call/Recents behavior. The first media-card test passed; the initial call-touch test failed because its preferences DI fixture was missing, and that setup was corrected before rerunning.

`tools/validation/run-ui-device-tests.sh` preserves the test exit status while collecting MediaStore screenshots even after an assertion failure. A successful test run also requires successful evidence retrieval. The emulator workflow invokes the wrapper as a single shell command so exit-status variables survive the action’s per-line script execution.

The first successful Media component screenshot run exposed a missing explicit content-color scope in the expanded music player fixture. The player now provides the theme on-surface role for headings, transport icons and position text, including when hosted without an enclosing Surface. Final screenshot verification must use this correction.

## Theme overlay readability (October 26)

Source changes cover the retained Stremio catalog with opaque theme detail/gradient colors, wrap primary actions, and give shared app/settings roots an opaque fallback when no blurred wallpaper frame is available. Additional non-root surface roles remain opaque. FrostedTintTest covers light/dark fallback, requested transparency with a valid frame, and transparent chrome without a fallback. The execution environment was unavailable: no local build, unit-test run or device/visual verification is claimed. Verify both Telos themes, Greek labels, large fonts, details/Back, settings transitions, menus, static/video wallpaper, and unavailable wallpaper pixels before accepting visual completion. No merge or release is authorized.

## Phone follow-up on PR #67

Added regression tests for Greek/international aliases and suffix collisions, legacy hidden contacts with multiple numbers, call-screen/notification mutual exclusion and overlapping Activity owner tokens. CallerLookupTest uses intercepted synthetic responses with no real network requests to exercise opt-out, alias caching and foreign redirect refusal. Call photo/state transition screenshots and Android execution evidence are pending. Local host has Java 17 and no configured Android SDK; no local Android build/device result is claimed. The validation-only PR workflow compiles/builds and runs core/preferences/comms/UI unit tests. Carrier answer/disconnect, OEM notifications, actual contact-provider photos and the reported crash require physical-device verification and a stack trace if the crash persists. No merge/release is authorized.

CallSceneTest adds 24 synthetic incoming/outgoing/active screenshots across light/dark, portrait/landscape and font 1/2, checking the ringing-to-active transition and loaded-photo semantics. It does not place carrier calls. Static virtual-app manifest and System route validation passed locally; Android build and emulator execution remain in CI. The contact-delete confirmation now requests WRITE_CONTACTS at the user action and stays open on failure; provider deletion itself needs a disposable-device contact test.

## Supplied crash trace and notification regression

The user supplied the Android 16 / OnePlus / 1.42.13 trace for notification 7201: an ongoing CallStyle without FGS/UIJ/fullScreenIntent was rejected. The new policy uses ordinary actionable ongoing notifications and first-incoming-only full-screen CallStyle, and explicitly cleans up on unbind/destruction. JVM tests cover phase/token staleness and presentation selection. CallNotificationTest posts a synthetic ongoing notification on the emulator, checks absence of CallStyle/fullScreenIntent, then verifies that an ended live session cancels it even when the requested state is stale. This does not certify OnePlus carrier behavior.

Earlier head a2a5b46 passed the validation-only debug build and core/preferences/comms/UI unit-test workflow (40347937532). Its native compile/comms/network job also passed. The Stremio validation gate exposed missing existing labels in the newer Latin and Ancient Greek locales; actual translations are added, rather than disabling the gate. Final-head execution and screenshot outcomes remain pending.

The earlier daa5722 emulator run 40349140550 completed 17 Android tests with zero failures/errors/skips. Its artifact 12663767936 contains all 24 call-state screenshots. The call scene contact sheet was reviewed: fitted photos, initials, action placement and landscape layout rendered; the synthetic active-controls label revealed missing standalone content-color scope in the dark scene. CallScreenContent now explicitly supplies onSurface, and the final run must verify that correction. This is component evidence, not a carrier/OEM or full-device visual pass.

## Compact Media follow-up

MediaChromeTest checks one overflow, source-title switching, search expansion/input/close and selector/action geometry on 320/720dp widths, short landscape, font 1/2 and light/dark palettes, exporting collapsed/expanded search screenshots. Production components are used without network accounts. Execution and visual review of this final change remain pending; no full app/account or IME/device certification is claimed. The user has now authorized merge and release after implementation and successful checks; earlier no-merge notes describe the previous authorization state.

The Media header fixture also exercises three simultaneous 48dp toolbar actions at 320dp, guarding against a partially clipped overflow icon. Search submit/close explicitly hides the IME and clears focus. Documentation build completed locally with VitePress; existing Sass/chunk-size warnings were non-fatal.

The first Media emulator pass (40376277544, head 180155f) completed 20 tests without failures/errors/skips and exported 16 compact-search screenshots. Visual review confirmed the missing duplicate overflow and readable light/dark layouts; it also exposed two visually identical X actions inside expanded search. Close is now a leading back arrow with a full 48dp allocation, leaving the normal query-clear X at the end. MediaChromeTest checks that close precedes the input. Final-head verification is required before merge.


Follow-up to v1.42.14: compact filled media filters/source icons, Radio directory search action and transparent RGB fix, contact names for ended calls/recordings, direct Stremio cache setting, and Root task-listener bridge. Validation at the initial implementation checkpoint was pending; final commit checks, retrieved screenshots and review results are recorded in [PR #68](https://github.com/koukomin/Telos-Launcher/pull/68). Earlier screenshots do not certify the follow-up. The helper instrumentation uses Android shell privileges to validate registration and complete snapshot protocol, not a physical OnePlus root grant or OEM freeze outcome.

The follow-up also refreshes a reused singleTop missed-call popup on new intents and keeps the Recents parcel reader compatible below API 33. New/changed labels reuse existing translations; Latin and Ancient Greek receive the previously missing Radio/cache labels and Root observation text.

Follow-up local checks: `TELOS_TRANSLATION_BASE=d081761 python -B tools/translations/validate.py` passed 49 base locales; both virtual-app metadata/route validators passed; `npm run docs:build` passed (existing nonfatal Sass/chunk warnings). The first emulator pass had one helper test-launch failure (UiAutomation executes an argument vector, not shell expansion); the fixture now sets CLASSPATH via env. The remaining tests and 16 Media component captures passed and were reviewed; final-head retesting is required. Contact trash now invokes contact deletion beside overflow; clear history remains in that menu.


The implementation scope expanded before merge: repository-wide Shizuku private-factory removal, a shared supported UserService/process adapter, live System/Tweaks/graphics privilege refresh, Enter launch routing, and an external full-suite AppOpsNext button. New Android tests exercise production UserService pipes (stdin EOF, stdout, stderr, nonzero exit, timeout/destruction) without modifying permissions. They do not certify real Shizuku authorization or OEM audio enforcement. The prior helper callback/snapshot emulator workflow 40530150502 passed; final expanded-head compilation and tests remain required.

## Embedded AppOps and document scanner (2026-10-28)

Native run `40652059321` at `2d80bdc22ba4bbbd2de32589984c0f0d0674fef8` passed compilation/unit checks and 32 Android tests with no failures/errors/skips. Artifact `12800199089` (SHA-256 `570e7356e42ea2bb1828c90ea24142965f9d9f4d26105b7eb9c6feafb6e975c9`) contains the AppOps and scanner light/dark compact/wide screenshots. Inspection found poor mid-word wrapping in narrow AppOps navigation/tabs and showed that the scanner Dialog fixture did not inherit the test's font density. Passing assertions alone therefore did not establish large-font completion.

AppOps navigation now uses two rows when narrow and tall, or accessible compact icons when narrow and short, while wide windows retain labels in one row. Browse tabs scroll instead of breaking words. Scanner retains the caller's actual font density across its separate Dialog window; a text-layout assertion verifies fontScale 2. The AppOps fixture waits for the loaded search surface before capturing. These corrections require a fresh instrumented run and screenshot inspection. No physical camera, root/Shizuku grant or live account behavior is inferred from these tests.

### Shizuku pairing pill — 2026-11-05

Physical OnePlus 15 / ColorOS Android 17, portrait, dark, font scale 1.15: collapsed pill,
manual input, hardware Enter and user-operated software Done were exercised with the isolated
app-management test APK. Automatic and manual flows reached a live shell-UID Shizuku binder;
IME was hidden after submission. The production launcher was retained. See
[transport audit and evidence](./shizuku-pairing.md). This does not certify landscape, light mode
or the full font/display-size matrix. Device screenshots are not published because they contain
private Settings/device data.

### Shizuku notification reply — 1.44.15

Supersedes the experimental floating pairing pill. The 1.44.14 production overlay was confirmed
via ADB to hold focus over another app and overlap navigation. It was disabled on the reported
OnePlus while preparing this fix. Telos pairing now creates no WindowManager views; manual input
is Android SystemUI notification RemoteInput. Validation for the notification flow is recorded
in the pairing audit; earlier overlay test results must not be read as certification of this flow.

On 2026-11-06 the notification-only harness completed pairing and a fresh UID 2000 server
bootstrap on the OnePlus Android 17 device. SystemUI owned the reply keyboard; Send hid it,
Back returned to Settings and no pairing overlay existed. The test APK/server were removed.
The signed production APK has not been installed because the user disconnected USB before release.

### Startup scripts and paired-key reconnection

New System → Shizuku controls use shared preference rows, FlowRow actions, and a scrollable
script editor with saveable drafts. New/edited scripts are disabled until explicitly enabled;
manual Run and automatic execution share a serial worker. Local tests/build results are recorded
in the PR. No physical device is connected for this feature: boot, root prompts, notification
permissions, light/dark, compact/expanded, short landscape, font/display sizes and IME interaction
have not been visually certified. This does not replace the notification pairing evidence above.
The UI instrumentation failure was traced using the crash buffer to an unconditional Shizuku
watchdog boot/install entry point accessing Koin in the standalone library test Application.
The watchdog now stops when launcher DI is unavailable; boot preference failures are contained.
The device-test helper preserves crash-buffer evidence and rejects failed JUnit reports even
when AGP returns exit code zero. Final rerun results are recorded on PR #81.
