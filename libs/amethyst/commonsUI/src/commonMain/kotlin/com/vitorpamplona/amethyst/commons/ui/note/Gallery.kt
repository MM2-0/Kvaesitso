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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.commons.ui.theme.Size25dp
import com.vitorpamplona.amethyst.commons.util.showCount
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Gallery(
    users: ImmutableList<User>,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
    maxPictures: Int = 6,
) {
    FlowRow(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
    ) {
        users.take(maxPictures).forEach {
            ClickableUserPicture(
                it,
                Size25dp,
                accountViewModel,
                onClick = {
                    nav.nav(routeFor(it))
                },
            )
        }

        if (users.size > maxPictures) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(Size25dp).clip(shape = CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = "+" + showCount(users.size - maxPictures),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GalleryUnloaded(
    users: ImmutableList<HexKey>,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
    maxPictures: Int = 6,
) {
    FlowRow(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
    ) {
        users.take(maxPictures).forEach {
            ClickableUserPicture(
                it,
                Size25dp,
                accountViewModel,
                onClick = {
                    nav.nav {
                        routeForUser(it)
                    }
                },
            )
        }

        if (users.size > maxPictures) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(Size25dp).clip(shape = CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = "+" + showCount(users.size - maxPictures),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
