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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.richtext.BlossomUriSegment
import com.vitorpamplona.amethyst.commons.richtext.BuzzInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.CashuSegment
import com.vitorpamplona.amethyst.commons.richtext.ClinkOfferSegment
import com.vitorpamplona.amethyst.commons.richtext.ConcordInviteLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.ImageGalleryParagraph
import com.vitorpamplona.amethyst.commons.richtext.InvoiceSegment
import com.vitorpamplona.amethyst.commons.richtext.MathSegment
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.NowhereLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayGroupLinkSegment
import com.vitorpamplona.amethyst.commons.richtext.RelayUrlSegment
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.richtext.WithdrawSegment
import com.vitorpamplona.amethyst.commons.ui.components.BechLink
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.CashuPreview
import com.vitorpamplona.amethyst.commons.ui.components.ClickableBuzzInviteLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableConcordInviteLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableEmail
import com.vitorpamplona.amethyst.commons.ui.components.ClickablePhone
import com.vitorpamplona.amethyst.commons.ui.components.ClickableRelayGroupLink
import com.vitorpamplona.amethyst.commons.ui.components.ClickableRelayUrl
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.CompactWithdrawalLabel
import com.vitorpamplona.amethyst.commons.ui.components.ConcordInviteCard
import com.vitorpamplona.amethyst.commons.ui.components.ImageGallery
import com.vitorpamplona.amethyst.commons.ui.components.MayBeWithdrawal
import com.vitorpamplona.amethyst.commons.ui.components.NowhereLinkCard
import com.vitorpamplona.amethyst.commons.ui.components.RelayGroupCard
import com.vitorpamplona.amethyst.commons.ui.components.TagLink
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.ClinkOfferPreview
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.CompactInvoiceLabel
import com.vitorpamplona.amethyst.commons.ui.note.creators.invoice.MayBeInvoicePreview
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.theme.RichTextBlockPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The rich-text segment renderer every front end shares: mentions, quoted notes, links and link
 * previews, media, relay and invite links, payment cards, `blossom:` URIs and secret emoji. The
 * leaves that need a platform engine (LaTeX) degrade to text here; a platform subclasses this and
 * overrides them with its own.
 *
 * Recreate one per `RichTextViewer` call (it captures per-call state); it is cheap.
 */
