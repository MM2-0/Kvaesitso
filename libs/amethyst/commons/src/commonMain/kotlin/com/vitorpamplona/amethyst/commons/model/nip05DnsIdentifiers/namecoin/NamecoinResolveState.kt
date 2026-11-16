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
package com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin

import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNostrResult

/**
 * Observable state for a Namecoin resolution in progress.
 */
sealed class NamecoinResolveState {
    data object Loading : NamecoinResolveState()

    data class Resolved(
        val result: NamecoinNostrResult,
    ) : NamecoinResolveState()

    data object NotFound : NamecoinResolveState()

    data class Error(
        val reason: Reason,
    ) : NamecoinResolveState()

    /** Why a lookup failed, as a typed fact; the UI words it (and localizes it). */
    sealed interface Reason {
        data class NoNostrField(
            val name: String,
        ) : Reason

        /** [detail] is the parser's own message, kept verbatim so the record's publisher can find the bad byte. */
        data class MalformedRecord(
            val name: String,
            val detail: String,
        ) : Reason

        data object ServersUnreachable : Reason

        data object InvalidIdentifier : Reason

        data object Timeout : Reason

        /** An unexpected failure; [message] is the exception's own text. */
        data class Other(
            val message: String?,
        ) : Reason
    }
}
