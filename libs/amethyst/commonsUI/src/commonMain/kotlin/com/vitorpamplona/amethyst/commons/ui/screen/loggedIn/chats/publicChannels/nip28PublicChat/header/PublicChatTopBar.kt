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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.LongPublicChatChannelHeader
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.header.ShortPublicChatChannelHeader
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PublicChatTopBar(
    baseChannel: PublicChatChannel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TopBarExtensibleWithBackButton(
        title = {
            ShortPublicChatChannelHeader(
                baseChannel = baseChannel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        extendableRow = {
            LongPublicChatChannelHeader(baseChannel = baseChannel, accountViewModel = accountViewModel, nav = nav)
        },
        // No back arrow when opened as a bottom-nav tab (nothing to pop); the bottom bar shows instead.
        nav = nav,
    )
}
