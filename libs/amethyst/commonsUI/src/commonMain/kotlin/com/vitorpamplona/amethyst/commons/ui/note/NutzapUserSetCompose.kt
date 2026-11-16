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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.hashtags.Cashu
import com.vitorpamplona.amethyst.commons.hashtags.CustomHashTagIcons
import com.vitorpamplona.amethyst.commons.model.ZapAmountCommentNotification
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.notifications.NutzapUserSetCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nutzap
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.AboutDisplay
import com.vitorpamplona.amethyst.commons.ui.note.AuthorGalleryZaps
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.claimedSatsTotal
import com.vitorpamplona.quartz.utils.BigDecimal
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Per-sender NIP-61 nutzap aggregate card — mirrors [ZapUserSetCompose] but
 * renders the cashu icon instead of the lightning bolt so the user sees the
 * rail at a glance. Built from the kind:9321 events directly: amount comes
 * from each event's parsed proof total, sender from the event's pubkey.
 * No NIP-44 decryption needed — nutzap is plaintext over the wire.
 */
@Composable
fun NutzapUserSetCompose(
    nutzapSetCard: NutzapUserSetCard,
    isInnerNote: Boolean = false,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = nutzapSetCard.createdAt,
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    val authorComments: ImmutableList<ZapAmountCommentNotification> =
        remember(nutzapSetCard.nutzapEvents) {
            nutzapSetCard.nutzapEvents
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

    Column(
        modifier =
            Modifier
                .background(backgroundColor.value)
                .clickable {
                    nav.nav(routeFor(nutzapSetCard.user))
                },
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (!isInnerNote) 12.dp else 0.dp,
                    end = if (!isInnerNote) 12.dp else 0.dp,
                    top = 10.dp,
                ),
        ) {
            if (!isInnerNote) {
                Box(
                    modifier = Size55Modifier,
                ) {
                    Icon(
                        imageVector = CustomHashTagIcons.Cashu,
                        contentDescription = stringRes(Res.string.nutzap),
                        modifier = Modifier.size(Size25dp).align(Alignment.TopEnd),
                        // Tint the monochrome cashu outline brand orange so it
                        // matches the lightning ZappedIcon in the zap set card.
                        tint = BitcoinOrange,
                    )
                }
            }

            Column(modifier = Modifier) {
                Row(Modifier.fillMaxWidth()) {
                    AuthorGalleryZaps(authorComments, backgroundColor, nav, accountViewModel)
                }

                Spacer(DoubleVertSpacer)

                Row(
                    Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserPicture(
                        nutzapSetCard.user,
                        Size55dp,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )

                    Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UsernameDisplay(nutzapSetCard.user, accountViewModel = accountViewModel)
                        }
                        AboutDisplay(nutzapSetCard.user, accountViewModel)
                    }
                }

                Spacer(DoubleVertSpacer)
            }
        }
    }
}
