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
package com.vitorpamplona.amethyst.commons.model.observables

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event

val CreatedAtIdHexComparator: Comparator<Note> =
    Comparator { note1, note2 ->
        // Sort by created_at and idhex, but define uniqueness by reference
        if (note1 === note2) return@Comparator 0

        val createdAt1 = note1.createdAt()
        val createdAt2 = note2.createdAt()

        if (createdAt1 == createdAt2) {
            val event1 = note1.event
            val event2 = note2.event

            if (event1 == null && event2 == null) return@Comparator 0
            if (event1 == null) return@Comparator 1 // null is greater, moves to last
            if (event2 == null) return@Comparator -1 // null is greater, moves to last

            event1.id.compareTo(event2.id)
        } else {
            // Descending, inverts
            compareValues(createdAt2, createdAt1)
        }
    }

private inline fun <T> compareByCreatedAt(
    a: T,
    b: T,
    eventOf: (T) -> Event?,
): Int {
    val firstEvent = eventOf(a)
    val secondEvent = eventOf(b)
    return when {
        firstEvent == null && secondEvent == null -> 0
        firstEvent == null -> 1
        secondEvent == null -> -1
        else -> firstEvent.createdAt.compareTo(secondEvent.createdAt)
    }
}

object CreatedAtComparator : Comparator<Note> {
    override fun compare(
        a: Note,
        b: Note,
    ): Int = compareByCreatedAt(a, b) { it.event }
}

object CreatedAtComparatorAddresses : Comparator<AddressableNote> {
    override fun compare(
        a: AddressableNote,
        b: AddressableNote,
    ): Int = compareByCreatedAt(a, b) { it.event }
}
