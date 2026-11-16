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
package com.vitorpamplona.amethyst.commons.util

import com.vitorpamplona.amethyst.commons.notices.ActionFailed
import com.vitorpamplona.amethyst.commons.notices.UserNotice
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * `BaseAccountViewModel.launchSigner` and `runOnIO` run on viewModelScope,
 * which has no exception handler: anything that escapes them kills the app. Bad input from a
 * relay or another app (Kite's parse errors, a NIP-55 signer's malformed reply) throws
 * [IllegalArgumentException], so the guards they run their work in must contain it.
 */
class SignerErrorsTest {
    @Test
    fun notifySignerErrorsReportsUnreadableData() =
        runTest {
            val notices = mutableListOf<UserNotice>()

            notifySignerErrors({ notices.add(it) }) { Event.fromJson("not json") }

            assertIs<ActionFailed>(notices.single())
        }

    @Test
    fun logUnusableDataContainsUnreadableData() =
        runTest {
            // Throwing here fails the test.
            logUnusableData("test") { Event.fromJson("[\"results\"") }
        }
}
