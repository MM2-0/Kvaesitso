# LENS keyboard and ARC accessibility follow-up — 2026-10-14

Continues task 020 on `codex/intelia-wallpaper-design`, based on `586bd5585`.
Scope: existing native Home/LENS/ARC and localized accessibility descriptions.
No new dependency, permission, service, global setting, or product surface.

## Result and changed paths

`InteliaLens.kt`: LENS is focusable, declares Button semantics, and has localized
search/long-click/state descriptions. Enter/numpad Enter/Space activate search
on key release. Up opens ARC and moves keyboard focus into its icons. Escape is
handled by the containing focus scope so it also cancels when an icon is focused.
A subtle outline appears only for keyboard focus. ARC targets are explicitly
48 dp while artwork stays 44 dp; selected labels are bounded and ellipsized.
Outgoing ARC controls are disabled/hidden from accessibility during their fade.
Transition alpha is read in the graphics layer instead of a composition branch
on every frame. The gesture reducer and user-owned ordering remain unchanged.

`InteliaHome.kt` and English/Czech `intelia.xml`: search description, app-row
Button role and localized long-click action. Ordinary Home appearance remains
wallpaper, optional clock/date and LENS, with no added explanatory text.

## Build, archive and file checks

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:app:assembleDefaultRelease '-Pintelia.localPreview=true' :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest --offline --console=plain --max-workers=2 --no-problems-report
```

PASS, 2m16s, 2332 tasks: 26 executed, 2306 up-to-date. Native UI tests executed;
unchanged other test tasks reused successful results. JUnit XML: 104 passed,
one existing locations skip, zero failures/errors. These are existing contract
tests; keyboard/Compose semantics were verified separately on the device.

`apksigner verify --verbose <APK>`: PASS v2. `aapt2 dump badging <APK>`:
`dev.intelia.launcher.debug`, version 0.1.0/code 2026101301, non-debuggable;
only QUERY_ALL_PACKAGES and own private receiver permission. APK and embedded
source CRC checks PASS; ten required files, including all four edited source/
resource files, match the embedded ZIP byte-for-byte. Required license/wrapper/
attribution entries present; no private build/signing/log/backup entries.
Known-secret-pattern and unexpected-artifact scan of 2149 source files PASS;
release APK's 1033 entries PASS. This is a scoped audit, not proof of universal
safety or a new dependency vulnerability scan.

`npm.cmd run docs:build` from `docs`: PASS, 5.94 s, including page rendering and
normal link checks. `git diff --check`: PASS. Documentation records verified
results; no architecture decision changed, so DECISIONS was not amended.

APK: `app/app/build/outputs/apk/default/release/app-default-release.apk`.
SHA-256 `f436fa23afc5387ea898db51ac2a03f2e78dc29f39a07bc86753a5d9a9bdcde5`.
Embedded source SHA-256
`46aded1deaa3594a09ffc725ad49cde261fa04f9ff2398e39a0b90e872e53819`.

## Physical device checks

A059 / Android 16/API 36. `adb install -r <APK>` PASS, existing package updated.
User unlocked the phone and authorized exclusive Intelia testing. Existing
phone-only backup was neither read/copied nor overwritten. Other apps were not
closed or changed. HOME role query still returns Intelia's existing package.

Reproduction uses `adb shell am start -n dev.intelia.launcher.debug/dev.intelia.launcher.ui.InteliaHomeActivity`
to return to own Home; fresh own-package uiautomator trees verify each step.
Allow 400 ms after keyboard focus changes before inspecting the tree. An early
assertion without this settling interval failed; the settled sequence passed.

- TAB focuses LENS; Enter (key 66) opens exactly one search EditText: PASS.
- Return Home, TAB then Space (62) opens search: PASS.
- Return Home, TAB then Up (19): five original ARC applications, an icon focused:
  PASS. All five target bounds at least 126 px at device density 2.625 (48 dp).
- Escape (111) from the focused ARC icon removes ARC: PASS. This failed on the
  intermediate candidate and was fixed before final build/install.
- LENS touch opens search: PASS. Stored alias prefix has zero Run controls;
  exact alias has one: PASS. No FLOW dispatch or target app content was needed.
- Ten held-LENS releases without upward selection remain on Home with ARC
  removed and no application launch: PASS.
- Ten rapid LENS/system-HOME returns: completed; final Home role retained.

## Measured limits

Reset `adb shell dumpsys gfxinfo dev.intelia.launcher.debug reset` before each
workload and read `dumpsys gfxinfo` afterward; no UI dumps inside measured loops.
ARC workload: DOWN on observed LENS center, wait 750 ms, UP at same position,
wait 420 ms; ten cycles. LENS workload: tap, wait 350 ms, system HOME (3), wait
350 ms; ten cycles. Coordinates are read from this device, not portable constants.

| Workload / candidate | Frames | Janky | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| ARC before edits | 334 | 5.39% | 20 ms | 31 ms |
| ARC final candidate | 359 | 5.29% | 21 ms | 32 ms |
| Rapid LENS previous preview | 771 | 5.71% | 20 ms | 40 ms |
| Rapid LENS final candidate | 758 | 6.73% | 24 ms | 48 ms |

These individual runs do not demonstrate an additional speed improvement.
ARC is similar; LENS has worse observed tails in this run, without controlled
repetitions/trace sufficient to establish cause. Do not claim fixed FPS, energy
acceptance or elimination of jank. Current device blur remains disabled; this
is wallpaper with contrast fallback, not verified enabled GPU blur.

Full TalkBack voice/switch interaction, large font settings, physical multitouch,
older devices, controlled startup/energy and repeated frame traces remain UNRUN.
No global accessibility setting was changed. Next smallest task is a controlled
trace/repeat of remaining LENS/IME frame tails, then accessibility acceptance.
