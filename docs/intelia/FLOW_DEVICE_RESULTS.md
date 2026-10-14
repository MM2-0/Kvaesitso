# FLOW device validation — 2026-10-13

Task 010, local Codex session. Base `c646d97aa`, branch
`codex/flow-device-validation`, plus the focused `FlowAliasSheet` layout fix.
Physical USB device: A059, Android 16 / API 36, personal profile. Device serial,
raw UI dumps, installed-app inventories and machine configuration are excluded
from Git. The debug launcher was absent before this session.

## Build and installation evidence

Portable Temurin JDK 21.0.12.1+1 and Android SDK platform 37.1 / build-tools
36.0.0 were prepared locally; upstream source and dependency versions were kept.
From the checkout root with those environment paths:

```powershell
.\gradlew.bat :app:app:assembleDefaultDebug :data:search-actions:testDebugUnitTest :core:base:testDebugUnitTest :data:locations:testDebugUnitTest --console=plain --max-workers=4
```

PASS, 10m 28s, 1018 tasks: 738 executed, 280 from cache. Fresh local JUnit
reports: 16 FLOW, 52 core/base and 16 locations passed; 1 existing locations
test skipped, zero failures/errors.

After the layout fix, the same command with `--offline` passed in 25s:
5 tasks executed, 1013 up-to-date. Unit tests were up-to-date on this second
run; they are not counted as freshly executed again.

`npm ci --cache ../../.local-tools/npm --no-audit --no-fund` and
`npm run docs:build` from `docs` passed; VitePress completed in 6.95s with link
checking enabled. `git diff --check` passed. The inherited Vite sidebar-loader
warning was retained; npm's optional watcher script was not approved or run.

```powershell
apksigner.bat verify --verbose app-default-debug.apk
adb -s SERIAL install -r app-default-debug.apk
adb -s SERIAL shell am start -n de.mm20.launcher2.debug/de.mm20.launcher2.ui.settings.SettingsActivity --es de.mm20.launcher2.settings.ROUTE settings/search/searchactions
```

PASS: v2 signature, installation and actual settings UI on the physical device.
The installed corrected APK is
`app/app/build/outputs/apk/default/debug/app-default-debug.apk`, SHA-256
`b32534b3a0e018864b50b38f09db0dfcba709029d7862c813b6a3961395e6982`.
This is a local debug build, with a local debug signature, not the cloud APK.

## Observed defect and fix

The original editor opened partially expanded: the target list extended below
the visible panel and Save/Cancel were absent from the UI hierarchy. Expanding
the panel exposed them. `FlowAliasSheet` now skips partial expansion and makes
its outer content scrollable while retaining the bounded, virtualized app list.
On the corrected APK, Save/Cancel appeared immediately on first open. Editing
and validation remained usable; long content with the IME can use outer scrolling
or dismiss the IME. Largest-font and landscape coverage remain unrun.

## Functional evidence

Only the launcher and a locally authored disposable target (`org.intelia.flowtest`,
label `FLOW Test`) were operated. The target has no permissions or network code
and shows `FLOW Test: explicit launch received`. Its source/APK are local test
helpers outside the repository, not production dependencies.

| Check | Result and evidence |
| --- | --- |
| Create and preview | PASS: `vlak` saved through the UI with its chosen target; settings showed the stored name and target. |
| Exact matching | PASS: `VLAK` and ` vlak ` showed one `vlak → FLOW Test` chip after scrolling the existing horizontal action row. |
| Typing never launches | PASS: the target had no running PID after entering the exact alias, before explicit activation. |
| Prefix / extra words | PASS for tested inputs `vla` and `vla dnes`: no FLOW chip. Exact `vlak dnes`, unrelated and empty-query cases were not exhaustively rerun. |
| Explicit activation | PASS: tapping the observed chip opened the target's confirmation screen. Rapid taps/lifecycle cancellation remain unrun on device. |
| Blank / length validation | PASS: Save showed `Enter an alias` for empty input and `Use at most 64 characters` for 65 ASCII characters. Duplicate-name validation remains unrun on device. |
| Rename and process persistence | PASS: renamed to `cesta`, saved, force-stopped only the debug launcher, reopened settings and obtained a fresh UI dump showing `cesta` and `FLOW Test`. |
| Missing target | PASS for settings: uninstalling only the disposable target retained `cesta` and displayed the unavailable message without the old app label. Reinstalling that exact target restored the preview. Missing-target search/activation and explicit repair to a different available activity remain unrun. |
| Delete and restart | PASS: deletion through the editor followed by a debug-launcher process restart left neither test alias in a fresh settings UI dump. |
| Ordinary actions | PASS for configuration presence: Call, YouTube and Google Play remained in settings before/after the alias changes and restart. Ordinary web actions remained visible in search. No third-party action was launched. |

Process checks used:

```powershell
adb -s SERIAL shell pidof org.intelia.flowtest
adb -s SERIAL shell am force-stop de.mm20.launcher2.debug
adb -s SERIAL shell am start -n de.mm20.launcher2.debug/de.mm20.launcher2.ui.settings.SettingsActivity --es de.mm20.launcher2.settings.ROUTE settings/search/searchactions
adb -s SERIAL uninstall org.intelia.flowtest
```

UI evidence came from fresh `uiautomator dump` files filtered to the two test
packages. An immediate dump after activity restart sometimes returned a null
root; those dumps were discarded and successfully repeated after startup.

The debug launcher itself was initially offered as a target but became
unavailable after opening its main activity; a later process restart restored
its picker entry. Self-targeting is not used as functional acceptance evidence;
the disposable target provided an independent launch observation.

## Remaining checks and handoff

UNRUN: actual offline dispatch with networking disabled, hiding/suspension/profile
boundaries, duplicate validation, full backup/restore, TalkBack, external keyboard,
largest fonts, rotation/IME matrix, rapid activation/cancellation and reference
performance workload. System connectivity/default Home/accessibility/font settings
and unrelated apps were left unchanged. This session is partial device acceptance,
not a complete task-010 pass or a performance claim.

The installed debug launcher and test target remain available; temporary aliases
were deleted after their persistence tests. No phone data was cleared, no personal
backup was restored, and no license/permission/production dependency was changed.
Next smallest action: finish the outstanding device cases using
[the runbook](FLOW_DEVICE_VALIDATION.md), with the disposable target.
