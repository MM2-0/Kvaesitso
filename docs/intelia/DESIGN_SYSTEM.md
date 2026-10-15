# Intelia Design System V1

Task 040, 2026-10-14. This is an adaptation of the existing GPL launcher, not a
clean-room claim. Licenses remain in source and the Legal screen. No third-party
launcher artwork, private Nothing resources, new fonts or dependencies imported.

## Identity and layout

System typography is the default; optional system serif uses the device's
licensed font implementation. Text respects Android font scaling. The clock is
our own vector digit paths with staggered punctuation, not a bundled font/image.
Search, FLOW, library, back/settings and paired-stroke Pulse use original code
geometry. Neutral surfaces, restrained accents, open letter lanes and deliberate
spacing carry identity; there is no neon/glass visual layer or branded text on Home.

LENS remains the single lower Home control: tap/upward swipe opens local search;
hold opens the stable five-slot ARC. Release selects, returning to LENS cancels.
ARC tolerates fast sampled crossings/limited overshoot while far movement clears
selection. Centre/left/right reach shifts the control and arc together. Minimum
48 dp focus/touch targets and reduced motion remain. Existing subtle haptics are
retained. Configurable haptic intensity is not implemented.

Home background belongs to the full window; system insets apply only to content.
Public Android SHOW_WALLPAPER displays the actual system wallpaper without reading
or copying its image. Auto blur uses the public supported-window path; unsupported
or system-disabled blur falls back to contrast. This Nothing currently reports
blur disabled. We cannot import Nothing's private wallpaper/launcher preferences.

## Working architecture

The native `InteliaIconEngine` is a small adapter over existing `IconPackManager`
importers and `LauncherIcon` renderer, rather than a replacement icon-pack system.
It shares a 4 MiB/128 px bitmap cache across native Search, Registry, ARC, FLOW
editor/library, shortcut parent icons and Pulse. Package add/remove/update/change
invalidates cache and pack list. Theme/night/pack/per-app mode are part of the
cache key and Compose reload identity. Pulse uses the same renderer asynchronously.
Hidden/private/suspended/other-profile apps stay filtered by existing native
eligibility; appearance never changes the component used for launching.

Modes (System/Device is the default, including old backups):

| Mode | Rendering and fallback |
| --- | --- |
| System / Device | Real activity adaptive/original icon; application icon, then Android default activity icon if absent. No private settings import. |
| Intelia | Original app mark on a modest uniform neutral rounded plate. No new replacement app-logo pack. |
| Monochrome | Android 13+ adaptive monochrome when exposed; otherwise desaturated original mark on a neutral plate. |
| Custom | Existing compatible installed AppFilter/grayscale-map pack selected by user; unmatched/removed/unreadable resources fall back to original app icon. |

Per-app appearance menu offers inherit/global or any of the four rendering modes;
custom uses the selected global pack. It stores mode by actual app key and cannot
redirect a launch. Arbitrary replacement images or borrowing a different app's
logo are not provided. Resource permissions and third-party icon licenses remain
owned by their installed packs; assets are not extracted into this repository.

On the target phone the public theme-intent inventory exposes two compatible
installed packages: `com.jndapp.lux.nothing.iconpack` and
`dev.narikdesign.nothingadaptive`. This establishes availability, not which pack
the user selected privately in Nothing Launcher. Both packs were actually selected
and rendered on the phone, including Chrome launch through each styled ARC icon.
System, Intelia and Monochrome passed the same check. Unmatched School Online kept
its original icon in the custom packs. A per-app Chrome monochrome override visibly
updated native search immediately and was reset to inherit. System icons were
restored after testing. See the verification report for exact artifact/evidence.

## Appearance settings

Four focused pages under **Vzhled a přizpůsobení**:

- Colors: system/light/dark; Material You on API 31+, neutral monochrome,
  sage or slate accent. Older devices safely use neutral colors. Accent presets
  are real choices, not an arbitrary RGB editor.
- Icons: four modes and compatible installed pack names. Per-app override lives
  in the app's own appearance menu. Missing pack/icons have safe fallback.
- Typography/clock: system sans or system serif, clock and date visibility.
  Our drawn clock keeps its identity; font selection affects interface text.
- Interface/reach: system wallpaper auto/clear/solid, reduced motion,
  centre/left/right reach. Public blur limits are explained in the existing hint.

All are locally persisted in validated, bounded `InteliaUiState` and included in
the existing backup path. Missing new fields retain safe defaults. Restore cannot
silently enable Pulse. No settings restart, network request or new permission is
needed for an appearance change. Pulse has separate explicit Android consent.

## Intentionally deferred

See task 050. Custom accent editor, imported font packages, typography weights and
size controls beyond Android scaling, independent date/clock layouts, icon sizes/
label visibility, LENS size, ARC base strength/icon spacing, haptic intensity,
manual blur/opacity sliders and arbitrary per-app icon resources need geometry,
accessibility and persistence acceptance. No inert controls are shown for them.
Dynamic pack clock icons are sampled when loaded, not continuously ticked in
lists; no battery/FPS guarantee or performance certification is claimed.

Verification and independent rectangle/HOME/Recents results are maintained in
[native foundation report](NATIVE_FOUNDATION_REPORT.md) and [status](STATUS.md).

## Pulse 2.0 design proposal / calibration — 2026-10-15

See [PULSE_V2](PULSE_V2.md) for the proposed native ambient design: no visible idle
surface, content-sized transient capsule, original paired-stroke identity and a
separate downward global ARC. Preserve Home/LENS geometry. Adaptive theme,
contrast, scrollable expanded content and 48 dp targets must pass font-scale and
rotation tests. Camera-area placement depends on actual touch/system collision
verification; safe-anchor fallback must be labelled honestly. Motion timings in
that proposal are targets, not measurements. No copied MT Capsule artwork/code.
Only a debug manual calibration interface currently exists: native Czech
controls, live crosshair/outline, X/Y/width/height sliders, 1 px nudges and local
profiles. It is a functional calibration tool, not the final capsule design.
