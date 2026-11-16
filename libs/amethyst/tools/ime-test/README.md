# IME / text-selection test harness

A single-file web page (`index.html`) for exercising and profiling the embedded
WebView IME + text-selection relay (see
`amethyst/plans/2026-06-25-embed-text-selection-native-parity.md`). It has a
plain `<input>` and a `<textarea>` plus an on-page green log that records, with
millisecond timestamps:

- focus/blur, `selectionchange`, `keydown`/`beforeinput`/`input`, composition
  events, and the resulting `value`/selection — to catch erase, caret-jump, and
  focus-transfer regressions;
- **paint latency** (`requestAnimationFrame` after each DOM change) — the metric
  that exposed the first-letter freeze;
- **long-task** + **main-thread-block** detectors and a focus/selection
  **heartbeat** — to catch anything stalling the WebView main thread or
  spontaneously moving focus/selection.

The log lines are tagged `[ImeDiag]` and also go to `console.log`, so they show
up in `adb logcat` (the `:napplet` process owns the WebView console). Nothing
here ships in the app — it's a dev tool, which is why the `[ImeDiag]` strings
live only under `tools/`.

## Run it

1. Serve this directory over HTTP from your dev machine:

   ```bash
   cd tools/ime-test && python3 -m http.server 8765
   ```

