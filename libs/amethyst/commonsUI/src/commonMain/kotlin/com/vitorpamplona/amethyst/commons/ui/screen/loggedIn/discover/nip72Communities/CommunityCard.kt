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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip72Communities

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.ParticipantListBuilder
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.community_about_topic
import com.vitorpamplona.amethyst.commons.ui.layouts.LeftPictureLayout
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DisplayAuthorBanner
import com.vitorpamplona.amethyst.commons.ui.note.Gallery
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.HalfTopPadding
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@Immutable
data class CommunityCard(
    val name: String,
    val description: String?,
    val cover: String?,
    val moderators: ImmutableList<HexKey>,
)

@Composable
fun RenderCommunitiesThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Narrow observation: we only care about the CommunityDefinitionEvent itself,
    // not every reaction/zap/boost that would otherwise recompose the whole card.
    val definition by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)
    val event = definition ?: return

    // Memoize card + moderator list identity so LaunchedEffect(moderators) in
    // LoadModerators doesn't retrigger the ParticipantListBuilder traversal on
    // every recomposition.
    val card =
        remember(event.id) {
            CommunityCard(
                name = event.name()?.ifBlank { null } ?: event.dTag(),
                description = event.description(),
                cover = event.image()?.imageUrl,
                moderators = event.moderatorKeys().toImmutableList(),
            )
        }

    RenderCommunitiesThumb(
        card,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderCommunitiesThumb(
    card: CommunityCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LeftPictureLayout(
        onImage = {
            card.cover?.let {
                Box(contentAlignment = BottomStart) {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(QuoteBorder),
                    )
                }
            } ?: run { DisplayAuthorBanner(baseNote, accountViewModel) }
        },
        onTitleRow = {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = StdHorzSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing,
            ) {
                LikeReaction(
                    baseNote = baseNote,
                    grayTint = MaterialTheme.colorScheme.onSurface,
                    accountViewModel = accountViewModel,
                    nav,
                )
            }
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        onDescription = {
            Text(
                text = card.description ?: stringRes(Res.string.community_about_topic, card.name),
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                modifier = HalfTopPadding,
            )
        },
        onBottomRow = {
            LoadModerators(card.moderators, baseNote, accountViewModel) { participantUsers ->
                if (participantUsers.isNotEmpty()) {
                    Gallery(participantUsers, HalfTopPadding, accountViewModel, nav)
                }
            }
        },
    )
}

@Composable
fun LoadModerators(
    moderators: ImmutableList<String>,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable (ImmutableList<User>) -> Unit,
) {
    var participantUsers by remember {
        mutableStateOf<ImmutableList<User>>(
            persistentListOf(),
        )
    }

    // Keying by the note id + moderator identity keeps this effect from
    // restarting on every recomposition (reaction/zap updates would otherwise
    // reallocate `moderators` and rerun the expensive traversal below).
    LaunchedEffect(key1 = baseNote.idHex, key2 = moderators) {
        launch(Dispatchers.IO) {
            val authorHex = baseNote.author?.pubkeyHex
            val hosts =
                moderators.mapNotNull { part ->
                    if (part != authorHex) {
                        LocalCache.checkGetOrCreateUser(part)
                    } else {
                        null
                    }
                }
            val hostSet = hosts.toSet()

            val topFilter = accountViewModel.account.liveDiscoveryFollowLists.value
            val discoveryTopFilterAuthors =
                when (topFilter) {
                    is AuthorsByOutboxTopNavFilter -> topFilter.authors
                    is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
                    is AllFollowsByOutboxTopNavFilter -> topFilter.authors
                    is SingleCommunityTopNavFilter -> topFilter.authors
                    is AuthorsByProxyTopNavFilter -> topFilter.authors
                    is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
                    is AllFollowsByProxyTopNavFilter -> topFilter.authors
                    else -> null
                }

            val builder = ParticipantListBuilder()
            val allParticipants =
                builder.followsThatParticipateOn(baseNote, discoveryTopFilterAuthors) - hostSet

            val newParticipantUsers =
                if (discoveryTopFilterAuthors == null) {
                    val allFollows = accountViewModel.account.kind3FollowList.flow.value.authors
                    val followingParticipants =
                        builder.followsThatParticipateOn(baseNote, allFollows) - hostSet

                    (hosts + followingParticipants + (allParticipants - followingParticipants))
                        .toImmutableList()
                } else {
                    (hosts + allParticipants).toImmutableList()
                }

            if (!equalImmutableLists(newParticipantUsers, participantUsers)) {
                participantUsers = newParticipantUsers
            }
        }
    }

    content(participantUsers)
}
