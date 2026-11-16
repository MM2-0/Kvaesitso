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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable

/** The DM protocols whose messages live in the [ChatroomList], each behind its own Settings › Messages toggle. */
val DM_CHAT_FEED_TYPES: Set<ChatFeedType> = setOf(ChatFeedType.NIP04, ChatFeedType.NIP17)

/**
 * The Settings › Messages toggle that governs this DM: kind 4 is NIP-04, the gift-wrapped kind 14/15
 * rumors are NIP-17. Null for any other room message, so a new DM kind has to be mapped here
 * deliberately instead of silently riding the NIP-17 switch.
 */
fun ChatroomKeyable.chatFeedType(): ChatFeedType? =
    when (this) {
        is EncryptedDmEvent -> ChatFeedType.NIP04
        is BaseDMGroupEvent -> ChatFeedType.NIP17
        else -> null
    }
