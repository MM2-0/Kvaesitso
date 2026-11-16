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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEventAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.follow_list_item_label
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GalleryUnloaded
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip51FollowSets.FollowSetCard
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.FollowSetImageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.blackTagModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayStarterPack(
    baseNote: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card =
        observeNoteEventAndMap(baseNote, accountViewModel) { event: StarterPackEvent? ->
            if (event == null) {
                FollowSetCard(
                    name = "",
                    media = "",
                    description = "",
                    users = persistentListOf(),
                )
            } else {
                FollowSetCard(
                    name = event.title()?.ifBlank { null } ?: event.dTag(),
                    media = event.image()?.ifBlank { null },
                    description = event.description(),
                    users = accountViewModel.sortUsersSync(event.followIds()).toImmutableList(),
                )
            }
        }

    RenderFollowSetThumbEmbed(
        card.value,
        baseNote,
        makeItShort,
        accountViewModel,
        nav,
    )
}

@Composable
fun RenderFollowSetThumbEmbed(
    card: FollowSetCard,
    baseNote: Note,
    makeItShort: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth().clickable {
                nav.nav { routeFor(baseNote, accountViewModel.account) }
            },
        verticalArrangement = SpacedBy5dp,
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
        ) {
            card.media?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(Res.string.preview_card_image_for, it),
                    contentScale = ContentScale.Crop,
                    mainImageModifier = Modifier,
                    loadedImageModifier = FollowSetImageModifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageHeader(baseNote, accountViewModel) },
                )
            } ?: run { DefaultImageHeader(baseNote, accountViewModel, FollowSetImageModifier) }

            GalleryUnloaded(card.users, StdPadding, accountViewModel, nav)
        }

        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringRes(Res.string.follow_list_item_label),
                color = MaterialTheme.colorScheme.background,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = MaterialTheme.colorScheme.blackTagModifier,
            )
        }

        if (!makeItShort) {
            card.description?.let {
                val defaultBackground = MaterialTheme.colorScheme.background
                val background = remember { mutableStateOf(defaultBackground) }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = true,
                    quotesLeft = 2,
                    modifier = Modifier.fillMaxWidth(),
                    tags = baseNote.event?.tags?.toImmutableListOfLists() ?: EmptyTagList,
                    backgroundColor = background,
                    id = it,
                    callbackUri = null,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Preview
@Composable
fun RenderFollowSetThumbPreview() {
    val accountViewModel = mockAccountViewModel()

    val followCard =
        StarterPackEvent(
            id = "eca31634fce7c9068b56fa8db9f387da70bdcceb3986a77ca1a9844f3128eb5f",
            pubKey = "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da",
            createdAt = 1761736286,
            tags =
                arrayOf(
                    arrayOf("title", "Retro Computer Fans"),
                    arrayOf("d", "xmbspe8rddsq"),
                    arrayOf("image", "https://blog.johnnovak.net/2022/04/15/achieving-period-correct-graphics-in-personal-computer-emulators-part-1-the-amiga/img/dream-setup.jpg"),
                    arrayOf("p", "3c39a7b53dec9ac85acf08b267637a9841e6df7b7b0f5e2ac56a8cf107de37da"),
                    arrayOf("p", "9a9a4aa0e43e57873380ab22e8a3df12f3c4cf5bb3a804c6e3fed0069a6e2740"),
                    arrayOf("p", "4f5dd82517b11088ce00f23d99f06fe8f3e2e45ecf47bc9c2f90f34d5c6f7382"),
                    arrayOf("p", "ac92102a2ecb873c488e0125354ef5a97075a16198668c360eda050007ed42cd"),
                    arrayOf("p", "47f54409a4620eb35208a3bc1b53555bf3d0656b246bf0471a93208e20672f6f"),
                    arrayOf("p", "2624911545afb7a2b440cf10f5c69308afa33aae26fca664d8c94623dc0f1baf"),
                    arrayOf("p", "6641f26f5c59f7010dbe3e42e4593398e27c087497cb7d20e0e7633a17e48a94"),
                    arrayOf("description", "Retro computer fans and enthusiasts "),
                ),
            content = "",
            sig = "3aa388edafad151e81cb0228fe04e115dbbcaa851c666bfe3c8740b6cd99575f0fc3ba2d47acda86f7626564a05e9dbc05ef452a7bd0ac00f828dbad0e1bae6c",
        )

    LocalCache.justConsume(followCard, null, false)

    val card =
        FollowSetCard(
            name = followCard.title()?.ifBlank { null } ?: followCard.dTag(),
            media = followCard.image()?.ifBlank { null },
            description = followCard.description()?.ifBlank { null },
            users = followCard.followIds().toImmutableList(),
        )

    ThemeComparisonColumn {
        RenderFollowSetThumbEmbed(
            card = card,
            baseNote = LocalCache.getOrCreateNote(followCard.id),
            makeItShort = false,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}
