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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.more_options
import com.vitorpamplona.amethyst.commons.resources.search_button
import com.vitorpamplona.amethyst.commons.search.SearchSeed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.searchRouteFor
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.commons.ui.theme.ZeroPadding
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileTopBar(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    ShorterTopAppBar(
        title = {},
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        navigationIcon = {
            if (nav.canPop()) {
                Button(
                    modifier = Modifier.padding(start = 10.dp).size(30.dp),
                    onClick = nav::popBack,
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                        ),
                    contentPadding = ZeroPadding,
                ) {
                    ArrowBackIcon(tint = MaterialTheme.colorScheme.placeholderText)
                }
            }
        },
        actions = {
            var popupExpanded by remember { mutableStateOf(false) }

            // Search this person's posts. Styled as the other floating buttons here rather than a
            // plain icon: this bar sits over the profile banner, where a bare icon can land on an
            // image and disappear.
            Button(
                modifier = Modifier.padding(end = 10.dp).size(30.dp),
                onClick = { nav.nav(searchRouteFor(SearchSeed.byAuthor(baseUser.pubkeyHex))) },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                contentPadding = ZeroPadding,
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    symbol = MaterialSymbols.Search,
                    contentDescription = stringRes(Res.string.search_button),
                )
            }

            Button(
                modifier = Modifier.padding(end = 10.dp).size(30.dp),
                onClick = { popupExpanded = true },
                shape = ButtonBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                contentPadding = ZeroPadding,
            ) {
                Icon(
                    tint = MaterialTheme.colorScheme.placeholderText,
                    symbol = MaterialSymbols.MoreVert,
                    contentDescription = stringRes(Res.string.more_options),
                )

                UserProfileDropDownMenu(
                    baseUser,
                    popupExpanded,
                    { popupExpanded = false },
                    accountViewModel,
                )
            }
        },
    )
}
