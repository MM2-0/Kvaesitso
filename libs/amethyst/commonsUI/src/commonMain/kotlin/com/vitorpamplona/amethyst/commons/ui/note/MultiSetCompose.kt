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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.CombinedZap
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.authorRouteFor
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.notifications.MultiSetCard
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserAssertionsScore
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserPicture
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.components.ExpandableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayBlankAuthor
import com.vitorpamplona.amethyst.commons.ui.note.FollowingIcon
import com.vitorpamplona.amethyst.commons.ui.note.LikedIcon
import com.vitorpamplona.amethyst.commons.ui.note.RepostedIcon
import com.vitorpamplona.amethyst.commons.ui.note.ScoreTagSmall
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthorWithBlank
import com.vitorpamplona.amethyst.commons.ui.note.WatchUserFollows
import com.vitorpamplona.amethyst.commons.ui.note.ZappedIcon
import com.vitorpamplona.amethyst.commons.ui.note.elements.NoteDropDownMenu
import com.vitorpamplona.amethyst.commons.ui.note.hovercard.UserHoverCardAnchor
import com.vitorpamplona.amethyst.commons.ui.richtext.InLineIconRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.NotificationIconModifier
import com.vitorpamplona.amethyst.commons.ui.theme.NotificationIconModifierSmaller
import com.vitorpamplona.amethyst.commons.ui.theme.Size10Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size19dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.WidthAuthorPictureModifierWithPadding
import com.vitorpamplona.amethyst.commons.ui.theme.bitcoinColor
import com.vitorpamplona.amethyst.commons.ui.theme.overPictureBackground
import com.vitorpamplona.amethyst.commons.ui.theme.profile35dpModifier
import com.vitorpamplona.amethyst.commons.util.customEmojiUrlOrNull
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip30CustomEmoji.CustomEmoji
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalTime::class)
@Composable
fun MultiSetCompose(
    multiSetCard: MultiSetCard,
    routeForLastRead: String,
    showHidden: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val baseNote = remember { multiSetCard.note }

    val popupExpanded = remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // A MultiSetCard that carries a single zap or nutzap — the common case for a
    // freshly-arrived notification appended at the top of the feed — is rendered
    // as a large activity card, the same big display used for onchain zaps and the
    // thread view, instead of a one-icon gallery. Once a full rebuild groups
    // several reactions/zaps on the same post into one card (size > 1), it falls
    // back to the compact gallery below. This is purely a rendering decision: the
    // card-building logic is unchanged, so the additive path naturally produces the
    // single-item (large) cards and a rebuild naturally produces the grouped
    // (gallery) ones.
    val singleZap =
        remember(multiSetCard) {
            multiSetCard.zapEvents
                .singleOrNull()
                ?.takeIf {
                    multiSetCard.nutzapEvents.isEmpty() &&
                        multiSetCard.likeEvents.isEmpty() &&
                        multiSetCard.boostEvents.isEmpty()
                }
        }

    val singleNutzap =
        remember(multiSetCard) {
            multiSetCard.nutzapEvents
                .singleOrNull()
                ?.takeIf {
                    multiSetCard.zapEvents.isEmpty() &&
                        multiSetCard.likeEvents.isEmpty() &&
                        multiSetCard.boostEvents.isEmpty()
                }
        }

    val isLargeCard = singleZap != null || singleNutzap != null

    val backgroundColor =
        calculateBackgroundColor(
            createdAt = multiSetCard.maxCreatedAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    val columnModifier =
        remember(backgroundColor.value, isLargeCard) {
            Modifier
                .fillMaxWidth()
                .background(backgroundColor.value)
                .combinedClickable(
                    onClick = {
                        scope.launch { routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) } }
                    },
                    onLongClick = { popupExpanded.value = true },
                ).padding(
                    // The large-card branch renders through NoteCompose, which applies
                    // its own 12dp/10dp note padding; let it own the inset there so we
                    // don't double it up. The gallery branch keeps the card's padding.
                    start = if (isLargeCard) 0.dp else 12.dp,
                    end = if (isLargeCard) 0.dp else 12.dp,
                    top = if (isLargeCard) 0.dp else 10.dp,
                    bottom = 0.dp,
                )
        }

    Column(modifier = columnModifier) {
        when {
            // Render the lone zap/nutzap as a full note (author picture on the left,
            // the zap card as its body) by reusing NoteCompose on the receipt note,
            // instead of dropping the bare activity card into the feed.
            singleZap != null ->
                NoteCompose(
                    baseNote = singleZap.response,
                    routeForLastRead = null,
                    isHiddenFeed = showHidden,
                    quotesLeft = 1,
                    parentBackgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

            singleNutzap != null ->
                NoteCompose(
                    baseNote = singleNutzap,
                    routeForLastRead = null,
                    isHiddenFeed = showHidden,
                    quotesLeft = 1,
                    parentBackgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )

            else -> {
                Galeries(multiSetCard, backgroundColor, accountViewModel, nav)

                Row(Modifier.fillMaxWidth()) {
                    Spacer(modifier = WidthAuthorPictureModifierWithPadding)

                    NoteCompose(
                        baseNote = baseNote,
                        modifier = HalfTopPadding,
                        routeForLastRead = null,
                        isBoostedNote = true,
                        isHiddenFeed = showHidden,
                        quotesLeft = 1,
                        parentBackgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }

        if (popupExpanded.value) {
            NoteDropDownMenu(baseNote, { popupExpanded.value = false }, null, accountViewModel, nav)
        }
    }
}

@Composable
private fun Galeries(
    multiSetCard: MultiSetCard,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (multiSetCard.zapEvents.isNotEmpty()) {
        DecryptAndRenderZapGallery(multiSetCard, backgroundColor, accountViewModel, nav)
    }

    if (multiSetCard.nutzapEvents.isNotEmpty()) {
        RenderNutzapGallery(multiSetCard.nutzapEvents, backgroundColor, accountViewModel, nav)
    }

    if (multiSetCard.boostEvents.isNotEmpty()) {
        RenderBoostGallery(multiSetCard.boostEvents, nav, accountViewModel)
    }

    if (multiSetCard.likeEvents.isNotEmpty()) {
        multiSetCard.likeEventsByType.forEach {
            RenderLikeGallery(it.key, it.value, nav, accountViewModel)
        }
    }
}

@Composable
fun RenderLikeGallery(
    reactionType: String,
    likeEvents: ImmutableList<Note>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    if (likeEvents.isNotEmpty()) {
        Row(Modifier.fillMaxWidth()) {
            GalleryIconColumn(NotificationIconModifier) { iconAlignment ->
                val modifier = remember(iconAlignment) { Modifier.align(iconAlignment) }

                // A bare `:shortcode:` (its event had no emoji tag) has no image: it falls to the text branch.
                val renderable =
                    remember(reactionType) {
                        reactionType.customEmojiUrlOrNull()?.let { persistentListOf(CustomEmoji.ImageUrlType(it)) }
                    }

                if (renderable != null) {
                    InLineIconRenderer(
                        renderable,
                        style = SpanStyle(color = Color.White),
                        maxLines = 1,
                        modifier = modifier,
                    )
                } else {
                    when (val shortReaction = reactionType) {
                        "+" -> {
                            LikedIcon(modifier.size(Size19dp))
                        }

                        "-" -> {
                            Text(text = "\uD83D\uDC4E", modifier = modifier)
                        }

                        else -> {
                            if (EmojiCoder.isCoded(shortReaction)) {
                                DisplaySecretEmojiAsReaction(
                                    shortReaction,
                                    modifier,
                                    accountViewModel,
                                    nav,
                                )
                            } else {
                                Text(text = shortReaction, modifier = modifier)
                            }
                        }
                    }
                }
            }

            // Opens the reaction's own thread (where it can be replied to,
            // boosted, or zapped) instead of the reactor's profile.
            AuthorGallery(likeEvents, nav, accountViewModel) { Route.Note(it.idHex) }
        }
    }
}

@Composable
fun DisplaySecretEmojiAsReaction(
    reaction: String,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var secretContent by remember(reaction) {
        mutableStateOf(CachedRichTextParser.cachedText(EmojiCoder.decode(reaction), EmptyTagList))
    }

    var showPopup by remember {
        mutableStateOf(false)
    }

    if (secretContent == null) {
        LaunchedEffect(reaction) {
            launch(Dispatchers.IO) {
                secretContent =
                    CachedRichTextParser.parseText(
                        EmojiCoder.decode(reaction),
                        EmptyTagList,
                    )
            }
        }
    }

    val localSecretContent = secretContent

    AnimatedBorderTextCornerRadius(
        reaction,
        modifier.clickable {
            showPopup = !showPopup
        },
    )

    if (localSecretContent != null && showPopup) {
        val iconSizePx = with(LocalDensity.current) { -24.dp.toPx().toInt() }

        Popup(
            alignment = Alignment.TopCenter,
            offset = IntOffset(0, -iconSizePx),
            onDismissRequest = { showPopup = false },
            properties = PopupProperties(focusable = true),
        ) {
            Surface(Modifier.padding(10.dp)) {
                val color = remember { mutableStateOf(Color.Transparent) }
                LocalRichTextPlatform.current.SecretMessage(localSecretContent, null, 3, color, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun DecryptAndRenderZapGallery(
    multiSetCard: MultiSetCard,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val zapEvents by
        // Keyed on the zaps: a late zap can join a card without changing its list key, and the
        // gallery has to decrypt the new list rather than keep showing the first one.
        produceState(initialValue = accountViewModel.cachedDecryptAmountMessageInGroup(multiSetCard.zapEvents), multiSetCard.zapEvents) {
            accountViewModel.decryptAmountMessageInGroup(multiSetCard.zapEvents) { value = it }
        }

    RenderZapGallery(zapEvents, backgroundColor, accountViewModel, nav)
}

@Composable
fun RenderZapGallery(
    zapEvents: ImmutableList<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(Modifier.fillMaxWidth()) {
        GalleryIconColumn { iconAlignment ->
            ZappedIcon(
                modifier = Modifier.size(Size25dp).align(iconAlignment),
            )
        }

        AuthorGalleryZaps(zapEvents, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderNutzapGallery(
    nutzapEvents: ImmutableList<Note>,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Convert each kind:9321 Note into the same shape AuthorGalleryZaps
    // already renders for lightning. Amount comes from the parsed proof
    // total (lazy via NutzapEvent.claimedSatsTotal), comment from the
    // event's content. No NIP-44 decryption needed — nutzap doesn't
    // have a private variant the way NIP-57 does.
    val nutzapAuthorComments: ImmutableList<ZapAmountCommentNotification> =
        remember(nutzapEvents) {
            nutzapEvents
                .map { note ->
                    val event = note.event as? NutzapEvent
                    val sats = event?.claimedSatsTotal() ?: 0L
                    ZapAmountCommentNotification(
                        user = note.author,
                        comment = event?.content?.ifBlank { null },
                        amount = showAmount(BigDecimal(sats)),
                        zapNote = note,
                    )
                }.toImmutableList()
        }

    Row(Modifier.fillMaxWidth()) {
        // Indented, reuse the reaction galleries' icon column (55dp wide with a
        // 5dp end inset) so the cashu glyph lines up with the like/boost icons
        // above it, instead of sitting flush-right like the lightning ZappedIcon.
        // Full width centres every gallery's icon in the same 35dp slot.
        GalleryIconColumn(NotificationIconModifier) { iconAlignment ->
            Icon(
                imageVector = CustomHashTagIcons.Cashu,
                contentDescription = stringRes(Res.string.nutzap),
                modifier = Modifier.size(Size20dp).align(iconAlignment),
                // Tint the monochrome cashu outline brand orange so the nutzap
                // gallery matches the lightning ZappedIcon in RenderZapGallery.
                tint = BitcoinOrange,
            )
        }

        AuthorGalleryZaps(nutzapAuthorComments, backgroundColor, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    boostEvents: ImmutableList<Note>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        GalleryIconColumn(NotificationIconModifierSmaller) { iconAlignment ->
            RepostedIcon(
                modifier = Modifier.size(Size20dp).align(iconAlignment),
            )
        }

        AuthorGallery(boostEvents, nav, accountViewModel)
    }
}

@Composable
fun RenderBoostGallery(
    noteToGetBoostEvents: NoteState,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        GalleryIconColumn(NotificationIconModifierSmaller) { iconAlignment ->
            RepostedIcon(
                modifier = Modifier.size(Size20dp).align(iconAlignment),
            )
        }

        AuthorGallery(noteToGetBoostEvents, nav, accountViewModel)
    }
}

@Composable
fun MapZaps(
    zaps: ImmutableList<CombinedZap>,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<ZapAmountCommentNotification>) -> Unit,
) {
    // Starts from what is already decrypted, not an empty list: the card is rebuilt every time the
    // feed comes back on screen, and an empty first frame grows the card once the zaps arrive,
    // which animateItem() then plays as a slide of every card below it.
    @Suppress("ProduceStateDoesNotAssignValue")
    val zapEvents by
        produceState(initialValue = accountViewModel.cachedDecryptAmountMessageInGroup(zaps), zaps) {
            accountViewModel.decryptAmountMessageInGroup(zaps) { value = it }
        }

    content(zapEvents)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGalleryZaps(
    authorNotes: ImmutableList<ZapAmountCommentNotification>,
    backgroundColor: MutableState<Color>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    CompositionLocalProvider(
        LocalAuthorGalleryRenderContext provides rememberAuthorGalleryRenderContext(accountViewModel),
    ) {
        Column(modifier = StdStartPadding) {
            FlowRow { authorNotes.forEach { RenderState(it, backgroundColor, accountViewModel, nav) } }
        }
    }
}

@Composable
private fun ParseAuthorCommentAndAmount(
    zapRequest: Note,
    zapEvent: Note,
    accountViewModel: AccountViewModel,
    onReady: @Composable (MutableState<ZapAmountCommentNotification>) -> Unit,
) {
    val content =
        remember {
            mutableStateOf(
                ZapAmountCommentNotification(
                    user = zapRequest.author,
                    comment = null,
                    amount = null,
                    zapNote = zapEvent,
                ),
            )
        }

    LaunchedEffect(key1 = zapRequest.idHex, key2 = zapEvent.idHex) {
        accountViewModel.decryptAmountMessage(zapRequest, zapEvent) { newState ->
            if (newState != null) {
                content.value = newState
            }
        }
    }

    onReady(content)
}

fun click(
    content: ZapAmountCommentNotification,
    nav: INav,
) {
    val zapNote = content.zapNote
    if (zapNote != null) {
        // Opens the zap's own thread, where anyone can reply, boost,
        // zap, or share it. The sender's profile is one tap away there.
        nav.nav(Route.Note(zapNote.idHex))
    } else {
        content.user?.let { nav.nav(routeFor(it)) }
    }
}

@Composable
private fun RenderState(
    content: ZapAmountCommentNotification,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = Modifier.clickable { click(content, nav) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisplayAuthorCommentAndAmount(
            authorComment = content,
            backgroundColor = backgroundColor,
            nav = nav,
            accountViewModel = accountViewModel,
        )
    }
}

val amountBoxModifier = Modifier.size(Size35dp).clip(shape = CircleShape)

val textBoxModifier = Modifier.padding(start = 5.dp).fillMaxWidth()

val bottomPadding1dp = Modifier.padding(bottom = 1.dp)

val commentTextSize = 12.sp

@Composable
private fun DisplayAuthorCommentAndAmount(
    authorComment: ZapAmountCommentNotification,
    backgroundColor: MutableState<Color>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    Box(modifier = Size35Modifier, contentAlignment = Alignment.BottomCenter) {
        WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
            authorComment.user,
            accountViewModel,
            nav,
        )
        authorComment.amount?.let { CrossfadeToDisplayAmount(it) }
    }

    authorComment.comment?.let {
        Box(textBoxModifier) {
            CrossfadeToDisplayComment(it, backgroundColor, nav, accountViewModel)
        }
    }
}

@Composable
fun CrossfadeToDisplayAmount(amount: String) {
    Box(
        modifier = amountBoxModifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        val backgroundColor = MaterialTheme.colorScheme.overPictureBackground
        Box(
            modifier = remember { Modifier.width(Size35dp).background(backgroundColor) },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = amount,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.bitcoinColor,
                fontSize = commentTextSize,
                modifier = bottomPadding1dp,
            )
        }
    }
}

@Composable
fun CrossfadeToDisplayComment(
    comment: String,
    backgroundColor: MutableState<Color>,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    TranslatableRichTextViewer(
        content = comment,
        id = comment,
        translationMessageModifier = Modifier.padding(top = 2.dp),
        accountViewModel = accountViewModel,
    ) {
        ExpandableRichTextViewer(
            it,
            true,
            1,
            Modifier,
            EmptyTagList,
            backgroundColor,
            comment,
            null,
            null,
            accountViewModel,
            nav,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGallery(
    authorNotes: ImmutableList<Note>,
    nav: INav,
    accountViewModel: AccountViewModel,
    clickRoute: (Note) -> Route? = ::authorRouteFor,
) {
    CompositionLocalProvider(
        LocalAuthorGalleryRenderContext provides rememberAuthorGalleryRenderContext(accountViewModel),
    ) {
        Column(modifier = StdStartPadding) {
            FlowRow { authorNotes.forEach { note -> BoxedAuthor(note, nav, accountViewModel, clickRoute) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorGallery(
    noteToGetBoostEvents: NoteState,
    nav: INav,
    accountViewModel: AccountViewModel,
) {
    CompositionLocalProvider(
        LocalAuthorGalleryRenderContext provides rememberAuthorGalleryRenderContext(accountViewModel),
    ) {
        Column(modifier = StdStartPadding) {
            FlowRow {
                noteToGetBoostEvents.note.boosts.forEach { note -> BoxedAuthor(note, nav, accountViewModel) }
            }
        }
    }
}

@Composable
private fun BoxedAuthor(
    note: Note,
    nav: INav,
    accountViewModel: AccountViewModel,
    clickRoute: (Note) -> Route? = ::authorRouteFor,
) {
    Box(modifier = Size35Modifier.clickable(onClick = { clickRoute(note)?.let { nav.nav(it) } })) {
        WatchAuthorWithBlank(note, Size35Modifier, accountViewModel) { author ->
            WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
                author,
                accountViewModel,
                nav,
            )
        }
    }
}

/**
 * A gallery face. With [nav], resting the mouse on it opens the author's [UserHoverCardAnchor]
 * card, as faces in the feed do.
 */
@Composable
fun WatchUserMetadataAndFollowsAndRenderUserProfilePictureOrDefaultAuthor(
    author: User?,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) {
    if (author != null) {
        if (nav != null) {
            UserHoverCardAnchor(author, accountViewModel, nav, Size35Modifier) {
                WatchUserMetadataAndFollowsAndRenderUserProfilePicture(author, accountViewModel)
            }
        } else {
            WatchUserMetadataAndFollowsAndRenderUserProfilePicture(author, accountViewModel)
        }
    } else {
        DisplayBlankAuthor(Size35dp, accountViewModel = accountViewModel)
    }
}

@Composable
fun WatchUserMetadataAndFollowsAndRenderUserProfilePicture(
    author: User,
    accountViewModel: AccountViewModel,
) {
    // When rendered inside a gallery, the account-global auto-play and follow-set
    // reads are hoisted to a single collection for the whole gallery (see
    // [rememberAuthorGalleryRenderContext]). Falls back to per-author collection
    // for callers that don't provide a context.
    val galleryContext = LocalAuthorGalleryRenderContext.current

    // One shared relay subscription per author. The profile picture and the
    // contact-card score below both fetch the same kind-0 metadata, so we
    // subscribe once here and let them skip their own (formerly duplicate) one.
    UserFinderFilterAssemblerSubscription(author, accountViewModel)

    WatchUserMetadata(author, accountViewModel, subscribe = false) { baseUserPicture ->
        val autoPlayGif =
            if (galleryContext != null) {
                galleryContext.autoPlayGif
            } else {
                accountViewModel.settings.autoPlayVideosFlow
                    .collectAsStateWithLifecycle()
                    .value
            }

        RobohashFallbackAsyncImage(
            robot = author.pubkeyHex,
            model = baseUserPicture,
            contentDescription = stringRes(id = Res.string.profile_image),
            modifier = MaterialTheme.colorScheme.profile35dpModifier,
            contentScale = ContentScale.Crop,
            loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
            loadRobohash = LocalDisplaySettings.current.loadRobohash,
            autoPlayGif = autoPlayGif,
        )
    }

    if (galleryContext != null) {
        val isFollowing =
            accountViewModel.isLoggedUser(author.pubkeyHex) ||
                author.pubkeyHex in galleryContext.follows
        if (isFollowing) {
            Box(modifier = Size35Modifier, contentAlignment = Alignment.TopEnd) {
                FollowingIcon(Size10Modifier)
            }
        }
    } else {
        WatchUserFollows(author.pubkeyHex, accountViewModel) { isFollowing ->
            if (isFollowing) {
                Box(modifier = Size35Modifier, contentAlignment = Alignment.TopEnd) {
                    FollowingIcon(Size10Modifier)
                }
            }
        }
    }

    ObserveAndRenderBoxedUserCards(author, accountViewModel, subscribe = false)
}

@Composable
fun ObserveAndRenderBoxedUserCards(
    user: User,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
) {
    val score by observeUserAssertionsScore(user, accountViewModel, subscribe)

    score?.let {
        Box(modifier = Size35Modifier, contentAlignment = Alignment.BottomCenter) {
            ScoreTagSmall(it, Modifier)
        }
    }
}

@Composable
private fun WatchUserMetadata(
    author: User,
    accountViewModel: AccountViewModel,
    subscribe: Boolean = true,
    onNewMetadata: @Composable (String?) -> Unit,
) {
    val userProfile by observeUserPicture(author, accountViewModel, subscribe)

    onNewMetadata(userProfile)
}
