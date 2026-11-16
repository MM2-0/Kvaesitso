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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.ephemChat.metadata

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.channel_name
import com.vitorpamplona.amethyst.commons.resources.group_relay
import com.vitorpamplona.amethyst.commons.resources.my_awesome_group
import com.vitorpamplona.amethyst.commons.resources.relay_chat
import com.vitorpamplona.amethyst.commons.resources.relay_chat_explainer
import com.vitorpamplona.amethyst.commons.resources.relay_chat_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel

@Composable
fun NewEphemeralChatScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: NewEphemeralChatMetaViewModel = rememberViewModel { NewEphemeralChatMetaViewModel() }
    postViewModel.load(accountViewModel.account)

    ChannelMetadataScaffold(
        postViewModel = postViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Preview
@Composable
private fun DialogContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val postViewModel: NewEphemeralChatMetaViewModel = rememberViewModel { NewEphemeralChatMetaViewModel() }
    postViewModel.load(accountViewModel.account)

    ThemeComparisonColumn {
        ChannelMetadataScaffold(
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelMetadataScaffold(
    postViewModel: NewEphemeralChatMetaViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            CreatingTopBar(
                titleRes = Res.string.relay_chat,
                isActive = postViewModel::canPost,
                onCancel = {
                    nav.popBack()
                },
                onPost = {
                    postViewModel.buildRoom()?.let {
                        nav.popBack()
                        nav.nav(routeFor(it))
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding(),
                ).consumeWindowInsets(pad)
                .imePaddingSafe(),
        ) {
            item {
                SettingsCategory(
                    Res.string.relay_chat_title,
                    Res.string.relay_chat_explainer,
                    SettingsCategoryFirstModifier,
                )

                RelayUrl(postViewModel)

                Spacer(modifier = DoubleVertSpacer)

                ChannelName(postViewModel)
            }
        }
    }
}

@Composable
private fun ChannelName(postViewModel: NewEphemeralChatMetaViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.channel_name)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.channelName.value,
        onValueChange = { postViewModel.channelName.value = it },
        placeholder = {
            Text(
                text = stringRes(Res.string.my_awesome_group),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}

@Composable
private fun RelayUrl(postViewModel: NewEphemeralChatMetaViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(Res.string.group_relay)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.relayUrl.value,
        onValueChange = { postViewModel.relayUrl.value = it },
        placeholder = {
            Text(
                text = "nos.lol",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}
