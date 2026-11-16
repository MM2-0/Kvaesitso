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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.decentralizedLists.dListItemView
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.resources.rating_stars_out_of_five
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.ResolveDListConcept
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.isDListItemPage
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.presentAmongMany
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RatingMark
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyOnlyAsHexOrNull
import kotlin.math.floor
import kotlin.math.roundToInt

private val StarSize = Modifier.size(Size18dp)

// A 2:3 portrait thumbnail — the aspect ratio of a book cover and a film poster alike, which is
// what the two richly-rendered marks need. Hoisted so the shape isn't rebuilt per recomposition.
private val CoverShape = RoundedCornerShape(6.dp)

// Also the avatar size when the rated thing is a person, so a profile card lines up with a book's.
private val CoverWidth = 44.dp
private val CoverModifier =
    Modifier
        .width(CoverWidth)
        .height(66.dp)
        .clip(CoverShape)

/**
 * Renders a kind-34259 entity rating: the stars, the thing being rated, and the review body.
 *
 * The kind is generic (see [EntityRatingEvent]), so this degrades in layers rather than blanking.
 * A publication we have cached shows its cover, title, author and summary; one we do not shows a
 * name derived from the coordinate's identifier; a mark that names a plain string (a hashtag, a
 * relay) shows that string; and a rating we cannot score at all still shows its review.
 */
