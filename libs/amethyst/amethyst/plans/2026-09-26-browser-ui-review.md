# Browser surfaces: UI review and redesign

Status: **shipped** (see §5). The components live in
`commonsUI/src/commonMain/kotlin/com/vitorpamplona/amethyst/commons/browser/ui/pill/`. They are rendered
offscreen by `BrowserPillRenderTest` (commonsUI jvmTest), which writes PNGs to
`commonsUI/build/browser-pill/`.

Follows `2026-09-26-browser-pwa-parity.md`. That plan settled *what* the browser does. This one is about
how it looks and feels.

## 1. What's wrong with what shipped

Reviewed against the code as merged: `TopControlSheet`, `NappletControlSheet`, `BottomConsoleSheet`,
`NappletConsolePanel`, `BrowserFindBar`, `EmbeddedFindBar`, `EmbeddedPageDialogs`, `BrowserJsDialogs`
and the full-screen permission / page-info `AlertDialog`s.

**Top pill**
1. **It's a settings list, not a menu.** Up to 15 rows of equal weight in one scrolling column. Share,
   copy and find (used daily) look exactly like desktop site and open-in-another-browser (used rarely).
   Chrome solves this with an icon strip plus a short list; we copied the list and made it longer.
2. **No visual hierarchy.** Section labels are 12sp caps you don't notice. Every row is the same 44dp
   icon + label. Nothing groups.
3. **Security state is a word, not a signal.** "Not secure" and "Onion-routed over Tor" are the same
   grey 13sp text. Plain HTTP isn't warned about, and Tor, the thing Amethyst does that Chrome doesn't,
   isn't celebrated.
