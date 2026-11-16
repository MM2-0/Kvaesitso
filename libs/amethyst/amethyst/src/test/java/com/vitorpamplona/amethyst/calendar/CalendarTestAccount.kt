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
package com.vitorpamplona.amethyst.calendar

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.LiveHiddenUsers
import com.vitorpamplona.amethyst.commons.model.nip51Lists.HiddenUsersState
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavFilter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An account that sees everything. The appointments feed reads exactly two things off an
 * [Account] — the calendars top-nav filter and the hidden-user sets — so this is the whole
 * surface a calendar test needs.
 */
fun seeEverythingAccount(): Account {
    val hiddenUsers =
        mockk<HiddenUsersState>().also {
            every { it.flow } returns
                MutableStateFlow(
                    LiveHiddenUsers(
                        showSensitiveContent = true,
                        hiddenWordsCase = emptyList(),
                        hiddenUsersHashCodes = emptySet(),
                        spammersHashCodes = emptySet(),
                    ),
                )
        }

    val global =
        GlobalTopNavFilter(
            outboxRelays = MutableStateFlow(emptySet()),
            proxyRelays = MutableStateFlow(emptySet()),
            relayFeeds = MutableStateFlow(emptySet()),
        )

    return mockk<Account>().also {
        every { it.liveCalendarsFollowLists } returns MutableStateFlow(global)
        every { it.hiddenUsers } returns hiddenUsers
    }
}
