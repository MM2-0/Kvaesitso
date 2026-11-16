# EGG-06: Reactions (`kind:7`)

`status: draft`
`requires: EGG-01`
`category: optional`

## Summary

Audience-side ephemeral feedback (👏 ❤️ 🎉 ...) is carried on standard
NIP-25 `kind:7` reaction events, scoped to the room via an `a`-tag.
Clients render these as a short-lived floating overlay over the
participant grid.

A reaction is NOT durable state. Clients SHOULD NOT page through
historical reactions; only the recent window matters.

## Wire format

```json
{
  "kind": 7,
  "pubkey": "<reactor pubkey hex>",
  "tags": [
    ["a", "30312:<host pubkey hex>:<room d>", "<relay hint>"],
    ["p", "<target pubkey hex>"]?,
    ["emoji", "<shortcode>", "<image url>"]?
  ],
  "content": "<emoji glyph or :shortcode:>",
  ...
}
```

`content` carries the reaction itself. Standard Unicode emoji glyphs are
used directly. Custom emoji follow NIP-30: `content = ":shortcode:"` with
a sibling `["emoji", shortcode, url]` tag.

A reaction MAY include a `["p", target]` tag scoping the reaction to a
specific speaker (renders over their avatar). With no `p` tag, the reaction
is room-wide (renders centered on the room canvas).

## Behavior

1. Reactors MUST emit `kind:7` events with at least one `a`-tag matching
   EGG-01 and a non-empty `content` field.
2. Receivers MUST apply a 30-second sliding window measured against the
   reaction's `created_at`: a reaction is rendered from the moment it is
   observed until `created_at + 30 s` (wall-clock unix seconds), then
   dropped from the floating overlay. A reaction that arrives already
   older than 30 s MUST be dropped on receipt without ever being shown.
3. Receivers SHOULD throttle their reaction-overlay updates to at most 4 Hz
   (250 ms minimum between repaints). At ~30 reactions per second a naive
   recomposition can starve the audio decoder.
4. Receivers MUST tolerate unknown `content` values (e.g. a Unicode glyph
   without a font fallback) by falling back to a generic indicator rather
   than dropping the reaction.
5. Receivers MUST NOT auto-fetch the `["emoji", _, url]` image at the
   moment of receipt — that opens an animated-emoji-driven DDoS vector.
   Image fetch SHOULD be batched and rate-limited.
6. A reactor MAY include the same `a`-tagged reaction more than once
   (e.g. spamming claps). Receivers MUST NOT de-duplicate by content
   alone; events with different ids are always distinct.

## Example

A clap reaction targeted at a speaker:

```json
{
  "kind": 7,
  "pubkey": "ghi...audience",
  "created_at": 1714003220,
  "tags": [
    ["a", "30312:abchost:office-hours-2026-04"],
    ["p", "def...co"]
  ],
  "content": "👏",
  "id": "...",
  "sig": "..."
}
```

A custom-emoji reaction (NIP-30):

```json
{
  "kind": 7,
  "pubkey": "ghi...audience",
  "created_at": 1714003221,
  "tags": [
    ["a", "30312:abchost:office-hours-2026-04"],
    ["emoji", "amethyst", "https://example/amethyst.png"]
  ],
  "content": ":amethyst:",
  "id": "...",
  "sig": "..."
}
```

## Compatibility

EGG-06 is independent from EGG-05 (chat). A peer MAY implement either,
both, or neither without affecting interop on EGG-01 through EGG-04.

Future EGGs MAY introduce paid reactions (`kind:9735` zap receipts at the
same `a`-tag). Implementers SHOULD be prepared to source the reaction
overlay from both `kind:7` and `kind:9735` streams.
