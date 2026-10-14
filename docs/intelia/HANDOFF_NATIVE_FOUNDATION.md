# Task handoff — next session

- Task: 030 original interactions + priority 040 native foundation/design V1.
- Agent/branch: Codex, `codex/intelia-wallpaper-design`; delivery commit is Git HEAD.
  Base before this work: `753cea330`. No reset/force push/merge; current delivery
  is local, GitHub PR #3 remains draft at its previously approved remote head.
- Changed paths: native `app/ui/.../dev/intelia/launcher/ui`, native resources/tests,
  shared FLOW editor, `data/search-actions` codec/runtime/tests, app manifest for
  explicitly approved optional Pulse, and `docs/intelia`/tasks. No new dependency.
- Behavior: real full-window wallpaper without inner rectangle, original clock,
  upward LENS search, tolerant ARC/reach, open index/manual spaces, ordered app
  FLOW and global opt-in Pulse. Four shared icon modes, compatible packs, per-app
  modes, grouped live appearance settings and stricter public-shortcut matching.
- Verification: exact Gradle command, signature/source/file audits, per-device
  steps, frame counters and separate rectangle/Recents evidence are in
  [NATIVE_FOUNDATION_REPORT.md](NATIVE_FOUNDATION_REPORT.md). Build PASS, 118 tests
  PASS, one existing ignored opening-hours test. `git diff --check` and docs build
  PASS. Skipped/unrun acceptance is listed explicitly in that report.
- APK exists at `app/app/build/outputs/apk/default/release/app-default-release.apk`,
  SHA-256 `bc90ff9c3509d9c452b907c1048c75f8f5d320e3ea42afe884228771899798e4`.
  Installed with `install -r` on Nothing A059/API 36, 2026-10-14. Non-debuggable
  preview keeps `dev.intelia.launcher.debug` identity/signature/data and HOME role.
- Privacy/licenses: GPL notices and exact embedded source retained; no proprietary
  graphics or notification payload reader. Pulse uses public media sessions,
  requires user-granted overlay/listener access, hides locked/off, defaults off
  and stays off after backup restore. Phone-only private backup was not copied.
- Active appearance restored to System icons, original font/colors/centre reach/
  normal motion. Last tested compatible custom pack is remembered but inactive.
  Five ARC slots and saved FLOW remain. Own temporary media fixture absent.
- Limits: precise physical gesture/animation acceptance remains open; user said
  “ok, asi dobrý” and asked to finish/continue tomorrow. No further phone control
  after that instruction. No guaranteed cold ISIC screen readiness, configurable
  FLOW gesture/pinned export, full accessibility, reboot or battery/thermal proof.
- Next smallest action: review actual design/gesture feel with user, reproduce any
  concrete Home/Recents symptom independently of wallpaper, then focused task 050
  accessibility/geometry acceptance. Do not start more features automatically.
- STATUS and DECISIONS updated for verified milestones and D012 architecture.
