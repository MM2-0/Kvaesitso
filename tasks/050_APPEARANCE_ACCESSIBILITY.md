# 050 — Focused appearance and accessibility follow-up (planned)

Not started/implemented. Do not silently advance from task 040.

Use existing native InteliaUiState/IconEngine/Appearance page boundaries. Add one
executable slice at a time, no extra permissions/dependencies or copied artwork.
Candidate next slice: font scaling/TalkBack, ARC large-label geometry and Pulse
focus/accessibility. Then configurable icon size/labels and LENS/ARC dimensions,
validated to retain 48 dp targets and one-handed reach. Later: arbitrary accent
editor, imported legally licensed fonts, weight/size overrides, haptic strength,
contrast/blur controls with actual public platform support, and safe resource-only
per-app icon picker that never modifies the launch component.

Each actual setting must hot-apply, persist/backup, fail safely on malformed
restore, respect hidden/profile eligibility, and invalidate a bounded icon cache.
Measure on target hardware before claiming improved performance/battery behavior.
Test reboot/revocation, real-player audio and long-session thermals separately.
