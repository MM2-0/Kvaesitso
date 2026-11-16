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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip51FollowSets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteAndMap
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GalleryUnloaded
import com.vitorpamplona.amethyst.commons.ui.note.LikeReaction
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ZapReaction
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageBanner
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageBannerBackground
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.FollowSetImageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.RowColSpacing5dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class FollowSetCard(
    val name: String,
    val media: String?,
    val description: String?,
    val users: ImmutableList<HexKey>,
)

@Composable
fun RenderFollowSetThumb(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val card by observeNoteAndMap(baseNote, accountViewModel) {
        val noteEvent = it.event as? StarterPackEvent

        FollowSetCard(
            name = noteEvent?.title()?.ifBlank { null } ?: noteEvent?.dTag() ?: "",
            media = noteEvent?.image()?.ifBlank { null },
            description = noteEvent?.description(),
            users =
                accountViewModel
                    .sortUsersSync(
                        noteEvent?.followIds() ?: emptyList(),
                    ).toImmutableList(),
        )
    }

    RenderFollowSetThumb(
        card,
        baseNote,
        accountViewModel,
        nav,
    )
}

@Preview
@Composable
fun RenderFollowSetThumbPreview() {
    val accountViewModel = mockAccountViewModel()
    val nav = EmptyNav()

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
            description = followCard.description(),
            users = followCard.followIds().toImmutableList(),
        )

    ThemeComparisonColumn {
        RenderFollowSetThumb(
            card = card,
            baseNote = LocalCache.getOrCreateNote(followCard.id),
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun RenderFollowSetThumb(
    card: FollowSetCard,
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.BottomStart,
        ) {
            card.media?.let {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription =
                        stringRes(
                            Res.string.preview_card_image_for,
                            it,
                        ),
                    contentScale = ContentScale.Crop,
                    mainImageModifier = FollowSetImageModifier,
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageBannerBackground(baseNote, accountViewModel) },
                    onError = { DefaultImageBanner(baseNote, accountViewModel) },
                )
            } ?: run { DefaultImageBanner(baseNote, accountViewModel, FollowSetImageModifier) }

            GalleryUnloaded(card.users, StdPadding, accountViewModel, nav)
        }

        Spacer(modifier = DoubleVertSpacer)

        Row(
            verticalAlignment = CenterVertically,
            horizontalArrangement = RowColSpacing5dp,
        ) {
            Text(
                text = card.name,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = StdHorzSpacer)
            LikeReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav,
            )
            Spacer(modifier = StdHorzSpacer)
            ZapReaction(
                baseNote = baseNote,
                grayTint = MaterialTheme.colorScheme.onSurface,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        baseNote.author?.let { author ->
            Spacer(modifier = DoubleVertSpacer)
            Row(
                verticalAlignment = CenterVertically,
                horizontalArrangement = RowColSpacing5dp,
            ) {
                UserPicture(author, Size25dp, accountViewModel = accountViewModel, nav = nav)
                UsernameDisplay(author, fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
            }
        }
    }
}
