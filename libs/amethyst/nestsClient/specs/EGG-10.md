# EGG-10: Theming

`status: draft`
`requires: EGG-01`
`category: decorative`

## Summary

A host MAY skin their room with custom colors, font, and a background
image. Theme tags are added to the existing `kind:30312` event; clients
without theme support ignore them and render with their default theme.

This EGG is decorative: theming MUST NOT affect protocol semantics
(joining, audio, presence, chat). A non-themed renderer MUST NOT show
fewer features than a themed one.

## Wire format

Three optional tags on `kind:30312`:

```json
["c", "<hex color>", "background" | "text" | "primary"]
["f", "<font family>", "<optional font URL>"]
["bg", "<background image URL>", "cover" | "tile"]
```

### Color (`c`)

- The hex value is six hex digits (`#RRGGBB`); a leading `#` is OPTIONAL.
  Three-digit shorthand (`#abc`) is NOT supported.
- The third element selects the role: `background`, `text`, or `primary`.
- A room MAY include up to one `c` tag per role. Extras MUST be ignored
  by receivers (palette fallbacks are reserved for a future EGG).

### Font (`f`)

- The family name is a free-form string. Receivers SHOULD match the
  four CSS-style generic names case-insensitively: `sans-serif`,
  `serif`, `monospace`, `cursive`. Other names fall through to the
  platform default unless the renderer also supports the URL.
- The URL element is OPTIONAL. When present, it MUST be a fully-qualified
  HTTPS URL pointing at a single-style font file (TTF/OTF/WOFF2). HTTP
  URLs MUST be rejected (mixed-content + integrity concerns).

### Background (`bg`)

- The URL is a fully-qualified HTTPS URL pointing at an image (PNG/JPEG/
  WEBP).
- The mode is `cover` (scale to fill, crop overflow) or `tile` (repeat
  on both axes). Unknown modes MUST fall back to `cover`.
- Hosts SHOULD keep the asset under 1 MB and 4096 px on its longest
  edge. Receivers MUST cap any asset they fetch at 8 MB and 8192 px on
  its longest edge; over-cap assets MUST be discarded with a fall-through
  to the `background` color so a malicious host cannot OOM clients.

## Behavior

1. Theme tags are PARSED ONLY by clients with rendering support. Clients
   without that support MUST ignore them silently.
2. Color values MUST be parsed strictly: `^#?[0-9a-fA-F]{6}$`. Anything
   else MUST result in the role falling back to the platform default,
   not in a render error.
3. The `text` color is the foreground for room name, summary, and chat
   bodies. The `background` color paints behind everything except the
   `bg` image (image overlays the color). The `primary` color drives
   accent surfaces (active-speaker ring, send button).
4. Font URL fetching MUST be opt-in to the renderer's image / asset
   pipeline (Tor or proxy if so configured). Renderers MUST cache the
   fetched font on disk keyed by URL hash; the same URL MUST NOT be
   re-fetched in the same session.
5. Background image fetching MUST go through the same channel as
   inline images (typically the renderer's image cache).
6. Themed renderers MUST tolerate a TIME-OF-FETCH gap: the room screen
   opens with platform defaults, font / background swap in once
   loaded. There MUST be no flash-of-blank-screen.
7. A room theme MUST NOT alter element positions, sizes, or behavior.
   This EGG controls colors, font family, and a background image — no
   layout primitives.

## Example

```json
{
  "kind": 30312,
  "pubkey": "abc...host",
  "tags": [
    ["d", "purple-room"],
    ["room", "Purple"],
    ["status", "open"],
    ["service", "..."],
    ["endpoint", "..."],
    ["p", "abc...host", "wss://relay", "host"],
    ["c", "#1a0033", "background"],
    ["c", "#FFFFFF", "text"],
    ["c", "#a020f0", "primary"],
    ["f", "Inter", "https://fonts.example/inter.woff2"],
    ["bg", "https://example.com/stars.png", "tile"]
  ],
  "content": "",
  "...": "..."
}
```

## Compatibility

A non-themed receiver renders the room without any of the cosmetic
overrides. A themed receiver that fails to fetch one of the assets
(font / background) MUST render the rest of the theme and the room is
still fully functional.

Future EGGs MAY introduce per-user profile themes (e.g. `kind:16767`)
and shared theme references (e.g. `kind:36767` Ditto themes). Those
specs MUST be additive: a theme defined on the room itself overrides
any per-user or shared theme.
