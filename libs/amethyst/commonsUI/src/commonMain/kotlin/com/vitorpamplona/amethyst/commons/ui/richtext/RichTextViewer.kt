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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.richtext.Base64Segment
import com.vitorpamplona.amethyst.commons.richtext.BechSegment
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.BuzzInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.EmailSegment
import com.vitorpamplona.amethyst.commons.richtext.EmojiSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.ImageSegment
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.LinkSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.NipMentionSegment
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.PdfSegment
import com.vitorpamplona.amethyst.commons.richtext.PhoneSegment
import com.vitorpamplona.amethyst.commons.richtext.RegularTextSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SchemelessUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.TimestampSegment
import com.vitorpamplona.amethyst.commons.richtext.VideoSegment
import com.vitorpamplona.amethyst.commons.richtext.WebxdcSegment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.richtext.dropBlankLinesAroundBlocks
import com.vitorpamplona.amethyst.commons.richtext.isBlankLine
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.components.ProvideZoomSourceGroup
import com.vitorpamplona.amethyst.commons.ui.note.WebxdcPostCard

/**
 * How tall a blank line between paragraphs renders, as a fraction of a text line. 1 everywhere by
 * default. The open and compact chat layouts set it lower: there a message has no bubble edge, and
 * a full-height blank line inside one is as tall as the gap to the next message, so one message's
 * paragraphs read as separate messages.
 */
val LocalBlankLineScale = compositionLocalOf { 1f }

/**
 * Cross-platform rich-text renderer. Owns everything identical on every front
 * end — paragraph splitting, RTL, word layout, plain text, custom emoji, and
 * hashtags — and delegates every platform-divergent segment to the
 * [LocalRichTextSegmentRenderer] and universal activations to
 * [LocalRichTextInteractions]. See [RichTextSegmentRenderer] for the rationale.
 *
 * Callers pass an already-parsed [RichTextViewerState] (the parser is pure and
 * lives in `commons/richtext`), so this composable takes no account, navigation,
 * or cache handle of its own — those enter through the two CompositionLocals the
 * host provides.
 */
