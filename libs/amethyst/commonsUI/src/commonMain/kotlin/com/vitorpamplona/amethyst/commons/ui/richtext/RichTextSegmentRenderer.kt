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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment

/**
 * The seam that lets one shared [RichTextViewer] serve every front end.
 *
 * ## Why a strategy and not a callback bag
 *
 * The parse → paragraph/word-layout → plain-text/emoji/hashtag rendering is
 * *identical* on every platform, so the shared core owns it outright. But a
 * handful of segment kinds — media, embedded notes, mentions, payments, link
 * unfurls, LaTeX — differ between a **touch** front end (Amethyst Android) and a
 * **mouse-first** one (Desktop). They differ in *two* ways at once:
 *
 * - **Presentation.** A phone shows an image in a full-bleed, tap-to-zoom pager;
 *   a desktop shows it inline with a hover affordance and opens it in a window.
 * - **Call-to-action.** A phone opens a lightning invoice in a bottom sheet; a
 *   desktop opens a popover. A phone navigates on a mention tap; a desktop may
 *   show a hover-card first.
 *
 * Because *both* the visual and the interaction diverge (not just the click
 * handler), a callback-only contract is not enough — the platform has to own the
 * whole rendering of these segments. So each divergent kind is a method here, and
 * the platform provides an implementation via [LocalRichTextSegmentRenderer].
 *
 * This is the same idiom the codebase already uses for inline quotes
 * (`LocalInlineQuoteRenderer`), generalised to every platform-divergent segment:
 * a [androidx.compose.runtime.CompositionLocal] set once at the app shell, read
 * deep inside the recursive render tree, and re-providable per subtree (e.g. chat
 * bubbles vs. the feed) without threading a parameter through every call site.
 *
 * ## Feature parity, not presentation parity
 *
 * A platform is expected to *cover the same range* of segments over time — the
 * Desktop being simpler today is a gap, not the design. What it is **not**
 * expected to do is render them the same way. Every method has a plain-text
 * default (see [PlainTextSegmentRenderer]) so an unimplemented kind degrades to
 * readable text rather than vanishing, which also keeps the core usable from
 * tests, previews, and headless callers.
 *
 * Every method receives a [Modifier] the core already aligned for RTL; draw into
 * it. `quotesLeft` is the remaining recursion budget for embedded content — a
 * renderer that recurses back into [RichTextViewer] must decrement it.
 */
@Stable
interface RichTextSegmentRenderer {
    /** A single image / video / pdf / base64 / blossom-uri media word. */
    @Composable
    fun Media(
        segment: Segment,
        state: RichTextViewerState,
        modifier: Modifier,
    )

    /** A whole paragraph that is nothing but images — laid out as a grid/gallery. */
    @Composable
    fun Gallery(
        paragraph: ImageGalleryParagraph,
        state: RichTextViewerState,
        modifier: Modifier,
    )

    /** An inline/display LaTeX equation (platform math renderer). */
    @Composable
    fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    )

    /** A bare `nostr:` bech entity whose kind (user vs event) the renderer resolves. */
    @Composable
    fun NostrEntity(
        bech: String,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    )

    /** A `#[i]`-style event mention resolved to [eventHex]; renders the quoted note. */
    @Composable
    fun QuotedEvent(
        eventHex: String,
        addedChars: String?,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    )

    /** A `#[i]`-style user mention resolved to [userHex]. */
    @Composable
    fun UserMention(
        userHex: String,
        addedChars: String?,
        modifier: Modifier,
    )

    /** A payable token: lightning invoice, LNURL-withdraw, Cashu token, or Clink offer. */
    @Composable
    fun Payment(
        segment: Segment,
        modifier: Modifier,
    )

    /** An external link that may unfurl into a preview card (touch) or hover card (mouse). */
    @Composable
    fun LinkPreview(
        url: String,
        modifier: Modifier,
    )

    /**
     * A plain external link rendered inline (no preview): the no-preview [LinkPreview]
     * fallback and schemeless URLs. [displayText] is what the user sees; [url] is where
     * it opens. Each front end styles the link and owns how it opens.
     */
    @Composable
    fun Url(
        url: String,
        displayText: String,
        modifier: Modifier,
    )

    /** An email address. Each front end styles it and owns the compose/open action. */
    @Composable
    fun Email(
        address: String,
        modifier: Modifier,
    )

    /** A phone number. Each front end styles it and owns the dial action. */
    @Composable
    fun Phone(
        number: String,
        modifier: Modifier,
    )

    /** A relay URL, NIP-29 group invite, or Concord invite chip. */
    @Composable
    fun RelayLink(
        segment: Segment,
        modifier: Modifier,
    )

    /** A "nowhere.ink" ephemeral-tool link — a card when previewing, a link otherwise. */
    @Composable
    fun NowhereLink(
        segment: NowhereLinkSegment,
        canPreview: Boolean,
        modifier: Modifier,
    )

    /** A NIP-C0 secret-emoji span that expands into its own decoded rich-text message. */
    @Composable
    fun SecretMessage(
        segment: SecretEmoji,
        state: RichTextViewerState,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    )
}