4. **Three rows are just switches** (Tor, desktop site, console). Each is a full-width row whose only
   content is the switch. They say what they are, not what they mean ("Loads over Tor" vs "the site
   can't see your IP").
5. **The text-size stepper** is squeezed into a list row: two 24dp buttons and a percentage, with no
   preview and no way back to 100%.
6. **The collapsed grabber is mute.** It never shows loading, an insecure page, or console errors, so
   there's nothing to invite the pull.

**Inputs**

7. **Edit address is a bare text field** dropped into the header. It has no clear button, no go button,
   no suggestions (the launcher's omnibox has them), and no paste-and-go.
8. **Find in page** is a full-width sheet with a default `TextField`. The count is loose text, and "no
   matches" has no state of its own. The native version draws a different bar.
9. **The JS prompt field** has no label. "Block dialogs from this page" is a third button squeezed in
   beside Cancel/OK. Chrome uses a checkbox, because it's an option, not an answer.

**Dialogs and sheets**

10. **The permission prompt** is a stock `AlertDialog` with bullet text and only Allow / Block. There's
    no "just this time", no icon for what's being asked, and nothing about the connection.
11. **Page info** is a paragraph in an `AlertDialog` with three crowded buttons. The site's permissions
    aren't there, and Clear site data runs immediately, with no confirmation.
12. **The console** is a flat list with no filters (errors are buried in logs), no copy, and a count
    that only shows inside the menu.

**Parity**

13. **Two renderers, two looks.** The full-screen pill is plain Views: framework `Switch`, framework
    ripple, hand-rolled type sizes. Sharing the layout spec stopped the two drifting in content, but
    they still don't look alike. `:nappletHost` already depends on `:commonsUI` (Compose), so the
    full-screen window can host the same Compose pill in a `ComposeView`.

## 2. Principles

- **One tap for daily things, two for rare ones.** Navigation, star and share sit in a capsule.
  Page actions are a grid of tiles. Privacy and developer settings are grouped cards below.
- **State is visible, even collapsed.** The security colour (error for HTTP, Tor accent for onion),
  loading progress and console errors show on the grabber itself.
- **Inputs are first-class.** Every text input has:
  - a real container and a leading icon that says what it is;
  - a placeholder or label;
  - clear and submit buttons;
  - the right keyboard and IME action;
  - an empty or error state.

  Choices are segmented buttons or switches that state their consequence, never a bare "on".
- **Explain consequences in plain words.** Say "The site can't see your IP address", not "Loads over
  Tor". Say "Allow while visiting / Only this time / Don't allow", not "Allow / Block".
- **One implementation.** Stateless composables in `commonsUI`, driven by `BrowserChrome` (already
  shared). The embedded tab, the full-screen window (through `ComposeView`) and a future desktop browser
  all draw the same pixels.

## 3. The redesign

### 3.1 Collapsed handle

A 48×24dp capsule at the top centre holding a 32dp bar.
- A 2dp progress line runs under the bar while the page loads.
- The bar takes the error colour on HTTP and the Tor accent when onion-routed.
- A 6dp error dot appears at the right edge when the console has errors.
- It keeps its current gestures: pull or tap to open.

### 3.2 Expanded pill

```
╭────────────────────────────────────────────╮
│  [P]  Primal                          ( ✕ ) │  monogram tile · title · close (full screen)
│  ╭────────────────────────────────────────╮ │
│  │ 🧅 primal.net                   ✎      │ │  origin field: tap edits the address
│  ╰────────────────────────────────────────╯ │
│  ⓘ You left primal.net   [ Back to app ]    │  only when out of scope
│  ╭────────────────────────────────────────╮ │
│  │   ←      →      ↻      ★      ⇪        │ │  navigation capsule
│  ╰────────────────────────────────────────╯ │
│  ╭────╮ ╭────╮ ╭────╮ ╭────╮               │
│  │ ⧉  │ │ 🔍 │ │ Aa │ │ ⌂+ │               │  page actions (tiles, 4 per row)
│  │Copy│ │Find│ │Text│ │Home│               │
│  ╰────╯ ╰────╯ ╰────╯ ╰────╯               │
│  ╭────╮ ╭────╮ ╭────╮                      │  toggle tiles fill when on (Desktop)
│  │ 🖥 │ │ ⬈  │ │ ⛶  │                      │
│  ╰────╯ ╰────╯ ╰────╯                      │
│  A  ━━━━━━●━━━━━━━  A    115%   Reset      │  appears when "Text" is on
│  ╭ Privacy ───────────────────────────────╮ │
│  │ (🧅) Onion routing             [ ●  ]  │ │
│  │      The site can't see your IP        │ │
│  │ (⚙)  Site settings                 ›   │ │
│  │      Camera allowed · Location blocked │ │
│  ╰────────────────────────────────────────╯ │
│  >_ Console        (3 errors)     [    ]    │
╰────────────────────────────────────────────╯
```

- **Origin field.** It looks like an input on purpose (the address is one tap away without an address
  bar taking space):
  - a pill-shaped container on `surfaceContainerHighest`;
  - the security icon in its colour;
  - the host in `titleSmall`;
  - a trailing pencil.

  Tapping it opens the address editor (§3.3). Long-pressing copies the link.
- **Navigation capsule.** Five 48dp icon buttons on `surfaceContainerHigh`, `CircleShape`. Back and
  forward dim when they can't be used. The star fills in `primary` when the page is pinned. Reload
  becomes Stop, with a progress ring, while loading.
- **Tiles.** 72dp-tall rounded (16dp) cards on `surfaceContainer`, each with an icon and a two-line
  `labelMedium` label. Toggle tiles (desktop site, text size open) switch to `secondaryContainer` with
  a check badge.
- **Privacy card.** Grouped rows on one rounded container:
  - each row has a leading icon in a 36dp tinted circle, a title, and a supporting line that states the
    consequence;
  - a trailing switch, or a chevron for navigation;
  - site settings summarise camera / mic / location decisions inline.
- **Developer row.** The console with an error badge (`errorContainer` pill) and a switch. It's the
  only developer item, so it gets no heading.
- **Sandboxed apps** use the same frame:
  - the origin field reads "Sandboxed app" with a shield and doesn't edit;
  - the capsule is reload + star;
  - the tiles are find and text size;
  - "What it can access" joins the privacy card.

### 3.3 Address editor (first-class input)

Replaces the origin field inside the pill:
- A 56dp pill field with the security/search icon leading, the URL selected, and trailing clear (✕)
  and Go (a filled `primary` circle with an arrow). The keyboard is URI type with IME Go.
- Below it:
  - a **Paste and go** chip when the clipboard holds a URL;
  - then up to five suggestions (favorites first, then history, from `OmniboxSuggestions`), each with a
    monogram, the title and the URL;
  - each suggestion has a trailing ↖ that fills its URL into the field without going.
- Back or tapping outside collapses it to the origin field.

### 3.4 Bottom: find in page and console

- **Find pill.** A floating 56dp capsule, 12dp above the bottom edge, with shadow. It holds:
  - a search icon;
  - the field with placeholder "Find in page";
  - a match chip ("3 / 12", tonal; "No matches" in `errorContainer`);
  - up/down, a divider, then close.

  The IME Search action jumps to the next match.
- **Console sheet.** A drag handle, then a title with filter chips "All 42 · Errors 3 · Warnings 5",
  then Copy and Clear. Log rows have:
  - a 3dp level-coloured stripe;
  - the message in monospace;
  - `file:line` muted on the right;
  - long-press to copy.

  Errors show first when the Errors chip is on.

### 3.5 Permission prompt

A card sheet:
- the origin field (read-only) on top;
- 48dp tinted icon circles for each thing asked (camera / mic / location);
- a title in plain words ("Use your camera and microphone?") and one body line;
- when the site is on Tor and asks for camera or mic, a muted note: "Calls can reveal your IP address
  even over Tor".

Three stacked full-width buttons: **Allow while visiting** (filled, remembered), **Only this time**
(tonal, not remembered), **Don't allow** (text, remembered).

### 3.6 JS dialogs

A card with:
- the origin field as its header (so a page can't spoof Amethyst UI);
- the message in `bodyLarge`, scrolling past 40% of the screen;
- for `prompt`, an `OutlinedTextField` with a label, a clear button, autofocus, and IME Done that
  submits;
- a **"Don't let this page show more dialogs"** checkbox (second dialog onward);
- right-aligned Cancel / OK. "Leave site?" uses a destructive-tinted Leave.

### 3.7 Page info

A sheet with:
- **Header:** monogram, host, and the full origin.
- **Connection:** rows with icons: encryption state (a warning tone for HTTP), then the route (Tor or
  open web, with the same consequence line as the pill), then the certificate: issued to, issued by,
  and expiry.
- **Permissions:** one row per camera / mic / location with a segmented button **Ask · Allow ·
  Block**, editable in place.
- **Cookies and site data:** an outlined destructive button, "Clear site data". The first tap turns it
  into an inline confirmation ("Sign out of this site and delete its data? · Cancel · Clear") instead
  of acting at once.

## 4. Tokens

| Use | Token |
|---|---|
| Pill surface | `surface`, 0 tonal elevation, 6dp shadow, 24dp bottom corners |
| Inputs, origin field | `surfaceContainerHighest`, `CircleShape` |
| Navigation capsule | `surfaceContainerHigh`, `CircleShape` |
| Tiles, grouped cards | `surfaceContainer`, 16dp |
| Toggle "on" | `secondaryContainer` / `onSecondaryContainer` |
| Insecure | `error` / `errorContainer` |
| Tor accent | `tertiary` (no new colour, readable in both themes) |
| Spacing | 4dp grid, 16dp sheet padding, 8dp between tiles |
| Type | title `titleMedium`, origin `titleSmall`, tiles `labelMedium`, supporting `bodySmall` |

## 5. What shipped

Every browser surface now draws the components in `commonsUI/…/browser/ui/pill/`; the hand-built chrome
is gone.

- **Embedded tabs** (`EmbeddedTabLayer`): `EmbeddedTabChrome` now carries a `BrowserPillUi` and one
  `onEvent(BrowserPillEvent)` callback. The layer draws `BrowserPill`, `FindInPagePill` and
  `ConsoleSheet`, and handles find and the console itself. The address editor gets suggestions from
  favorites and history (`OmniboxSuggestions`), and "Paste and go" checks only the clip's type.
- **Embedded page dialogs** (`WebAppScreen`): `PageDialogCard`, `PermissionPromptCard` and
  `PageInfoSheet` in Compose `Dialog`s. `MSG_PAGE_INFO` now sends the certificate fields
  (`KEY_CERT_ISSUED_TO` / `_BY` / `_VALID_UNTIL`) instead of a paragraph of text. Permissions in page
  info are edited in place, straight into `WebSitePermissionRegistry`.
- **Full-screen windows** (`NappletBrowserActivity`, `NappletHostActivity`): `BrowserChromeHost` hosts
  the same composables in two `ComposeView`s over the page. The top view grows to fill the window only
  while the pill is open, so it can catch taps outside the pill; the bottom view holds find or the
  console. Dialogs, the permission prompt and page info are Compose `Dialog`s. The browser asks the
  broker for the site's decisions before showing page info. `:nappletHost` now applies the Compose
  compiler and links the same JetBrains Compose libraries (Apache-2.0) the app already ships.
- **Permissions:** Allow while visiting (remembered), Only this time (granted, not remembered), and
  Don't allow (remembered). Dismissing the prompt denies the request once and remembers nothing.
- **Clear site data** asks for confirmation inline, in both surfaces.
- **Removed:** `NappletControlSheet`, `BrowserFindBar`, `NappletConsolePanel`, `BrowserJsDialogs`,
  `BrowserChromeLabels`, `TopControlSheet`, `EmbeddedFindBar`, `BottomConsoleSheet`,
  `EmbeddedPageDialogs`, `ConsoleLogEntry` and `BrowserWebTools.pageInfo`, plus the Android strings only
  they used.

**Sandboxed apps, embedded and full screen alike:**
- An embedded nSite is labelled as an nSite, not "Sandboxed app", and gets the Tor row. Switching the
  route saves it in `NsiteNetworkRegistry` and rebuilds the session, as the full-screen host relaunches.
- "What it can access" is `AccessInfoSheet`: the launch capabilities, the keys-stay-in-Amethyst row, the
  route (nSites), and Manage permissions. It replaces both the platform `AlertDialog` and the embedded
  tab's `AccessDialog`.
- The embedded tab has find, text size and the console. `NappletEmbedContract` gained `MSG_FIND`,
  `MSG_FIND_NEXT`, `MSG_FIND_RESULT`, `MSG_SET_TEXT_ZOOM` and `MSG_CONSOLE_LOG`, which
  `NappletHostService` serves the same way `NappletBrowserService` does. Load and HTTP errors show up as
  console errors, as they do full screen.
