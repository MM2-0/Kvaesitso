# commons/ui/richtext — shared rich-text rendering contract

A **prototype** of one cross-platform rich-text renderer that Amethyst Android
(touch) and Amethyst Desktop (mouse-first) can both drive, replacing the two
current forks (`amethyst/ui/components/RichTextViewer.kt` ~1031 LOC and
`desktopApp/…/DesktopRichTextViewer.kt` ~745 LOC).

## What lives here

- **`RichTextViewer.kt`** — the shared skeleton. Owns everything identical on
  every front end: paragraph splitting, RTL, FlowRow word layout, plain text,
  inline custom emoji, and hashtags. Takes an already-parsed
  `RichTextViewerState` (the parser is pure and already in `commons/richtext`);
  it holds **no** account, nav, or cache handle.
- **`RichTextSegmentRenderer.kt`** — the seam. Two things the host provides:
  - `RichTextSegmentRenderer` (via `LocalRichTextSegmentRenderer`) — one method
    per **platform-divergent** segment (media, equation, quoted event, user
    mention, payment, link preview, relay/invite chip, secret message). The
    platform owns both the **visual** and the **call-to-action** for these.
  - `RichTextInteractions` (via `LocalRichTextInteractions`) — plain callbacks
    for segments whose **action is universal** and only the trigger styling
    differs (open URL/email/phone, jump to hashtag).

## Why this split (and not desktop's callback-only bag)

The divergent segments differ in *two* ways at once between touch and mouse: the
**presentation** (a tap-to-zoom media pager vs. an inline image that opens in a
window) *and* the **call-to-action** (a bottom sheet vs. a popover; navigate on
tap vs. a hover-card). A callbacks-only contract assumes shared rendering + a
different click handler — which isn't true here — so the platform must own the
whole rendering of those segments. Hence a renderer strategy, not just callbacks.

This is the same idiom the codebase already uses for inline quotes
(`LocalInlineQuoteRenderer`), generalised to every divergent segment: a
CompositionLocal set at the shell, read deep in the recursive tree, re-providable
per subtree — no parameter threading through 55+ call sites.

**Feature parity, not presentation parity.** Desktop being simpler today is a
gap, not the design; it is expected to cover the same range over time, its own
mouse-first way. Every method has a plain-text default (`PlainTextSegmentRenderer`)
so an unimplemented kind degrades to readable text and the core stays usable from
previews, `commonTest`, and headless callers.

## How each front end plugs in (next steps — not in this prototype)

- **Android** (`amethyst`): implement `RichTextSegmentRenderer` by wrapping the
  existing leaf composables (`ZoomableContentView`, `LoadUrlPreview`,
  `CashuPreview`, `MayBeInvoicePreview`, `LatexEquation`, `BechLink` →
  `LocalInlineQuoteRenderer`, …), closing over `AccountViewModel`/`INav`. The
  Android `RichTextViewer` becomes a thin wrapper that parses the content, then
  provides the two CompositionLocals and calls this shared core — keeping its
  current signature so no call site changes. The flavor-specific
  `TranslatableRichTextViewer` stays native and feeds the final string in.
- **Desktop** (`desktopApp`): implement the strategy with `AsyncImage` +
  window-open for media, `RenderMarkdown` for markdown, popovers for payments,
  hover-cards for mentions — then delete `DesktopRichTextViewer`.

## Status

Compiles in `:commons` (`compileCommonMainKotlinMetadata`). No consumer is wired
yet — this is the contract + skeleton for review before the per-platform
implementations and the fork deletions land. Known follow-ups: share the custom
emoji **icon** table (hashtag icons) and a `CreateTextWithEmoji` equivalent;
unify the two `CachedRichTextParser` forks so callers don't each parse.
