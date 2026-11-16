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
package com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeToMessage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.error_dialog_talk_to_user
import com.vitorpamplona.amethyst.commons.resources.error_dialog_talk_to_user_name
import com.vitorpamplona.amethyst.commons.resources.error_dialog_zap_error
import com.vitorpamplona.amethyst.commons.resources.ic_dm
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.MultiErrorToastMsg
import com.vitorpamplona.amethyst.commons.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.painterRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size30dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size40dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
@Preview
fun ErrorListPreview() {
    val accountViewModel = mockAccountViewModel()

    var user1: User? = null
    var user2: User? = null
    var user3: User? = null

    runBlocking {
        withContext(Dispatchers.IO) {
            user1 = LocalCache.getOrCreateUser("460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c")
            user2 = LocalCache.getOrCreateUser("ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b")
            user3 = LocalCache.getOrCreateUser("7eb29c126b3628077e2e3d863b917a56b74293aa9d8a9abc26a40ba3f2866baf")
        }
    }

    val model = MultiErrorToastMsg(Res.string.error_dialog_zap_error)
    model.add("Could not fetch invoice from https://minibits.cash/.well-known/lnurlp/victorieeman: There are too many unpaid invoices for this name.", user1)
    model.add("No Wallets found to pay a lightning invoice. Please install a Lightning wallet to use zaps.", user2)
    model.add("Could not fetch invoice", user3)

    ThemeComparisonColumn {
        ErrorList(
            model = model,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@Composable
fun ErrorList(
    model: MultiErrorToastMsg,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val errorState by model.errors.collectAsStateWithLifecycle()
    LazyColumn {
        itemsIndexed(errorState) { index, it ->
            ErrorRow(it, accountViewModel, nav)
            if (index < errorState.size - 1) {
                HorizontalDivider(thickness = DividerThickness)
            }
        }
    }
}

@Composable
fun ErrorRow(
    errorState: UserBasedErrorMessage,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        errorState.user?.let {
            Column(Modifier.width(Size40dp), horizontalAlignment = Alignment.Start) {
                UserPicture(it, Size30dp, Modifier, accountViewModel, nav)
                Spacer(StdVertSpacer)
                IconButton(
                    modifier = Size30Modifier,
                    onClick = {
                        nav.nav {
                            routeToMessage(it, errorState.error, accountViewModel = accountViewModel)
                        }
                    },
                ) {
                    val descriptor =
                        it.metadataOrNull()?.flow?.value?.info?.bestName()?.let {
                            stringRes(Res.string.error_dialog_talk_to_user_name, it)
                        } ?: stringRes(Res.string.error_dialog_talk_to_user)

                    Icon(
                        painter = painterRes(Res.drawable.ic_dm, 2),
                        contentDescription = descriptor,
                        modifier = Size20Modifier,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Row(Modifier.weight(1f)) {
            SelectionContainer {
                Text(errorState.error)
            }
        }
    }
}
