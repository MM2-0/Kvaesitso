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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.lists.display

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.remove_user_from_the_list
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserComposeNoAction
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfHalfHorzModifier
import com.vitorpamplona.amethyst.commons.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun PeopleListView(
    memberList: ImmutableList<User>,
    modifier: Modifier = Modifier,
    onDeleteUser: (User) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier,
        contentPadding = FeedPadding,
        state = listState,
    ) {
        itemsIndexed(memberList, key = { _, item -> "u" + item.pubkeyHex }) { _, item ->
            PeopleListItem(
                modifier = Modifier.animateContentSize(),
                user = item,
                accountViewModel = accountViewModel,
                nav = nav,
                onDeleteUser = onDeleteUser,
            )
        }
    }
}

@Composable
fun PeopleListItem(
    modifier: Modifier = Modifier,
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDeleteUser: (User) -> Unit,
) {
    Column(
        modifier = modifier,
    ) {
        Row(HalfHalfHorzModifier) {
            UserComposeNoAction(
                user,
                modifier = HalfPadding.weight(1f, fill = false),
                accountViewModel = accountViewModel,
                nav = nav,
            )
            OutlinedIconButton(
                onClick = {
                    onDeleteUser(user)
                },
                modifier = HalfPadding,
            ) {
                Icon(
                    symbol = MaterialSymbols.PersonRemove,
                    contentDescription = stringRes(Res.string.remove_user_from_the_list),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        HorizontalDivider(thickness = DividerThickness)
    }
}
