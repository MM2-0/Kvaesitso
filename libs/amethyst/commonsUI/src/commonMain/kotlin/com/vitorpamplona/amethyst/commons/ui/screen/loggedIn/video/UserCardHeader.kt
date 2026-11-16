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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.commons.ui.theme.Size35dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun UserCardHeader(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
    showTimeAgo: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    val route = routeFor(baseNote, accountViewModel.account)
                    if (route != null) {
                        nav.nav(route)
                    }
                }.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NoteAuthorPicture(
            baseNote = baseNote,
            size = Size35dp,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        Column(modifier = Modifier.weight(1f)) {
            NoteUsernameDisplay(
                baseNote = baseNote,
                weight = Modifier.fillMaxWidth(),
                accountViewModel = accountViewModel,
            )
        }

        // The header time-ago is the note's createdAt (publication time). For most card types
        // that's a meaningful "posted 5h ago" affordance. For calendar appointments and
        // collections, the user cares about the event time, not when the metadata was
        // published — showing both was confusing, so calendar cards hide this one.
        if (showTimeAgo) TimeAgo(baseNote)

        MoreOptionsButton(
            baseNote = baseNote,
            editState = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}