@Composable
fun RichTextViewer(
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier = Modifier,
    // Who wrote the text, when the note knows (a direct message's URI names its gift wrap's key).
    authorPubKey: String? = null,
) {
    val renderer = LocalRichTextSegmentRenderer.current
    val baseStyle = LocalTextStyle.current
    val paragraphStyle = remember(baseStyle) { baseStyle.copy(lineHeight = 1.3.em) }
    val paragraphs = remember(state, canPreview) { dropBlankLinesAroundBlocks(state.paragraphs, canPreview) }
    val blankLineScale = LocalBlankLineScale.current
    val density = LocalDensity.current
    val blankLineHeight =
        remember(paragraphStyle, blankLineScale, density) {
            val fontSize = if (paragraphStyle.fontSize.isSp) paragraphStyle.fontSize else 16.sp
            with(density) { (fontSize * 1.3f * blankLineScale).toDp() }
        }

    // Only this text's own media may run past its column; cards and quotes it embeds keep theirs in.
    val mediaBleed = LocalMediaBleed.current

    // One group for the note's media, so the viewer closes into whichever one it ends on.
    ProvideZoomSourceGroup {
        ProvideMediaBleed(0.dp) {
            Column(modifier) {
                paragraphs.forEach { paragraph ->
                    val align = if (paragraph.isRTL) Alignment.End else Alignment.Start
                    if (blankLineScale != 1f && paragraph.isBlankLine()) {
                        Spacer(Modifier.height(blankLineHeight))
                    } else if (paragraph is ImageGalleryParagraph) {
                        ProvideMediaBleed(mediaBleed) {
                            renderer.Gallery(paragraph, state, Modifier.align(align))
                        }
                    } else {
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (paragraph.isRTL) LayoutDirection.Rtl else LayoutDirection.Ltr,
                            LocalTextStyle provides paragraphStyle,
                        ) {
                            RenderParagraph(paragraph, state, canPreview, quotesLeft, mediaBleed, authorPubKey, Modifier.align(align))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderParagraph(
    paragraph: ParagraphState,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    mediaBleed: Dp,
    authorPubKey: String?,
    modifier: Modifier,
) {
    val spaceWidth = measureSpaceWidth(LocalTextStyle.current)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spaceWidth),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        paragraph.words.forEach { word ->
            RenderWord(word, state, canPreview, quotesLeft, mediaBleed, authorPubKey)
        }
    }
}

@Composable
private fun RenderWord(
    word: Segment,
    state: RichTextViewerState,
    canPreview: Boolean,
    quotesLeft: Int,
    mediaBleed: Dp,
    authorPubKey: String?,
) {
    val renderer = LocalRichTextSegmentRenderer.current
    val actions = LocalRichTextInteractions.current

    when (word) {
        is RegularTextSegment -> Text(word.segmentText)
        is EmojiSegment -> RenderCustomEmoji(word.segmentText, state.customEmoji)
        is HashTagSegment -> HashTagText(word) { actions.onClickHashtag(word.hashtag) }
        is NipMentionSegment -> NipMentionText(word) { actions.onClickNip(word.number) }

        // Presentation + CTA are platform-owned: each front end styles its own
        // clickable link/mail/phone and decides how to open it.
        is EmailSegment -> renderer.Email(word.segmentText, Modifier)
        is PhoneSegment -> renderer.Phone(word.segmentText, Modifier)

        // Divergent media — presentation and CTA are platform-owned.
        is ImageSegment, is VideoSegment, is PdfSegment, is Base64Segment, is BlossomUriSegment ->
            ProvideMediaBleed(mediaBleed) { renderer.Media(word, state, Modifier) }

        is MathSegment -> renderer.Equation(word, Modifier)

        // The post's author as the note knows it: a direct message's URI names its gift wrap's
        // throwaway key instead.
        is WebxdcSegment -> WebxdcPostCard(word.app, word.postUri, authorPubKey)

        is LinkSegment ->
            if (canPreview) {
                // A link that previews as media or a card runs edge to edge with the post's media.
                ProvideMediaBleed(mediaBleed) { renderer.LinkPreview(word.segmentText, Modifier) }
            } else {
                renderer.Url(word.segmentText, word.segmentText, Modifier)
            }

        is SchemelessUrlSegment ->
            renderer.Url("https://${word.segmentText}", word.segmentText, Modifier)
        is NowhereLinkSegment -> renderer.NowhereLink(word, canPreview, Modifier)

        is RelayUrlSegment, is RelayGroupLinkSegment, is ConcordInviteLinkSegment, is BuzzInviteLinkSegment ->
            renderer.RelayLink(word, Modifier)

        is InvoiceSegment, is WithdrawSegment, is CashuSegment, is ClinkOfferSegment ->
            renderer.Payment(word, Modifier)

        is HashIndexUserSegment -> renderer.UserMention(word.hex, word.extras, Modifier)
        is HashIndexEventSegment -> renderer.QuotedEvent(word.hex, word.extras, canPreview, quotesLeft, Modifier)
        is BechSegment -> renderer.NostrEntity(word.segmentText, canPreview, quotesLeft, Modifier)
        is SecretEmoji -> renderer.SecretMessage(word, state, canPreview, quotesLeft, Modifier)
        is TimestampSegment -> LocalTimestampText(word)

        // Unknown/other segments fall back to their raw text.
        else -> Text(word.segmentText)
    }
}

/** A `NIP-44` mention in the theme's primary color; glued punctuation keeps the text color. */
@Composable
private fun NipMentionText(
    segment: NipMentionSegment,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground
    val annotated =
        remember(segment.segmentText, primary, background) {
            buildAnnotatedString {
                val extras = segment.extras
                val mention = if (extras != null) segment.segmentText.removeSuffix(extras) else segment.segmentText
                withStyle(SpanStyle(color = primary)) { append(mention) }
                extras?.let { withStyle(SpanStyle(color = background)) { append(it) } }
            }
        }
    Text(text = annotated, modifier = Modifier.clickable(onClick = onClick))
}

private val HashtagIconPlaceholder =
    Placeholder(width = 17.sp, height = 17.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)

/**
 * A `#hashtag` chip in the theme's primary color, with the shared inline icon for
 * well-known tags (see [checkForHashtagWithIcon]). Any trailing punctuation glued
 * to the tag ([HashTagSegment.extras]) renders in the normal text color.
 */
@Composable
private fun HashTagText(
    segment: HashTagSegment,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground
    val hashtagIcon = remember(segment.hashtag) { checkForHashtagWithIcon(segment.hashtag) }

    val annotated =
        remember(segment.segmentText, primary, background) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) { append("#${segment.hashtag}") }
                if (hashtagIcon != null) {
                    withStyle(SpanStyle(color = primary)) { appendInlineContent("inlineContent", "[icon]") }
                }
                segment.extras?.let { withStyle(SpanStyle(color = background)) { append(it) } }
            }
        }

    Text(
        text = annotated,
        modifier = Modifier.clickable(onClick = onClick),
        inlineContent =
            if (hashtagIcon != null) {
                mapOf(
                    "inlineContent" to
                        InlineTextContent(HashtagIconPlaceholder) {
                            Icon(
                                imageVector = hashtagIcon.icon,
                                contentDescription = hashtagIcon.description,
                                tint = Color.Unspecified,
                                modifier = hashtagIcon.modifier,
                            )
                        },
                )
            } else {
                emptyMap()
            },
    )
}

/** Width of a single space in [textStyle], used to space FlowRow words. */
@Composable
fun measureSpaceWidth(textStyle: TextStyle): Dp {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return remember(fontFamilyResolver, density, layoutDirection, textStyle) {
        val widthPx =
            TextMeasurer(fontFamilyResolver, density, layoutDirection, 1)
                .measure(" ", textStyle)
                .size
                .width
        with(density) { widthPx.toDp() }
    }
}