@Composable
fun RenderEntityRating(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: these kinds are DEFINED by replacement — a newer rating,
    // review or redirect lands on the same Note instance. `Note` is @Stable and `event`
    // is a plain @Volatile var, so a bare read registers no snapshot dependency and
    // Compose would keep showing the superseded version.
    val observedEvent by observeNoteEvent<EntityRatingEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val stars = remember(noteEvent) { noteEvent.stars() }
    val mark = remember(noteEvent) { noteEvent.mark() }
    val targetAddress = remember(noteEvent) { noteEvent.targetAddress() }
    val targetEventId = remember(noteEvent) { noteEvent.targetEventId() }

    Column(Modifier.fillMaxWidth()) {
        if (targetAddress != null) {
            LoadAddressableNote(targetAddress) { targetNote ->
                if (targetNote != null) {
                    RatedAddressableCard(
                        targetNote = targetNote,
                        mark = mark,
                        stars = stars,
                        fallbackIdentifier = noteEvent.targetIdentifier(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                } else {
                    stars?.let { RatingStarsRow(it, mark) }
                }
            }
        } else if (targetEventId != null) {
            // The spec's default mark is `event`, and such a rating names its target by event id
            // rather than by coordinate. Without this the card printed the raw 64-hex id as its
            // title, which tells a reader nothing about what was rated.
            LoadNote(targetEventId) { targetNote ->
                if (targetNote != null) {
                    RatedNoteCard(targetNote, mark, accountViewModel, nav)
                } else {
                    RatedTargetCard(mark, targetEventId.take(8))
                }
            }

            stars?.let { RatingStarsRow(it, mark) }
        } else {
            // Marks like `hashtag` or `relay` name their target with a plain string rather than a
            // coordinate, so there is no note to load. Showing the identifier is the difference
            // between a review of something and a row of stars floating on its own.
            val identifier = remember(noteEvent) { noteEvent.targetIdentifier() }

            // A `profile` rating names a pubkey. Printed as is, that is 64 hex characters under a
            // generic person icon; resolved, it is the person's picture and name.
            val ratedPubKey =
                remember(mark, identifier) {
                    if (mark == RatingMark.PROFILE) decodePublicKeyOnlyAsHexOrNull(identifier) else null
                }

            if (ratedPubKey != null) {
                LoadUser(ratedPubKey) { user ->
                    if (user != null) {
                        RatedProfileCard(user, mark, accountViewModel, nav)
                    } else {
                        // Only until LoadUser creates the cache entry, normally the next frame.
                        RatedTargetCard(mark, ratedPubKey.take(8))
                    }
                }
            } else if (identifier.isNotEmpty()) {
                RatedTargetCard(mark, identifier)
            }

            stars?.let { RatingStarsRow(it, mark, cardNamesTarget = ratedPubKey != null) }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = Size10dp),
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

/**
 * The stars under a rating's card, not above it: they are a verdict on the thing named in the card,
 * so a reader who meets them first has nothing to attach them to.
 */
@Composable
private fun RatingStarsRow(
    stars: Double,
    mark: String,
    // True when the card above names and pictures the target (a loaded publication, a titled
    // list item, a resolved person), so the mark chip beside the stars would only repeat it.
    cardNamesTarget: Boolean = false,
) {
    Box(Modifier.padding(top = Size5dp)) {
        RatingStars(stars, mark, showMark = !cardNamesTarget && mark !in MARKS_WITH_A_CARD)
    }
}

/**
 * The score, as a row of five stars.
 *
 * Filled and empty stars are the **same codepoint**: Material Symbols carries fill on a variable
 * axis rather than in separate glyphs, so `star`, `star_border` and `star_outline` all resolve to
 * U+F09A. An earned star is therefore drawn by asking the painter for FILL=1 (`filled = true`),
 * not by tinting — tint alone leaves five hollow outlines that read as an empty row. Only
 * `star_half` is a glyph of its own.
 *
 * The numeral is shown only when the score is fractional — next to five filled stars, a "5" is
 * noise. The whole row carries one merged content description, because five undescribed icons
 * announce nothing to a screen reader.
 */
@Composable
internal fun RatingStars(
    stars: Double,
    mark: String,
    showMark: Boolean = mark !in MARKS_WITH_A_CARD,
) {
    val filled = floor(stars).roundToInt()
    val hasHalf = stars - filled >= 0.25 && filled < EntityRatingEvent.MAX_STARS
    val label = formatStars(stars)
    // The glyphs can only show whole and half stars, so anything else would drop precision
    // silently; the numeral carries it. Next to five filled stars a "5" is just noise.
    val showLabel = label.contains('.')
    val a11y = stringRes(Res.string.rating_stars_out_of_five, label)

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(EntityRatingEvent.MAX_STARS) { index ->
            val isHalf = index == filled && hasHalf
            val isOn = index < filled || isHalf

            Icon(
                symbol =
                    when {
                        index < filled -> MaterialSymbols.Star
                        isHalf -> MaterialSymbols.StarHalf
                        else -> MaterialSymbols.StarBorder
                    },
                contentDescription = null,
                modifier = StarSize,
                tint =
                    if (isOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                // `Star` and `StarBorder` are the same codepoint — Material Symbols carries fill on
                // a variable axis — so without this a five-star review draws five hollow stars and
                // reads as an empty row. Tint alone cannot say "earned" here.
                filled = isOn,
            )
        }

        if (showLabel) {
            Spacer(StdHorzSpacer)

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The mark is the only clue to what was rated when the target card cannot name it, but it
        // would just repeat the icon and title of a card that can.
        if (showMark) {
            Spacer(StdHorzSpacer)

            Text(
                text = mark,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The rated coordinate, drawn by what it turns out to be once loaded.
 *
 * [observeNote] both watches the cache and asks the relays for the event, so a rating that arrives
 * before its target fills in once the target does.
 */
@Composable
private fun RatedAddressableCard(
    targetNote: AddressableNote,
    mark: String,
    stars: Double?,
    fallbackIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(targetNote, accountViewModel)
    val event = noteState.note.event

    if (event != null && isDListItemPage(event)) {
        RatedDListItemCard(targetNote, event, mark, stars, fallbackIdentifier, accountViewModel, nav)
    } else {
        val publication = event as? PublicationIndexEvent
        RatedPublicationCard(targetNote, publication, mark, fallbackIdentifier, accountViewModel, nav)
        // A loaded publication names itself; until then the slug title needs the mark beside it.
        stars?.let { RatingStarsRow(it, mark, cardNamesTarget = publication != null) }
    }
}

/**
 * The rated publication as a cover-led card. Until its kind-30040 index arrives the title falls
 * back to the coordinate's identifier, which for the slugs publishers use ("wuthering-heights")
 * already reads as a title.
 */
@Composable
private fun RatedPublicationCard(
    targetNote: AddressableNote,
    publication: PublicationIndexEvent?,
    mark: String,
    fallbackIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title =
        remember(publication, targetNote) {
            publication?.titleOrIdentifier()
                ?: PublicationIndexEvent.humanizeIdentifier(targetNote.address.dTag.ifEmpty { fallbackIdentifier })
        }

    val subtitle = remember(publication) { publication?.author() ?: publication?.summary() }
    val cover = remember(publication) { publication?.image() }

    RatedTargetCard(
        mark = mark,
        title = title,
        subtitle = subtitle,
        onClick = { routeFor(targetNote, accountViewModel.account)?.let { nav.nav(it) } },
        cover = { RatedCover(cover, mark, accountViewModel) },
    )
}

/**
 * A rated Decentralized List item (kinds 9999 / 39999), e.g. a book from Brainstorm's catalog.
 * Read the way the item reads among many in its own list, through its concept's definition: a
 * book shows its cover, title and author instead of the slug in its coordinate.
 */
@Composable
private fun RatedDListItemCard(
    targetNote: AddressableNote,
    item: Event,
    mark: String,
    stars: Double?,
    fallbackIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ResolveDListConcept(item, accountViewModel) { resolved, _, _ ->
        val view =
            remember(item, resolved) {
                val hinted = dListItemView(item.tags, item.content, resolved?.governing)
                if (resolved != null) presentAmongMany(item, resolved.communityCoordinate, resolved.governing, hinted) else hinted
            }

        val title =
            view.title ?: PublicationIndexEvent.humanizeIdentifier(targetNote.address.dTag.ifEmpty { fallbackIdentifier })

        RatedTargetCard(
            mark = mark,
            title = title,
            subtitle = view.summary,
            onClick = { routeFor(targetNote, accountViewModel.account)?.let { nav.nav(it) } },
            cover = { RatedCover(view.image, mark, accountViewModel) },
        )

        // A titled item names itself; an untitled one falls back to its slug, which the mark explains.
        stars?.let { RatingStarsRow(it, mark, cardNamesTarget = view.title != null) }
    }
}

/** The target's artwork in the cover slot, or the mark's icon while there is none or it fails. */
@Composable
private fun RatedCover(
    cover: String?,
    mark: String,
    accountViewModel: AccountViewModel,
) {
    if (cover != null) {
        Box(CoverModifier) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = stringRes(Res.string.preview_card_image_for, cover),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = CoverModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { CoverPlaceholder(mark) },
                onError = { CoverPlaceholder(mark) },
            )
        }
    } else {
        CoverPlaceholder(mark)
    }
}

/**
 * A rated nostr event, named by id rather than by coordinate.
 *
 * Observing drives the fetch, so a note the reader has never seen is pulled in by being rated.
 * Its author and the opening of its content stand in for a title, because a plain note has none.
 */
@Composable
private fun RatedNoteCard(
    targetNote: Note,
    mark: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(targetNote, accountViewModel)

    val event = noteState.note.event
    val title =
        remember(noteState) {
            event
                ?.content
                ?.trim()
                ?.take(140)
                ?.ifBlank { null } ?: targetNote.idHex.take(8)
        }
    // A name arrives with its author's kind-0, which routinely lands long after the note it
    // signs. Observed so the card fills in the name instead of freezing on the hex it started
    // with, and so the observation itself asks the relays for that metadata.
    val author = noteState.note.author
    val authorName =
        if (author != null) {
            val authorInfo by observeUserInfo(author, accountViewModel)
            authorInfo?.info?.bestName() ?: author.pubkeyDisplayHex()
        } else {
            null
        }

    RatedTargetCard(
        mark = mark,
        title = title,
        subtitle = authorName,
        onClick = { nav.nav(Route.Note(targetNote.idHex)) },
    )
}

/**
 * A rated person: their picture in the cover slot, their name as the title and the opening of their
 * bio as the second line, which is what tells a reader who this is. Observing the metadata asks the
 * relays for the kind-0, so a profile the reader has never met fills in instead of staying an npub.
 */
@Composable
private fun RatedProfileCard(
    user: User,
    mark: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The name the reader knows this person by (their own nickname for them first), the same one
    // every other user row shows. This also holds the relay subscription for the kind-0.
    val title by observeUserName(user, accountViewModel)

    // Read straight from the cache: the name above already subscribes for this user's metadata.
    val userInfo by user.metadata().flow.collectAsStateWithLifecycle()

    // Bios are paragraphs: kept as written, a blank line after the first sentence spends the card's
    // second line on nothing but an ellipsis. Folded to one line, the two lines show the bio.
    val about = userInfo?.info?.about
    val subtitle = remember(about) { about?.replace(WhitespaceRun, " ")?.trim() }

    RatedTargetCard(
        mark = mark,
        title = title,
        subtitle = subtitle,
        onClick = { nav.nav(routeFor(user)) },
        // Not UserPicture: the whole card is already the link to the profile, so the avatar needs
        // neither its own click target nor a hover card.
        cover = { BaseUserPicture(user, CoverWidth, accountViewModel) },
    )
}

private val WhitespaceRun = Regex("\\s+")

/**
 * One card shape for every kind of rated thing: a cover when there is one, the mark's icon when
 * there is not, and a title plus optional second line. Shared so the resolvable and unresolvable
 * cases cannot drift apart visually.
 */
@Composable
private fun RatedTargetCard(
    mark: String,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    cover: @Composable () -> Unit = { CoverPlaceholder(mark) },
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size10dp)
                .clip(QuoteBorder)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(Size10dp)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()

        Spacer(Modifier.width(Size10dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Size5dp))

                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The cover slot when there is no artwork: the mark's icon on a tinted plate, same size as a cover. */
@Composable
private fun CoverPlaceholder(mark: String) {
    Box(
        modifier = CoverModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = iconForMark(mark),
            contentDescription = null,
            modifier = StarSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The marks whose surrounding card already names and pictures the thing, so the chip beside the
 * stars would only repeat it. Relay reviews belong here too: [RenderRelayReview] prints the relay
 * URL directly above the star row.
 */
internal val MARKS_WITH_A_CARD = setOf(RatingMark.BOOKS, RatingMark.MOVIES, RatingMark.RELAY, PRODUCT_RATING_MARK)

/**
 * Not a NIP mark: what an Open Markets product review (31555) passes to [RatingStars], whose card
 * names the product above the stars.
 */
internal const val PRODUCT_RATING_MARK = "product"

/**
 * Not spec marks: what Brainstorm publishes when rating a Decentralized List book or place
 * (kind 39999), singular where the spec's examples say `books`.
 */
private const val BOOK_MARK = "book"
private const val PLACE_MARK = "place"

private fun iconForMark(mark: String): MaterialSymbol =
    when (mark) {
        RatingMark.BOOKS, BOOK_MARK -> MaterialSymbols.MenuBook
        RatingMark.MOVIES -> MaterialSymbols.Movie
        RatingMark.PROFILE -> MaterialSymbols.Person
        RatingMark.RELAY -> MaterialSymbols.Dns
        RatingMark.HASHTAG -> MaterialSymbols.Tag
        PLACE_MARK -> MaterialSymbols.LocationOn
        else -> MaterialSymbols.AutoMirrored.Article
    }

/** "4.5" for a fractional score, "4" for a whole one. */
internal fun formatStars(stars: Double): String {
    val rounded = (stars * 10).roundToInt()
    return if (rounded % 10 == 0) "${rounded / 10}" else "${rounded / 10}.${rounded % 10}"
}

@Preview
@Composable
fun RatingStarsPreview() {
    ThemeComparisonColumn {
        Column {
            // Every shape the stars() ladder can produce, plus a mark that gets no card.
            RatingStars(5.0, RatingMark.BOOKS)
            RatingStars(3.5, RatingMark.BOOKS)
            RatingStars(1.0, RatingMark.BOOKS)
            RatingStars(4.0, RatingMark.HASHTAG)
        }
    }
}

@Preview
@Composable
fun RatedTargetCardPreview() {
    ThemeComparisonColumn {
        Column {
            RatedTargetCard(
                mark = RatingMark.BOOKS,
                title = "Wuthering Heights",
                subtitle = "Emily Bronte",
                onClick = {},
            )
            RatedTargetCard(
                mark = RatingMark.BOOKS,
                title = "A publication whose title is long enough to need a second line and then some",
                subtitle = "Collection of selected fables from the ancient Greek philosopher, known as Aesop.",
                onClick = {},
            )
            RatedTargetCard(mark = RatingMark.HASHTAG, title = "bookstr")
        }
    }
}
