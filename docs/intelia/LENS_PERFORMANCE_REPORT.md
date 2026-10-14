# LENS performance follow-up — 2026-10-14

Continues task 020 / draft PR #3 on `codex/intelia-wallpaper-design`, base
`ff26a4a8f`, at the user's explicit request. A059 / Android 16 / API 36. No
other app was closed, no system blur/rotation/refresh/power setting was changed.
Peak refresh setting read 120.1; current thermal status read 0 (no OS-reported
throttling). These observations do not replace controlled energy/thermal tests.

## Findings and focused changes

Longer dwell materially changed debug measurements, so the earlier rapid result
alone was insufficient to locate the problem. Same debug build: 10 LENS/system
HOME cycles with 800 ms pauses produced 15.79% jank; moving only the keyboard in
already-open search produced 9.29%; returning through own Home produced 9.49%.
The last 120 framestats rows had traversal p95 8.22 ms versus draw 1.71 ms and
render 1.75 ms. This points to UI/layout/scheduling, not repeated bitmap uploads;
it is not a complete CPU trace or attribution to one composable.

Baseline non-debuggable release was then built and measured in a separate,
previously absent `.release` installation. Its own-Home workload measured
4.57%/3.94% jank. Default Home stayed the user's existing Intelia throughout.

Changes: replace AnimatedContent/lookahead with a simple crossfade; update bar
appearance only when context/theme changes; avoid writing unchanged KEEP_SCREEN_ON
flags. Add explicit developer Gradle opt-in `intelia.localPreview=true` to build
release under the existing `.debug` ID, retaining data and Home role on install -r
with the same signing key. Normal release remains `.release`; normal debug is
unchanged. No permission, service, production dependency or feature was added.
Inherited R8 dontoptimize/dontobfuscate rules remain; this removes debug/tooling
overhead but does not enable every possible optimizer transformation.

## Build / test / source evidence

```powershell
./Intelia-Launcher/gradlew.bat -p Intelia-Launcher :app:app:assembleDefaultRelease '-Pintelia.localPreview=true' :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest --offline --console=plain --max-workers=2 --no-problems-report
```

PASS, 4m42s, 2332 tasks: 85 executed, two from cache, 2245 up-to-date.
104 JVM tests passed, one existing locations skip, zero failures/errors. Native
UI test task executed; other unchanged tests reused successful results.
Initial offline standard release failed for uncached existing artifacts; network
build fetched them without changing versions and passed in 8m (1829 tasks,
1349 executed, 479 from cache, one up-to-date). An unquoted PowerShell -P argument
split incorrectly and failed; the corrected quoted command above passed.

Signature: PASS v2. Metadata/badging: actual package `dev.intelia.launcher.debug`,
version 0.1.0 / code 2026101301, not debuggable. Permissions: package discovery
and own private receiver only. APK/source ZIP CRC and required licenses passed;
changed build/UI/activity/WORKFLOW files match embedded source byte-for-byte;
no private signing/machine/log/backup entries. `git diff --check` passed.
Source file/known-secret-pattern audit passed for 2149 tracked/nonignored files.
`npm.cmd run docs:build` passed in 13.40 s, including link checking.

Preview APK: `app/app/build/outputs/apk/default/release/app-default-release.apk`.
SHA-256 `7018eb89e69c07384e6a16f9d390614aa58b8f20614b70211dc4bc92c953c826`.
Embedded source SHA-256
`1e400db52f35d45a8bf51f22127827fb017290e6f49dff33b23442173816efad`.

## Phone preservation and verification

The user explicitly approved a new phone-only backup and updating the existing
installation. Backup created through Intelia UI as
`Download/intelia-before-preview-20261014.intelia`, 3741 bytes. Content was never
pulled to PC or committed. An earlier export-and-pull proposal was rejected by
automatic review and never executed; the approved phone-only alternative passed.
Existing personal backups were not overwritten. Signed rollback APK is retained
outside Git at `.local-tools/intelia-debug-before-performance.apk` (SHA-256
`3162da94af13ddff88590c1b746562742f5a7a9e63bb12fe87805ee0d831dfaf`).

Actual preview install -r PASS. Native Home and stored exact FLOW remained;
prefix produced zero Run actions, exact alias one. Explicit Run opened the
user's chosen app; only focused component metadata was read. Held ARC still
showed all five original slots; an initial counter wrongly required clickable
on description child nodes and was corrected by inspecting the actual tree.
Release label/contents and Home role retain the existing identity. Nothing and
other user apps/data remain. Only our fresh temporary `.release` package was
removed after measurement. No debug keep-awake extra is honored in preview.

## Matched observations

Ten LENS tap / own Home cycles, 800 ms pauses after each action; two warm-ups for
release/preview; reset gfxinfo before each round, then gfxinfo and meminfo.
Controls were read from actual own UI bounds. No uiautomator inside timing loops.

| Variant / round | Frames | Janky | p95 | p99 | PSS KiB |
| --- | ---: | ---: | ---: | ---: | ---: |
| Existing debug | 780 | 9.49% | 38 ms | 73 ms | — |
| Baseline release 1 | 832 | 4.57% | 22 ms | 53 ms | — |
| Baseline release 2 | 837 | 3.94% | 18 ms | 48 ms | 123434 |
| Updated preview 1 | 840 | 4.29% | 21 ms | 53 ms | — |
| Updated preview 2 | 839 | 3.93% | 17 ms | 46 ms | 110662 |

The main demonstrated gain is removing debug overhead. The transition/window
changes did not show a convincing independent gain against baseline release.
Installed preview rapid system-HOME test (original 350 ms pauses): 771 frames,
44 janky / 5.71%, p95 20 ms, p99 40 ms; older debug rapid result was 51.08%.
Same script/device, but different warm-up/build/run history, so no causal or
statistical 10x optimization claim. Standard fresh-release cold am start -W was
835 ms; no repeated preview cold-start distribution is claimed. Preview RSS
249084 KiB. Public GPU blur remains disabled; fallback continues to work.

## Remaining acceptance

Improved practical preview, still a draft. Frame tails/jank remain; no fixed FPS,
battery/energy or universal smoothness promise. Full accessibility, physical
multitouch/interrupt/rotation, enabled-blur/older devices, controlled cold startup
and statistically repeated measurements remain open. User data were verified
through UI; no private raw-data equality or backup-restore trial is claimed.
Next smallest step is a controlled frame trace of remaining LENS/IME tails and
accessibility acceptance, without unrelated product changes.