2. Reach it from the device/emulator:
   - **Emulator:** the page is at `http://10.0.2.2:8765` (`10.0.2.2` is the
     emulator's alias for the host loopback).
   - **Physical device (USB):** `adb reverse tcp:8765 tcp:8765`, then the page is
     at `http://localhost:8765`.

3. Open that URL as an **embedded** tab (this is the path that uses the relay —
   *not* a full-screen activity):
   - Open the in-app browser (`BrowserScreen`) and type the URL into its address
     bar. The embedded browser handles `http`/`https`, so it loads into the
     `:napplet` SurfaceControlViewHost surface.

   To compare against native behavior, open the same URL in a full-screen
   activity (where the WebView renders in-window with the native keyboard) — that
   is also how you reproduce the **full-screen round-trip highlight bug** (open
   full-screen, `back`, then selection highlight is dead across all embeds).

## Reading the log

- `INPUT … val=… sel=…` right after a keystroke with the right value = no erase.
- `PAINT-LATENCY Nms` spiking to ~1000ms = the first-letter freeze (should stay
  low now that the surface no longer resizes on IME show).
- `MAINTHREAD BLOCKED` / `LONGTASK` = something is stalling the WebView thread.
- `HEARTBEAT` lines changing while idle = spontaneous focus/selection drift.

## `shim-events.mjs` — automated regression test for the IME protocol

`index.html` and `perf.html` are manual probes; this one is a real test. It loads
the shipped shim (`commons/.../napplet/shim.js`) into headless Chromium with the
embedded-surface flags set, drives genuine focus/tap/blur gestures, and asserts
the `ime.*` envelopes it emits — the doorbell fires on a tap in an
already-focused field, the doorbell stays payload-free, a host `ime.resync` is
answered with the field state and no geometry, `readonly` survives the round
trip, and a tap inside a `contenteditable` counts.

```bash
cd tools/ime-test
npm i playwright-core          # once; the browser itself is already on the box
node shim-events.mjs           # exits 0 on success, 1 with a per-case report
node shim-events.mjs /path/to/other/shim.js   # diff a candidate against it
```

Set `CHROMIUM_PATH` if your Chromium lives somewhere other than
`/opt/pw-browsers/chromium-1194/chrome-linux/chrome`.

**Why this and not a JVM unit test.** The half worth protecting is the
page↔host contract — real browser focus/gesture behavior and the envelopes the
shim emits for it — and that only exists in a browser. A JVM test of the
host-side parser (`parseImeEvent`, kotlinx.serialization) would only re-parse
envelopes the test itself fabricated.

## `shim-parity.mjs` — does every way of editing a field behave as in the browser?

A differential test. Each scenario (a field pattern plus a list of user actions) runs twice in headless
Chromium: **natively**, edited the way Android Chrome edits from a soft keyboard (committed text via
`Input.insertText`, Backspace/Enter as real keys, composition via `Input.imeSetComposition`, a real clipboard
paste), and **through the shim**, driven by a model of `RemoteImeView` that ships `ime.set` / `ime.action` /
`ime.paste` / `ime.cut` the way the app does. It then compares the field's text and caret, what the page did
(sent, searched, submitted, refused) and whether the host's mirror still matches the field.

The fixtures cover the patterns real pages use: plain inputs and textareas, `maxlength`, number/email/password,
an input mask that refuses characters in `beforeinput`, React-style controlled inputs (including one that
transforms what you type and one the page rewrites mid-typing), a chat composer that sends on a keydown Enter and
clears itself, a form, a keydown-driven search box, a combobox, a Brainstorm-style chip editor (chips whose text
differs from their token; Enter and Backspace handled in `beforeinput`), contenteditables with and without
`white-space: pre-wrap`, a field the page remounts mid-typing, a page paste handler, and a 20k-character draft.

```bash
node shim-parity.mjs            # all scenarios; exits 1 if any differs from native
node shim-parity.mjs chips      # only scenarios whose name contains "chips"
SHIM=/path/to/shim.js node shim-parity.mjs   # check a candidate (or an old) shim
```

## `shim-perf.mjs` — what a keystroke costs the page

Replays 200 host keystrokes into the shim on long fields (a 20k-character textarea, 1,000-line and
1,000-paragraph contenteditables, a page that rewrites its value on every input) and prints p50/p95/max per
keystroke. Run it before and after touching the contenteditable offset mapping: that is where a careless walk
turns a keystroke quadratic.

## `device.html` — the same fixtures, on a device

The riskiest fixtures (form Next/Go, chips, line breaks, `maxlength`, a paste handler, a chat box) as one page
to drive by hand through the real keyboard. Serve this directory (`python3 -m http.server 8765`) and load
`http://10.0.2.2:8765/device.html` in an **embedded** tab (a bottom-bar web app — the Browser tab opens URLs
full screen, where the native keyboard is used and the relay is not involved); `window.__out` logs what the
page did.

## `perf.html` — why does the embed feel slower than the full-screen browser?

`index.html` profiles the IME relay. `perf.html` answers a different question:
the embedded tab and the full-screen browser are the **same WebView in the same
`:napplet` process** with byte-identical `WebSettings`, so when a site's JS feels
slower in the embed, the cause is host-induced — and this page measures which
host effect it is.

Serve the directory (above) and open **the same URL in both hosts**, then compare
the summary line at the bottom of the page:

- **`vis=hidden`** — decisive. Chromium considers the embedded page hidden, so it
  clamps timers to ~1Hz and suspends `requestAnimationFrame`. Everything the site
  schedules lands late; it reads as "the JS got slow". Confirmed by
  `timer50` (a 50ms interval firing at 500-1000ms) and `raf` (0 fps).
- **`vis=visible` but `cpu` is 2-4× the full-screen number** — the process is
  running on the little cores. The site's JS runs in the WebView *renderer*
  process, whose scheduling class is inherited from its host: `:napplet` is
  `top-app` when it fronts the full-screen activity, but only a bound service
  (`BIND_AUTO_CREATE`, no `BIND_IMPORTANT`) when it serves the embed. Cross-check
  off-device with:

  ```bash
  adb shell dumpsys activity processes | grep -E 'napplet|sandboxed'
  adb shell "cat /proc/$(adb shell pidof com.vitorpamplona.amethyst:napplet)/cgroup"
  ```

  Expect `/top-app` with the full-screen browser open and `/foreground` (or lower)
  with an embed tab open.
- **`cpu` matches but `inputDelivery` is much higher** — the gap is input routing
  into the embedded window, not compute. `inputDelivery` is the time between the
  platform stamping the touch and JS receiving it.
- **`layout` much higher in the embed** — layout/paint is the bottleneck (check
  logcat for WebView software-rendering warnings; a non-hardware-accelerated
  `SurfaceControlViewHost` window would put Chromium on the software path).
- **`focus=false` in the embed is expected** and is not itself a throttle: the
  host window owns the keyboard, which is the whole reason `RemoteImeView` exists.

`longtasks` counts main-thread blocks over 50ms while the page was measuring —
high counts in the embed with a matching `cpu` number point at something else in
the process competing (e.g. parked warm tabs that are never paused).
