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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.profile.reports.dal.UserProfileReportFeedViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun TabReports(
    baseUser: User,
    feedViewModel: UserProfileReportFeedViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxHeight()) {
        val items by feedViewModel.followersFlow.collectAsStateWithLifecycle()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = FeedPadding,
            state = rememberLazyListState(),
        ) {
            itemsIndexed(
                items,
                key = { _, item -> item.idHex },
                contentType = { _, item -> item.event?.kind ?: -1 },
            ) { _, item ->
                Row(Modifier.fillMaxWidth().animateItem()) {
                    NoteCompose(
                        item,
                        modifier = Modifier.fillMaxWidth(),
                        routeForLastRead = null,
                        isBoostedNote = false,
                        isHiddenFeed = false,
                        quotesLeft = 3,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }

                HorizontalDivider(
                    thickness = DividerThickness,
                )
            }
        }
    }
}
