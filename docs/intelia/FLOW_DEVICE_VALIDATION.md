# FLOW v0 device validation

Use this runbook for the remaining acceptance checks in
[task 010](https://github.com/ondrejmacko-it/Intelia-Launcher/blob/feat/flow-aliases/tasks/010_FLOW_MVP.md). A passing JVM suite or signed APK does
not establish a pass here. Record each result as PASS, FAIL or UNRUN with the
device, Android version, source commit and APK SHA-256.

## Install and prepare

The next validation session will run in a local Codex chat on the computer
connected to the user's phone over USB. The cloud chat cannot access that USB
connection. Enable USB debugging and accept the computer's authorization prompt
on the phone, then verify it appears as `device` in `adb devices -l`.

To resume from GitHub in a fresh local checkout:

```sh
git clone --branch feat/flow-aliases https://github.com/ondrejmacko-it/Intelia-Launcher.git
cd Intelia-Launcher
```

For an existing checkout, preserve local changes first, then fetch and switch to
`feat/flow-aliases`. Read `AGENTS.md`, `docs/intelia/STATUS.md` and task 010 before
testing. Use the portable JDK/SDK prerequisites and build command in
[WORKFLOW](WORKFLOW.md) to produce the debug APK, or use the verified APK from
the cloud chat. Copy that APK to `app-default-debug.apk` in the local working
directory before using the install command below. APKs are build outputs, not
tracked source files.

The user has authorized local live testing and APK installation once ADB is
available. Obtain explicit consent before clearing the user's app data or
restoring a backup over their everyday configuration. Run those cases on an
isolated test installation otherwise. If multiple devices are connected, select
the intended device with `adb -s SERIAL` for every command.

Use a test device/profile with Android 8 (API 26) or newer and a visible
personal-profile app that can safely be launched offline. The debug application
ID is `de.mm20.launcher2.debug`. Installation updates that debug app if present;
use an isolated test installation for restore checks.

From a machine with Android platform-tools and a connected, authorized device:

```sh
adb devices -l
adb install -r app-default-debug.apk
```

Select the debug launcher through Android's default Home app settings. Open
launcher Settings → Search → Search actions. The new entry is **Create FLOW
alias** (currently an English string). Keep an existing ordinary search action
enabled and record its order/configuration for the preservation checks.

## Functional checks

| ID | Steps | Expected result |
| --- | --- | --- |
| F01 | Create `vlak`, select the chosen app, save; type ` VLAK `. | One FLOW chip previews `vlak → target`. Typing alone never opens the app. Ordinary results remain available. |
| F02 | Type `vla`, `vlak dnes`, an unrelated query and an empty query. | No FLOW chip for this alias; ordinary search/actions behave as before. |
| F03 | Activate the exact-match chip, then return Home; repeat with rapid taps. | An ordinary launch of the selected activity; no automatic execution when returning or rebuilding search. No duplicate in-flight request. |
| F04 | Disable Wi-Fi/mobile data; repeat F01 and F03. | Matching and launch dispatch still work. The target's own network-dependent content is outside this check. |
| F05 | Try creating ` VLAK ` again, a blank name and a name of 65 ASCII characters; then edit the existing `vlak` without changing its name. | Duplicate, blank and excessive length produce validation feedback. Editing the same alias succeeds. |
| F06 | Change the alias to `cesta` and choose a different visible app. | Only `cesta` matches; preview and explicit launch use the newly selected target. The ordinary action's configuration/order is preserved. |
| F07 | Force-stop the debug launcher using the command below; return Home and inspect settings/search. | `cesta` and its exact target survive process restart; merely restoring search never launches an app. |
| F08 | Hide the target in launcher settings while the alias exists; inspect settings and search. Unhide it and repeat. | Hidden target has no executable FLOW chip or disclosed label in alias settings; the saved alias remains repairable/deletable. Unhiding restores availability. |
| F09 | For a disposable target app, uninstall it; inspect the alias, then repair it by choosing another app. | Alias survives, shows unavailable without the old target label, and cannot launch or silently switch apps. Explicit repair works. |
| F10 | If a work/private profile or suspended test app is available, inspect the picker and exercise target unavailability. | Only visible, unsuspended personal-profile apps are selectable/executable. Otherwise mark the specific case UNRUN. |
| F11 | Delete the alias, then repeat F07. | Alias stays deleted; ordinary search/actions remain intact. |

For F07, use this on the connected test device, then press Home:

```sh
adb shell am force-stop de.mm20.launcher2.debug
```

## Backup and restore

On the isolated test installation, create two different aliases plus an ordinary
custom search action. Record their names, targets, order and configuration.
Use Settings → Backup to export a backup file. Edit/delete one alias and alter
the custom action, then restore that exported file through the same screen.
Verify the recorded aliases, targets and ordinary action are restored. Force-stop
and reopen the launcher; repeat exact matching and explicit launch. Hide one
restored target and verify F08 still applies. Keep the backup file local; it may
contain unrelated launcher data.

## Accessibility and lifecycle

- With TalkBack enabled, navigate to the alias editor and matching chip. Confirm
  the alias, target, selection, validation feedback and unavailable state can be
  understood; activate explicitly with TalkBack.
- With an external keyboard, reach the chip and editor controls, activate them
  and verify ordinary search's Enter behavior does not auto-launch the alias.
- At the device's largest font setting, verify target choices, Save, Cancel and
  Delete remain reachable with the keyboard open, in portrait and landscape.
- Rotate during unsaved editing; record whether text/selection and the sheet
  remain usable. Leave the launcher during activation and check for delayed or
  duplicate dispatch on return. Record any failures with reproduction steps.

Latency, frame and memory measurements require a named reference device and
workload from [PERFORMANCE](PERFORMANCE.md); visual impressions are not a
performance pass.

## Result record

```text
Date / tester:
Source commit / APK SHA-256:
Device / Android API / profile:
Target activities:
F01–F11 (each PASS / FAIL / UNRUN with evidence):
Backup / restore / subsequent restart:
TalkBack / keyboard / large fonts / rotation / lifecycle:
Performance workload and measurements (or UNRUN):
Failures and reproduction steps:
```

No device checks have been executed by adding this runbook.
