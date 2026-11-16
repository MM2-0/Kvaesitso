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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.about_us
import com.vitorpamplona.amethyst.commons.resources.community_no_descriptor
import com.vitorpamplona.amethyst.commons.resources.edit_community
import com.vitorpamplona.amethyst.commons.resources.guidelines
import com.vitorpamplona.amethyst.commons.resources.join
import com.vitorpamplona.amethyst.commons.resources.leave
import com.vitorpamplona.amethyst.commons.resources.moderators
import com.vitorpamplona.amethyst.commons.resources.owner
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.resources.profile_image
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.resources.quick_action_share_browser_link
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberTextSharer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.commons.ui.screen.LocalDisplaySettings
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.commons.util.equalImmutableLists
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hasHashtags
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.tags.ModeratorTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RenderCommunity(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (baseNote is AddressableNote) {
        Row(
            MaterialTheme.colorScheme.innerPostModifier
                .clickable {
                    routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(Size10dp),
        ) {
            ShortCommunityHeader(
                baseNote = baseNote,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
fun Title(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.W600,
        textAlign = TextAlign.Start,
    )
}

@Composable
fun LongCommunityHeader(
    baseNote: AddressableNote,
    lineModifier: Modifier = Modifier.padding(horizontal = Size10dp, vertical = Size5dp),
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)
    val callbackUri = baseNote.toNostrUri()

    val defaultBackground = MaterialTheme.colorScheme.background
    val background = remember { mutableStateOf(defaultBackground) }

    val description = noteEvent?.description()?.ifBlank { null }
    val guidelines = noteEvent?.rules()?.ifBlank { null }
    val image = noteEvent?.image()?.imageUrl
    val tagList = noteEvent?.tags?.toImmutableListOfLists() ?: EmptyTagList

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(contentAlignment = Alignment.TopEnd) {
            image?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription =
                        stringRes(
                            Res.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.FillWidth,
                    mainImageModifier = Modifier,
                    loadedImageModifier = Modifier.fillMaxWidth(),
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageHeader(baseNote, accountViewModel) },
                )
            } ?: run {
                DefaultImageHeader(baseNote, accountViewModel)
            }
            Box(
                modifier = Modifier.padding(5.dp),
            ) {
                LongCommunityActionOptions(baseNote, accountViewModel, nav)
            }
        }

        Column(lineModifier) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = noteEvent?.name() ?: baseNote.dTag(),
                fontSize = 20.sp,
                fontWeight = FontWeight.W600,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Title(title = stringRes(Res.string.about_us))
            Spacer(modifier = Modifier.height(16.dp))

            Column(Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp)) {
                TranslatableRichTextViewer(
                    content = description ?: stringRes(id = Res.string.community_no_descriptor),
                    id = baseNote.idHex + "description",
                    accountViewModel = accountViewModel,
                ) {
                    RichTextViewer(
                        it,
                        modifier = Modifier.fillMaxWidth(),
                        canPreview = false,
                        quotesLeft = 1,
                        tags = tagList,
                        backgroundColor = background,
                        callbackUri = callbackUri,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            guidelines?.let {
                Spacer(modifier = Modifier.height(24.dp))
                Title(title = stringRes(Res.string.guidelines))
                Spacer(modifier = Modifier.height(16.dp))

                Column(Modifier.padding(16.dp, 0.dp, 16.dp, 0.dp)) {
                    TranslatableRichTextViewer(
                        content = guidelines,
                        id = baseNote.idHex + "guidelines",
                        accountViewModel = accountViewModel,
                    ) {
                        RichTextViewer(
                            it,
                            modifier = Modifier.fillMaxWidth(),
                            canPreview = false,
                            quotesLeft = 1,
                            tags = tagList,
                            backgroundColor = background,
                            callbackUri = callbackUri,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }

            noteEvent?.let {
                if (it.hasHashtags()) {
                    DisplayUncitedHashtags(
                        event = it,
                        content = description ?: "",
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Title(title = stringRes(Res.string.owner))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteAuthorPicture(baseNote, Size25dp, accountViewModel = accountViewModel, nav = nav)
                Spacer(DoubleHorzSpacer)
                NoteUsernameDisplay(baseNote, Modifier.weight(1f), accountViewModel = accountViewModel)
            }

            var participantUsers by
                remember(baseNote) {
                    mutableStateOf<ImmutableList<Pair<ModeratorTag, User>>>(
                        persistentListOf(),
                    )
                }

            LaunchedEffect(key1 = noteEvent) {
                val participants = noteEvent?.moderators()

                if (participants != null) {
                    accountViewModel.loadParticipants(participants) { newParticipantUsers ->
                        val noOwner = newParticipantUsers.filter { it.second != baseNote.author }.toImmutableList()
                        if (!equalImmutableLists(noOwner, participantUsers)) {
                            participantUsers = noOwner
                        }
                    }
                }
            }

            if (participantUsers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Title(title = stringRes(Res.string.moderators))
                Spacer(modifier = Modifier.height(16.dp))

                participantUsers.forEach {
                    Column(Modifier.padding(vertical = 5.dp).clickable { nav.nav(routeFor(it.second)) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ClickableUserPicture(it.second, Size25dp, accountViewModel)
                            Spacer(DoubleHorzSpacer)
                            UsernameDisplay(it.second, Modifier.weight(1f), accountViewModel = accountViewModel)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(15.dp))
        }
    }
}

@Composable
fun ShortCommunityHeader(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)

    Row(verticalAlignment = Alignment.CenterVertically) {
        noteEvent?.image()?.let {
            RobohashFallbackAsyncImage(
                robot = baseNote.idHex,
                model = it.imageUrl,
                contentDescription = stringRes(Res.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = HeaderPictureModifier,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif =
                    accountViewModel.settings.autoPlayVideosFlow
                        .collectAsStateWithLifecycle()
                        .value,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(Size35dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = noteEvent?.name() ?: baseNote.dTag(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .height(Size35dp)
                    .padding(start = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShortCommunityActionOptions(baseNote, accountViewModel, nav)
        }
    }
}

@Composable
fun ShortCommunityHeaderNoActions(
    baseNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val noteEvent by observeNoteEvent<CommunityDefinitionEvent>(baseNote, accountViewModel)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        noteEvent?.image()?.let {
            RobohashFallbackAsyncImage(
                robot = baseNote.idHex,
                model = it.imageUrl,
                contentDescription = stringRes(Res.string.profile_image),
                contentScale = ContentScale.Crop,
                modifier = HeaderPictureModifier,
                loadProfilePicture = LocalDisplaySettings.current.showProfilePictures,
                loadRobohash = LocalDisplaySettings.current.loadRobohash,
                autoPlayGif =
                    accountViewModel.settings.autoPlayVideosFlow
                        .collectAsStateWithLifecycle()
                        .value,
            )
        }

        Column(
            modifier =
                Modifier
                    .padding(start = 10.dp)
                    .height(Size35dp)
                    .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = noteEvent?.name() ?: baseNote.dTag(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ShortCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Spacer(modifier = StdHorzSpacer)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = RowColSpacing,
    ) {
        LikeReaction(
            baseNote = note,
            grayTint = MaterialTheme.colorScheme.onSurface,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
    Spacer(modifier = StdHorzSpacer)
    ZapReaction(
        baseNote = note,
        grayTint = MaterialTheme.colorScheme.onSurface,
        accountViewModel = accountViewModel,
        nav = nav,
    )
    WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
        if (!isFollowing) {
            Spacer(modifier = StdHorzSpacer)
            JoinCommunityButton(accountViewModel, note, nav)
        }
    }
}

@Composable
private fun LongCommunityActionOptions(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row {
        if (note.author?.pubkeyHex == accountViewModel.account.signer.pubKey) {
            EditCommunityButton(note, nav)
        }
        ShareCommunityButton(accountViewModel, note, nav)
        WatchAddressableNoteFollows(note, accountViewModel) { isFollowing ->
            if (isFollowing) {
                LeaveCommunityButton(accountViewModel, note, nav)
            }
        }
    }
}

@Composable
fun EditCommunityButton(
    note: AddressableNote,
    nav: INav,
) {
    FilledTonalIconButton(
        onClick = { nav.nav(Route.EditCommunity(note.address)) },
    ) {
        Icon(
            symbol = MaterialSymbols.Edit,
            modifier = Size18Modifier,
            contentDescription = stringRes(Res.string.edit_community),
        )
    }
}

@Composable
fun WatchAddressableNoteFollows(
    note: AddressableNote,
    accountViewModel: AccountViewModel,
    onFollowChanges: @Composable (Boolean) -> Unit,
) {
    val state by accountViewModel.account.communityList.flowSet
        .collectAsStateWithLifecycle()

    onFollowChanges(state.contains(note.idHex))
}

@Composable
fun JoinCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: INav,
) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { accountViewModel.follow(note) },
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.join), color = Color.White)
    }
}

@Composable
fun LeaveCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: INav,
) {
    FilledTonalButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = { accountViewModel.unfollow(note) },
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(Res.string.leave))
    }
}

@Composable
fun ShareCommunityButton(
    accountViewModel: AccountViewModel,
    note: AddressableNote,
    nav: INav,
) {
    val quickActionShareBrowserLinkStr = stringRes(Res.string.quick_action_share_browser_link)
    val quickActionShareStr = stringRes(Res.string.quick_action_share)
    val sharer = rememberTextSharer()

    FilledTonalIconButton(
        onClick = {
            sharer.share(externalLinkForNote(note), quickActionShareBrowserLinkStr, quickActionShareStr)
        },
    ) {
        Icon(
            symbol = MaterialSymbols.Share,
            modifier = Size18Modifier,
            contentDescription = stringRes(Res.string.quick_action_share),
        )
    }
}
