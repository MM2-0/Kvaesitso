# Napplets inside notes — 2026-10-30

Status: implemented. Desktop verified headless; Android compiles and passes lint but has not run on
a device.

A NIP-5D napplet card in a feed can run the napplet inside the note (Run here), instead of only
opening it full screen or in a window. Both platforms keep the sandbox they already use: Android's
keyless `:napplet` process, the desktop's Chromium with its SOCKS router.

## What the user sees

`StaticWebsiteCard` gains a stage under the description (`InlineNapplet.kt`, `commonsUI`):

- **Poster** until the user starts it: the app's icon (or the last frame it showed, kept for the
  last 8 napplets in `InlineNappletPosters`) and Run here. Nothing runs and nothing is fetched
  beyond the prefetch the card already did.
- **One at a time** (`InlineNappletRuns`): starting a napplet stops the one running elsewhere, and
  a note leaving the composition (scrolled away, another screen) stops its own.
- **Nothing over the page.** A running app is the page itself in a primary-colored border. Stop is
  on the card's header beside its Open (`InlineStopButton`), and the "wants to ask you" notice sits
  below the page: on Android the page is another process's surface, which may draw above anything
  Amethyst puts over it. Opening the app full screen or in a window is the card's own Open, which
  stops the note's run first (`InlineNappletRuns.stopApp`). A bar above the page ("Running in this
  note", Open, Stop) and an Open on the notice made three Opens for one action, and the bar said
  what the user could already see.
- **No questions in the feed.** The run's identity is marked `inline` by the trusted launcher
  (`NappletLaunch.inline(instanceId)`). The broker keeps every grant the user already gave the
  app, but where it would show a dialog (capability, first connect, signature) it refuses and
  records the ask in `NappletInlineAsks`; nothing is held against the app. The note then shows
  "… wants to ask you something. Open it to answer."
- **Desktop bridge names** are per session. Chromium keeps one message router per query-function
  name for every browser in a renderer, and every shell shares one renderer: with one shared name,
  a window opened while the note's run was closing joined that run's router, the close removed
  it, and the window's page reached no one. Its question was never asked.
- **Scroll stays with the feed**: on Android the surface does not claim vertical drags (unlike the
  favorite tabs); on the desktop the wheel scrolls the page only after it was clicked.

## Android

`InlineNappletView` puts a `SandboxedSdkView` in the note and drives it with the same
`EmbeddedNostrAppController` the favorite tabs use (cross-process `SurfaceControlViewHost`,
Android 11+; older devices keep the plain card). It is owned by the note, not by `EmbeddedTabHost`,
so disposing the note tears the session down. Its token keeps the note's instance id
(`NappletLaunchRegistry`), so the broker's ask record and the note agree on the run.

## Desktop

A windowed Chromium browser is a native child window: it draws over Compose and ignores clipping,
so it can't sit in a scrolling list. The note uses an off-screen browser instead:

- `OffscreenCefBrowser` (Java, `org.cef.browser`): subclasses JCEF's package-private
  `CefBrowser_N` and is its own render handler. JCEF's own off-screen browser drops every frame
  until its JOGL canvas is on screen. The engine now starts with `windowless_rendering_enabled`;
  windows are unaffected (the whole Chromium suite passes).
- `OffscreenNappletView`: frames (BGRA, copied once into a Skia image), the `<select>` popup laid
  over them, the cursor, and input forwarding: mouse and wheel re-based to the page, AWT key events
  passed through (with a typed event made from the press when the window sends none).
- `CefNappletSession(offscreen = …)` runs the same shell, bridge, routes and broker as a window;
  `DesktopNappletHost.openInline` / `closeInline` keep these runs apart from the windows.
- `InlineNappletPane` draws the frames at the note's size times the screen's scale.

## Known

- Every off-screen browser makes JCEF's native code print `Exception in thread "AWT-EventQueue-0"`
  with no trace; under a test agent it shows as a `java.lang.instrument` assertion. Nothing
  visible from Java throws, and frames, clicks and keys work. `-Xcheck:jni` aborts inside JCEF's
  own startup, so it can't locate it. Unresolved.
- CEF warns that `windowless_rendering_enabled` "may reduce rendering performance on some
  systems" for windowed browsers. It can only be set at startup, so the engine turns it on always;
  no slowdown showed in the window tests here, but this was not measured on real GPUs.
- The page renders at the note's real size (4:3, the note's width). A napplet designed for a full
  screen looks cramped; there is no manifest hint for an embeddable size yet.
- WebGL is unavailable in the headless container (software GL is blocklisted), so 3D napplets
  show their error there; that is the container, not this path.

## Tests

- `NappletBrokerTest`: an inline app never asks and its ask is recorded; it keeps what was
  granted; it neither connects nor signs without the user. Checked to fail without the gate.
- `CefNappletSessionTest.aNappletInsideANotePaintsWithoutAWindowTakesClicksAndNeverAsks`:
  frames at the size given, a click and a key reach the page, and a storage write is refused
  without a prompt.
- Desktop app, headless: Mini Samurai, a ping-pong game and the Napplet Test Harness run inside
  their notes; clicks and keys reach them; the harness shows the Open-to-answer banner; Stop leaves
  the last frame.
