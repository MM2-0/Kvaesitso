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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemMessage
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.privateChats.isSubjectOnlyChatMessage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation_to
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation_to_you
import com.vitorpamplona.amethyst.commons.resources.chat_system_renamed_conversation_you
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.ChatTimeWithDelivery
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18dp
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent

/**
 * A rename ([isSubjectOnlyChatMessage]) as a centered system line — "Alice renamed the
 * conversation" with Alice's avatar — under the subject divider that carries the new name. The
 * divider alone left an empty bubble below it whose only content was the time.
 *
 * Unlike the other system lines this one is a real message with a sender and a delivery, so the
 * pill keeps the bubble's tappable time and, on our own renames, its relay-acceptance ticks.
 *
 * Inside a reply quote there is no divider to name the subject, so the sentence includes it and
 * the time is dropped, as on any quoted message.
 */
@Composable
fun RenderChatSubjectChange(
    note: Note,
    innerQuote: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? ChatMessageEvent ?: return
    val isLoggedInUser = remember(event.pubKey) { accountViewModel.isLoggedUser(event.pubKey) }

    val text =
        if (innerQuote) {
            val subject = remember(event) { event.subject() ?: "" }
            if (isLoggedInUser) {
                stringRes(Res.string.chat_system_renamed_conversation_to_you, subject)
            } else {
                stringRes(Res.string.chat_system_renamed_conversation_to, observeUserNameByHex(event.pubKey, accountViewModel), subject)
            }
        } else {
            if (isLoggedInUser) {
                stringRes(Res.string.chat_system_renamed_conversation_you)
            } else {
                stringRes(Res.string.chat_system_renamed_conversation, observeUserNameByHex(event.pubKey, accountViewModel))
            }
        }

    ChatSystemMessage(
        text = text,
        onClick = { nav.nav(Route.Profile(event.pubKey)) },
        leading = {
            UserPicture(
                userHex = event.pubKey,
                size = Size18dp,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
        trailing =
            if (innerQuote) {
                null
            } else {
                { ChatTimeWithDelivery(note, isLoggedInUser, accountViewModel, nav) }
            },
    )
}
