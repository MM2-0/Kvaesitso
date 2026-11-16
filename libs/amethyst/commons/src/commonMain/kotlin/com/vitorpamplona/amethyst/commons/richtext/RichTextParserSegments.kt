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
package com.vitorpamplona.amethyst.commons.richtext

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.quartz.experimental.clink.pointers.NOffer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

@Immutable
class RichTextViewerState(
    val urlSet: Urls,
    val mediaForPager: ImmutableMap<String, MediaUrlContent>,
    val mediaList: ImmutableList<MediaUrlContent>,
    val customEmoji: ImmutableMap<String, String>,
    val paragraphs: ImmutableList<ParagraphState>,
    val tags: ImmutableListOfLists<String>,
)

@Immutable
open class ParagraphState(
    val words: ImmutableList<Segment>,
    val isRTL: Boolean,
)

@Immutable
class ImageGalleryParagraph(
    words: ImmutableList<Segment>,
    isRTL: Boolean,
) : ParagraphState(words, isRTL)

@Immutable
open class Segment(
    val segmentText: String,
)

@Immutable
class ImageSegment(
    segment: String,
) : Segment(segment)

@Immutable
class VideoSegment(
    segment: String,
) : Segment(segment)

@Immutable
class PdfSegment(
    segment: String,
) : Segment(segment)

@Immutable
class LinkSegment(
    segment: String,
) : Segment(segment)

/** A webxdc app attached to the post: drawn as the app's card, not as a link. */
@Immutable
class WebxdcSegment(
    segment: String,
    val app: WebxdcApp,
    /** The post's `nostr:` URI, which names the app instance and its poster. */
    val postUri: String?,
) : Segment(segment)

@Immutable
class EmojiSegment(
    segment: String,
) : Segment(segment)

@Immutable
class InvoiceSegment(
    segment: String,
) : Segment(segment)

@Immutable
class WithdrawSegment(
    segment: String,
) : Segment(segment)

@Immutable
class CashuSegment(
    segment: String,
) : Segment(segment)

@Immutable
class ClinkOfferSegment(
    segment: String,
    val offer: NOffer,
) : Segment(segment)

@Immutable
class EmailSegment(
    segment: String,
) : Segment(segment)

class SecretEmoji(
    segment: String,
) : Segment(segment)

@Immutable
class PhoneSegment(
    segment: String,
) : Segment(segment)

@Immutable
class BechSegment(
    segment: String,
) : Segment(segment)

@Immutable
class Base64Segment(
    segment: String,
) : Segment(segment)

open class HashIndexSegment(
    segment: String,
    val hex: String,
    val extras: String?,
) : Segment(segment)

@Immutable
class HashIndexUserSegment(
    segment: String,
    hex: String,
    extras: String?,
) : HashIndexSegment(segment, hex, extras)

@Immutable
class HashIndexEventSegment(
    segment: String,
    hex: String,
    extras: String?,
) : HashIndexSegment(segment, hex, extras)

@Immutable
class HashTagSegment(
    segment: String,
    val hashtag: String,
    val extras: String?,
) : Segment(segment)

/** A mention of an official NIP (`NIP-44`), opened in the reader; [extras] is punctuation glued after it. */
@Immutable
class NipMentionSegment(
    segment: String,
    val number: String,
    val extras: String?,
) : Segment(segment)

@Immutable
class RelayUrlSegment(
    segment: String,
) : Segment(segment)

/**
 * A NIP-29 group invite link in the `<relay>'<groupId>[?code=<code>]` form (Wisp/0xchat).
 * Rendered as a tappable chip that opens the group; [segmentText] is the whole literal.
 */
@Immutable
class RelayGroupLinkSegment(
    segment: String,
) : Segment(segment)

/**
 * A Concord invite link (`…/invite/<naddr>#<fragment>`). Rendered as a tappable
 * chip that opens the redeem flow; [segmentText] is the whole literal (including
 * the URL fragment, which carries the unlock token and never hits a server).
 */
@Immutable
class ConcordInviteLinkSegment(
    segment: String,
) : Segment(segment)

/**
 * A Buzz workspace invite link (`https://<host>/invite/<token>`). Rendered as a tappable
 * chip that opens the in-app join browser instead of the external browser, so the Buzz web
 * app can sign the NIP-98 claim with the user's key via the injected `window.nostr`.
 */
@Immutable
class BuzzInviteLinkSegment(
    segment: String,
) : Segment(segment)

@Immutable
class BlossomUriSegment(
    segment: String,
) : Segment(segment)

@Immutable
class SchemelessUrlSegment(
    segment: String,
) : Segment(segment)

@Immutable
class NowhereLinkSegment(
    segment: String,
    val host: String,
    val tool: String?,
) : Segment(segment)

@Immutable
class RegularTextSegment(
    segment: String,
) : Segment(segment)

/**
 * A LaTeX math span delimited by `$...$` (inline) or `$$...$$` (display).
 *
 * [segmentText] keeps the original text *including* the `$` delimiters so the
 * raw form can be shown as a fallback when rendering fails, while [latex] holds
 * just the inner formula that gets handed to the math renderer. [leading] is any
 * opening punctuation glued before the opening `$` (e.g. `(` in `($x$)`) and
 * [trailing] any punctuation glued after the closing `$` (e.g. `.` in `$x$.`),
 * both rendered right next to the equation so they don't drift off behind a space.
 */
@Immutable
class MathSegment(
    segment: String,
    val latex: String,
    val displayMode: Boolean,
    val leading: String = "",
    val trailing: String = "",
) : Segment(segment)

/**
 * A word holding one or more `<t:SECONDS[:STYLE]>` local-time tokens
 * ([LocalTimestamp]), with any text glued around them. [segmentText] keeps the
 * raw word, so a renderer that does not know the segment still shows it as typed.
 */
@Immutable
class TimestampSegment(
    segment: String,
    val pieces: List<LocalTimestamp.Piece>,
) : Segment(segment)
