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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.amethyst.commons.model.IAccount

/**
 * A subscription query state that belongs to one logged-in account.
 *
 * Around 66 query-state classes already carry an `account`, but nothing tied them together, so a
 * subscription manager could not ask "whose subscription is this?" without knowing the concrete
 * type. That is why the Active Relay Subscriptions screen filed the home feed under "not attributed"
 * despite it being built from one specific person's follow list: the base manager checked for
 * `AccountQueryState` and the home feed uses `HomeQueryState`.
 *
 * Implement this on any query state whose subscriptions belong to a single account, and the base
 * managers attribute them automatically.
 */
interface AccountScopedQuery {
    val account: IAccount
}