open class DefaultRichTextSegmentRenderer(
    protected val accountViewModel: AccountViewModel,
    protected val nav: INav,
    protected val backgroundColor: MutableState<Color>,
    protected val callbackUri: String?,
    protected val canPreview: Boolean,
) : RichTextSegmentRenderer {
    @Composable
    override fun Media(
        segment: Segment,
        state: RichTextViewerState,
        modifier: Modifier,
    ) {
        if (segment is BlossomUriSegment) {
            BlossomUri(segment.segmentText, state)
            return
        }

        if (canPreview) {
            state.mediaForPager[segment.segmentText]?.let {
                BleedingMedia(RichTextBlockPadding, enabled = it !is MediaUrlPdf) { roundedCorner ->
                    ZoomableContentView(
                        content = it,
                        images = state.mediaList,
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                }
            }
        } else {
            ClickableUrlOrBlossom(segment.segmentText, segment.segmentText)
        }
    }

    @Composable
    override fun Gallery(
        paragraph: ImageGalleryParagraph,
        state: RichTextViewerState,
        modifier: Modifier,
    ) = BleedingMedia(modifier) { roundedCorner -> ImageGallery(paragraph, state, accountViewModel, roundedCorner = roundedCorner) }

    @Composable
    override fun Equation(
        segment: MathSegment,
        modifier: Modifier,
    ) {
        Text(segment.latex)
    }

    @Composable
    override fun NostrEntity(
        bech: String,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = BechLink(bech, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    @Composable
    override fun QuotedEvent(
        eventHex: String,
        addedChars: String?,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) {
        val segment = remember(eventHex, addedChars) { HashIndexEventSegment(eventHex, eventHex, addedChars) }
        TagLink(segment, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
    }

    @Composable
    override fun UserMention(
        userHex: String,
        addedChars: String?,
        modifier: Modifier,
    ) {
        val segment = remember(userHex, addedChars) { HashIndexUserSegment(userHex, userHex, addedChars) }
        TagLink(segment, accountViewModel, nav)
    }

    /** Invoices, withdrawals, Cashu tokens and Clink offers, as their payment cards. */
    @Composable
    override fun Payment(
        segment: Segment,
        modifier: Modifier,
    ) {
        when (segment) {
            // Don't surface a pay/withdraw affordance when previews are suppressed (reply and quote
            // previews, reported notes, profile bios, channel headers): a one-line label with a copy
            // button instead of hundreds of bech32 characters.
            is InvoiceSegment -> if (canPreview) MayBeInvoicePreview(segment.segmentText, accountViewModel) else CompactInvoiceLabel(segment.segmentText)
            is WithdrawSegment -> if (canPreview) MayBeWithdrawal(segment.segmentText, accountViewModel) else CompactWithdrawalLabel(segment.segmentText)
            // Cashu + Clink decode locally (network only on tap), so they render in both modes.
            is CashuSegment -> CashuPreview(segment.segmentText, accountViewModel)
            is ClinkOfferSegment -> ClinkOfferPreview(segment.offer, accountViewModel, nav)
            else -> Text(segment.segmentText)
        }
    }

    @Composable
    override fun LinkPreview(
        url: String,
        modifier: Modifier,
    ) = LoadUrlPreview(url, url, callbackUri, accountViewModel, nav)

    @Composable
    override fun Url(
        url: String,
        displayText: String,
        modifier: Modifier,
    ) = ClickableUrlOrBlossom(displayText, url)

    @Composable
    override fun Email(
        address: String,
        modifier: Modifier,
    ) = ClickableEmail(address)

    @Composable
    override fun Phone(
        number: String,
        modifier: Modifier,
    ) = ClickablePhone(number)

    @Composable
    override fun RelayLink(
        segment: Segment,
        modifier: Modifier,
    ) {
        when (segment) {
            is RelayUrlSegment -> ClickableRelayUrl(segment.segmentText, nav)
            is RelayGroupLinkSegment ->
                if (canPreview) {
                    RelayGroupCard(segment.segmentText, accountViewModel, nav)
                } else {
                    ClickableRelayGroupLink(segment.segmentText, nav)
                }
            is ConcordInviteLinkSegment ->
                if (canPreview) {
                    ConcordInviteCard(segment.segmentText, accountViewModel, nav)
                } else {
                    ClickableConcordInviteLink(segment.segmentText, nav)
                }
            is BuzzInviteLinkSegment -> ClickableBuzzInviteLink(segment.segmentText, nav)
            else -> Text(segment.segmentText)
        }
    }

    @Composable
    override fun NowhereLink(
        segment: NowhereLinkSegment,
        canPreview: Boolean,
        modifier: Modifier,
    ) {
        if (canPreview) {
            NowhereLinkCard(segment)
        } else {
            ClickableUrlOrBlossom(segment.segmentText, segment.segmentText)
        }
    }

    /** A message hidden in an emoji, revealed on tap. */
    @Composable
    override fun SecretMessage(
        segment: SecretEmoji,
        state: RichTextViewerState,
        canPreview: Boolean,
        quotesLeft: Int,
        modifier: Modifier,
    ) = DisplaySecretEmoji(segment, state, callbackUri, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    /** A `blossom:` URI, resolved to a server that holds the blob (BUD-10) and previewed. */
    @Composable
    open fun BlossomUri(
        uri: String,
        state: RichTextViewerState,
    ) {
        if (canPreview) {
            BlossomUriRenderer(uri, state, callbackUri, accountViewModel, nav)
        } else {
            BlossomUriRendererNoPreview(uri, accountViewModel)
        }
    }
}