/**
 * Activations for the segments the shared core renders itself. Only hashtags
 * qualify: the core draws the chip (with the shared inline icon) identically on
 * every platform, and only the navigation target differs — a pure callback. Links,
 * mail, phone, mentions, etc. render *and* act per-platform, so they live on
 * [RichTextSegmentRenderer], not here.
 */
@Immutable
data class RichTextInteractions(
    val onClickHashtag: (hashtag: String) -> Unit = {},
    /** A `NIP-44` mention: [number] is the two-character NIP number. */
    val onClickNip: (number: String) -> Unit = {},
)

/**
 * The default: render every divergent segment as its raw text. Safe for previews,
 * `commonTest`, and headless callers; a real front end replaces it wholesale.
 */
object PlainTextSegmentRenderer : RichTextSegmentRenderer {
    @Composable
    override fun Media(
        segment: Segment,
        state: RichTextViewerState,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)

    @Composable
    override fun Gallery(
        paragraph: ImageGalleryParagraph,
        state: RichTextViewerState,
        modifier: Modifier,
    ) = Text(paragraph.words.joinToString(" ") { it.segmentText }, modifier)

    @Composable
    override fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)

    @Composable
    override fun NostrEntity(
        bech: String,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = Text(bech, modifier)

    @Composable
    override fun QuotedEvent(
        eventHex: String,
        addedChars: String?,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = Text(addedChars?.let { "$eventHex$it" } ?: eventHex, modifier)

    @Composable
    override fun UserMention(
        userHex: String,
        addedChars: String?,
        modifier: Modifier,
    ) = Text(addedChars?.let { "$userHex$it" } ?: userHex, modifier)

    @Composable
    override fun Payment(
        segment: Segment,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)

    @Composable
    override fun LinkPreview(
        url: String,
        modifier: Modifier,
    ) = Text(url, modifier)

    @Composable
    override fun Url(
        url: String,
        displayText: String,
        modifier: Modifier,
    ) = Text(displayText, modifier)

    @Composable
    override fun Email(
        address: String,
        modifier: Modifier,
    ) = Text(address, modifier)

    @Composable
    override fun Phone(
        number: String,
        modifier: Modifier,
    ) = Text(number, modifier)

    @Composable
    override fun RelayLink(
        segment: Segment,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)

    @Composable
    override fun NowhereLink(
        segment: NowhereLinkSegment,
        canPreview: Boolean,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)

    @Composable
    override fun SecretMessage(
        segment: SecretEmoji,
        state: RichTextViewerState,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = Text(segment.segmentText, modifier)
}

/**
 * Deep-tree seam for platform-divergent segment rendering. Uses [compositionLocalOf]
 * (not static) so a subtree can re-provide a variant — e.g. a compact renderer in a
 * preview card — and only the readers under it recompose.
 */
val LocalRichTextSegmentRenderer =
    compositionLocalOf<RichTextSegmentRenderer> { PlainTextSegmentRenderer }

/** Universal activations for core-rendered segments. Static: it changes at the shell, rarely below. */
val LocalRichTextInteractions =
    staticCompositionLocalOf { RichTextInteractions() }
