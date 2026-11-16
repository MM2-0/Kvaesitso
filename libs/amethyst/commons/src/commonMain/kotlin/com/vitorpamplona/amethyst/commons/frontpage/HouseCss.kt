/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.frontpage

/**
 * The house stylesheet the writer inlines, as given. The paper is always light.
 *
 * Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer), as
 * corrected for a client harness by Brainstorm's port (client/src/lib/frontPage/house.css). Kept
 * as a constant because commons has no resources; edit it there and copy it here.
 */
internal const val HOUSE_CSS: String =
    """
/* Ported from NosFabrica/the-nostr-observer @ 0901b92 (MIT, see ./LICENSE.observer):
   .claude/skills/nostr-observer/reference/house.css. Its two dark-theme blocks are
   removed and `color-scheme: light` added: the paper is always newsprint (the
   skill's hard rule 11), and inside the reader's frame prefers-color-scheme would
   otherwise follow their OS. */
/* The Nostr Observer — house stylesheet.
 *
 * Tokens and primitives, not a layout. The generator composes the page out of
 * these each morning; the point of the file is that it never has to reinvent a
 * palette, a type scale, or the dark-theme handling.
 *
 * THEMES: the original redefined these tokens for a dark theme in two more
 * blocks. Both are removed in this port; the paper is light only.
 */

:root {
  color-scheme: light;
  --paper: #faf8f3;
  --paper-2: #f2efe8;
  --ink: #16121b;
  --ink-2: #4c4553;
  --ink-3: #6e6675;
  --rule: #c8c1cc;
  --rule-soft: #e0dae2;
  --accent: #57277f;
  --spot: #9c2b24;
  --reverse-bg: #16121b;
  --reverse-ink: #f5f1ec;
  --reverse-ink-2: #a79db0;
  --reverse-spot: #e38375;

  --serif: "Times New Roman", Times, "Liberation Serif", "Nimbus Roman", serif;
  --body: Georgia, "Liberation Serif", "Times New Roman", Times, serif;
  --util: "Helvetica Neue", Helvetica, Arial, "Liberation Sans", sans-serif;
  --mono: ui-monospace, "SF Mono", Menlo, Consolas, "Liberation Mono", monospace;
}

* {
  box-sizing: border-box;
}

/* An explicit background: the viewer paints its own ground behind the page, so
 * a transparent body silently borrows the host's theme. */
body {
  margin: 0;
  background: var(--paper);
  color: var(--ink);
  font-family: var(--body);
  font-size: 16px;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

/* THE FOLIO GOES ON TOP, wherever the writer put it.
 *
 * A folio is the thin line of standing detail a newspaper sets ABOVE its
 * nameplate -- volume and number, the date, the price. Printed below it
 * instead, it stops being a folio and becomes a block of metadata standing
 * between the reader and the first headline, and on a phone that block is
 * eight lines deep and pushes the lead off the screen.
 *
 * The prompt asks for it on top, but placement is markup and the prompt is
 * advice; one edition put it there and the next did not. `order: -1` settles
 * it in the stylesheet, and costs nothing on an edition that already had it
 * right. */
.sheet {
  max-width: 1240px;
  margin: 0 auto;
  padding: 20px 22px 72px;
  display: flex;
  flex-direction: column;
}
.sheet > .folio {
  order: -1;
}

/* --- type ------------------------------------------------------------- */

h1,
h2,
h3,
h4 {
  font-family: var(--serif);
  font-weight: 700;
  margin: 0;
  line-height: 1.06;
  text-wrap: balance;
}
p {
  margin: 0;
}
a {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid var(--rule);
}
a:hover {
  color: var(--accent);
  border-bottom-color: var(--accent);
}
a:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.kicker {
  font-family: var(--util);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--accent);
}
.byline {
  font-family: var(--util);
  font-size: 0.72rem;
  letter-spacing: 0.11em;
  text-transform: uppercase;
  color: var(--ink-3);
}
.dek {
  font-size: 0.95rem;
  line-height: 1.45;
  color: var(--ink-2);
  font-style: italic;
}
.note {
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--ink-3);
  font-style: italic;
}

.lead-head {
  font-size: clamp(1.9rem, 3.1vw, 2.7rem);
}
.main-head {
  font-size: clamp(1.6rem, 2.5vw, 2.15rem);
}
.sub-head {
  font-size: 1.28rem;
}
.small-head {
  font-size: 1.02rem;
}

/* --- masthead --------------------------------------------------------- */

.masthead {
  border-top: 5px solid var(--ink);
  border-bottom: 1px solid var(--ink);
  padding: 18px 0 12px;
  text-align: center;
}
.masthead h1 {
  font-size: clamp(2.3rem, 7.6vw, 5.1rem);
  letter-spacing: 0.015em;
  text-transform: uppercase;
  line-height: 0.92;
}
.masthead .the {
  font-size: 0.44em;
  display: block;
  letter-spacing: 0.42em;
  margin-bottom: 0.28em;
  text-indent: 0.42em;
  color: var(--ink-2);
}
.motto {
  margin-top: 14px;
  font-family: var(--util);
  font-size: 0.7rem;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: var(--ink-3);
}
/* Three columns, and the middle one is CENTRED on the page.
 *
 * `space-between` was the obvious way to set these rows and it is subtly the
 * wrong one: it centres the middle span only when the two beside it happen to
 * be the same width, so the date drifts left or right by whatever the window
 * stamp and the voice count weigh that morning. A `1fr auto 1fr` grid pins it
 * to the centre of the sheet, under the middle of the nameplate, whatever sits
 * either side -- including nothing at all, which is the case for the folio's
 * right-hand slot today. */
.folio,
.dateline {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 3px 12px;
  align-items: baseline;
  font-family: var(--util);
  font-size: 0.7rem;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--ink-3);
}
.folio > :nth-child(2),
.dateline > :nth-child(2) {
  text-align: center;
}
.folio > :nth-child(3),
.dateline > :nth-child(3) {
  text-align: right;
}

/* A row of two is centre-and-right, not left-and-centre.
 *
 * The folio's left slot is empty -- there is no volume number and no cover
 * price -- so the row carries two spans where the grid expects three, and by
 * source order they would land in columns one and two. Written this way, the
 * markup stays free of an empty `<span>` whose only job is to push the next
 * one along, and the date still sits under the middle of the nameplate. */
.folio > :first-child:nth-last-child(2) {
  grid-column: 2;
  text-align: center;
}
.folio > :nth-child(2):nth-last-child(1) {
  grid-column: 3;
  text-align: right;
}

/* COLLAPSE WHEN IT MUST, AND NOT ONE PIXEL SOONER.
 *
 * These rows used to stack below 720px along with the columns, because that is
 * where everything else on the page breaks. Measured, they had no need to: the
 * dateline's three spans hold one line down to 528px and the folio's two down
 * to 364px, so the shared breakpoint was stacking the dateline 190px early and
 * the folio 350px early -- three lines of standing detail on a screen with room
 * for one.
 *
 * So they get their own breakpoints, one each, set just under what they
 * measure. The folio's is below any phone in circulation, which is the point:
 * it is there for a 320px screen, not for a 390px one. Stacked, they centre --
 * the same symmetry the nameplate has, with the centred span still centred.
 */
/* Below the fold, the outer tracks stop paying for each other.
 *
 * `1fr auto 1fr` centres exactly, and it does it by making the right-hand track
 * as wide as the left -- so "234 voices", which needs 69px, reserves the 170px
 * that "Ranked as Vitor Pamplona" needs beside it. That is a hundred wasted
 * pixels, and it is why a row whose text measures 400px was breaking at 528.
 *
 * On a phone the page is one column and there is no nameplate centre to line up
 * against pixel-for-pixel, so the tracks size to their contents and the middle
 * span is centred in what is left. The row then holds one line to about 430px
 * instead of 528. Above this the exact version is back. */
@media (max-width: 720px) {
  .dateline {
    grid-template-columns: auto 1fr auto;
  }
}

@media (max-width: 430px) {
  .dateline {
    grid-template-columns: 1fr;
    text-align: center;
  }
  .dateline > :nth-child(3) {
    text-align: center;
  }
}

@media (max-width: 380px) {
  .folio {
    grid-template-columns: 1fr;
    text-align: center;
  }
  .folio > :first-child:nth-last-child(2),
  .folio > :nth-child(2):nth-last-child(1) {
    grid-column: 1;
    text-align: center;
  }
}

.folio {
  padding-bottom: 8px;
}
.dateline {
  border-bottom: 3px double var(--ink);
  padding: 7px 0;
  color: var(--ink-2);
}

/* --- columns ---------------------------------------------------------- */

.fold {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  border-bottom: 3px double var(--ink);
}
.col {
  padding: 20px;
  border-left: 1px solid var(--rule);
}
.col:first-child {
  padding-left: 0;
  border-left: 0;
}
.col:last-child {
  padding-right: 0;
}
/* Column children need air between them. Without this a stack of stories in one
 * column runs together and reads as a single item with several headlines. */
.col > * + * {
  margin-top: 18px;
}
.span-3 {
  grid-column: span 3;
}
.span-4 {
  grid-column: span 4;
}
.span-5 {
  grid-column: span 5;
}
.span-6 {
  grid-column: span 6;
}
.span-8 {
  grid-column: span 8;
}
.span-12 {
  grid-column: span 12;
}

.band {
  border-bottom: 1px solid var(--ink);
  padding: 22px 0 26px;
}
.band-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--ink);
  padding-bottom: 6px;
}
.band-head h2 {
  font-size: 1.05rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}
.cols2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
}
.cols3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
}
.cols4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}
.cell {
  padding: 0 18px;
  border-left: 1px solid var(--rule-soft);
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.cell:first-child {
  padding-left: 0;
  border-left: 0;
}
.cell:last-child {
  padding-right: 0;
}

/* --- stories ---------------------------------------------------------- */

.story {
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.story + .story {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--rule-soft);
}
.story p {
  font-size: 0.925rem;
  line-height: 1.52;
  hyphens: auto;
}
.story p.first::first-letter {
  float: left;
  font-family: var(--serif);
  font-weight: 700;
  font-size: 3.1em;
  line-height: 0.79;
  padding: 0.05em 0.09em 0 0;
  color: var(--accent);
}

/* --- art -------------------------------------------------------------- */

figure {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
figure img {
  width: 100%;
  height: auto;
  display: block;
  border: 1px solid var(--rule);
  background: var(--paper-2);
}
figcaption {
  font-style: italic;
  font-size: 0.82rem;
  line-height: 1.4;
  color: var(--ink-2);
}
figcaption .credit {
  font-family: var(--util);
  font-style: normal;
  font-size: 0.7rem;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--ink-3);
  display: block;
  margin-top: 4px;
}

/* --- boxes ------------------------------------------------------------ */

.box {
  border: 1px solid var(--ink);
  padding: 14px 15px;
  background: var(--paper-2);
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.box-head {
  font-family: var(--util);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--ink);
  padding-bottom: 7px;
}
.wire-item {
  padding: 11px 0;
  border-bottom: 1px dotted var(--rule);
}
.wire-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.wire-item p {
  font-size: 0.85rem;
  line-height: 1.45;
  color: var(--ink-2);
}

blockquote {
  margin: 0;
  font-family: var(--serif);
  font-size: 1.22rem;
  line-height: 1.24;
}
q {
  quotes: "“" "”";
}

/* Reverse panel — for the one thing on the page that should stop the reader.
 *
 * IT IS DARK IN BOTH THEMES, which is why it gets its own accent. `--spot` is
 * tuned against the PAGE's ground, so in the light theme it is a deep red for
 * cream paper — and printing that on the reverse panel's near-black put the
 * kicker at 2.45:1, on every story that had one. Measured, not noticed: it
 * reads as "small red caps" until you check it. */
.reverse {
  background: var(--reverse-bg);
  color: var(--reverse-ink);
  padding: 26px;
}
.reverse h2,
.reverse h3 {
  color: var(--reverse-ink);
}
.reverse .dek,
.reverse .byline {
  color: var(--reverse-ink-2);
}
.reverse .kicker {
  color: var(--reverse-spot);
}

/* --- data ------------------------------------------------------------- */

.tablewrap {
  overflow-x: auto;
}
table {
  border-collapse: collapse;
  width: 100%;
  font-variant-numeric: tabular-nums;
}
th,
td {
  text-align: left;
  padding: 7px 10px 7px 0;
  border-bottom: 1px dotted var(--rule);
  font-size: 0.83rem;
}
th {
  font-family: var(--util);
  font-size: 0.7rem;
  letter-spacing: 0.13em;
  text-transform: uppercase;
  color: var(--ink-3);
  border-bottom: 1px solid var(--ink);
}
td.num {
  text-align: right;
  padding-right: 0;
  font-family: var(--mono);
  font-size: 0.78rem;
}

/* --- responsive ------------------------------------------------------- */

@media (max-width: 1000px) {
  .span-3,
  .span-4,
  .span-5 {
    grid-column: span 6;
  }
  .cols4 {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 720px) {
  .sheet {
    padding: 14px 15px 56px;
  }
  .fold {
    display: flex;
    flex-direction: column;
  }
  .col {
    padding: 18px 0;
    border-left: 0;
    border-bottom: 1px solid var(--rule);
  }
  .col:last-child {
    border-bottom: 0;
  }
  .cols2,
  .cols3,
  .cols4 {
    display: flex;
    flex-direction: column;
  }
  .cell {
    padding: 16px 0;
    border-left: 0;
    border-top: 1px solid var(--rule-soft);
  }
  .cell:first-child {
    border-top: 0;
    padding-top: 0;
  }
  .reverse {
    padding: 20px 16px;
  }
}
"""
