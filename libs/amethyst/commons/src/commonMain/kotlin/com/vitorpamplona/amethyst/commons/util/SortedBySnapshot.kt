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

/**
 * Sorts by a key computed ONCE per element, before any comparison runs.
 *
 * `sortedBy { it.liveProperty }` re-evaluates the key on every comparison, which is a crash — not a
 * cosmetic issue — when the key comes from mutable shared state such as a User's or channel's display
 * name. A relay event landing mid-sort changes the key underneath TimSort, it detects the
 * inconsistency, and throws `IllegalArgumentException: Comparison method violates its general
 * contract!`. It needs no user action and scales with list size: a directory of a thousand groups
 * trips it readily while a handful never does.
 *
 * Snapshotting first makes the comparator total and stable for the duration of the sort, whatever the
 * network does. The extra list is the price of not crashing.
 */
inline fun <T, R : Comparable<R>> Iterable<T>.sortedBySnapshot(key: (T) -> R): List<T> =
    map { it to key(it) }
        .sortedBy { it.second }
        .map { it.first }
