# 060 — Pulse 2.0

User approved implementation on 2026-10-15 after documentation/model switch.
Active boundary: **A — capability experiment**, not the entire redesign.

Reference: docs/intelia/PULSE_V2.md. Baseline source 09bf44885; documentation
checkpoint 1e7311aa1. Preserve all existing user settings and launcher behavior.

Allowed: pure anchor geometry/tests, debug-only calibration/touch probe and
optional debug accessibility overlay host with explicit user enablement,
capability evidence and relevant docs. No new production permission/service,
FLOW migration or replacement of the production Pulse in this stage. User later
explicitly requested manual camera placement: X/Y/dimensions, precise nudges and
local profiles. Minimal existing-service adapter temporarily suspends its own
capsule while calibration is visible; no persisted preference change.

Acceptance: record actual cutout/insets/window/touch delivery; distinguish ADB
injection from physical gestures; verify teardown and status-bar conflict;
compare application/accessibility hosts only when explicitly enabled. Missing
physical/security/rotation evidence remains UNRUN. Stop before stage B.

Implemented: debug probe/manual calibrator and 9 geometry unit tests. Final build
and all 31 UI tests PASS; source/CRC audit PASS; device nudge/persistence PASS.
Original injected rotation matrix PASS; physical automatic alignment FAIL per
user, replaced with manual calibration. Await user alignment and physical test.
See docs/intelia/PULSE_V2.md for complete evidence and candidate-specific limits.

Reference follow-up: user authorized MT UI/web/practical review, now recorded in
PULSE_V2.md and decision D014. Camera calibration geometry must be independent
of activation size and visible event content. No reference code/artwork copied.
Next bounded stage B starts with that contract; do not claim production capsule,
full touch pass-through or completed hardware matrix from the reference trial.
# Stage B activation — 2026-10-15

User approved corrected reference-driven implementation after 588df32a5.
Active slice: production calibration/config/backup, optional accessibility host,
HIDDEN/COMPACT/explicit detail and existing guarded ARC/FLOW adapter. No copied
reference code/assets. Original source recoverable through 588df32a5. Preserve
HOME, old aliases/profiles/settings; no push/merge. Stop before generic Action
Registry, priority engine and additional event modules. Physical cutout acceptance,
new Android accessibility opt-in and temporary reference-overlay conflict handling
remain explicit device gates. Do not silently enable a permission or disable MT.

Stage B bounded slice implemented and installed without data wipe. Production
calibration user-confirmed; new service explicitly enabled and reference overlay
temporarily disabled by user. 35 UI + 20 FLOW tests PASS, source/license/CRC checks
PASS; injected hidden/search/hold/preview/media/pause/shade/Home checks PASS.
See PULSE_V2.md for exact commands/hashes and UNRUN physical/security/rotation
matrix. Stop here before generic action registry or additional event modules.

Acceptance follow-up completed on available hardware: physical gestures confirmed
by user; actual ARC launch, Settings/dialer restriction, fullscreen outside touch,
all display rotations, screen-off and automatic unlock restoration verified.
Fixed missing 180-degree display observation, unconfirmed-profile media attachment
and latched post-unlock restriction. 59 tests PASS; optimized non-debuggable preview
installed with data/calibration/HOME retained. Fixture removed; no push/merge.
Exact command/hashes and remaining external-hardware/accessibility/benchmark limits
are recorded in docs/intelia/PULSE_V2.md. This closes this bounded stage B task.
