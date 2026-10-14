# 020 — Recovery, minimal Home, LENS and ARC

User-approved scope from attached audit/redesign brief, 2026-10-14. Branch
`codex/intelia-wallpaper-design`; base `c646d97aa`. Recovery checkpoint of all
tracked/nonignored new source stored outside repo in `.local-tools` before
continuing edits. Preserve native Intelia/FLOW/security work and original PRs.

Allowed: focused native Home/settings/preferences/gesture layer, identity theme
and portrait manifest, contract tests, source bundling and evidence. Acceptance:
system wallpaper and runtime blur fallback; no marketing/header/tab bar on Home;
single LENS; offline app/FLOW search; stable user-owned ARC positions and guarded
hold/drag/release/cancel behavior; existing data/backup retained. No HALO, TRACE,
AI, global overlays or widgets in this task. Build/test, device checks only if
actually connected; commit separated changes and prepare PR without merge.

Audit: previous native slice was built/device-verified; current redesign is
unbuilt and incomplete. PR #1/#2 still OPEN, heads unchanged; no merge/reset.
Inherited widget/shortcut infrastructure remains, not deleted as unseen code.
Native surface had not exposed widgets or shortcut results in the prior slice.

## Verified delivery and open acceptance

See [report](../docs/intelia/LENS_ARC_REPORT.md). Build and 104 tests passed, one
existing skip. Device LENS/ARC/FLOW/Home/persistence checks passed; current OS blur
is disabled and fallback was verified. Performance remains FAIL/open: rapid
keyboard/Home cycles produce substantial debug jank. Only a draft PR/design is
appropriate. Enabled-blur, physical multitouch/rotation, TalkBack/font scaling,
release startup and battery checks remain unrun; no final product approval.

### Requested performance continuation — 2026-10-14

User asked to continue and explicitly approved a phone-only backup and release
preview update. Build option keeps existing installation/data; matched release
measurements improve materially over debug. Source changes remain focused on
transition/window effects and the opt-in build identity. See
[follow-up evidence](../docs/intelia/LENS_PERFORMANCE_REPORT.md). Performance tails,
accessibility/physical multitouch/energy and final design approval remain open.

### Keyboard and ARC accessibility continuation — 2026-10-14

LENS Enter/Space and Up-to-ARC focus, Escape from focused ARC icon, touch search,
exact FLOW availability and no-selection release passed on the actual phone.
Minimal Home appearance and all five saved ARC slots retained. Explicit touch
targets/localized semantics/layer-driven fade added without new permissions.
Build/104 tests (one existing skip)/signature/source/file audit passed. See
[current evidence](../docs/intelia/LENS_ACCESSIBILITY_REPORT.md), including exact
commands, changed paths, measured frame tails and unrun acceptance. No additional
performance improvement or complete TalkBack/font-scale acceptance is claimed.
