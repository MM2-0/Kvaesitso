# Intelia native interface — verified handoff, 2026-10-13

Task 015: original minimal Intelia interface over the preserved audited Android
foundation. Agent: Codex; branch `codex/intelia-native-ui`; base `c646d97aa`.
Work is local and uncommitted; no push or merge. Source history/notices retained.

## Change and behavior

- `app/ui/.../dev/intelia/launcher/ui/`: original Compose Home, app library,
  FLOW authoring/preview, settings, hidden-app controls, theme/motion/clock
  preferences, own transitions and Android document/role adapters. Offline app
  lookup and explicit FLOW activation reuse existing repositories and guards.
- Application manifest/build/resources: Intelia identity/icon/runtime package,
  opaque own theme, native Home activity, `.intelia` backup naming and exact
  corresponding source ZIP embedded in the APK. Ordinary UI has no competing
  product branding; Legal preserves original copyright and library notices.
- FLOW editor/runtime: full-height scrollable editor, searchable targets,
  Czech strings and own-package exclusion. Hidden targets fail safely without
  disclosing their label; typing never executes a flow.
- Backup core/repositories/UI: reject traversal, duplicate entries and bounded
  expanded archives; validate records before replacing them; handle cancellation
  and failures explicitly. New UI preference codec participates in backups.
- Dependency catalog: justified security fixes to Jackson 2.21.7 and jsoup
  1.23.2. No new production dependency for the native surface.

Source and documentation changes include `readme.md`, ATTRIBUTIONS, WORKFLOW,
STATUS, DECISIONS and tasks 010/015. Internal inherited namespaces/source remain
for reuse and compatibility; this is a modified GPL work, not a clean-room app.

## Verification commands and results

Commands ran from the outer local workspace with configured JDK 21/Android SDK
and isolated Gradle/Android user homes. Private helper scripts and device outputs
stay outside the repository/APK; device serial and personal app data are omitted.

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:app:assembleDefaultDebug :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest --offline --console=plain --max-workers=4
```

PASS, final build 9 s, 1052 actionable tasks: 4 executed, 1048 up-to-date.
Test XML confirms 95 passed / 1 existing locations skip / 0 failures or errors:
FLOW 16, core/base 60 (including eight archive security tests), locations 16+1
skip, native UI backup codec three. Final test tasks reused earlier successful
results; the final APK packaging executed. No instrumentation suite is claimed.

```powershell
git -C Intelia-Launcher diff --check
python .local-tools/audit-files.py
python .local-tools/audit-native-apk.py
```

PASS. File scan covers Git tracked and nonignored new files, known secret patterns
and unexpected signing/binary/log artifacts. None found. APK/source archive CRC
checks passed; no private helper/signing/machine files. Seven key bundled source
files were compared byte-for-byte with the worktree. Snapshot contains 2761 ZIP
entries including directories, build scripts/wrapper, source/resources/licenses.
Evidence reports are excluded to prevent circular APK/hash updates.

`npm.cmd run docs:build` from `Intelia-Launcher/docs`: PASS in 8.22 s,
including link checking. Existing Vite warnings concern a future configuration
loader default, not a failed build.

APK signature verification with `apksigner verify --verbose`: PASS v2.
`aapt dump permissions`: only QUERY_ALL_PACKAGES and the generated private
DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. No INTERNET, sensitive data permissions,
notification collection or Accessibility service in the active manifest.
OSV querybatch audit of 321 runtime dependency components: zero reported findings
after fixes (20 original advisory matches). Pattern/database audits cannot prove
all code safe or all functionality correct.

APK: `app/app/build/outputs/apk/default/debug/app-default-debug.apk`.
SHA-256: `2c4a6747dd35c532ea51d06f461e4645781dbab274f8f4f9ab6a5ef79417ad45`.
Embedded source SHA-256:
`f3f939379296dcffcd979a39dff8748ab7b34113bab26ef30e21a38a3a06780f`.
Version 0.1.0; versionCode 2026101301; package `dev.intelia.launcher.debug`.

## Actual phone acceptance

A059 / Android 16 / API 36, USB-authorized, tested 2026-10-13. PASS:

- Installed the actual APK and verified native Home; HOME role holder is
  `dev.intelia.launcher.debug`. Nothing launcher remains installed with data
  untouched. Other apps were not closed. Only our superseded debug launcher and
  disposable FLOW test target were removed.
- Created `vlak -> Můj vlak`; exact ` VLAK ` preview succeeds, `vla` and
  `vlak dnes` do not match FLOW. Duplicate normalized alias rejected.
  Explicit Run launched the public target activity; typing did not launch it.
  No third-party app screens were captured or automated.
- Hide target: executable preview and its target label disappear; stored alias
  remains with safe unavailable state. Unhide restores eligibility/label.
- Exported corresponding source through native Legal/system document picker.
  Pulled exported ZIP has exactly the embedded source SHA-256 above.
- Exported `intelia-2026-10-13.intelia` (3589 bytes), changed own theme, imported
  that same fresh-install backup with explicit overwrite confirmation. Restored
  dark theme (`theme=1`) and FLOW; both survived process restart. Original
  launcher/user backup data was not overwritten.
- Ended on native dark Home, target unhidden, alias retained. Own window
  KEEP_SCREEN_ON flag is false; temporary debug validation flag was cleared.
  Renamed only our previous successful test backups to `.intelia` and removed
  only our deliberately invalid temporary test archive.

Performance command/workload:

```text
adb shell dumpsys gfxinfo dev.intelia.launcher.debug reset
10 cycles: observed Apps dock tap, 350 ms pause, observed Home dock tap, 350 ms pause
adb shell dumpsys gfxinfo dev.intelia.launcher.debug
adb shell dumpsys meminfo dev.intelia.launcher.debug
```

773 rendered frames, 19 deadline/janky frames (2.46%); legacy metric 11.90%.
Frame p50 8 ms, p90 11 ms, p95 13 ms, p99 129 ms. PSS 187298 KiB,
RSS 337804 KiB. Debug build on this workload only, includes icon loading; no
fixed-FPS, battery, release-performance or no-stutter acceptance is claimed.
Automatic default-Home restart made attempted `am start -W` timing invalid for
cold-start measurement; cold-start acceptance remains SKIPPED.

## Limits and next task boundary

Working bounded local launcher slice, not a completed feature roadmap. Full
TalkBack/font/rotation/lifecycle matrix, release startup/frame/battery profiling,
full work/private profiles, widgets, typed multi-step FLOW and global ORBIT are
unverified or not implemented. Minimal local UI intentionally offers apps+FLOW.
Optional inherited modules remain in source/build dependencies but are not
exposed as active product flows. Removing all inherited source/names would be
incompatible with preserving the foundation/required attribution.

GPL/library notices and exact source distribution are preserved; no universal
legal compliance or trademark clearance certification is asserted. Next smallest
action: profile release transitions and address observed tail latency, then
complete accessibility acceptance. STATUS/DECISIONS updated for verified native
identity/architecture. Do not proceed into the next task without instruction.
