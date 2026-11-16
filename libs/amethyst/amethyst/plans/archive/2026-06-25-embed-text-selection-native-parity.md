# Embedded text selection — native-Android parity

> **Status:** shipped — Doc states core feature-complete; host-drawn selection (handles, magnifier, IME proxy) landed in `EmbeddedTabLayer`/`RemoteImeView`.
> _Audited 2026-06-30._

**Status:** core feature-complete. The working set landed in `fix(embed): IME typing +
host-drawn text selection for embedded surfaces` (commit `e0a2a9ab81`); subsequent
sessions added the magnifier, the `SelectionUiState` refactor, hybrid word+char
handle-extend, and a run of polish/bug fixes (below). **The one open platform bug —
full-screen round-trip kills selection paint — is now FIXED** (root cause was
process-global `pauseTimers()` + an attached `WebView.destroy()`; see that section).

**2026-06-26 fixes (branch `fix/embed-ime-selection`):**
- **No-blink word-select** — the overlay handles + toolbar blinked 2–3× on long-press
  word-select; cause was the shim's selection-*reveal* scrolls (a `<textarea>` auto-
  scrolling to show a forming/re-asserted range) tripping the hide-on-scroll path. The
  shim now timestamps selection activity (`lastSelActivityAt`) and treats a scroll within
  350 ms as a reveal-scroll (reposition, don't hide, don't re-arm the timer). Plus a
  `RemoteImeView` range-lost debounce as a safety net. (#2, #3)
- **Page selection clears on field focus** — focusing a field left the page-text handles
  + Copy bar up (the shim's page `selectionchange` is muted once a field is focused), and
  being z-above they STOLE the field handle's drag. Fixed: `focusin` emits `pagesel:false`
  (+ resets the scroll state); host `ImeEvent.Focus` also drops the page overlay. (#2)
- **Caret handle drag** moved the loupe but not the caret — the unified `awaitEachGesture`
  did `change.consume()` BEFORE `change.positionChange()`, and `positionChange()` returns
  `Offset.Zero` once consumed, so `fp` never accumulated. Fixed with
  `positionChangeIgnoreConsumed()` (also immune to the sandbox surface consuming the move). (#10)
- **Hybrid word+char handle-extend** completed (#5, below).
- **Full-screen round-trip corruption FIXED** (was the open bug; see section).
- **Embed WebViews follow the APP theme** — separate from selection, but same surfaces:
  see `embed-webview-prefers-color-scheme-limitation` memo / `EmbedWebViewTheme.kt`.

## Why we draw selection ourselves

Editable fields inside an embedded napplet/nsite/browser tab live in the keyless
`:napplet` process and render through `SurfaceControlViewHost` /
`SandboxedSdkView` (privacy-sandbox UI). A WebView rendered into an off-window
surface like this **cannot host the soft keyboard and cannot present Chrome's
own text-selection UI** (handles, the floating action-mode toolbar, the
magnifier). Chrome detects it has nowhere to put that UI and collapses the
selection to the focus endpoint.

So, exactly like Flutter's `TextInputPlugin` did for its virtual-display era, we
relay editing to the main process: an invisible `EditText` (`RemoteImeView`)
hosts the keyboard, `shim.js` mirrors DOM selection/caret geometry out over the
Messenger channel, and `EmbeddedTabLayer` draws the selection UI in Compose on
top of the surface. Everything we want for parity, we draw — the platform gives
us nothing here.

## What native Android gives a text field (the parity target)

This is the full feature inventory we are cloning, with activation/deactivation
rules, so we can check off coverage. Native impl lives in `android.widget.Editor`
(+ `SelectionActionModeHelper`, `android.widget.Magnifier`,
`PopupTouchHandleDrawable` on the Chrome side).

| # | Native feature | Activates | Deactivates | Our status |
|---|----------------|-----------|-------------|------------|
| 1 | **Insertion handle** (the teardrop "blob" under the caret) | tap in editable text; tap again to re-show | typing, scroll start, focus loss, ~4s inactivity timeout | ✅ `InsertionHandle`. **Native availability rule now matched (2026-06-25):** only shown when the field is NON-EMPTY (`Editor` gates the handle behind `text.length() > 0`, via `SelectionUiState.fieldHasText`) — fixes it popping up on focus of an empty box; hides on typing (`onEdited`), scroll (`scrolling`), focus loss, and ~4s inactivity (`hideCaret` timeout), re-showing on the next tap — via an explicit `ime.carettap` shim signal (DOM `click`), so a tap that doesn't move the caret still re-shows it. Device-verified. |
| 2 | **Selection handles** (asymmetric left/right teardrops) | long-press word, double-tap word, drag-extend | tap-collapse, typing, new selection | ✅ `SelectionHandle(isStart)` + drag-to-extend, for BOTH plain page text (`pageExtend`) AND in-field `<input>`/`<textarea>` selections (`fieldExtend`, 2026-06-25). The shim reports the selection's caret feet (`sx/sb`,`ex/eb`, flagged `rng`) via the same mirror-div as the caret; the host holds the range geometry separately so Chrome's transient collapse-to-caret (the re-assert fight) doesn't yank the handles to the field edges. **Tap-to-collapse (2026-06-25):** a single tap inside a selection dismisses it to a caret at the tapped offset + insertion handle — the shim's `click` handler collapses explicitly via `offsetFromPoint` (off-window Chrome doesn't do it itself). Device-verified. |
| 3 | **Floating toolbar** (Cut/Copy/Paste/Select-All/Share/…) | selection made, or tap insertion handle (Paste/Select-All) | scroll/fling (hides, returns on settle), handle drag (hides), tap-collapse | ⚠️ `EmbeddedSelectionToolbar` (Cut/Copy/Paste/Select-All). **Hide-during-handle-drag ✅ device-verified.** Hide-during-scroll via `SelectionUiState.scrolling` (shim `ime.scroll` + re-report on settle). **2026-06-26: the scroll path was hardened** — selection-*reveal* scrolls (forming/re-asserting a range auto-scrolls a `<textarea>`) are no longer treated as user scrolls (they blinked the overlays); the shim guards them via `lastSelActivityAt` and the hide self-heals instead of re-arming. (User content-scroll-hide still wants a clean on-device pass.) Still missing: overflow, Share/Web-Search/process-text. |
| 4 | **Magnifier / loupe** (the zoom bubble above the finger while dragging a handle or the caret) | finger down + moving on a handle or the caret | finger up | ✅ **Built (2026-06-25).** `Magnifier` bubble in [EmbeddedMagnifier.kt] follows the dragged caret/selection handle, showing live magnified page pixels captured in the `:napplet` provider and shipped over IPC ([EmbeddedMagnifierProbe], option B). Both embed paths wired (browser + napplet); verified on device for the browser path. Capture Y is locked to the caret/selection line (X follows the finger). Possible further polish: RGB_565 to cut encode, themed crosshair, clamp capture X to the line so a fast drag past EOL doesn't show blank. |
| 5 | **Word-granularity long-press** then char-extend | long-press | — | ✅ HYBRID word+char in-field handle-extend (2026-06-25, completed): `fieldExtend` keeps per-drag state (`fieldDragWordEnd`/`fieldDragWordStart`, reset on a >250ms gap or edge switch). The drag baselines at the current selection edge; sweeping PAST that word's far boundary snaps to the next whole word (`wordEndAt`/`wordStartAt`), while moving within/back from the furthest-reached word gives CHARACTER precision — so you can fine-tune to a single character (the previously-missing "then char" mode). Page-text extend stays char (no offset model). |
| 6 | **Double-tap = word, long-press = word, (triple-tap/drag = paragraph)** | tap count | — | ✅ double-tap + long-press both select a word (2026-06-25). Chrome word-selects on the 2nd tap, then abandons it by collapsing to the end (off-window quirk); the host re-assert restores it. The shim `click` handler DEFERS its tap-to-collapse ~300ms and the real `dblclick` cancels that timer, so the word selection survives (a timing-only guard was flaky ~40%). Triple-tap/paragraph not done. |
| 7 | **Smart selection / entity expansion** (`TextClassifier`: phone, URL, address, date → entity actions in toolbar) | selection lands on an entity | — | ❌ not built (low priority) |
| 8 | **Drag selected text** (long-press a selection → drag-and-drop to move) | long-press on existing selection | drop | ❌ not built (low priority) |
| 9 | **Auto-scroll while dragging to a viewport edge** | handle dragged near top/bottom edge | finger leaves edge / up | ✅ works (2026-06-25, user-confirmed). The drag driver (`onMagnify`) detects the finger in the surface's top/bottom edge zone and sends `ime.autoscroll`; the shim scrolls the textarea (else the window) and re-reports geometry, flagged so the hide-on-scroll path doesn't fire. Scrolls per drag-move in the edge zone (not on a perfectly-held finger). **Fixed alongside:** the nav drawer's left-edge swipe was hijacking the edge drag — `EmbeddedSelectionDrag.dragging` (set by `onMagnify`) now suspends the drawer's `gesturesEnabled` while a handle is dragged. |
| 10 | **Caret snapping to character boundaries** | always during caret/handle drag | — | ✅ via `offsetFromPoint` binary search + Y-clamp. **2026-06-26 fix:** the insertion-handle drag stopped moving the caret (loupe showed, caret frozen) — the unified `awaitEachGesture` consumed the pointer change BEFORE reading `positionChange()`, which returns `Offset.Zero` once consumed, so the accumulated finger position never advanced. Now reads `positionChangeIgnoreConsumed()` first. |
| 11 | **Themed handle/caret drawables + blink** | always | — | ✅/⚠️ The host-drawn handles use `colorScheme.primary` — which IS the native `textSelectHandle`/accent color — so the handle drawables are themed (the actionable part). The caret bar + selection-highlight are drawn by Chrome inside the off-window surface: the caret already blinks natively, and theming its color/the highlight would mean injecting CSS into arbitrary third-party pages (intrusive; `::selection` was already ruled out as non-painting), so those are intentionally left to Chrome. |
| 12 | **Insertion-handle Paste/Select-All mini-popup** | tap the insertion handle | tap elsewhere | ✅ built (2026-06-25). Tapping the bare insertion handle toggles a Paste/Select-All bar above the caret (`SelectionUiState.insertionPopup`, toggled from the handle's unified tap/drag gesture); tap-elsewhere/typing/blur/selection/scroll dismiss it. Fixed a latent bug: toolbar items now consume the *down* (not just the up) so the tap doesn't bleed through to the surface and blur the field. Device-verified (Select-all selects all text, field stays focused). |

Legend: ✅ done · ⚠️ partial · ❌ missing.

### Activation/deactivation is the hard part

Most of the bugs we already fixed were activation-timing bugs (cursor-jumps-to-end,
collapse-on-tap, focus-transfer races). The remaining features each carry their
own state machine.

**Done (2026-06-25): `SelectionUiState`** (`SelectionUiState.kt`) now centralizes
what used to be scattered flags in `EmbeddedTabLayer` (`showInsertionHandle`,
`showSelectionToolbar`, `fieldGeometry`, `rangeFieldGeometry`, `pageSelection`).
It holds the three mutually-exclusive contexts (insertion caret / in-field range /
page-text range) plus the transient modifiers `dragging` and `scrolling`, and
exposes derived visibility (`insertionHandle`, `fieldHandles`, `fieldToolbar`,
`pageHandles`, `pageToolbar`) so the rules are expressed once:
- **toolbar hides while a handle is dragged** (`dragging`, set from the same
  `OnMagnify` lifecycle that drives the loupe) — the dragged handle also hides its
  own teardrop (the loupe stands in), like Android; the other handle stays.
- **all overlays hide while scrolling** (`scrolling`, from the shim's `ime.scroll`);
  the shim re-reports geometry just before `active=false` so they reappear
  repositioned.
Still emergent / TODO: insertion-handle auto-timeout, tap-to-re-show.

**Selection-blink fix (2026-06-25).** A field selection — especially in a `<textarea>` —
flickered: off-window Chrome abandons the selection by collapsing the caret to an
endpoint every ~25 ms, and the FIELD re-assert round-tripped through the host EditText
(`RemoteImeView.onPageState` → `ime.set` → page), leaving a visible collapsed frame each
cycle. Fix: re-assert field selections **synchronously in the shim's `selectionchange`
handler** (mirror of the page-text path that never blinked) — `lastFieldRange`/`lastFieldAt`
tracked via `noteSel()`, and a collapse-to-endpoint within 1500 ms is reverted with `setSel`
(guarded, not re-reported) so it reverts before paint. The host re-assert stays as a
fallback. Device-verified: textarea selection is stable; input select still works.

## Priority order for parity work

1. **Magnifier (#4).** Biggest perceived gap. We already report caret/handle
   geometry; the magnifier needs a *magnified pixel view of the surface* at the
   drag point. Options:
   - **A. Compose-side zoom of a surface snapshot. ❌ RULED OUT (spiked 2026-06-25).**
     The surface is a `SurfaceControlViewHost` — we can't trivially `Bitmap`-grab a
     remote surface from the main process. We spiked `PixelCopy.request(SurfaceView, …)`
     against the live embedded surface (`SurfaceMagnifierProbe`, wired into
     `EmbeddedTabLayer` behind `BuildConfig.DEBUG`, fired on field focus). The capture
     target is the privacysandbox `ContentView extends SurfaceView` — the only real
     child of `SandboxedSdkView` once the session opens. **Result on a clearly-painted
     surface (1080×2088): every capture returns `ERROR_SOURCE_NO_DATA`** (center
     region, repeated 3×). Cause: the WebView pixels live in a *child* `SurfaceControl`
     reparented under the SurfaceView via `ContentView.setChildSurfacePackage(...)`; the
     host SurfaceView's *own* buffer is never drawn into, so `PixelCopy` on the parent
     reads an empty buffer. Host-side pixel capture of the sandboxed content is not
     available. (A `PixelCopy.request(Window, …)` against the host window would also
     miss it — the sandbox layer is a *separate* SurfaceControl z-ordered below the
     window.)
   - **B. Capture in `:napplet` and ship the loupe content. ✅ SPIKED & VIABLE
     (2026-06-25).** Inside the keyless provider the WebView IS a real in-window view, so
     `WebView.draw(Canvas)` into a software bitmap renders real DOM pixels. Spike added
     `MSG_MAGNIFIER_REQUEST`/`MSG_MAGNIFIER_FRAME` to `NappletBrowserContract`:
     `NappletBrowserService.onMagnifierRequest` draws a zoomed slice
     (`canvas.scale(zoom); translate(-(cx-box/2), -(cy-box/2)); webView.draw(canvas)`),
     PNG-encodes it, and ships the bytes back; `EmbeddedBrowserController` (now also an
     `EmbeddedMagnifierProbe`) requests on focus and `EmbeddedTabLayer` logs the result.
     **10-frame burst, 160px source × 1.5× zoom → 240×240 PNG, center `#FF111111`
     (real opaque content):** provider draw 0.7–1.2 ms steady (≈5 ms cold), provider
     total draw+PNG 3–4 ms steady (≈12 ms cold), client round-trip 4–8 ms steady
     (occasional ~18 ms), payload 8–15 KB (far under the 1 MB Binder limit). Comfortably
     within a frame budget if throttled to ~30 fps.

     **✅ Real loupe shipped (2026-06-25).** `MagnifierUiState` + `Magnifier`
     (`EmbeddedMagnifier.kt`); the caret/selection handles call an `OnMagnify` callback
     on drag start/move/end; `EmbeddedTabLayer` tracks the drag point, throttles capture
     requests to one in flight (100 ms timeout), decodes each `MagnifierFrame` to an
     `ImageBitmap`, and floats the bubble above the finger (clamped, flips below near the
     top). Capture mirrored onto BOTH embed paths (browser:
     `NappletBrowserContract`/`NappletBrowserService`/`EmbeddedBrowserController`;
     napplet: `NappletEmbedContract`/`NappletHostService`/`EmbeddedNappletController`).
     Verified on device (browser): drag → bubble shows live magnified "ello world" with
     the caret, centered on the line → follows finger → hides on release. The dead
     Option-A probe (`SurfaceMagnifierProbe`) was removed. **Polish done (2026-06-25):**
     capture Y is locked to the authoritative caret/selection line (the handles pass a
     `lineHalfPx` so the box centers on the line, not the finger or the caret foot); X
     still follows the finger. Remaining nice-to-haves: RGB_565/raw to cut PNG encode,
     themed crosshair, clamp capture X to the line so a fast drag past EOL isn't blank,
     reuse one off-screen bitmap.
   - **Gesture-routing caveat (found during the spike).** The host-drawn handles'
     drag is fragile: the sandbox `ContentView.onTouchEvent` always returns `true`, so
     via Compose's `AndroidView` interop it consumes the drag-move pointer and cancels
     the overlay handle's `detectDragGestures` (synthetic `adb` drags on the handle
     never produced an `onDrag`). The magnifier trigger should ride the existing
     caret/selection-move path (which already round-trips through the shim), not a fresh
     Compose drag layered over the surface.
2. **Toolbar state rules (#3 hide-during-drag/scroll) + insertion-handle popup
   (#12).** Pure Compose/state work, no platform unknowns. Do alongside the
   `SelectionUiState` refactor.
3. **Handle inactivity timeout + tap-to-re-show (#1).** Small.
4. **Double-tap-to-select (#6)** and **word-granularity drag (#5).**
5. **Auto-scroll (#9), themed drawables/blink (#11).**
6. Defer: smart selection (#7), drag-to-move (#8).

## ✅ FIXED — full-screen round-trip corrupted the embedded WebViews (2026-06-26)

**Symptom (was).** Open an embedded field's page in its own full-screen activity,
then `back` to the embedded version. From then on **every** embedded surface in the
`:napplet` process was broken — and it was far more than the selection highlight: DOM
reads returned empty (a field that visibly showed text reported `value == ""`, so typing
prepended at offset 0 and backspace did nothing), DNS died (`ERR_NAME_NOT_RESOLVED`), the
selection highlight stopped painting, and IME broke. The page showed a stale last frame
over a functionally-dead renderer.

**Root cause — two process-global defects in the full-screen hosts** corrupting the
shared multiprocess WebView state the embedded surfaces rely on. (Diagnosed with temporary
logging: page console → logcat, all `onReceivedError`/`onReceivedHttpError`, and the shim's
focused-field type/caret. The user's own insight — "the activity is gone but the service
doesn't come back; am I using something from the activity?" — pointed straight at it.)

1. **`pauseTimers()`/`resumeTimers()` are PROCESS-GLOBAL** (they pause JS, layout and
   parsing timers for *every* WebView in the process). `NappletBrowserActivity` /
   `NappletHostActivity` `onPause`/`onResume` and `NappletHostService`'s embed
   pause/resume all called them on their own lifecycle — so returning from full-screen
   *froze* the embedded surfaces, which had no resume of their own. **Fix:** removed ALL
   process-global timer calls; rely only on per-WebView `onPause()`/`onResume()` (which
   pause just that surface's JS/DOM — still meets the napplet background-security goal).
2. **`WebView.destroy()` while still attached to the window** corrupts the shared
   multiprocess renderer (`cr_AwContents: "WebView.destroy() called while WebView is still
   attached to window"`). **Fix:** `stopLoading()` + `(parent as ViewGroup).removeView(...)`
   before `destroy()` in both full-screen activities' `onDestroy`.

Device-verified: the round-trip no longer corrupts the embeds. This supersedes the old
"recreate the session on return" hypothesis and the ruled-out attempts (`::selection` CSS,
surface resize, focus-cycle, `MSG_WAKE`) — none were the real cause.

## ✅ Embed WebViews follow the app theme (2026-06-26, separate concern)

Not text-selection, but the same off-window surfaces: embedded (and full-screen) WebViews
rendered web content in the *device* theme, ignoring the app's DARK/LIGHT preference.
**Root cause:** WebView's dark decision (`prefers-color-scheme` via algorithmic darkening)
reads the context's **theme** (`?android:attr/isLightTheme`), NOT just `Configuration.uiMode`
— and the off-window `SurfaceControlViewHost` surface context carries neither. The old
`applyNightMode` used `UiModeManager.setNightMode` (permission-gated no-op). **Fix:** build
every WebView from `nightThemedContext()` — `ContextThemeWrapper(createConfigurationContext(
<night|day>), Theme.DeviceDefault.DayNight)` for the resolved theme — shared in
`nappletHost/.../EmbedWebViewTheme.kt`, used by both embed services + both full-screen
activities. The full debugging arc (config-only context fails; `setForceDark` gone at
targetSdk 37; `setApplicationNightMode` does nothing; it's the unthemed context, not the
process boundary) is in the `embed-webview-prefers-color-scheme-limitation` memo. Upstream
WebView is still buggy here (a cross-process SCVH WebView ignores `uiMode`); the
theme-wrapper is the app-side workaround.

## How to test

The on-device harness lives at **`tools/ime-test/`** (`index.html` + `README.md`).
It's a single page with an `<input>`, a `<textarea>`, and an on-page log that
timestamps focus/selection/input/composition events, **paint latency**,
long-tasks, and main-thread blocks — the instrumentation that pinned the erase,
caret-jump, and first-letter-freeze bugs, and exactly what we'll want when
profiling the magnifier.

Run it (full details in `tools/ime-test/README.md`):

1. `cd tools/ime-test && python3 -m http.server 8765`
2. Reach it: emulator → `http://10.0.2.2:8765`; USB device →
   `adb reverse tcp:8765 tcp:8765` then `http://localhost:8765`.
3. Open that URL in the **in-app browser** to load it as an *embedded* tab. (Opening
   the same URL full-screen and pressing `back` used to reproduce the
   highlight/corruption bug — now fixed; it's still the regression test for it.)

Console log lines are tagged `[ImeDiag]` and surface in `adb logcat` (the
`:napplet` process owns the WebView console). This is a dev tool — nothing under
`tools/` ships, which is why those diagnostic strings are kept out of `src/`.

## Key files

- `commons/src/commonMain/composeResources/files/napplet/shim.js` — DOM bridge.
  Geometry sources: `caretCoords` (287), `offsetFromPoint` (318), `fieldGeom`
  (336), `reportState` (360), `pageGeom` (462), `sendPageSel` (470), `pageExtend`
  (494). A magnifier built via option B would add a loupe-render here.
- `amethyst/.../embed/EmbeddedTabLayer.kt` — the Compose overlay. `InsertionHandle`
  (557), `SelectionHandle` (496), `EmbeddedSelectionToolbar` (616),
  `PageSelectionOverlay` (460), the `showInsertionHandle`/`showSelectionToolbar`
  state to be folded into a `SelectionUiState`. Magnifier popup (option A) lands
  here.
- `amethyst/.../embed/RemoteImeView.kt` — invisible host `EditText`; selection
  re-assert + copy/cut/paste/select-all + edit callbacks.
- `amethyst/.../embed/EmbeddedImeBridge.kt` — `SelectionGeometry` (caret + handle
  feet + viewport), `ImeEvent.{Focus,State,PageSelection}`, `parseSelectionGeometry`.
- `amethyst/.../{browser/EmbeddedBrowserController,favorites/EmbeddedNappletController}.kt`
  — parse `ime.pagesel` + geometry off the Messenger channel.
- Context: `amethyst/plans/2026-06-19-napplet-sandbox-host.md`,
  `2026-06-24-napplet-embedded-tabs.md`.
