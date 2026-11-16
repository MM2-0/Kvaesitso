# Adaptive UI contract

This guide covers this repository, not other Telos repositories. The work builds on merged
wallpaper-readability PR #46 (main merge `feaff22`); its head was `211d1fac`.
A passing compiler or policy test does **not** establish visual completion.

## Scope and source of truth

`settings.gradle.kts` defines the build modules. The Android application is `app:app`;
Compose screens live together in `app:ui`, not in separate APK modules per Telos app.
`SettingsActivity` owns the standalone route registry, `TelosAppActivity` reuses that host,
and `TelosPages` in `TelosAppPageComponent.kt` lists the subset embeddable in the launcher.
`core/base/.../search/VirtualAppProvider.kt` defines launcher entries. Do not invent capabilities
for a route or claim that an external application was updated.

| In-repo route family | UI implementation / scope of this pass |
| --- | --- |
| Launcher home, drawer/search, widgets | Actual window dimensions; measured search/actions/filter heights; bounded icon columns and touch targets; scrollable home clock fallback |
| Settings and app settings | Shared preference rows, headings, toggle semantics, sliders, reading width, safe insets/IME and growing titles |
| Phone, contacts, messages | Shared search and host protection; growing three-destination navigation; dialpad from available space, naturally tall keys and panning numbers |
| Calculator / converter | Constraint-driven two panes, scrolling keys/display, semantic key surfaces, natural key/card heights; saved mode/category remains |
| Files / connections | Two panes only with enough width **and height** at the active font scale; otherwise active tab remains accessible through workspace tabs; VM state retained |
| Notes | Shared search; font-aware card columns and theme shape; existing saveable draft retained |
| Calendar | Month, selected-day heading and events share a lazy list; week grid pans instead of shrinking day targets; existing saveable event draft retained |
| Network / DNS / firewall / Tor / WireGuard | Font-aware hub columns, natural card/toggle heights, wrapping stats, theme primary for active status; subsettings share preferences |
| System | Shared composited cards, theme active-status color; existing telemetry-unavailable state retained |
| Clock | Important time values pan horizontally instead of dividing their font size by system font scale; tabs already scroll |
| Screenshot | Adaptive gallery columns; shared search/settings |
| Media hub, music, radio, TV, video, YouTube, Stremio, photos/document viewer, Akou | Shared host/search/theme; growing media navigation labels. Playback, document canvases and third-party/WebView content retain their specialized layouts |
| Voice recorder / screen recorder | Scrollable short-window controls/list access; recording durations pan at full text scale. Actual capture and permission states require a device |
| Ermis mail | Shared composited message cards; non-secret composer/filter fields and attachment URIs use saveable state; account authentication and send/sync require accounts |
| Downloads, store, trash, camera, magnifier | Shared host/theme/search/preferences where used. Hardware, account, loading and permission paths require device review; this pass does not claim every nested editor/player is visually certified |
| Flashlight / siren / Morse | Theme-aware controls, status and cards; the actual screen-light color remains a functional output, not a theme surface |
| Termux / web apps | Termux is a separately installed app recommended by Store. No embedded terminal or bootstrap remains. Web content retains its own rendering; host protection does not restyle external apps or web pages |

Assistant/island/floating overlays are launcher capabilities, not additional standalone app modules.

## Reuse the existing foundations

List dashboards (Store, Downloads, Freeze, ART Optimizer, and new apps like them) use
`TelosCommandHud`: icon search, icon filters with long-press labels, badge counts, and
dot-matrix status. Do not stack a full search field, a Filters button and a bulk-action
button. Keep 48dp targets. See `AGENTS.md` Command HUD.

- `LauncherTheme` supplies the user's Material color scheme, typography, shapes and the product
  font multiplier **on top of** Android font scale. Do not install a second palette/type system,
  set a replacement font density, clamp fontScale or shrink important text to force it into a box.
- Use semantic pairs: surface/onSurface, surfaceContainer*/onSurfaceVariant, primary/onPrimary,
  secondaryContainer/onSecondaryContainer, errorContainer/onErrorContainer. A status must also
  have text/selection semantics; color alone is insufficient. Recording, camera and night/output
  surfaces can have a functional exception; document the reason locally.
