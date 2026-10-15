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
