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

import kotlinx.collections.immutable.ImmutableList

/**
 * Compares two [ImmutableList] instances by element identity (`===`), not value equality (`==`).
 *
 * Returns `true` only when both lists have the same size and each element at the same index
 * is the exact same object reference.
 *
 * Useful for cheap change detection in UI/state pipelines where object identity is meaningful.
 */
fun <T> equalImmutableLists(
    list1: ImmutableList<T>,
    list2: ImmutableList<T>,
): Boolean {
    if (list1 === list2) return true
    if (list1.size != list2.size) return false
    for (i in 0 until list1.size) {
        if (list1[i] !== list2[i]) {
            return false
        }
    }
    return true
}