- `LauncherCard` resolves normal text/icons against the composited panel. Preserve #46's
  wallpaper scrims, local sampling gates and explicit user choices. Use `VirtualAppTheme` /
  `VirtualAppWindow` for app roots and the existing glass helpers for bars. Do not introduce
  arbitrary 55% opacity or hard-coded white text on theme-derived cards.
- `UiSpacing` supplies 8dp small gaps, 12dp section gaps, 16dp content padding and the 48dp
  minimum interactive target. MaterialTheme supplies typography and shapes; `shapes.medium`
  is the default card shape. These are defaults, not reasons to flatten media/editor layouts.
- Use `PreferenceScreen`, `PreferenceCategory`, `Preference`, `SwitchPreference`,
  `CheckboxPreference`, `SliderPreference` and `TelosSearchBar` rather than copying their layouts.
  Shared toggles expose one labeled toggle action. Keep the child switch/checkbox callback null;
  the parent owns the change. Disabled sliders must actually disable dragging.
- `AdaptiveNavigationBar` / `AdaptiveNavigationItem` are shared by Comms and Media. Their
  labels wrap with natural height, and selected destinations expose `Role.Tab` and selection.
  Keep destination identity/state outside the layout; changing width must not reset navigation.

## Layout rules

Use `BoxWithConstraints` at the component boundary, after parent padding/insets. Use the actual
`LocalWindowInfo.containerSize` only for root geometry / pixel consumers such as wallpaper
sampling and launch transitions. System display metrics and orientation/device type are not
content constraints. Display scaling changes available dp; do not apply it a second time.

**CRITICAL RESPONSIVENESS MANDATES FOR VIRTUAL APPS:**
1. **Never use hardcoded heights (`height(dp)`) for structural containers.** If a container holds dynamic text or scaling UI elements, use `heightIn(min = ...)` combined with `Modifier.weight(1f)` for flexible expansion. Hardcoding heights will cause vertical overflow and clipping when the user increases the font scale up to 2.0x.
2. **Always handle Landscape properly (Side-by-Side).** Apps with a top content area (e.g., results, previews) and a bottom control area (e.g., dialpads, calculator keys, media controls) MUST switch to a `Row` (Side-by-Side) layout in landscape mode (`maxWidth > maxHeight`). Do not stack tall control panels vertically in landscape, as it forces unusable scrolling or glitches.
3. **Use WindowInsets.safeDrawing universally.** Virtual app hosts must pad content with the full `WindowInsets.safeDrawing` (not just horizontal), so that system bars and navigation gestures never overlap with the app's interactive areas.

`AdaptiveLayoutPolicy` centralizes only pure space decisions, not rendering or mutable state:

- Grid columns fit the available width and minimum cell budget, with the supplied preferred
  count as a maximum. Existing explicit adaptive-column preferences may first compute a higher
  preferred count. Never rewrite a saved grid preference because the window is small.
- Preference controls move below text below a 360dp effective reading budget (increased for
  larger fonts). The complete title/summary keeps natural height.
- Side-by-side content needs two 320dp effective pane budgets plus a 12dp gap **and** a 320dp
  effective height. Larger text increases these budgets. A wide but short window is insufficient.
- Settings reading content is centered at up to 840dp. Do not apply this cap to a photo, camera,
  terminal, waveform, document, map or user-managed multi-pane workspace.
- Calculator keys keep their label budget and pan if necessary. Do not trade away touch size
  or text size to keep all scientific functions on one small screen.

Prefer `heightIn(min=...)`, wrapping and scrolling to fixed heights/aspect ratios around text.
Single-line search fields can scroll their value; important numbers need panning or an accessible
full-value view. Ellipsis is appropriate for a preview with a full-detail destination, not for a
value the user must read to act. Long lists need stable keys and lazy containers. Never put an
unbounded vertically scrollable child inside another vertical scrolling container.

Insets belong to one owner and must be consumed before descendants handle them. Scaffold
padding is included/consumed by preference lists; IME adds only the remaining space. Standalone
and embedded app hosts protect horizontal cutouts and IME, while screens retain their own
vertical system-bar handling. Test both entry paths. Do not add bottom keyboard height into a
measured search/filter *content* height.

