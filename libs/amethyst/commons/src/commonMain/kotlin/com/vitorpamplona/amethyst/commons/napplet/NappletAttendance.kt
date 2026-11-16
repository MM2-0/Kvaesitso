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
package com.vitorpamplona.amethyst.commons.napplet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The broker's view of which surfaces the user is looking at right now, as each surface reports it.
 *
 * The surfaces hold a page's acting requests (sign, encrypt, decrypt…) themselves while nobody is looking,
 * but relay reads decrypt on the broker side: an encrypted event a relay pushes to a parked page's
 * subscription, or returns for its query, would be decrypted with the user's key and handed over unwatched.
 * The broker checks here first and waits until the page is attended again.
 *
 * Unattended until a surface says otherwise, so one that never reports can't read unwatched.
 */
class NappletAttendance<K : Any> {
    private val attended = MutableStateFlow<Set<K>>(emptySet())

    fun set(
        owner: K,
        isAttended: Boolean,
    ) = attended.update { if (isAttended) it + owner else it - owner }

    fun isAttended(owner: K): Boolean = owner in attended.value

    /** Waits up to [timeoutMs] for [owner] to be attended; false when it wasn't in time. */
    suspend fun awaitAttended(
        owner: K,
        timeoutMs: Long,
    ): Boolean = withTimeoutOrNull(timeoutMs) { attended.first { owner in it } } != null

    /** [owner] went away. */
    fun forget(owner: K) = set(owner, false)
}
