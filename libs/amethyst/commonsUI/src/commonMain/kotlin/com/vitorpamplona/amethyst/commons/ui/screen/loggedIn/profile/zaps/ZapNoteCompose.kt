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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.zaps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.profile.zaps.dal.ZapAmount
import com.vitorpamplona.amethyst.commons.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserActionOptions
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.commons.ui.theme.Size55dp
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.util.showAmountInteger
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.utils.BigDecimal

@Composable
fun ZapNoteCompose(
    zap: ZapAmount,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier.clickable(
                onClick = { zap.user.let { nav.nav(routeFor(it)) } },
            ),
        verticalArrangement = Arrangement.Center,
    ) {
        RenderZapNoteSlim(zap, accountViewModel, nav)
    }
}

@Preview
@Composable
fun RenderZapNoteSlimPreview() {
    val accountViewModel = mockAccountViewModel()

    val user1: User = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")

    ThemeComparisonColumn {
        RenderZapNoteSlim(
            ZapAmount(user1, BigDecimal(10)),
            accountViewModel,
            EmptyNav(),
        )
    }
}

@Composable
private fun RenderZapNoteSlim(
    zap: ZapAmount,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    SlimListItem(
        leadingContent = {
            UserPicture(zap.user, Size55dp, accountViewModel = accountViewModel, nav = nav)
        },
        headlineContent = {
            UsernameDisplay(zap.user, accountViewModel = accountViewModel)
        },
        supportingContent = {
            Text(
                text = remember { showAmountInteger(zap.amount) },
                color = BitcoinOrange,
                fontSize = 20.sp,
                fontWeight = FontWeight.W500,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserActionOptions(zap.user, accountViewModel, nav)
            }
        },
    )
}