Keep drafts, selected tabs, modes and scroll state in the existing ViewModels, `rememberSaveable`,
Navigation3 back stacks / state holders. Layout branches must not mutate user navigation or clear
an editor. Do not store Activities in layout policies. Recreation, live resize and process death
are different cases and need explicit validation.

## Completion criteria and evidence

A UI phase can close only when all of the following are recorded against the tested commit:

1. Main paths can launch, navigate, search, change settings and return; essential values/actions
   remain reachable with no overlap, clipped text or hidden keyboard actions in the matrix below.
2. Theme typography, card shapes, 8/12/16 spacing rhythm, icon alignment and semantic surfaces
   agree across launcher/settings/Comms/Media and the modified app screens. User theme choices
   and wallpaper readability from #46 remain intact.
3. Empty/search-empty, loading, error/permission-denied, disabled and selected states convey their
   meaning and provide an appropriate reachable action. Hardware/account-only states are listed
   separately if unavailable, not marked passed by reviewing code.
4. Touch targets reach 48dp, TalkBack labels/toggle/selection roles are correct, focus order and
   keyboard navigation work, and important content is preserved at large text.
5. Resizing/rotation preserves active workspace, navigation, calculator input/mode and unsaved
   note/calendar/message/mail drafts. Permission revocation and background/resume do not crash.
6. Compilation, regression suites, focused lint and actual screenshot/device findings are attached
   with exact outcomes. No-emulator static checks and previews cannot satisfy visual criteria.

| Window (available dp) | Purpose |
| --- | --- |
| 320×480 and 360×800 | Compact, short and tall; portrait; all critical actions reachable |
| 800×360 | Short landscape; scrolling and single-pane fallback |
| 600×800 and 840×480 | Medium and expanded-width boundary behavior |
| 1200×800 / 1440×900 | Expanded; reading widths, pane thresholds and long labels |

For each, exercise font scales **0.85, 1.0, 1.3, 2.0** (small where Android supports it), default
and enlarged display size (e.g. density multiplier 1.25), light/dark themes, bright/dark/mixed
wallpapers, IME open/closed, long Greek/German labels and RTL. Include static/live wallpaper and
denied wallpaper pixel access; verify scrim fallback rather than expecting unavailable pixels.

Regression coverage:

- `AdaptiveLayoutPolicyTest`: width/font/display-scale matrix, preferred-count bounds,
  pane width+height budgets, settings reflow, reading width and scientific key label budgets.
- `PreferenceAdaptiveTest` (instrumented): wrapped-title/control geometry, labeled toggle and
  disabled state, growing navigation labels/selected semantics. On API 29+ it exports light/dark preference and navigation PNGs through MediaStore into
  Pictures/TelosUiValidation, which survives test-app cleanup. CI collects them as artifacts;
  inspect them before calling the result visually verified.
- Existing contrast tests remain separate and must continue to pass.

Run with one worker and in-process Kotlin compilation on 8 GiB runners. Use a 2 GiB Gradle
heap for compile/unit tests; do not start multiple Gradle builds simultaneously. Full app lint
previously exceeded this runner's memory during #46. Focused lint results must name their check
IDs and must never be described as a clean full lint result.

Execution results and outstanding device checks are recorded in [UI validation](./ui-validation).

Compatibility fixes found by focused lint use SDK guards and existing AndroidX compat APIs.
Do not reference public wallpaper contrast hints before API 31, recoverable media actions before
29 or profile controls before 28. On older releases retain conservative scrims / unsupported
control states. Explicitly type collection branch results as Kotlin Map when mixed sorted/linked
maps could infer newer JDK interfaces such as SequencedMap on a JDK 21 build host.

Gender-specific resource directories must inherit base-language translations.
English filler entries that override a translated base string are removed; retain
actual grammatical variants instead of copying the full English catalog.

Virtual-app Recents metadata and notification identity are separate Android paths.
Keep static task aliases and dynamic TaskDescription metadata aligned. Media3
providers explicitly set a monochrome app glyph; other app notifications use
VirtualAppAppearance. Preserve media artwork and use a unique PendingIntent identity
when opening each app route. Android/OEM notification headers can still show the
shared APK name because virtual apps are not separate installed packages.
