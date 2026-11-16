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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.DefaultInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.InlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Inline-quote renderer provided by [ChatroomMessageCompose] so that a chat
 * message quoted inside another chat message's text keeps the chat reply
 * design ([ChatroomMessageCompose] with `innerQuote = true`, the same renderer
 * the reply row uses) instead of the default quoted-note card. Non-chat events
 * (and quotes whose event hasn't arrived yet) fall through to
 * [DefaultInlineQuoteRenderer].
 *
 * The callbacks are the host bubble's, so tapping a quote of a message in the
 * same room scrolls to it, exactly like tapping a reply quote.
 */
fun chatInlineQuoteRenderer(
    onWantsToReply: (Note) -> Unit,
    onWantsToEditDraft: (Note) -> Unit,
    onScrollToNote: ((Note) -> Unit)?,
) = InlineQuoteRenderer { note, quotesLeft, backgroundColor, accountViewModel, nav ->
    WatchNoteEvent(
        baseNote = note,
        onNoteEventFound = {
            if (isChatEvent(note.event)) {
                ChatroomMessageCompose(
                    baseNote = note,
                    routeForLastRead = null,
                    innerQuote = true,
                    parentBackgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    onWantsToReply = onWantsToReply,
                    onWantsToEditDraft = onWantsToEditDraft,
                    onScrollToNote = onScrollToNote,
                )
            } else {
                DefaultInlineQuoteRenderer.Render(note, quotesLeft, backgroundColor, accountViewModel, nav)
            }
        },
        onBlank = {
            DefaultInlineQuoteRenderer.Render(note, quotesLeft, backgroundColor, accountViewModel, nav)
        },
        accountViewModel = accountViewModel,
    )
}

private fun isChatEvent(event: Event?) =
    event is ChatroomKeyable ||
        event is ChatEvent ||
        event is IsInPublicChatChannel ||
        event is LiveActivitiesChatMessageEvent ||
        event is EphemeralChatEvent
