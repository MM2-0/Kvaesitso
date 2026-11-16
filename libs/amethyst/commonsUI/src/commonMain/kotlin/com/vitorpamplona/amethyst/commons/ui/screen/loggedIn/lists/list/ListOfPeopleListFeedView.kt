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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.lists.list.FollowPackViewModel
import com.vitorpamplona.amethyst.commons.lists.list.PeopleListViewModel
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip51Lists.ui.PeopleListItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.discover_follows
import com.vitorpamplona.amethyst.commons.resources.discover_follows_explainer
import com.vitorpamplona.amethyst.commons.resources.feed_is_empty
import com.vitorpamplona.amethyst.commons.resources.follow_sets
import com.vitorpamplona.amethyst.commons.resources.follow_sets_explainer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

@Composable
fun AllPeopleListFeedView(
    peopleListModel: PeopleListViewModel,
    followPackModel: FollowPackViewModel,
    listState: LazyListState = rememberLazyListState(),
    nav: INav,
) {
    val peopleListFeedState by peopleListModel.listFlow().collectAsStateWithLifecycle()
    val followPackFeedState by followPackModel.listFlow().collectAsStateWithLifecycle()

    LazyColumn(
        state = listState,
        contentPadding = FeedPadding,
    ) {
        stickyHeader {
            Row(
                modifier = MaxWidthWithHorzPadding,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy5dp,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.follow_sets),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.follow_sets_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                NewListButton(
                    onClick = {
                        nav.nav(Route.PeopleListMetadataEdit())
                    },
                )
            }
        }
        if (peopleListFeedState.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringRes(Res.string.feed_is_empty),
                        modifier = Modifier.padding(vertical = Size20dp),
                    )
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        } else {
            itemsIndexed(peopleListFeedState, key = { _, item -> item.identifierTag }) { _, followSet ->
                PeopleListItem(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .animateItem(),
                    peopleList = followSet,
                    onClick = { nav.nav(Route.MyPeopleListView(followSet.identifierTag)) },
                    onEditMetadata = { nav.nav(Route.PeopleListMetadataEdit(followSet.identifierTag)) },
                    onClone = { cloneName, cloneDescription -> peopleListModel.cloneItem(followSet, cloneName, cloneDescription) },
                    onDelete = { peopleListModel.deleteItem(followSet) },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
        stickyHeader {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = SpacedBy5dp,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringRes(Res.string.discover_follows),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringRes(Res.string.discover_follows_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
                NewListButton(
                    onClick = {
                        nav.nav(Route.FollowPackMetadataEdit())
                    },
                )
            }
        }
        if (followPackFeedState.isEmpty()) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringRes(Res.string.feed_is_empty),
                        modifier = Modifier.padding(vertical = Size20dp),
                    )
                    HorizontalDivider(thickness = DividerThickness)
                }
            }
        } else {
            itemsIndexed(followPackFeedState, key = { _, item -> item.identifierTag }) { _, followSet ->
                PeopleListItem(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .animateItem(),
                    peopleList = followSet,
                    onClick = { nav.nav(Route.MyFollowPackView(followSet.identifierTag)) },
                    onEditMetadata = { nav.nav(Route.FollowPackMetadataEdit(followSet.identifierTag)) },
                    onClone = { cloneName, cloneDescription -> followPackModel.cloneItem(followSet, cloneName, cloneDescription) },
                    onDelete = { followPackModel.deleteItem(followSet) },
                )
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}
