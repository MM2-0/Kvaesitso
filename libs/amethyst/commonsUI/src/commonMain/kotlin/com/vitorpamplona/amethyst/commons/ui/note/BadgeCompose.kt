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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.notifications.BadgeCard
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.new_badge_award_notif
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.note.types.AcceptBadgeControls
import com.vitorpamplona.amethyst.commons.ui.note.types.BadgeDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip58Badges.award.BadgeAwardEvent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BadgeCompose(
    likeSetCard: BadgeCard,
    isInnerNote: Boolean = false,
    routeForLastRead: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(likeSetCard.note, accountViewModel)
    val note = noteState.note

    val backgroundColor =
        calculateBackgroundColor(
            createdAt = likeSetCard.createdAt(),
            routeForLastRead = routeForLastRead,
            accountViewModel = accountViewModel,
        )

    Column(
        modifier =
            Modifier
                .background(backgroundColor.value)
                .clickable(
                    onClick = {
                        routeFor(
                            note,
                            accountViewModel.account,
                        )?.let { nav.nav(it) }
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (!isInnerNote) 12.dp else 0.dp,
                    end = if (!isInnerNote) 12.dp else 0.dp,
                    top = 10.dp,
                ),
        ) {
            // Draws the like picture outside the boosted card.
            if (!isInnerNote) {
                Box(
                    modifier = Modifier.width(55.dp).padding(0.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.MilitaryTech,
                        null,
                        modifier = Modifier.size(25.dp).align(Alignment.TopEnd),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(modifier = Modifier.padding(start = if (!isInnerNote) 10.dp else 0.dp)) {
                Row {
                    Text(
                        stringRes(Res.string.new_badge_award_notif),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 5.dp).weight(1f),
                    )

                    Text(
                        timeAgo(note.createdAt()),
                        color = MaterialTheme.colorScheme.placeholderText,
                        maxLines = 1,
                    )

                    MoreOptionsButton(note, null, accountViewModel, nav)
                }

                note.replyTo?.firstOrNull()?.let {
                    BadgeDisplay(baseNote = it, accountViewModel)
                }

                (note.event as? BadgeAwardEvent)?.let { award ->
                    AcceptBadgeControls(award, accountViewModel)
                }
            }
        }
    }
}
