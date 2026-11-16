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
package com.vitorpamplona.amethyst.commons.defaults

/**
 * Substitute app defaults only when we have **never seen** the user's list — never when they
 * published an empty one.
 *
 * There are three states, and collapsing the last two is how the app ends up overriding an explicit
 * choice:
 *
 * | we have | effective list |
 * |---|---|
 * | no event | [defaults] — we do not know what they want |
 * | an event, empty list | **empty** — they told us: nothing |
 * | an event with relays | those relays |
 *
 * Written as one named primitive because the rule was open-coded at four call sites and three of
 * them got it wrong the same way: `event?.relays()?.ifEmpty { null } ?: DEFAULTS` reads naturally
 * but folds "published nothing" into "published nothing we know of", so a kind:10002 carrying only
 * write relays silently acquired a default *inbox* list.
 *
 * [read] may return null — several event accessors end in `.ifEmpty { null }` — and null from a
 * present event means the same thing as an empty set: the user listed nothing.
 *
 * **Not for partially-resolved sources.** A reader that can only see *already decrypted* private
 * tags returns empty both for "the user listed nothing" and for "we have not decrypted it yet",
 * which this cannot distinguish; those callers legitimately want defaults until the decrypt lands.
 */
inline fun <E : Any, T> relayListOrDefaultsWhenUnknown(
    event: E?,
    defaults: Set<T>,
    read: (E) -> Set<T>?,
): Set<T> = if (event == null) defaults else read(event) ?: emptySet()
