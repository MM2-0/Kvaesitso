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
package com.vitorpamplona.amethyst.commons.nip99Classifieds.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardBody
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketCardFrame
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketFactLine
import com.vitorpamplona.amethyst.commons.nip15Marketplace.ui.MarketKindLabel
import com.vitorpamplona.amethyst.commons.nip99Classifieds.normalized
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.open_markets_does_not_recommend
import com.vitorpamplona.amethyst.commons.resources.open_markets_product_not_found
import com.vitorpamplona.amethyst.commons.resources.open_markets_product_review
import com.vitorpamplona.amethyst.commons.resources.open_markets_rating_communication
import com.vitorpamplona.amethyst.commons.resources.open_markets_rating_delivery
import com.vitorpamplona.amethyst.commons.resources.open_markets_rating_quality
import com.vitorpamplona.amethyst.commons.resources.open_markets_rating_value
import com.vitorpamplona.amethyst.commons.resources.open_markets_recommends
import com.vitorpamplona.amethyst.commons.resources.open_markets_review_of
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.types.PRODUCT_RATING_MARK
import com.vitorpamplona.amethyst.commons.ui.note.types.RatingStars
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.ProductReviewEvent
import com.vitorpamplona.quartz.nip99Classifieds.review.tags.ReviewRatingTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

/** One category score of a review, 0..1. */
@Immutable
private class ReviewAspect(
    val category: String,
    val score: Double,
)

@Immutable
private class ProductReviewCardState(
    val product: Address?,
    val recommends: Boolean,
    /** Open Markets' total on five stars. */
    val stars: Double,
    val aspects: ImmutableList<ReviewAspect>,
) {
    companion object {
        fun from(event: ProductReviewEvent): ProductReviewCardState? {
            val thumb = event.thumb() ?: return null
            val score = event.score() ?: return null
            return ProductReviewCardState(
                product = event.product()?.normalized(),
                recommends = thumb >= 0.5,
                stars = score * 5,
                aspects =
                    event
                        .ratings()
                        .filterNot { it.isThumb() }
                        .map { ReviewAspect(it.category, it.score) }
                        .toImmutableList(),
            )
        }
    }
}

/**
 * An Open Markets product review (kind 31555): the product it reviews, the reviewer's thumb, the
 * spec's total on five stars (thumb × 0.5 + 0.5 × the categories' mean, the thumb alone without
 * any), each category's score, and the words. The product loads while the card is on screen.
 */
@Composable
fun RenderProductReview(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<ProductReviewEvent>(note, accountViewModel)
    val review = event ?: return
    val state = remember(review) { ProductReviewCardState.from(review) } ?: return

    MarketCardFrame {
        MarketCardBody {
            MarketKindLabel(MaterialSymbols.Star, stringRes(Res.string.open_markets_product_review))
            state.product?.let { ReviewedProductLine(it, accountViewModel, nav) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThumbChip(state.recommends)
                RatingStars(state.stars, PRODUCT_RATING_MARK)
            }

            if (state.aspects.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.aspects.forEach { AspectBar(it) }
                }
            }

            if (review.content.isNotBlank()) {
                val tags = remember(review) { review.tags.toImmutableListOfLists() }
                TranslatableRichTextViewer(
                    content = review.content,
                    canPreview = canPreview && !makeItShort,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

/** "Review of <product>", opening the product; quiet until it loads. */
@Composable
private fun ReviewedProductLine(
    product: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(product) { productNote ->
        val listing =
            if (productNote != null) {
                val observed by observeNoteEvent<ClassifiedsEvent>(productNote, accountViewModel)
                observed
            } else {
                null
            }
        val title = remember(listing) { listing?.title()?.trim()?.ifEmpty { null } }
        if (title == null) {
            MarketFactLine(MaterialSymbols.Storefront, stringRes(Res.string.open_markets_product_not_found))
        } else {
            Text(
                text = stringRes(Res.string.open_markets_review_of, title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { productNote?.let { routeFor(it, accountViewModel.account) }?.let { nav.nav(it) } },
            )
        }
    }
}

/** The reviewer's verdict: thumbs up "Recommends", or thumbs down. */
@Composable
private fun ThumbChip(recommends: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val container = if (recommends) scheme.secondaryContainer else scheme.errorContainer
    val content = if (recommends) scheme.onSecondaryContainer else scheme.onErrorContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.ThumbUp,
            contentDescription = null,
            tint = content,
            // The bundled font carries thumb_up only; upside down it is the thumb down.
            modifier = Modifier.size(14.dp).then(if (recommends) Modifier else Modifier.rotate(180f)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringRes(if (recommends) Res.string.open_markets_recommends else Res.string.open_markets_does_not_recommend),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

/** A category's name, its score as a bar, and the score on five stars. */
@Composable
private fun AspectBar(aspect: ReviewAspect) {
    val stars = aspect.score * 5
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = aspectName(aspect.category),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(aspect.score.toFloat().coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Text(
            text = "${(stars * 10).roundToInt() / 10}.${(stars * 10).roundToInt() % 10}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}

/** The spec's four categories in the reader's language; any other as its publisher wrote it, tidied. */
@Composable
private fun aspectName(category: String): String =
    when (category.trim().lowercase()) {
        ReviewRatingTag.VALUE -> stringRes(Res.string.open_markets_rating_value)
        ReviewRatingTag.QUALITY -> stringRes(Res.string.open_markets_rating_quality)
        ReviewRatingTag.DELIVERY -> stringRes(Res.string.open_markets_rating_delivery)
        ReviewRatingTag.COMMUNICATION -> stringRes(Res.string.open_markets_rating_communication)
        else ->
            category
                .trim()
                .replace('-', ' ')
                .replace('_', ' ')
                .replaceFirstChar { it.uppercaseChar() }
    }
