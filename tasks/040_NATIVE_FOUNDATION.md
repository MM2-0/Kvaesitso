# 040 — Native foundation, wallpaper/Recents and design system V1

Priority user extension read 2026-10-14 from supplied attachment. Preserve all
task 030 changes/data; no reset/push/merge. One agent, existing local branch.
First recovery checkpoint, then independently diagnose HOME/Recents integration
and inset dark rectangle. Verify actual Android role/window/task state and compare
Nothing without treating platform Quickstep restrictions as fixes. Do not mask.

Only after critical fixes: consistent design/icons via existing infrastructure,
System/Device default, Intelia/Monochrome/custom compatible installed pack modes,
safe per-app overrides, local/backup persistence and bounded invalidatable cache.
Group real working appearance settings; document deferred architecture rather
than inert controls. No new dependencies or proprietary icon assets. All earlier
FLOW/ARC/Pulse behavior/data remain protected.

Required separate evidence: normal HOME, external app→HOME, Recents→HOME,
repeated Home, Search/FLOW close, lock/unlock, original Nothing comparison;
icon modes actually tested, installed Nothing pack compatibility, system-theme
behavior and performance/launch validity. Update DESIGN_SYSTEM.md and STATUS.
Starting base: 753cea330 plus recovered task 030 work. Delivery checkpoint below.

## Today's delivery checkpoint — 2026-10-14

Implementation and bounded automated/device verification delivered; user asked
to finish today and continue tomorrow. See docs/intelia/NATIVE_FOUNDATION_REPORT.md,
DESIGN_SYSTEM.md and HANDOFF_NATIVE_FOUNDATION.md for exact artifacts/evidence.
Root rectangle fixed on device; HOME/Quickstep routing investigated separately.
Four icon modes, both installed compatible Nothing-style packs, safe per-app mode,
live colors/fonts, preserved identity/data and LENS/ARC regression passed. Final
118 unit tests pass, one inherited skip. No full physical-gesture/animation,
accessibility or energy acceptance claimed. Task 050 remains planned, not started.
