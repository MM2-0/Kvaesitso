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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent

/**
 * Where the account's Marmot processing reports what the user should hear about. A Welcome has no
 * `p` tag and a group message arrives inside a gift wrap, so the cache-observer notification path
 * never sees either; the decrypting side calls this instead. The app shows system notifications; a
 * host without them passes [None].
 */
interface MarmotGroupNotifier {
    /** "You've been added to <group>": [event] was just accepted for [account]. */
    suspend fun notifyWelcome(
        event: WelcomeEvent,
        account: Account,
    )

    /** A new chat message, [innerEvent], arrived in the Marmot group [nostrGroupId] for [account]. */
    suspend fun notifyGroupMessage(
        innerEvent: ChatEvent,
        nostrGroupId: String,
        account: Account,
    )

    /** Notifies nothing (previews, tests). */
    object None : MarmotGroupNotifier {
        override suspend fun notifyWelcome(
            event: WelcomeEvent,
            account: Account,
        ) = Unit

        override suspend fun notifyGroupMessage(
            innerEvent: ChatEvent,
            nostrGroupId: String,
            account: Account,
        ) = Unit
    }
}
